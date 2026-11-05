# 40 — Step 13: Curriculum and AI Knowledge Document Upload Backend Implementation

**Project:** Smart AI University Student Assistant  
**Module:** Admin AI Management / Curriculum Management  
**Specification:** `spec-md/40-step-13-curriculum-document-upload-api.md`  
**Stack:** Java 21, Spring Boot 4.x, Maven, PostgreSQL, Redis 8, Spring Security JWT RS256  
**Prerequisites:** Step 12A and Step 12B completed

---

## 1. Objective

Implement the backend for the Figma screen:

**AI Management → AI Knowledge Base → Upload Knowledge Document**

This feature enables authorized Admin users to upload and manage official university documents intended for future AI-assisted retrieval.

Supported documents include:

```text
Curriculum PDF
Student Handbook
Program Handbook
Course Syllabus
Academic Regulations
Graduation Requirements
Internship Rules
Course Catalog
Scholarship Policies
Academic Calendar
University Policies
Faculty Guidelines
Financial Aid
Campus Safety
```

The uploaded documents must be stored securely and associated with normalized PostgreSQL metadata.

The feature prepares documents for future RAG ingestion.

It must not claim uploaded content is already indexed or available to Gemini unless actual ingestion has succeeded.

---

## 2. Figma Design Reference

Reference:

`https://www.figma.com/make/lkglqHgBiQlUJlterhHIsp/Smart-AI-University-Student-Assistant-Figma-Make--Copy---Copy-`

The provided screenshot shows:

### Main AI Knowledge Base Page

```text
AI Knowledge Base
Manage official university information used by AI Student Assistant

Statistics:
- Total Documents
- Ready
- Processing
- Failed

Filters:
- Search Documents
- Categories
- Academic Years
- Processing Status

Table:
- Document Name
- Category
- Academic Year
- Type
- Uploaded By
- Date
- AI Status
- Actions
```

### Upload Knowledge Document Modal

```text
Document Title *

Category *

Academic Year *

Description

Document File *

Supported formats:
PDF, DOCX, TXT, XLSX

Maximum size:
50 MB

Buttons:
Cancel
Upload & Process
```

The backend must support these exact functional fields.

The UI can retain its existing design; frontend integration is a separate implementation task.

---

# Part A — Backend Architecture

## 3. Required Architecture

```text
Admin Web
    |
    | Multipart Document Upload
    v
Spring Security / Admin JWT
    |
    v
AdminKnowledgeDocumentController
    |
    v
KnowledgeDocumentService
    |
    +---- Document Validation
    |
    +---- Scope / Category Validation
    |
    +---- File Security Validation
    |
    +---- FileStorageService
    |           |
    |           v
    |      Protected Storage
    |
    +---- AcademicDocumentRepository
    |
    +---- AcademicDocumentFileRepository
    |
    +---- DocumentProcessingRepository
                |
                v
            PostgreSQL
                |
                v
       UPLOADED / QUEUED
                |
                v
      Future Ingestion Worker
                |
                v
      Extraction → Chunking
                |
                v
      Embedding → Indexing
                |
                v
              READY
```

PostgreSQL stores metadata.

The original file is stored in protected file/object storage.

Redis is not the permanent file storage system.

---

## 4. Mandatory Coding Agent Instructions

Before implementation:

1. Read `AGENTS.md`.
2. Inspect the existing `spec-md/` directory.
3. Verify Step 12 Flyway migrations succeeded.
4. Inspect the current Admin security implementation.
5. Inspect the existing exception handling and response wrappers.
6. Inspect existing Docker Compose volume configuration.
7. Inspect existing file-upload utilities, if any.
8. Follow the established feature package conventions.
9. Do not edit unrelated Academic Management modules.
10. Implement the feature with tests and build verification.

Do not:

- Create another database.
- Store original files directly in Redis.
- Store large document binary content inside PostgreSQL.
- Place uploaded files in the public web directory.
- Give Gemini access to uploaded files directly.
- Trust filename extensions alone.
- Allow user-supplied filesystem paths.
- Hard-code the upload location.
- Automatically mark a document READY before processing completes.
- Add permanent test documents through Flyway.
- Modify existing applied migrations.

