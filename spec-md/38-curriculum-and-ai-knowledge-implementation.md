# 39 — Step 12B: Curriculum Management Backend API Implementation

**Project:** Smart AI University Student Assistant  
**Module:** Academic Management → Curriculum Management  
**Scope:** Backend CRUD and read APIs  
**Prerequisite:** Step 12A Flyway migration completed successfully

## 1. Objective

Implement Admin APIs for managing official curriculum versions, document categories, and document metadata.

Use the existing Spring Boot backend, PostgreSQL, Spring Security, and Flyway schema.

Do not implement file upload or RAG extraction in this task. Step 13 handles upload.

## 2. Mandatory Agent Instructions

Before implementation:

1. Read `AGENTS.md` and `spec-md/`.
2. Inspect the completed Step 12A migrations.
3. Inspect existing Program, Department, and Admin authentication modules.
4. Verify the current Admin role/authority model.
5. Follow existing feature package conventions.
6. Reuse the project's API response wrapper, pagination, exception handling, and mapper conventions.
7. Do not change existing Program or Department APIs.
8. Do not expose JPA entities directly.
9. Use constructor injection and transactional services.
10. Run automated tests and build verification.

## 3. Recommended Package Structure

```text
feature/
├── curriculum/
│   ├── controller/
│   │   └── AdminCurriculumController.java
│   ├── dto/
│   │   ├── CurriculumCreateRequest.java
│   │   ├── CurriculumUpdateRequest.java
│   │   ├── CurriculumResponse.java
│   │   └── CurriculumListResponse.java
│   ├── entity/
│   │   └── CurriculumVersion.java
│   ├── repository/
│   │   └── CurriculumVersionRepository.java
│   ├── mapper/
│   │   └── CurriculumVersionMapper.java
│   └── service/
│       ├── CurriculumVersionService.java
│       └── impl/
│           └── CurriculumVersionServiceImpl.java
│
└── knowledge/
    ├── controller/
    │   ├── AdminKnowledgeDocumentController.java
    │   └── AdminDocumentCategoryController.java
    ├── dto/
    ├── entity/
    │   ├── AcademicDocument.java
    │   ├── AcademicDocumentFile.java
    │   ├── AcademicDocumentProcessing.java
    │   └── DocumentCategory.java
    ├── repository/
    ├── mapper/
    └── service/
```

Adapt to the real project structure and avoid creating unused classes.

## 4. Curriculum Version Endpoints

Base:

