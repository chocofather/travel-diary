# KTO Destination Save Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect validated KTO selections to destination create/update through one Service-layer transaction while keeping KTO downloads outside the transaction and cleaning newly created files on failure.

**Architecture:** A non-transactional destination save orchestrator validates the create-time main-image policy, prepares KTO files, and invokes a separate transactional persistence bean. The transactional bean reuses `DestinationService` and `KtoPhotoImportPersistenceService`; direct-upload files register rollback cleanup with the active Spring transaction.

**Tech Stack:** Java 17, Spring Boot 3.4.3, Spring transaction synchronization, MyBatis, JUnit 5, Mockito, AssertJ

**Spec:** Approved design and constraints in the current user request.

## Global Constraints

- KTO downloads happen before the DB transaction.
- Create always uses the same top-level transactional persistence path, including zero KTO photos.
- Update does not add a new direct-upload path.
- Existing destination-image main/order invariants remain unchanged.
- No DB/schema/Mapper changes and no git commit/push/reset/restore/checkout.

---

### Task 1: Orchestration contract

**Files:**
- Create: `src/test/java/com/example/travlediary/service/destination/DestinationSaveOrchestrationServiceTest.java`
- Create: `src/main/java/com/example/travlediary/service/destination/DestinationSaveOrchestrationService.java`

**Interfaces:**
- Consumes: `KtoPhotoImportService.preparePhotos(List<KtoSelectedPhotoRequest>)`
- Produces: `registerDestination(DestinationForm, Long, List<KtoSelectedPhotoRequest>)` and `updateDestination(Long, DestinationForm, List<KtoSelectedPhotoRequest>)`

- [ ] Write tests proving create main conflicts fail before prepare, empty KTO still reaches persistence with an empty prepared list, prepare precedes persistence, and persistence failure cleans prepared files.
- [ ] Run the focused test and confirm RED because the orchestration service is absent.
- [ ] Implement the smallest non-transactional orchestration service satisfying those contracts.
- [ ] Run the focused test and confirm GREEN.

### Task 2: Top-level transactional persistence

**Files:**
- Create: `src/test/java/com/example/travlediary/service/destination/DestinationSavePersistenceServiceTest.java`
- Create: `src/main/java/com/example/travlediary/service/destination/DestinationSavePersistenceService.java`
- Modify: `src/main/java/com/example/travlediary/service/destination/DestinationService.java`
- Modify: `src/test/java/com/example/travlediary/service/destination/DestinationPlaceIdBindingTest.java`

**Interfaces:**
- Consumes: `DestinationService.registerDestination(...)`, `DestinationService.updateDestination(...)`, `KtoPhotoImportPersistenceService.persistPhotos(...)`
- Produces: public `@Transactional` create/update methods; create passes the generated destination ID to KTO persistence.

- [ ] Write tests proving create returns/uses the generated ID, both zero/nonzero KTO lists use the same method, update preserves the supplied ID, and public transaction annotations exist.
- [ ] Run focused tests and confirm RED.
- [ ] Make `DestinationService.registerDestination(...)` return the generated ID without changing its existing work, then implement the persistence service.
- [ ] Run focused tests and confirm GREEN.

### Task 3: Direct-upload rollback cleanup

**Files:**
- Modify: `src/test/java/com/example/travlediary/service/destination/DestinationImageServiceTest.java`
- Modify: `src/main/java/com/example/travlediary/service/destination/DestinationImageService.java`
- Create: `src/test/java/com/example/travlediary/service/file/FileUploadServiceDestinationCleanupTest.java`
- Modify: `src/main/java/com/example/travlediary/service/file/FileUploadService.java`

**Interfaces:**
- Consumes: server-created `/uploads/destinations/...` URLs from `FileUploadService.saveFile(...)`
- Produces: rollback-only cleanup registered immediately after each new direct upload; safe managed destination-file deletion.

- [ ] Write tests proving an outer rollback deletes newly saved direct files, commit does not delete them, and cleanup cannot escape the destinations root.
- [ ] Run focused tests and confirm RED.
- [ ] Implement transaction synchronization and the constrained deletion helper.
- [ ] Run focused tests and confirm GREEN.

### Task 4: Controller integration

**Files:**
- Modify: `src/test/java/com/example/travlediary/controller/admin/AdminDestinationKtoSelectionControllerTest.java`
- Modify: `src/main/java/com/example/travlediary/controller/admin/AdminDestinationController.java`

**Interfaces:**
- Consumes: parser output and `DestinationSaveOrchestrationService`
- Produces: create/update form submits that return safe HTTP 400 for malformed selections or main-image conflicts.

- [ ] Update controller tests to prove valid create/update delegate parsed selections and conflicts become safe 400 responses.
- [ ] Run the focused test and confirm RED.
- [ ] Delegate create/update to the orchestration service and keep existing redirect contracts.
- [ ] Run the focused test and confirm GREEN.

### Task 5: Fresh verification

**Files:**
- Verify only; no production behavior added.

- [ ] Run all focused destination/KTO/controller tests.
- [ ] Run `./gradlew compileJava`.
- [ ] Run `./gradlew test`.
- [ ] Run `git diff --check` and `git status --short`.
- [ ] Review the final diff against every approved constraint before reporting.
