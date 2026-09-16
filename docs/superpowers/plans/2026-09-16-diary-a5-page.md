# Diary A5 Page Implementation Plan

> **Scope:** Convert diary inner pages to A5 portrait without changing PDF export or the cover coordinate system.

## Task 1: Lock the A5 geometry with tests

**Files:**
- Create: `src/test/java/com/example/travlediary/service/diary/DiaryPageGeometryTest.java`
- Modify: `src/test/java/com/example/travlediary/controller/diary/DiaryPageCoordinateAssetTest.java`
- Modify: `src/test/java/com/example/travlediary/repository/DiarySpiralNotebookUiContractTest.java`

1. Add assertions for 148:210, 576px reference width, 817.3px calculated height, 26 lines, and the legacy vertical factor 2812/4305.
2. Run the focused tests and confirm they fail against the 41:38 implementation.
3. Add the shared Java geometry constants and CSS variables.
4. Run the focused tests again.

## Task 2: Preserve free-position element geometry

**Files:**
- Create: `src/main/java/com/example/travlediary/service/diary/DiaryPageGeometry.java`
- Modify: `src/main/java/com/example/travlediary/service/diary/DiaryPhotoFrame.java`
- Modify: `src/main/java/com/example/travlediary/controller/diary/DiaryController.java`
- Modify: `src/main/java/com/example/travlediary/service/diary/DiaryElementServiceImpl.java`
- Modify: related controller/service/photo tests

1. Test the exact legacy-to-A5 vertical conversion and unchanged horizontal values.
2. Test PHOTO/STICKER/TAPE/NOTE/TEXT default sizes against A5 physical geometry.
3. Change page photo aspect to 148/210 and convert page-only default heights and Y offsets.
4. Keep rotation, z-index, drag, resize, and normalized save endpoints unchanged.

## Task 3: Migrate existing coordinates without a schema change

**Files:**
- Create: `docs/db/diary_elements_a5_coordinate_migration.sql`
- Modify: `src/main/resources/static/js/guest-diary-draft-store.js`
- Modify: `src/main/resources/static/js/guest-diary-editor.js`
- Modify: `src/main/resources/static/js/guest-diary-photo.js`
- Modify: guest contract tests

1. Document a one-time SQL update for `position_y` and `height`; do not execute it.
2. Bump the guest draft schema and migrate only inner-page elements once on read.
3. Leave cover elements untouched.
4. Align guest new-element defaults and page photo aspect with the server.

## Task 4: Reflow the A5 paper and ruled writing area

**Files:**
- Modify: `src/main/resources/static/css/diary.css`

1. Put the A5 page width, ratio, height, line interval, and 26-line count in shared variables.
2. Use the same 576px reference width for the editor and reading pages, with responsive page sizing on narrow screens.
3. Retune the writing area gap and book depth for the taller sheet while preserving the paper texture and safe edge spacing.
4. Keep the existing Quill baseline corrections and last-block overflow guard.

## Task 5: Verify regressions and responsive rendering

1. Run focused diary geometry, controller, service, font, guest, and notebook tests.
2. Run `./gradlew compileJava` and `./gradlew test`.
3. Run `git diff --check` and review `git status --short`.
4. Open the public guest editor in desktop and mobile widths to verify the computed 148:210 sheet, 26 complete ruled lines, no internal page scroll, and synchronized canvas scaling.
5. Confirm that no PDF export file changed in this task.