---

# Part B — Document Upload API

## 5. Upload Endpoint

Implement:

```http
POST /api/v1/admin/knowledge/documents/upload
```

Request type:

```http
Content-Type: multipart/form-data
```

Fields:

```text
title
categoryId
academicYearLabel
description
curriculumVersionId
programId
departmentId
file
```

Required fields:

```text
title
categoryId
academicYearLabel
file
```

Optional:

```text
description
curriculumVersionId
programId
departmentId
```

The scope fields are mutually exclusive according to the Step 12A schema.

---

## 6. Example Postman Form Data

| Key | Type | Example |
|---|---|---|
| title | Text | Student Handbook 2024-2025 |
| categoryId | Text | 1 |
| academicYearLabel | Text | 2024-2025 |
| description | Text | Official Student Handbook |
| curriculumVersionId | Text | Empty |
| programId | Text | Empty |
| departmentId | Text | Empty |
| file | File | student-handbook.pdf |

Request:

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/upload
Authorization: Bearer <admin-access-token>
Content-Type: multipart/form-data
```

The file must be transmitted as a multipart upload, not base64 JSON.

---

## 7. Upload Request DTO

Recommended:

```java
public record KnowledgeDocumentUploadRequest(

    @NotBlank
    @Size(max = 255)
    String title,

    @NotNull
    Long categoryId,

    @NotBlank
    String academicYearLabel,

    String description,

    Long curriculumVersionId,

    Long programId,

    Long departmentId

) {}
```

Accept the file separately as a Spring `MultipartFile`.

Use `@ModelAttribute` or the project's established multipart binding approach.

---

## 8. Controller Example

Conceptual:

```java
@RestController
@RequestMapping("/api/v1/admin/knowledge/documents")
public class AdminKnowledgeDocumentController {

    private final KnowledgeDocumentService service;

    public AdminKnowledgeDocumentController(
            KnowledgeDocumentService service) {
        this.service = service;
    }

    @PostMapping(
        value = "/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> upload(
            @Valid @ModelAttribute
            KnowledgeDocumentUploadRequest request,

            @RequestPart("file")
            MultipartFile file) {

        var result = service.upload(request, file);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }
}
```

Adapt to the existing API response wrapper and ensure multipart validation works.

The controller must not directly write files or perform database persistence.

---

## 9. Successful Upload Response

Use HTTP 201.

Example:

```json
{
  "success": true,
  "message": "Knowledge document uploaded successfully",
  "data": {
    "documentId": 31,
    "fileId": 81,
    "title": "Student Handbook 2024-2025",
    "categoryId": 1,
    "academicYearLabel": "2024-2025",
    "filename": "student-handbook.pdf",
    "revisionNumber": 1,
    "sizeBytes": 1843000,
    "processingStatus": "UPLOADED",
    "isActive": true
  }
}
```

The response must not include an arbitrary internal filesystem path.

`UPLOADED` is the correct state when the physical upload and metadata commit succeed but ingestion has not yet started.

---

# Part C — File Validation

## 10. Supported File Formats

The Figma design supports:

```text
PDF
DOCX
TXT
XLSX
```

Initial allowed MIME types:

| Extension | Expected media type |
|---|---|
| `.pdf` | `application/pdf` |
| `.docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| `.txt` | `text/plain` |
| `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |

Do not support arbitrary executable files, HTML uploads, JavaScript, ZIP archives, or macro-enabled Office files.

Extension, MIME header, and detected file content must be validated together.

---

## 11. Maximum File Size

Maximum:

```text
50 MB
```

Set explicit multipart limits.

Example:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 52MB
```

Make sure the reverse proxy and Docker deployment configuration also permit the intended upload size.

Do not load the entire file into memory unnecessarily.

Enforce a hard byte limit during server-side handling, not only through the `Content-Length` header.

---

## 12. File Security Rules

Validate:

```text
File is not empty.

File size is within limits.

Extension is allowed.

Detected content matches expected format.

Filename is sanitized for display.

Backend generates the storage key.

No executable content is allowed.

No path traversal is possible.

No archive decompression bomb is accepted.
```

