# Admin Festival Edit/Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect secure administrator edit, thumbnail reselection, and cascade-safe delete flows for existing FESTIVAL travel information.

**Architecture:** Add a dedicated `FestivalAdminService` so FESTIVAL-only locking, validation, multi-table updates, thumbnail ownership checks, and after-commit file cleanup stay separate from GENERAL administration and TourAPI registration. Reuse the existing festival form for create/edit presentation, but bind edit requests to a server-fixed `FestivalEditForm` that exposes neither `contentType` nor source identity fields.

**Tech Stack:** Java 17, Spring Boot MVC/Transactions/Security, MyBatis XML, Thymeleaf, JUnit 5, Mockito, AssertJ.

**Spec:** User-approved implementation request in this conversation, based on `CLAUDE.md` and `docs/db/travel_diary_schema_reference.md`.

## Global Constraints

- Do not change the database schema or execute SQL directly.
- Keep CREATE TourAPI search/download behavior unchanged.
- Keep `content_type=FESTIVAL`, `source_type`, `external_content_id`, and every image `is_main` value server-controlled.
- Edit may only choose zero or one existing image owned by the same festival as `is_thumbnail=1`.
- Delete only the `travel_info` root row; schema cascades remove festival, period, and image rows.
- Delete physical files only after transaction commit through the managed festival-image path validator.
- Do not change GENERAL administration or public list/detail UI.
- Do not commit or push.

---

### Task 1: FESTIVAL edit form and service read model

**Files:**
- Create: `src/main/java/com/example/travlediary/dto/FestivalEditForm.java`
- Create: `src/main/java/com/example/travlediary/dto/FestivalEditData.java`
- Create: `src/main/java/com/example/travlediary/service/travelinfo/FestivalAdminService.java`
- Test: `src/test/java/com/example/travlediary/service/travelinfo/FestivalAdminServiceTest.java`

**Interfaces:**
- Produces: `FestivalEditData getEditData(Long id)` containing `FestivalEditForm form()` and `List<InfoImage> images()`.
- `FestivalEditForm` contains editable text/date/scope/category fields plus nullable `Long thumbnailImageId`; it contains no content type or source identity fields.

- [ ] Write failing tests proving FESTIVAL values, period, optional `festival_info`, images, and current thumbnail are restored while GENERAL ids return 404.
- [ ] Run `./gradlew test --tests '*FestivalAdminServiceTest'` and confirm failures are caused by missing edit service types.
- [ ] Implement the DTOs and read-only service method using `findById`, `findPeriodsByInfoId`, `findByInfoId`, and `findImagesByInfoId`.
- [ ] Re-run the focused test and confirm the read-model tests pass.

### Task 2: Transactional FESTIVAL update and thumbnail ownership

**Files:**
- Modify: `src/main/java/com/example/travlediary/repository/travelinfo/TravelInfoMapper.java`
- Modify: `src/main/resources/mapper/TravelInfoMapper.xml`
- Modify: `src/main/java/com/example/travlediary/service/travelinfo/FestivalAdminService.java`
- Test: `src/test/java/com/example/travlediary/service/travelinfo/FestivalAdminServiceTest.java`
- Test: `src/test/java/com/example/travlediary/repository/TravelInfoMapperContractTest.java`

**Interfaces:**
- Produces: `void update(Long id, FestivalEditForm form)` under one `@Transactional` boundary.
- Produces mapper methods `clearThumbnailByInfoId(Long infoId)` and `setThumbnailByIdAndInfoId(Long imageId, Long infoId)`; neither statement updates `is_main`.

- [ ] Write failing tests for field updates, fixed FESTIVAL content type, preserved source identity, invalid date/category, foreign image rejection, thumbnail retain/change/clear, and mapper failure rollback.
- [ ] Run focused service and mapper tests and confirm expected failures.
- [ ] Implement validation, row lock, common/period/festival updates, defensive festival row insert, and thumbnail clear/set with ownership validation.
- [ ] Re-run focused tests and confirm update contracts pass.

### Task 3: Transactional cascade delete and after-commit file cleanup

**Files:**
- Modify: `src/main/java/com/example/travlediary/service/travelinfo/FestivalAdminService.java`
- Test: `src/test/java/com/example/travlediary/service/travelinfo/FestivalAdminServiceTest.java`
- Test: `src/test/java/com/example/travlediary/repository/TravelInfoMapperContractTest.java`
- Test: `src/test/java/com/example/travlediary/service/kto/KtoFestivalImageDownloadServiceTest.java`

**Interfaces:**
- Produces: `void delete(Long id)` that locks and validates FESTIVAL, reads all image URLs, deletes bookmarks, deletes only `travel_info`, and registers safe file deletion in `afterCommit`.

- [ ] Write failing tests for FESTIVAL/GENERAL/no-image delete, rollback suppression, post-commit cleanup, cleanup failure logging, and unmanaged/traversal/external URL rejection.
- [ ] Run focused tests and confirm failures are due to the missing delete flow or missing safety cases.
- [ ] Implement root deletion and per-file after-commit cleanup through `KtoFestivalImageDownloadService.deleteDownloadedFestivalImage(String)`.
- [ ] Re-run focused tests and confirm delete and path-safety contracts pass.

### Task 4: Controller routes and reusable create/edit UI

**Files:**
- Modify: `src/main/java/com/example/travlediary/controller/admin/AdminFestivalController.java`
- Modify: `src/main/resources/templates/admin/festivals/form.html`
- Modify: `src/main/resources/templates/admin/festivals/list.html`
- Modify: `src/main/resources/static/css/admin-travel-info.css`
- Test: `src/test/java/com/example/travlediary/controller/admin/AdminFestivalControllerTest.java`
- Test: `src/test/java/com/example/travlediary/repository/AdminFestivalUiContractTest.java`

**Interfaces:**
- Produces routes `GET /admin/festivals/{id}/edit`, `POST /admin/festivals/{id}/edit`, and `POST /admin/festivals/{id}/delete`.
- Edit model provides `festivalForm`, `festivalImages`, `editMode`, `formAction`, `pageTitle`, `pageDescription`, and `submitLabel`.

- [ ] Write failing MVC/UI tests for edit rendering, FESTIVAL-only categories, validation re-render with images, flash redirects, POST delete, CSRF form, and confirmation copy.
- [ ] Run focused controller/UI tests and confirm expected route/template failures.
- [ ] Implement controller mappings and conditionally hide create-only TourAPI UI while rendering the compact existing-image radio picker in edit mode.
- [ ] Re-run focused tests and confirm route/UI contracts pass.

### Task 5: Regression and final verification

**Files:**
- Review only all files changed above and existing CREATE/public regression tests.

- [ ] Run focused tests for festival registration, KTO registration, gallery, list thumbnail priority, controller security, and GENERAL travel administration.
- [ ] Run `./gradlew compileJava` and confirm exit code 0.
- [ ] Run `./gradlew test` and confirm exit code 0 with no failures.
- [ ] Run `git diff --check` and confirm no whitespace errors.
- [ ] Run `git status --short`, preserve all pre-existing changes, and report only files changed for this task plus browser checks that still require manual confirmation.
