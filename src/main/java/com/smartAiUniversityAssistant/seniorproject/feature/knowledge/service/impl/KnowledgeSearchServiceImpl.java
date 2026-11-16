package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.common.AcademicYearLabel;
import com.smartAiUniversityAssistant.seniorproject.config.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.QueryEmbeddingCache;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.math.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Semantic search (spec file 52). Each method has its own guard because the service serves two audiences: adminSearch
 * is limited to the Admin roles, studentSearch first resolves the signed-in Student. No transaction is open while the
 * embedding provider is called: the read-only transaction belongs to the vector store's search. The question, passages,
 * vectors and keys are never logged; only the query vector is cached, never results.
 * <p>
 * Step 15B (spec file 54): every passage of a search with a Student scope has an audience; with a quota and a program
 * in the scope a second statement reads the program's own documents and up to the quota places are reserved for them
 * (never below min-score), and the continuation of a hit's section in the same file is added as a neighbour. Without
 * these options the passages, order, ranks and scores are those of spec file 52.
 */
@Service
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchServiceImpl.class);
    public static final int MAX_TOP_K = 10;
    public static final int MAX_QUERY_CHARS = 500;

    private final EmbeddingClient client;
    private final KnowledgeVectorStore vectorStore;
    private final QueryEmbeddingCache cache;
    private final KnowledgeSearchProperties properties;
    private final KnowledgeCleaningProperties cleaning;
    private final CurrentStudentProvider students;

    public KnowledgeSearchServiceImpl(EmbeddingClient client, KnowledgeVectorStore vectorStore, QueryEmbeddingCache cache,
            KnowledgeSearchProperties properties, KnowledgeCleaningProperties cleaning, CurrentStudentProvider students) {
        this.client = client;
        this.vectorStore = vectorStore;
        this.cache = cache;
        this.properties = properties;
        this.cleaning = cleaning;
        this.students = students;
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    public KnowledgeSearchResponse adminSearch(AuthenticatedUser actor, KnowledgeSearchRequest request) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
        long started = System.nanoTime();
        String query = prepare(request.query(), cleaning.thaiDigitsToAscii());
        int topK = request.topK() == null ? properties.defaultTopK() : request.topK();
        double minScore = request.minScore() == null ? properties.minScore() : request.minScore();
        if (topK < 1 || topK > MAX_TOP_K || minScore < 0 || minScore > 1) throw KnowledgeFailure.invalid();
        String year = null;
        if (request.academicYear() != null) {
            year = AcademicYearLabel.normalize(request.academicYear());
            if (year == null) throw KnowledgeFailure.invalid();
        }
        positiveOrNull(request.categoryId());
        positiveOrNull(request.documentId());
        StudentKnowledgeScope scope = null;
        if (request.studentScope() != null) {
            positiveOrNull(request.studentScope().programId());
            positiveOrNull(request.studentScope().departmentId());
            scope = new StudentKnowledgeScope(request.studentScope().programId(), request.studentScope().departmentId());
        }
        int quota = request.ownProgramQuota() == null ? 0 : request.ownProgramQuota();
        int neighbors = request.maxNeighborPassages() == null ? 0 : request.maxNeighborPassages();
        if (quota < 0 || quota > 10 || neighbors < 0 || neighbors > 4) throw KnowledgeFailure.invalid();
        if (quota > 0 && (scope == null || scope.programId() == null)) throw KnowledgeFailure.invalid();
        var filter = new KnowledgeSearchFilter(scope, request.categoryId(), year, request.documentId());
        String audience = scope == null ? "ADMIN" : "STUDENT_PREVIEW";
        Outcome outcome = run(query, filter, topK, minScore, quota, neighbors, audience, actor.userId(), started);
        var results = outcome.results().stream().map(p -> {
            KnowledgeChunkMatch m = p.ranked().match();
            return new KnowledgeSearchResultResponse(p.ranked().rank(), p.ranked().score(), m.documentId(), m.title(),
                    new KnowledgeSearchResultResponse.Category(m.categoryId(), m.categoryCode(), m.categoryName()),
                    m.academicYearLabel(), scopeType(m), m.fileId(), m.revisionNumber(), m.chunkIndex(), m.sectionTitle(),
                    unitType(m.fileExtension()), m.pageStart(), m.pageEnd(), m.content(), p.audience(), p.neighbor());
        }).toList();
        return new KnowledgeSearchResponse(query, outcome.model(), topK, minScore, request.studentScope(), quota, neighbors,
                outcome.tookMs(), results);
    }

    @Override
    public List<KnowledgeSearchHit> studentSearch(String query, StudentKnowledgeScope scope, int topK) {
        students.getCurrentStudentId();
        if (topK < 1 || topK > MAX_TOP_K) throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K);
        return studentSearch(query, scope, StudentSearchOptions.plain(topK));
    }

    @Override
    public List<KnowledgeSearchHit> studentSearch(String query, StudentKnowledgeScope scope, StudentSearchOptions options) {
        students.getCurrentStudentId();
        if (scope == null) throw new IllegalArgumentException("A Student search needs a scope");
        if (options == null) throw new IllegalArgumentException("A Student search needs options");
        long started = System.nanoTime();
        String prepared = prepare(query, cleaning.thaiDigitsToAscii());
        Outcome outcome = run(prepared, new KnowledgeSearchFilter(scope, null, null, null), options.topK(), properties.minScore(),
                options.ownProgramQuota(), options.maxNeighborPassages(), "STUDENT", null, started);
        return outcome.results().stream().map(p -> {
            KnowledgeChunkMatch m = p.ranked().match();
            return new KnowledgeSearchHit(p.ranked().rank(), p.ranked().score(), m.documentId(), m.title(), m.categoryName(),
                    m.academicYearLabel(), m.sectionTitle(), unitType(m.fileExtension()), m.pageStart(), m.pageEnd(), m.content(),
                    p.audience(), p.neighbor());
        }).toList();
    }

    /** One result before it is mapped for an audience. */
    public record Ranked(int rank, double score, KnowledgeChunkMatch match) {}

    /** Step 15B: a hit or a neighbour (with the rank and score of its hit) and its audience. */
    public record Passage(Ranked ranked, KnowledgeAudience audience, boolean neighbor) {}

    private record Outcome(String model, long tookMs, List<Passage> results) {}

    private Outcome run(String query, KnowledgeSearchFilter filter, int topK, double minScore, int quota, int maxNeighbors,
            String audience, Long actorUserId, long started) {
        String actor = actorUserId == null ? "" : " actorUserId=" + actorUserId;
        if (!client.configured()) {
            log.warn("KNOWLEDGE_SEARCH audience={}{} failed code=KNOWLEDGE_SEARCH_NOT_CONFIGURED cause=EMBEDDING_NOT_CONFIGURED", audience, actor);
            throw KnowledgeFailure.searchNotConfigured();
        }
        String model = client.model();
        int dimensions = client.dimensions();
        long embedStarted = System.nanoTime();
        String cacheState = cache.enabled() ? "HIT" : "OFF";
        float[] vector = cache.get(model, dimensions, query).orElse(null);
        if (vector == null) {
            if (cache.enabled()) cacheState = "MISS";
            try {
                vector = client.embedQuery(query);
            } catch (EmbeddingException e) {
                log.warn("KNOWLEDGE_SEARCH audience={}{} failed code={} cause={}", audience, actor, searchCode(e.code()), e.code());
                throw switch (e.code()) {
                    case "EMBEDDING_NOT_CONFIGURED" -> KnowledgeFailure.searchNotConfigured();
                    case "EMBEDDING_RATE_LIMITED" -> KnowledgeFailure.searchRateLimited();
                    default -> KnowledgeFailure.searchUnavailable();
                };
            }
            cache.put(model, dimensions, query, vector);
        }
        long embedMs = (System.nanoTime() - embedStarted) / 1_000_000;
        if ("HIT".equals(cacheState)) embedMs = 0;
        long dbStarted = System.nanoTime();
        StudentKnowledgeScope scope = filter.studentScope();
        boolean reserve = quota > 0 && scope != null && scope.programId() != null;
        int maxPerDocument = filter.documentId() == null ? properties.maxPerDocument() : Integer.MAX_VALUE;
        List<KnowledgeChunkMatch> candidates;
        List<KnowledgeChunkMatch> programCandidates = null;
        List<Ranked> hits;
        List<KnowledgeChunkMatch> around = List.of();
        try {
            candidates = vectorStore.search(vector, model, filter, candidateLimit(topK));
            if (reserve) programCandidates = vectorStore.search(vector, model, filter.programDocuments(scope.programId()), candidateLimit(topK));
            hits = reserve ? selectWithReserve(candidates, programCandidates, topK, minScore, maxPerDocument, quota, scope)
                    : select(candidates, topK, minScore, maxPerDocument);
            List<KnowledgeChunkPosition> positions = maxNeighbors > 0 ? neighborPositions(hits) : List.of();
            if (!positions.isEmpty()) around = vectorStore.findChunks(model, positions);
        } catch (DataAccessException e) {
            log.warn("KNOWLEDGE_SEARCH audience={}{} failed code=KNOWLEDGE_SEARCH_UNAVAILABLE cause={}", audience, actor, e.getClass().getSimpleName());
            throw KnowledgeFailure.searchUnavailable();
        }
        long dbMs = (System.nanoTime() - dbStarted) / 1_000_000;
        List<Passage> results = withNeighbors(hits, around, maxNeighbors, scope);
        long reserved = reserve ? hits.stream().filter(r -> audience(r.match(), scope) == KnowledgeAudience.PROGRAM).count() : 0;
        long neighbors = results.stream().filter(Passage::neighbor).count();
        long tookMs = (System.nanoTime() - started) / 1_000_000;
        String topScore = candidates.isEmpty() ? "-" : String.format(Locale.ROOT, "%.4f", score(candidates.get(0).distance()));
        log.info("KNOWLEDGE_SEARCH audience={}{} queryChars={} cache={} candidates={} programCandidates={} returned={} programReserved={} "
                + "neighbors={} topScore={} embedMs={} dbMs={} totalMs={}", audience, actor, query.length(), cacheState, candidates.size(),
                programCandidates == null ? "-" : programCandidates.size(), hits.size(), reserved, neighbors, topScore, embedMs, dbMs, tookMs);
        return new Outcome(model, tookMs, results);
    }

    /** Section 6 of spec file 54: whom the passage applies to, seen from the scope; null without a scope. */
    public static KnowledgeAudience audience(KnowledgeChunkMatch m, StudentKnowledgeScope scope) {
        if (scope == null) return null;
        if (scope.programId() != null && (scope.programId().equals(m.programId()) || scope.programId().equals(m.curriculumProgramId())))
            return KnowledgeAudience.PROGRAM;
        if (m.departmentId() != null) return KnowledgeAudience.DEPARTMENT;
        return KnowledgeAudience.UNIVERSITY;
    }

    /**
     * Spec file 54, section 7.1: both candidate lists merged (each chunk once, by distance then chunk id), the threshold,
     * A. up to min(quota, topK) passages of the Student's own program, B. filled up to topK, C. sorted by distance and
     * chunk id again and ranked. max-per-document holds in A and B.
     */
    public static List<Ranked> selectWithReserve(List<KnowledgeChunkMatch> general, List<KnowledgeChunkMatch> program, int topK,
            double minScore, int maxPerDocument, int quota, StudentKnowledgeScope scope) {
        var byChunk = new LinkedHashMap<Long, KnowledgeChunkMatch>();
        for (KnowledgeChunkMatch m : general) byChunk.putIfAbsent(m.chunkId(), m);
        for (KnowledgeChunkMatch m : program == null ? List.<KnowledgeChunkMatch>of() : program) byChunk.putIfAbsent(m.chunkId(), m);
        var merged = byChunk.values().stream().filter(m -> score(m.distance()) >= minScore)
                .sorted(Comparator.comparingDouble(KnowledgeChunkMatch::distance).thenComparingLong(KnowledgeChunkMatch::chunkId)).toList();
        var kept = new ArrayList<KnowledgeChunkMatch>();
        var perDocument = new HashMap<Long, Integer>();
        int reserve = Math.min(quota, topK);
        for (KnowledgeChunkMatch m : merged) {
            if (kept.size() >= reserve) break;
            if (audience(m, scope) != KnowledgeAudience.PROGRAM) continue;
            if (perDocument.getOrDefault(m.documentId(), 0) >= maxPerDocument) continue;
            perDocument.merge(m.documentId(), 1, Integer::sum);
            kept.add(m);
        }
        for (KnowledgeChunkMatch m : merged) {
            if (kept.size() >= topK) break;
            if (kept.contains(m)) continue;
            if (perDocument.getOrDefault(m.documentId(), 0) >= maxPerDocument) continue;
            perDocument.merge(m.documentId(), 1, Integer::sum);
            kept.add(m);
        }
        kept.sort(Comparator.comparingDouble(KnowledgeChunkMatch::distance).thenComparingLong(KnowledgeChunkMatch::chunkId));
        var results = new ArrayList<Ranked>(kept.size());
        for (KnowledgeChunkMatch m : kept)
            results.add(new Ranked(results.size() + 1, BigDecimal.valueOf(score(m.distance())).setScale(4, RoundingMode.HALF_UP).doubleValue(), m));
        return results;
    }

    // The chunk before and after every hit that has a section title, in the hit's file revision.
    static List<KnowledgeChunkPosition> neighborPositions(List<Ranked> hits) {
        var positions = new LinkedHashSet<KnowledgeChunkPosition>();
        for (Ranked hit : hits) {
            if (hit.match().sectionTitle() == null) continue;
            if (hit.match().chunkIndex() > 0) positions.add(new KnowledgeChunkPosition(hit.match().fileId(), hit.match().chunkIndex() - 1));
            positions.add(new KnowledgeChunkPosition(hit.match().fileId(), hit.match().chunkIndex() + 1));
        }
        return List.copyOf(positions);
    }

    /**
     * Spec file 54, section 8.1: for every hit in rank order, the eligible chunk before and then after it with exactly the
     * same section title, while the budget lasts and when it is neither a hit nor already added. A neighbour has the rank
     * and score of its hit and stands directly before or after it.
     */
    public static List<Passage> withNeighbors(List<Ranked> hits, List<KnowledgeChunkMatch> around, int maxNeighbors,
            StudentKnowledgeScope scope) {
        var eligible = new HashMap<KnowledgeChunkPosition, KnowledgeChunkMatch>();
        for (KnowledgeChunkMatch m : around == null ? List.<KnowledgeChunkMatch>of() : around)
            eligible.put(new KnowledgeChunkPosition(m.fileId(), m.chunkIndex()), m);
        var taken = new HashSet<KnowledgeChunkPosition>();
        for (Ranked hit : hits) taken.add(new KnowledgeChunkPosition(hit.match().fileId(), hit.match().chunkIndex()));
        var before = new HashMap<Ranked, KnowledgeChunkMatch>();
        var after = new HashMap<Ranked, KnowledgeChunkMatch>();
        int budget = maxNeighbors;
        for (Ranked hit : hits) {
            if (budget <= 0) break;
            String section = hit.match().sectionTitle();
            if (section == null) continue;
            for (int offset : new int[] {-1, 1}) {
                if (budget <= 0) break;
                var position = new KnowledgeChunkPosition(hit.match().fileId(), hit.match().chunkIndex() + offset);
                KnowledgeChunkMatch neighbour = eligible.get(position);
                if (neighbour == null || !section.equals(neighbour.sectionTitle()) || taken.contains(position)) continue;
                taken.add(position);
                (offset < 0 ? before : after).put(hit, neighbour);
                budget--;
            }
        }
        var out = new ArrayList<Passage>();
        for (Ranked hit : hits) {
            if (before.containsKey(hit))
                out.add(new Passage(new Ranked(hit.rank(), hit.score(), before.get(hit)), audience(before.get(hit), scope), true));
            out.add(new Passage(hit, audience(hit.match(), scope), false));
            if (after.containsKey(hit))
                out.add(new Passage(new Ranked(hit.rank(), hit.score(), after.get(hit)), audience(after.get(hit), scope), true));
        }
        return out;
    }

    private static String searchCode(String embeddingCode) {
        return switch (embeddingCode) {
            case "EMBEDDING_NOT_CONFIGURED" -> "KNOWLEDGE_SEARCH_NOT_CONFIGURED";
            case "EMBEDDING_RATE_LIMITED" -> "KNOWLEDGE_SEARCH_RATE_LIMITED";
            default -> "KNOWLEDGE_SEARCH_UNAVAILABLE";
        };
    }

    /** Section 6: the same normalization as the CLEAN stage, line breaks to spaces, single spaces, trimmed, 1 to 500 chars. */
    public static String prepare(String query, boolean thaiDigitsToAscii) {
        if (query == null || query.isBlank() || query.length() > MAX_QUERY_CHARS) throw KnowledgeFailure.invalid();
        String q = KnowledgeTextNormalizer.normalize(query, thaiDigitsToAscii).replace('\n', ' ').replaceAll(" {2,}", " ").strip();
        if (q.isEmpty() || q.length() > MAX_QUERY_CHARS) throw KnowledgeFailure.invalid();
        return q;
    }

    public static int candidateLimit(int topK) { return Math.min(50, Math.max(20, topK * 4)); }

    /** Cosine similarity of two L2-normalized vectors, limited to −1 … 1. */
    static double score(double distance) { return Math.max(-1, Math.min(1, 1 - distance)); }

    /** Section 11: threshold, at most maxPerDocument per document, topK, ranks; rounding after the comparison. */
    public static List<Ranked> select(List<KnowledgeChunkMatch> candidates, int topK, double minScore, int maxPerDocument) {
        var perDocument = new HashMap<Long, Integer>();
        var results = new ArrayList<Ranked>();
        for (KnowledgeChunkMatch match : candidates) {
            double score = score(match.distance());
            if (score < minScore) continue;
            int count = perDocument.getOrDefault(match.documentId(), 0);
            if (count >= maxPerDocument) continue;
            perDocument.put(match.documentId(), count + 1);
            results.add(new Ranked(results.size() + 1, BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP).doubleValue(), match));
            if (results.size() == topK) break;
        }
        return results;
    }

    /** What pageStart and pageEnd count, derived from the file extension. */
    public static String unitType(String extension) {
        return switch (extension == null ? "" : extension) {
            case "pdf" -> "PAGE";
            case "docx" -> "SECTION";
            case "xlsx" -> "SHEET";
            default -> "TEXT";
        };
    }

    /** CURRICULUM, PROGRAM, DEPARTMENT or UNIVERSITY, derived as in KnowledgeDocumentMapper. */
    public static String scopeType(KnowledgeChunkMatch match) {
        if (match.curriculumVersionId() != null) return "CURRICULUM";
        if (match.programId() != null) return "PROGRAM";
        if (match.departmentId() != null) return "DEPARTMENT";
        return "UNIVERSITY";
    }

    private static void positiveOrNull(Long id) {
        if (id != null && id <= 0) throw KnowledgeFailure.invalid();
    }
}
