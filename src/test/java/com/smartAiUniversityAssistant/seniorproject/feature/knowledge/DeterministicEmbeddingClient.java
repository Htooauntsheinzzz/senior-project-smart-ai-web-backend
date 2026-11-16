package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.EmbeddingException;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Test-only EmbeddingClient (spec file 50, section 13): no network, the same text always gives the same 768-number unit
 * vector, and texts that share three-character sequences are closer than unrelated ones (also for Thai and Myanmar). It
 * is not a semantic model. Switches let tests fail a chosen call, change the model id and count calls and texts.
 */
public class DeterministicEmbeddingClient implements EmbeddingClient {
    public static final String MODEL = "test-embedding-768";
    private volatile String model = MODEL;
    private volatile boolean configured = true;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger documentCalls = new AtomicInteger();
    private final AtomicInteger queryCalls = new AtomicInteger();
    private final AtomicInteger texts = new AtomicInteger();
    private final List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, EmbeddingException> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile EmbeddingException failAlways;
    private volatile Consumer<String> onCall = text -> {};

    public static float[] vector(String text) {
        String t = KnowledgeTextNormalizer.normalize(text).toLowerCase(Locale.ROOT).replaceAll(" {2,}", " ");
        float[] v = new float[768];
        int[] cps = t.codePoints().toArray();
        if (cps.length < 3) add(v, new String(cps, 0, cps.length));
        else for (int i = 0; i + 3 <= cps.length; i++) add(v, new String(cps, i, 3));
        boolean zero = true;
        for (float x : v) if (x != 0) { zero = false; break; }
        if (zero) v[0] = 1;
        double sum = 0;
        for (float x : v) sum += (double) x * x;
        double norm = Math.sqrt(sum);
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        return v;
    }

    private static void add(float[] v, String window) {
        int h = window.hashCode();
        v[Math.floorMod(h, 768)] += ((h >>> 16) & 1) == 0 ? 1 : -1;
    }

    @Override public boolean configured() { return configured; }
    @Override public String model() { return model; }
    @Override public int dimensions() { return 768; }

    @Override
    public List<float[]> embedDocuments(List<String> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.stream().anyMatch(s -> s == null || s.isBlank())) throw new IllegalArgumentException();
        documentCalls.incrementAndGet();
        call(inputs);
        return inputs.stream().map(DeterministicEmbeddingClient::vector).toList();
    }

    @Override
    public float[] embedQuery(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException();
        queryCalls.incrementAndGet();
        call(List.of(text));
        return vector(text);
    }

    private void call(List<String> inputs) {
        if (!configured) throw new EmbeddingException("EMBEDDING_NOT_CONFIGURED");
        int n = calls.incrementAndGet();
        inputs.forEach(onCall);
        if (failAlways != null) throw failAlways;
        EmbeddingException failure = failures.remove(n);
        if (failure != null) throw failure;
        texts.addAndGet(inputs.size());
        batchSizes.add(inputs.size());
    }

    public DeterministicEmbeddingClient failCall(int n, String code) { failures.put(n, new EmbeddingException(code)); return this; }
    public DeterministicEmbeddingClient failAlways(String code) { failAlways = code == null ? null : new EmbeddingException(code); return this; }
    public DeterministicEmbeddingClient model(String model) { this.model = model; return this; }
    public DeterministicEmbeddingClient configured(boolean configured) { this.configured = configured; return this; }
    public DeterministicEmbeddingClient onCall(Consumer<String> onCall) { this.onCall = onCall; return this; }
    public int calls() { return calls.get(); }
    public int documentCalls() { return documentCalls.get(); }
    public int queryCalls() { return queryCalls.get(); }
    public int texts() { return texts.get(); }
    public List<Integer> batchSizes() { return List.copyOf(batchSizes); }
    public void reset() {
        model = MODEL; configured = true; failAlways = null; failures.clear(); onCall = text -> {};
        calls.set(0); documentCalls.set(0); queryCalls.set(0); texts.set(0); batchSizes.clear();
    }
}