```http
/api/v1/admin/curriculums
```

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/admin/curriculums` | Paginated list |
| GET | `/api/v1/admin/curriculums/{id}` | Detail |
| POST | `/api/v1/admin/curriculums` | Create |
| PUT | `/api/v1/admin/curriculums/{id}` | Update |
| PATCH | `/api/v1/admin/curriculums/{id}/activate` | Activate |
| PATCH | `/api/v1/admin/curriculums/{id}/archive` | Archive |
| DELETE | `/api/v1/admin/curriculums/{id}` | Soft delete |

Recommended filters:

```text
search
programId
academicYear
status
page
size
sort
```

Follow the existing pagination contract.

## 5. Create Curriculum Request

```json
{
  "programId": 5,
  "versionCode": "CS-2024",
  "versionName": "Computer Science Curriculum 2024",
  "academicYearLabel": "2024-2025",
  "description": "Official Bachelor of Computer Science curriculum",
  "effectiveFrom": "2024-08-01",
  "effectiveTo": null
}
```

Rules:

- Program required and must exist.
- Program must not be deleted or inactive.
- Version code must be unique within the Program.
- Academic year required.
- Invalid date range rejected.
- New versions are ACTIVE only if the Program has no other active version or an explicit activation operation handles archiving the previous one.
- Do not silently archive an existing active version without the specified transition behavior.

## 6. Curriculum Version Response

```json
{
  "id": 10,
  "programId": 5,
  "programName": "Bachelor of Computer Science",
  "versionCode": "CS-2024",
  "versionName": "Computer Science Curriculum 2024",
  "academicYearLabel": "2024-2025",
  "status": "ACTIVE",
  "documentCount": 4,
  "createdAt": "2026-11-03T09:00:00"
}
```

`documentCount` must be derived from the corresponding active/non-deleted academic documents.

Do not persist a duplicate counter.

## 7. Activation and Archiving

Activate:

```http
PATCH /api/v1/admin/curriculums/10/activate
```

Archive:

```http
PATCH /api/v1/admin/curriculums/10/archive
```

Enforce one active version per Program.

Activation should use a single database transaction and appropriate locking/concurrency handling. Archive the previous active version and activate the requested version atomically if the business rule allows replacing the active version.

Return a conflict on a concurrent or invalid transition.

Do not physically delete document files when a curriculum is archived.

An archived curriculum version must not become an active AI knowledge source unless a future explicit retrieval policy permits historical documents.

## 8. Document Category Endpoints

Base:

```http
/api/v1/admin/knowledge/categories
```

| Method | Endpoint |
|---|---|
| GET | `/api/v1/admin/knowledge/categories` |
| GET | `/api/v1/admin/knowledge/categories/{id}` |
| POST | `/api/v1/admin/knowledge/categories` |
| PUT | `/api/v1/admin/knowledge/categories/{id}` |
| PATCH | `/api/v1/admin/knowledge/categories/{id}/activate` |
| PATCH | `/api/v1/admin/knowledge/categories/{id}/deactivate` |
| DELETE | `/api/v1/admin/knowledge/categories/{id}` |

Example:

```json
{
  "categoryCode": "CURRICULUM",
  "categoryName": "Curriculum",
  "description": "Official university program curriculum documents"
}
```

Category codes must remain unique.

Prevent deletion/deactivation behavior that would leave active documents in an invalid state. Define whether existing documents remain readable after their category becomes inactive.

## 9. AI Knowledge Base Document Endpoints

Base:

```http
/api/v1/admin/knowledge/documents
```

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/admin/knowledge/documents` | Paginated list |
| GET | `/api/v1/admin/knowledge/documents/{id}` | Document details |
| PUT | `/api/v1/admin/knowledge/documents/{id}` | Edit metadata |
| PATCH | `/api/v1/admin/knowledge/documents/{id}/activate` | Activate |
| PATCH | `/api/v1/admin/knowledge/documents/{id}/archive` | Archive |
| DELETE | `/api/v1/admin/knowledge/documents/{id}` | Soft delete |
| GET | `/api/v1/admin/knowledge/documents/statistics` | Dashboard counters |

Creating a document with an uploaded file is implemented in Step 13 through a multipart endpoint.

Do not create empty document records through a separate public endpoint unless the UI explicitly supports draft metadata records.

## 10. Figma AI Knowledge Base List Mapping

The screen contains:

```text
Document Name
Category
Academic Year
Type
Uploaded By
Date
AI Status
Actions
```

API response should include:

```json
{
  "id": 31,
  "title": "Student Handbook 2024-2025",
  "description": "Official student handbook",
  "category": {
    "id": 1,
    "code": "STUDENT_HANDBOOK",
    "name": "Student Handbook"
  },
  "academicYearLabel": "2024-2025",
  "scope": {
    "departmentId": null,
    "programId": null,
    "curriculumVersionId": null
  },
  "file": {
    "id": 81,
    "originalFilename": "student-handbook.pdf",
    "extension": "pdf",
    "sizeBytes": 1843000,
    "revisionNumber": 1
  },
  "uploadedBy": {
    "id": 1,
    "displayName": "Admin"
  },
  "uploadedAt": "2026-11-03T09:00:00",
  "processingStatus": "UPLOADED",
  "isActive": true
}
```

Values are illustrative.

For uploaded-by display, derive the name from the related `app_users` record. Never trust an uploadedBy value supplied by a frontend.

## 11. Document Listing Filters

Implement:

```http
GET /api/v1/admin/knowledge/documents
```

Parameters:

```text
search
categoryId
academicYear
status
isActive
programId
departmentId
curriculumVersionId
page
size
sort
```

Support combinations such as:

```http
GET /api/v1/admin/knowledge/documents?categoryId=3&academicYear=2024-2025&status=READY&page=0&size=10
```