For DOCX/XLSX, enforce reasonable limits on ZIP entry count, compressed-to-uncompressed ratio, and total expanded size when parsing later.

Consider malware scanning before accepting files as usable for processing. If a scanner is unavailable, keep documents quarantined/unavailable to ingestion until the configured security policy is satisfied.

Never execute macros or embedded code.

---

# Part D — Storage Architecture

## 13. Recommended File Storage

For your development and initial UAT setup, use an abstraction over a protected Docker-mounted directory.

Example container path:

```text
/app/storage/academic-documents
```

The real host path must come from Docker Compose volume configuration.

The backend must not assume its working directory is persistent.

Later, the storage implementation may be replaced with S3-compatible object storage without changing the document service.

---

## 14. Storage Configuration

Recommended:

```env
KNOWLEDGE_STORAGE_TYPE=local

KNOWLEDGE_STORAGE_ROOT=/app/storage/academic-documents

KNOWLEDGE_MAX_FILE_SIZE_MB=50
```

For local development outside Docker, use a configured project-external storage path.

Do not store files under:

```text
src/main/resources/static
```

or:

```text
public/
```

---

## 15. FileStorageService

Create:

```java
public interface FileStorageService {

    StoredFile store(
        MultipartFile file
    );

    InputStream open(
        String storageKey
    );

    void delete(
        String storageKey
    );

}
```

Use a backend-generated opaque storage key.

Example:

```text
knowledge/2026/11/uuid-generated-name.pdf
```

The internal storage layout is an implementation detail.

Never concatenate the original client filename into a trusted path.

---

## 16. Secure Storage Requirements

- Store files outside public web access.
- Restrict filesystem permissions.
- Do not expose raw storage paths.
- Validate the resolved local path remains inside the configured storage root.
- Prevent symlink/path-traversal abuse.
- Store only sanitized metadata.
- Protect backups and UAT file copies.
- Make storage writes idempotent where practical.
- Use unique keys for separate revisions.
- Verify checksum after storage.

---

# Part E — Document Upload Service

## 17. KnowledgeDocumentService

Recommended:

```java
public interface KnowledgeDocumentService {

    KnowledgeDocumentUploadResponse upload(
        KnowledgeDocumentUploadRequest request,
        MultipartFile file
    );

    KnowledgeDocumentResponse getById(Long id);

    Page<KnowledgeDocumentResponse> search(
        KnowledgeDocumentSearchCriteria criteria,
        Pageable pageable
    );

    KnowledgeDocumentResponse updateMetadata(
        Long id,
        KnowledgeDocumentUpdateRequest request
    );

    KnowledgeDocumentResponse archive(Long id);

    KnowledgeDocumentResponse activate(Long id);

}
```

Reuse Step 12B service methods where they already exist.

Do not duplicate CRUD functionality.

---

## 18. Upload Workflow

Implement:

```text
1. Authenticate Admin.

2. Validate Admin permission.

3. Validate title and academic year.

4. Validate category.

5. Validate curriculum/program/department scope.

6. Validate uploaded file.

7. Generate safe storage key.

8. Store file in protected storage.

9. Calculate SHA-256.

10. Create academic_documents record.

11. Create academic_document_files revision 1.

12. Create academic_document_processing record.

13. Set status = UPLOADED.

14. Commit PostgreSQL metadata.

15. Return documentId and fileId.
```

The file-storage operation and PostgreSQL transaction are not one atomic transaction.

Implement cleanup/compensation when metadata persistence fails after the file has been stored, and reconcile any orphaned files caused by crashes.

Do not claim successful upload if required metadata was not persisted.

---

## 19. Category Validation

Before uploading:

```text
document_categories.id must exist

is_active = TRUE

is_deleted = FALSE
```

Reject invalid categories.

Do not allow arbitrary category names to be submitted as replacements for a valid category ID.

---

## 20. Academic Scope Validation

Validate the document's scope:

```text
curriculumVersionId
OR
programId
OR
departmentId
OR
none (university-wide)
```

Examples:

### Curriculum PDF

```text
curriculumVersionId = 10
programId = null
departmentId = null
```

### Program Handbook without curriculum version