`status` must filter the current file revision's processing status.

Search should cover document title and optionally description.

Avoid N+1 queries using appropriate fetch joins, projections, or entity graphs.

## 12. Dashboard Statistics

Endpoint:

```http
GET /api/v1/admin/knowledge/documents/statistics
```

Expected shape:

```json
{
  "totalDocuments": 12,
  "readyDocuments": 8,
  "processingDocuments": 3,
  "failedDocuments": 1
}
```

Count documents, not file revisions.

Define processing as:

```text
UPLOADED
QUEUED
PROCESSING
CHUNKING
EMBEDDING
```

Use a consistent active/non-deleted filter.

The example count values represent the Figma mockup, not real backend data.

## 13. Archive and Soft Delete

Archive:

```http
PATCH /api/v1/admin/knowledge/documents/{id}/archive
```

Set:

```text
is_active = FALSE
```

Activate:

```http
PATCH /api/v1/admin/knowledge/documents/{id}/activate
```

Set:

```text
is_active = TRUE
```

Only allow activation for documents whose scope and owning curriculum are valid.

Soft delete:

```http
DELETE /api/v1/admin/knowledge/documents/{id}
```

Set:

```text
is_deleted = TRUE
```

Do not physically delete the file or stored revisions as part of a normal Admin delete request.

File retention and purge should be managed separately.

## 14. Authorization

Use the existing Spring Security model.

Recommended permissions:

```text
SUPER_ADMIN
ADMIN
ACADEMIC_ADMIN
```

Only authorized Admin users may access these endpoints.

Use the existing permission distinctions, if already implemented, rather than granting every Admin role identical write access automatically.

Admin ID must be resolved through the authenticated Admin security context.

Never accept `createdBy`, `updatedBy`, or `uploadedBy` from request bodies.

## 15. Validation and Error Handling

Required cases:

```text
CURRICULUM_NOT_FOUND
CURRICULUM_VERSION_DUPLICATE
CURRICULUM_ALREADY_ACTIVE
CURRICULUM_INVALID_PROGRAM
CURRICULUM_INVALID_STATUS_TRANSITION
DOCUMENT_CATEGORY_NOT_FOUND
DOCUMENT_CATEGORY_DUPLICATE
DOCUMENT_NOT_FOUND
DOCUMENT_SCOPE_INVALID
DOCUMENT_NOT_EDITABLE
```

Reuse the standard application error format.

Apply appropriate 400, 401, 403, 404, and 409 responses.

## 16. Automated Tests

Test:

- Curriculum creation for valid Program.
- Duplicate version rejection.
- Invalid Program rejection.
- Only one active curriculum per Program.
- Concurrent activation behavior.
- Curriculum archiving.
- Category CRUD.
- Document metadata listing.
- Search and combined filters.
- Academic-year filtering.
- Derived processing counters.
- Active/archive document transitions.
- Soft-delete behavior.
- Admin authority restrictions.
- Unauthorized access.
- Correct audit attribution.

Do not require the Gemini API for these tests.

## 17. Build Verification

```bash
mvn test
mvn clean package
```

Test the new APIs using Postman and your development database.

## 18. Acceptance Criteria

- [ ] Curriculum Version CRUD completed.
- [ ] Program relationship validation completed.
- [ ] Curriculum activation/archive implemented.
- [ ] Document Category management completed.
- [ ] AI Knowledge Base list API completed.
- [ ] Filters and pagination completed.
- [ ] Statistics endpoint completed.
- [ ] Document archive/soft-delete implemented.
- [ ] Admin JWT authorization enforced.
- [ ] Audit fields derived from authenticated Admin.
- [ ] Existing academic schema and APIs preserved.
- [ ] No duplicated academic document storage.
- [ ] Automated tests pass.
- [ ] Maven build passes.

## 19. Agent Completion Report

Report files created, controllers, services, repositories, DTOs, endpoint contracts, security rules, database validations, automated tests, build results, and known limitations.

**After Step 12B passes, proceed to Step 13 — Curriculum and Knowledge Document File Upload.**