```text
programId = 5
curriculumVersionId = null
departmentId = null
```

### Department Guidelines

```text
departmentId = 3
curriculumVersionId = null
programId = null
```

### University Regulations

```text
All scope fields = null
```

Ensure referenced entities are valid and non-deleted.

The service must not allow the document scope to contradict an associated curriculum version.

---

# Part F — File Revision Management

## 21. Replace Document File

Implement:

```http
POST /api/v1/admin/knowledge/documents/{id}/files
```

Content type:

```http
multipart/form-data
```

Field:

```text
file
```

Purpose:

```text
Replace the current document file
while preserving document metadata and history.
```

---

## 22. Revision Workflow

Example:

```text
Document ID:
31

Existing File:
Revision 1

New Upload:
Revision 2
```

Expected:

```text
Revision 1:
is_current = FALSE

Revision 2:
is_current = TRUE
```

New revision processing status:

```text
UPLOADED
```

Do not inherit the previous revision's `READY` status.

Use a transaction, document-level locking, and the current-file unique constraint to prevent concurrent replacements from creating two current revisions.

If the replacement fails, keep the previous current revision intact.

---

## 23. Revision List

Implement:

```http
GET /api/v1/admin/knowledge/documents/{id}/files
```

Return revision metadata:

```json
[
  {
    "fileId": 81,
    "revisionNumber": 1,
    "filename": "handbook-v1.pdf",
    "isCurrent": false,
    "processingStatus": "READY"
  },
  {
    "fileId": 96,
    "revisionNumber": 2,
    "filename": "handbook-v2.pdf",
    "isCurrent": true,
    "processingStatus": "UPLOADED"
  }
]
```

Do not expose internal storage keys to ordinary Admin UI listing responses.

---

# Part G — Document Access

## 24. Download Endpoint

Implement:

```http
GET /api/v1/admin/knowledge/documents/{id}/download
```

The backend must:

```text
Authenticate Admin

Authorize document access

Resolve current file

Read protected storage

Return file with safe headers
```

Use `Content-Disposition: attachment` for normal download.

Set appropriate content type and prevent MIME sniffing.

Do not expose raw filesystem/object-storage paths.

---

## 25. Download Previous Revision

Optional but recommended:

```http
GET /api/v1/admin/knowledge/documents/{id}/files/{fileId}/download
```

Validate that `fileId` belongs to `documentId`.

Do not allow downloads of another document's revision through parameter substitution.

---

# Part H — Processing Status

## 26. Initial Processing Status

When upload completes:

```text
UPLOADED
```

Do not immediately set:

```text
READY
```

unless the complete extraction, chunking, embedding, and indexing pipeline has actually completed.

---

## 27. Processing Workflow

Design the backend to support:

```text
UPLOADED
    ↓
QUEUED
    ↓
PROCESSING
    ↓
CHUNKING
    ↓
EMBEDDING
    ↓
READY
```

Failures:

```text
FAILED
```

A future ingestion worker will own the actual stage transitions.

If that worker is not yet implemented, retain `UPLOADED` and clearly communicate that the document is awaiting processing.

---

## 28. Processing Status API

Implement:

```http
GET /api/v1/admin/knowledge/documents/{id}/processing
```

Example:

```json
{
  "documentId": 31,
  "fileId": 81,
  "status": "UPLOADED",
  "attemptCount": 0,
  "startedAt": null,
  "completedAt": null,
  "lastErrorCode": null
}
```

Never return internal stack traces as processing errors.

---

## 29. Retry Processing

Recommended endpoint:

```http
POST /api/v1/admin/knowledge/documents/{id}/reprocess
```

This endpoint should:

1. Validate Admin permission.
2. Validate current file exists.
3. Check processing status.
4. Ensure the actual ingestion worker is configured.
5. Queue the file for reprocessing.
6. Return accepted status.

If ingestion has not yet been implemented, do not expose a falsely functional retry endpoint. It may be reserved for the later ingestion step.

Use HTTP 202 when the processing job is successfully queued.

Prevent duplicate active jobs for the same file revision.

---

# Part I — Figma AI Knowledge Base Integration

## 30. Main Screen Data

The Figma screen needs:

```text
Total Documents
Ready
Processing
Failed
```

Use the existing Step 12B endpoint:

```http
GET /api/v1/admin/knowledge/documents/statistics
```

The screen must display actual derived counts, not static demonstration values.

---

## 31. Document Table

Use:

```http
GET /api/v1/admin/knowledge/documents
```

Response data should populate:

```text
Document Name
Category
Academic Year
File Type
Uploaded By
Upload Date
Processing Status
Actions
```

Processing badge mapping:

| Backend status | UI badge |
|---|---|
| UPLOADED | Uploaded / Processing |
| QUEUED | Processing |
| PROCESSING | Processing |
| CHUNKING | Chunking |
| EMBEDDING | Embedding |
| READY | Ready |
| FAILED | Failed |

Use the current file revision's status.

---

## 32. Upload Modal Mapping

| Figma field | Backend field |
|---|---|
| Document Title | `title` |
| Category | `categoryId` |
| Academic Year | `academicYearLabel` |
| Description | `description` |
| Document File | `MultipartFile file` |
| Upload & Process | Multipart upload endpoint |

Additional metadata available to Curriculum Management:

```text
curriculumVersionId
programId
departmentId
```

These scope fields may be added to a dedicated Curriculum upload screen or passed by the parent Curriculum detail page.

Do not assume every Knowledge Base document has a Program or Curriculum.

---

# Part J — Security and Authorization

## 33. Admin Authentication

Use the existing Admin JWT authentication.

Protect:

```text
/api/v1/admin/knowledge/**
```

Only authorized roles can:

```text
Upload documents

Replace files

Edit metadata

Archive documents

Activate documents

Download restricted files

Request reprocessing
```

The exact role permissions should follow the current project security rules.

---

## 34. Audit Information

Get:

```text
uploaded_by

created_by

updated_by
```

from the authenticated Admin.

Do not accept these fields from frontend requests.

Store timestamps using the project's standard time handling, and format them for Asia/Bangkok where appropriate at the API/UI boundary.

---

## 35. RAG Security Preparation

Document content must not be trusted as system instructions.

A curriculum document could contain unexpected or malicious text.

Future ingestion/retrieval must:

```text
Treat document text as data.

Preserve document provenance.

Restrict retrieval to authorized document scope.

Exclude inactive or deleted documents.

Exclude non-READY file revisions.

Never execute instructions found inside documents.
```

Do not give Gemini arbitrary access to storage paths or uploaded binary files.

---

# Part K — Error Handling

## 36. Required Error Codes

Implement or reuse:

```text
DOCUMENT_UPLOAD_VALIDATION_ERROR
DOCUMENT_FILE_REQUIRED
DOCUMENT_FILE_EMPTY
DOCUMENT_FILE_TOO_LARGE
DOCUMENT_FILE_TYPE_UNSUPPORTED
DOCUMENT_FILE_CONTENT_INVALID
DOCUMENT_STORAGE_FAILED
DOCUMENT_METADATA_SAVE_FAILED
DOCUMENT_NOT_FOUND
DOCUMENT_FILE_NOT_FOUND
DOCUMENT_CATEGORY_INVALID
DOCUMENT_SCOPE_INVALID
DOCUMENT_REVISION_CONFLICT
DOCUMENT_PROCESSING_NOT_AVAILABLE
DOCUMENT_PROCESSING_ALREADY_RUNNING
DOCUMENT_PROCESSING_FAILED
```

Return consistent error responses.

Recommended HTTP mappings:

| Error | HTTP |
|---|---:|
| Validation | 400 |
| Missing JWT | 401 |
| Forbidden Admin action | 403 |
| Document not found | 404 |
| File too large | 413 |
| Unsupported type | 415 |
| Revision conflict | 409 |
| Storage failure | 503 |
| Upload success | 201 |
| Accepted processing job | 202 |

---

# Part L — Testing

## 37. Required Unit Tests

Test:

```text
Valid PDF upload

Valid DOCX upload

Valid TXT upload

Valid XLSX upload

Empty file rejected

Unsupported extension rejected

Spoofed MIME type rejected

Oversized file rejected

Invalid category rejected

Invalid scope rejected

Unauthorized Admin rejected

Storage failure handled

Metadata failure compensated

Checksum generated correctly

File revision numbering correct

Current revision unique

Processing status initially UPLOADED

UploadedBy derived from JWT

File path cannot escape storage root
```

---

## 38. Required Integration Tests

Use PostgreSQL/Testcontainers and controlled temporary storage.

Verify:

```text
Upload inserts academic_documents.

Upload inserts academic_document_files.

Upload inserts academic_document_processing.

Document relationship is correct.

Original file exists in protected storage.

Metadata matches physical file.

Failed transaction does not leave falsely successful metadata.

Replacement retains prior revisions.

Current revision uniqueness is enforced.

Archive does not physically delete the file.

List filters work.

Statistics reflect current file processing status.

Download access is authorized.
```

Tests must not upload real university documents or require Gemini credits.

---

## 39. Postman Test 1 — Upload PDF

```http
POST http://localhost:8080/api/v1/admin/knowledge/documents/upload
Authorization: Bearer <admin-token>
Content-Type: multipart/form-data
```

Form Data:

```text
title = Student Handbook 2024-2025
categoryId = 1
academicYearLabel = 2024-2025
description = Official Student Handbook
file = handbook.pdf
```

Expected:

```text
HTTP 201

Document created

File stored

Processing status = UPLOADED
```

---

## 40. Postman Test 2 — List Documents

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents?page=0&size=10
```

Expected:

```text
Recently uploaded document appears.

Category correct.

Academic Year correct.

File type correct.

Uploaded By correct.

Processing status correct.
```

---

## 41. Postman Test 3 — Document Statistics

```http
GET http://localhost:8080/api/v1/admin/knowledge/documents/statistics
```

Expected:

```text
Total documents includes the uploaded document.

Processing count includes UPLOADED status.

Ready count does not increase merely because of upload.
```

---

## 42. Postman Test 4 — Replace File

```http
POST /api/v1/admin/knowledge/documents/{id}/files
```

Upload a second version.

Expected:

```text
Old revision retained.

New revision becomes current.

New processing status = UPLOADED.
```

---

## 43. Postman Test 5 — Archive

```http
PATCH /api/v1/admin/knowledge/documents/{id}/archive
```

Expected:

```text
is_active = FALSE

File metadata retained

Physical file retained

Future RAG retrieval excludes archived content
```

---

# Part M — Configuration

## 44. Recommended Environment Variables

```env
KNOWLEDGE_STORAGE_TYPE=local
KNOWLEDGE_STORAGE_ROOT=/app/storage/academic-documents
KNOWLEDGE_MAX_FILE_SIZE_MB=50
KNOWLEDGE_UPLOAD_ENABLED=true
```

Use backend-only configuration.

Do not expose internal storage paths to the mobile application.

---

## 45. Docker Compose Storage

Example concept:

```yaml
services:
  backend:
    volumes:
      - academic-document-storage:/app/storage/academic-documents
    environment:
      KNOWLEDGE_STORAGE_TYPE: local
      KNOWLEDGE_STORAGE_ROOT: /app/storage/academic-documents

volumes:
  academic-document-storage:
```

Merge this into the existing Compose configuration.

Do not replace the current PostgreSQL or Redis volumes.

Ensure file storage survives application container recreation.

Include uploaded-file storage in the backup and UAT deployment strategy.

---

# Part N — Implementation Order

## 46. Development Workflow

```text
PHASE 1
Read AGENTS.md and current specifications
        ↓
PHASE 2
Verify Step 12 migrations
        ↓
PHASE 3
Inspect existing Admin Security
        ↓
PHASE 4
Create document JPA entities/repositories
        ↓
PHASE 5
Create upload request/response DTOs
        ↓
PHASE 6
Implement file configuration properties
        ↓
PHASE 7
Implement FileStorageService
        ↓
PHASE 8
Implement validation and security
        ↓
PHASE 9
Implement upload transaction/compensation
        ↓
PHASE 10
Implement metadata persistence
        ↓
PHASE 11
Implement file revision handling
        ↓
PHASE 12
Implement processing-state initialization
        ↓
PHASE 13
Implement secure download
        ↓
PHASE 14
Integrate existing document list/statistics APIs
        ↓
PHASE 15
Implement archive and activation
        ↓
PHASE 16
Implement controlled error handling
        ↓
PHASE 17
Create unit tests
        ↓
PHASE 18
Create integration tests
        ↓
PHASE 19
Run Maven tests/build
        ↓
PHASE 20
Test endpoints using Postman
```

---

# Part O — Acceptance Criteria

## 47. File Upload

```text
[ ] Multipart endpoint implemented

[ ] Document Title required

[ ] Category required

[ ] Academic Year required

[ ] Document File required

[ ] Description optional

[ ] Curriculum/Program/Department scope supported

[ ] PDF supported

[ ] DOCX supported

[ ] TXT supported

[ ] XLSX supported

[ ] Maximum 50 MB enforced
```

## 48. Data and Storage

```text
[ ] Existing PostgreSQL database reused

[ ] Logical document metadata stored

[ ] Physical files stored outside PostgreSQL

[ ] Opaque storage keys generated

[ ] SHA-256 checksum stored

[ ] Uploading Admin derived from JWT

[ ] File revision history supported

[ ] Only one current file revision

[ ] Database/storage failure handled

[ ] Document categories normalized

[ ] No duplicate AI academic database
```

## 49. AI Processing Preparation

```text
[ ] Processing record created on upload

[ ] Initial status is UPLOADED

[ ] No fake READY status

[ ] Processing status API available

[ ] Future ingestion pipeline supported by schema

[ ] Archived documents excluded from future retrieval

[ ] Non-current file revisions excluded from future retrieval

[ ] No Gemini call required during upload
```

## 50. Security

```text
[ ] Admin JWT required

[ ] Role permissions enforced

[ ] Path traversal prevented

[ ] Content-type validation performed

[ ] File-size limit enforced

[ ] Unsafe file formats rejected

[ ] Public direct storage access disabled

[ ] Download authorization enforced

[ ] Sensitive file paths not exposed

[ ] Documents treated as untrusted content
```

## 51. Testing

```text
[ ] Unit tests pass

[ ] PostgreSQL integration tests pass

[ ] Storage integration tests pass

[ ] Revision tests pass

[ ] Security tests pass

[ ] Postman upload works

[ ] Postman list works

[ ] Postman archive works

[ ] Postman download works

[ ] Maven build passes
```

---

# 52. Agent Completion Report

After implementation, report:

```text
1. Files created

2. Files modified

3. Upload endpoint details

4. FileStorageService implementation

5. Allowed file types

6. File-size validation

7. Document metadata persistence

8. File revision implementation

9. Processing-state implementation

10. Document download implementation

11. Admin authorization

12. Docker storage changes

13. Error mappings

14. Automated tests

15. Postman testing results

16. mvn test result

17. mvn clean package result

18. Known limitations

19. Future ingestion/RAG dependencies
```

Do not claim file extraction, embeddings, or semantic retrieval are complete unless those capabilities have actually been implemented and tested.

---

# 53. Step 13 Completion Definition

Step 13 is complete when an authorized Admin can upload an official university document, the backend can validate and securely store it, normalized metadata is persisted in PostgreSQL, processing state is recorded accurately, and the document appears in the AI Knowledge Base list.

The upload must preserve the document's academic scope and support file revisions.

The document is not necessarily AI-searchable yet.

---

# 54. Next Step — Document Processing and RAG

After Step 13, the next implementation should handle:

```text
Stored Academic Document
        ↓
Secure Document Extraction
        ↓
Text Cleaning
        ↓
Chunking
        ↓
Embedding Generation
        ↓
Vector Indexing
        ↓
READY
        ↓
Knowledge Retrieval Tool
        ↓
Gemini
        ↓
Grounded Academic Answer
```

For the existing senior project, consider PostgreSQL with `pgvector` for the eventual vector index to avoid unnecessary infrastructure complexity.

However, do not create vector tables or introduce embedding dependencies during Step 13 unless the RAG processing implementation is explicitly started.

**Final goal:** Admin users can securely manage official academic documents, and the backend is ready for a subsequent RAG ingestion feature.