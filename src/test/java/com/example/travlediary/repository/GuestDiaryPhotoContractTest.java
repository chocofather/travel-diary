package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비회원 체험 사진의 계약.
 *
 * <p>빌드에 JS 런타임이 없어 실행 대신 규칙을 문장으로 고정한다.
 * 지키는 것은 네 가지다. 원본이 브라우저를 떠나지 않는다는 것,
 * localStorage 에는 참조만 남는다는 것, 뗀 사진의 원본이 함께 정리된다는 것,
 * 그리고 회원 사진 업로드가 예전 그대로라는 것.
 */
class GuestDiaryPhotoContractTest {

    /** 1) 원본은 IndexedDB 에 둔다. 페이지와 표지가 한 보관소를 함께 쓴다. */
    @Test
    void thePhotoBlobLivesInIndexedDb() throws IOException {
        String store = script("guest-diary-photo-store.js");

        assertThat(store)
                .contains("const DB_NAME = 'travelDiaryGuest';")
                .contains("const STORE = 'photos';")
                .contains("global.indexedDB.open(DB_NAME, DB_VERSION)")
                .contains("db.createObjectStore(STORE, {keyPath: 'photoRef'})")
                // 체험 다이어리 한 권의 사진만 골라 지우기 위한 색인
                .contains("store.createIndex(DRAFT_INDEX, 'draftId', {unique: false})");

        // 13) 페이지 사진과 표지 사진이 같은 보관소를 쓴다. scope 로만 갈린다.
        assertThat(script("guest-diary-editor.js")).contains("scope: 'PAGE'");
        assertThat(script("guest-diary-cover-editor.js")).contains("scope: 'COVER'");
        for (String name : new String[]{
                "guest-diary-editor.js", "guest-diary-cover-editor.js"}) {
            assertThat(script(name)).as(name)
                    .contains("global.GuestDiaryPhoto?.initialize(photoHost(), showPhotoStatus);");
        }
        assertThat(script("guest-diary-photo.js"))
                .contains("global.TravelDiaryGuestPhotoStore");
    }

    /** 2) 15) Blob/base64/data URL 은 localStorage 에 들어가지 않는다. */
    @Test
    void noBlobOrBase64IsEverPersistedToLocalStorage() throws IOException {
        // draft 저장소는 사진 참조만 담는다. Blob 을 담는 자리가 없다.
        String draftStore = script("guest-diary-draft-store.js");
        assertThat(draftStore)
                .contains("photoRef: text(raw.photoRef, null)")
                .doesNotContain("blob")
                .doesNotContain("Blob");
        // 원본 그림 주소 자리는 여전히 data: 를 거부한다.
        assertThat(draftStore).contains("url.slice(0, 5).toLowerCase() === \"data:\"");

        // 붙일 때 draft 에 담는 값에도 blob: 주소가 없다.
        assertThat(script("guest-diary-photo.js"))
                .contains("photoRef: saved.photoRef")
                .contains("imageUrl: null");
    }

    /** 3) base64 로 바꾸는 코드가 어디에도 없다. */
    @Test
    void noFileIsEverReadAsADataUrl() throws IOException {
        for (String name : new String[]{
                "guest-diary-photo.js", "guest-diary-photo-store.js",
                "guest-diary-editor.js", "guest-diary-cover-editor.js",
                "guest-diary-cover-preview.js", "guest-diary-demo.js"}) {
            assertThat(script(name)).as(name)
                    .doesNotContain("readAsDataURL")
                    .doesNotContain("toDataURL")
                    .doesNotContain("FileReader")
                    .doesNotContain("btoa(");
        }
    }

    /** 4) 사진을 붙이고 되살리는 과정에 서버로 나가는 요청이 없다. */
    @Test
    void theGuestPhotoCodeNeverUploadsToTheServer() throws IOException {
        for (String name : new String[]{
                "guest-diary-photo.js", "guest-diary-photo-store.js"}) {
            assertThat(script(name)).as(name)
                    .doesNotContain("fetch(")
                    .doesNotContain("XMLHttpRequest")
                    .doesNotContain("$.ajax")
                    .doesNotContain("FormData")
                    .doesNotContain("/uploads")
                    .doesNotContain("elements/photo");
        }
        // 고르개에 보낼 곳이 없다. 회원 화면의 업로드 스크립트가 이 칸을 잡지 못한다.
        for (String template : new String[]{
                "templates/diary/demo-edit.html", "templates/diary/demo-cover.html"}) {
            assertThat(resource(template)).as(template)
                    .contains("data-guest-photo-input")
                    .doesNotContain("data-create-url=@{")
                    .doesNotContain("enctype=\"multipart/form-data\"");
        }
    }

    /**
     * 어느 쪽이 일반이고 어느 쪽이 폴라로이드인지 글자로 알 수 있다.
     *
     * <p>고르개가 아이콘 두 개뿐이면 붙여 보기 전에는 구분할 수 없다.
     * 회원 화면은 아이콘 옆에 모습 이름을 함께 쓰므로, 체험 화면도 같은 조각을 쓴다.
     */
    @Test
    void thePhotoPickersSayWhichShapeTheyAdd() throws IOException {
        for (String template : new String[]{
                "templates/diary/demo-edit.html", "templates/diary/demo-cover.html",
                // 회원 화면(페이지/표지)도 그대로다.
                "templates/diary/detail.html", "templates/diary/cover-design-edit.html"}) {
            assertThat(resource(template)).as(template)
                    .contains("<span th:text=\"${style.label}\">일반</span>");
        }
        // 고르는 값(FULL / POLAROID)은 예전 그대로다. 글자만 늘었다.
        assertThat(resource("templates/diary/demo-edit.html"))
                .contains("th:data-photo-style=\"${style.code}\"");
        assertThat(source("model/DiaryCoverPhotoStyle.java"))
                .contains("FULL")
                .contains("POLAROID");
    }

    /** 5) 6) page 와 cover 모두 draft 에는 photoRef 와 메타데이터만 남는다. */
    @Test
    void onlyThePhotoRefAndMetadataArePersisted() throws IOException {
        String photo = script("guest-diary-photo.js");
        String attached = photo.substring(photo.indexOf("element = host.attach({"));

        assertThat(attached.substring(0, attached.indexOf("});")))
                .contains("elementType: 'PHOTO'")
                .contains("photoRef: saved.photoRef")
                .contains("imageUrl: null")
                .contains("photoStyle: photoStyle")
                .contains("positionX")
                .contains("positionY")
                .contains("width")
                .contains("height")
                .contains("rotation")
                // 원본도, 화면 주소도 담지 않는다.
                .doesNotContain("blob")
                .doesNotContain("objectUrl");
    }

    /** 7) 그릴 때는 photoRef → Blob → blob: 주소 순서를 쓴다. */
    @Test
    void theRuntimeImageComesFromTheStoredBlob() throws IOException {
        String photo = script("guest-diary-photo.js");

        assertThat(photo)
                .contains("store.getPhoto(photoRef)")
                .contains("global.URL.createObjectURL(found.photo.blob)")
                // 같은 사진에 주소를 두 번 만들지 않는다.
                .contains("if (objectUrls.has(photoRef)) {");
        // 회원 사진과 같은 마크업 생성기를 다시 쓴다. 별도 렌더러를 만들지 않는다.
        assertThat(photo).contains("(global.diaryElementRenderers || {}).PHOTO");
        assertThat(script("diary-cover-photo.js"))
                .contains("window.diaryElementRenderers.PHOTO = render;");
    }

    /** 8) 만들어 둔 blob: 주소를 돌려주는 자리가 있다. */
    @Test
    void everyObjectUrlIsRevoked() throws IOException {
        String photo = script("guest-diary-photo.js");

        assertThat(photo)
                .contains("global.URL.revokeObjectURL(url)")
                // 화면을 떠날 때 한 번에 돌려준다.
                .contains("global.addEventListener('pagehide', releaseAll);")
                .contains("function releaseUrl(photoRef)")
                .contains("objectUrls.delete(photoRef);");
    }

    /** 9) 사진을 떼면 원본도 정리한다. 아직 쓰고 있으면 남긴다. */
    @Test
    void removingAPhotoAlsoRemovesItsBlobUnlessStillUsed() throws IOException {
        assertThat(script("guest-diary-photo.js"))
                .contains("async function release(photoRef, stillUsed)")
                .contains("if (stillUsed) return;")
                .contains("await store.deletePhoto(photoRef)");

        // 두 편집기 모두 뗀 뒤에 draft 전체(모든 장 + 표지)를 보고 판단한다.
        for (String name : new String[]{
                "guest-diary-editor.js", "guest-diary-cover-editor.js"}) {
            assertThat(script(name)).as(name)
                    .contains("const removedPhotoRef = photoRefOf(elementId);")
                    .contains("releasePhoto(removedPhotoRef);")
                    .contains("function isPhotoStillUsed(photoRef)")
                    .contains("saved.pages.some((item) => usesPhoto(item.elements, photoRef))");
        }
    }

    /** 10) 체험 다이어리를 버리면 그 draft 의 사진도 함께 지운다. */
    @Test
    void discardingADraftAlsoRemovesItsPhotos() throws IOException {
        String shelf = script("guest-diary-demo.js");

        assertThat(shelf)
                .contains("async function discardDraft()")
                // draftId 를 먼저 읽고 사진을 지운 뒤에야 draft 를 비운다.
                .contains("await photoStore.deletePhotosForDraft(draft.draftId);")
                .contains("if (!store.clear())");
        assertThat(shelf.indexOf("deletePhotosForDraft"))
                .isLessThan(shelf.indexOf("if (!store.clear())"));

        // 지우는 범위는 언제나 그 draftId 안이다.
        assertThat(script("guest-diary-photo-store.js"))
                .contains("store.index(DRAFT_INDEX).getAllKeys(draftId)")
                .contains("function cleanupOrphans(draftId, validPhotoRefs)");
        // 주인 없는 사진은 책장에 들어올 때 정리한다.
        assertThat(shelf).contains("photoStore.cleanupOrphans(draft.draftId, usedPhotoRefs(draft))");
    }

    /** 11) 사진이 없던 기존 체험 다이어리도 그대로 열린다. */
    @Test
    void anOlderDraftWithoutPhotosStillWorks() throws IOException {
        // photoRef 가 없으면 그 요소는 사진 경로를 타지 않는다.
        assertThat(script("guest-diary-cover-preview.js"))
                .contains("if (element.elementType === 'PHOTO' && element.photoRef)");
        assertThat(script("guest-diary-photo-store.js"))
                // 보관소를 열 수 없어도 예외를 던지지 않고 비어 있는 값을 준다.
                .contains("resolve(null);")
                .contains("return {ok: false, reason: REASON.UNAVAILABLE};");
        // 사진 모듈이 없어도 나머지 체험은 이어진다.
        for (String name : new String[]{
                "guest-diary-editor.js", "guest-diary-cover-editor.js"}) {
            assertThat(script(name)).as(name).contains("global.GuestDiaryPhoto?.");
        }
    }

    /** 15) 받아들이는 파일은 안전한 raster 뿐이고, 한도는 회원과 같다. */
    @Test
    void onlySafeRasterImagesWithinTheMemberLimitAreAccepted() throws IOException {
        String photo = script("guest-diary-photo.js");

        /*
          서버가 실제로 펼쳐 볼 수 있는 형식만 둔다. 한쪽만 넓으면
          "붙일 수는 있는데 내 여행일기로 가져올 수 없는 사진" 이 생긴다.
          (WebP 는 표준 ImageIO 에 reader 가 없다)
        */
        assertThat(photo)
                .contains("'image/jpeg', 'image/png', 'image/gif'")
                .doesNotContain("image/webp")
                // SVG 는 스크립트를 품을 수 있어 받지 않는다.
                .doesNotContain("image/svg")
                // Spring max-file-size 와 같은 10MB
                .contains("const MAX_SIZE = 10 * 1024 * 1024;")
                // 확장자만 믿지 않는다. 실제로 그려지는지까지 본다.
                .contains("function decodedSize(file)")
                .contains("createImageBitmap");
        assertThat(resource("application.yml")).contains("max-file-size: 10MB");
        // 가져올 때 서버가 보는 목록도 같다.
        assertThat(source("service/file/FileUploadService.java"))
                .contains("java.util.Set.of(\"JPEG\", \"JPG\", \"PNG\", \"GIF\")")
                .contains("DIARY_PHOTO_MAX_SIZE = 10L * 1024 * 1024");
    }

    /** 5) 저장 차례는 원본 먼저다. 뒤가 실패하면 방금 넣은 원본을 도로 지운다. */
    @Test
    void aFailedDraftWriteRollsBackTheStoredBlob() throws IOException {
        String photo = script("guest-diary-photo.js");
        String attach = photo.substring(photo.indexOf("async function attachFile("));

        // 1) 검증 → 2) IndexedDB → 3) draft → 4) 화면
        assertThat(attach.indexOf("await inspect(file)"))
                .isLessThan(attach.indexOf("await store.savePhoto("));
        assertThat(attach.indexOf("await store.savePhoto("))
                .isLessThan(attach.indexOf("element = host.attach({"));
        // draft 에 남기지 못했으면 원본을 되돌린다.
        assertThat(attach)
                .contains("await store.deletePhoto(saved.photoRef);")
                .contains("return {ok: false, message: '사진을 붙이지 못했습니다.'};");
    }

    /** 14) 되살린 사진도 옮기기/크기/회전이 붙는다. */
    @Test
    void restoredPhotosAreHandedToTheCanvasEngine() throws IOException {
        for (String name : new String[]{
                "guest-diary-editor.js", "guest-diary-cover-editor.js"}) {
            String editor = script(name);
            String restore = editor.substring(editor.indexOf("function restorePhoto(element)"));
            assertThat(restore).as(name)
                    .contains("global.GuestDiaryPhoto")
                    .contains(".restore(element, elementUrls(element.elementId)")
                    .contains("global.diaryCanvas?.register(item);");
        }
    }

    /** 저장 공간이 부족하거나 IndexedDB 를 쓸 수 없으면 사진만 막고 안내한다. */
    @Test
    void storageFailuresOnlyBlockThePhotoAndExplainWhy() throws IOException {
        assertThat(script("guest-diary-photo-store.js"))
                .contains("QUOTA_EXCEEDED")
                .contains("error.name === 'QuotaExceededError'");
        assertThat(script("guest-diary-photo.js"))
                .contains("'브라우저 저장 공간이 부족해 사진을 추가할 수 없습니다.'")
                .contains("'이 브라우저에서는 사진 임시 저장을 사용할 수 없습니다.'");
    }

    /**
     * 1) 2) 회원 업로더는 "실제 업로드 주소가 있는 화면" 에서만 붙는다.
     *
     * <p>버그: 회원 표지 업로더가 class 만 보고 붙는 바람에 체험 화면의 고르개까지 잡았다.
     * 업로드 주소(data-create-url)는 그 뒤 upload() 안에서야 확인해서, 그때는 이미
     * change 처리가 돌아 input.value 를 비운 뒤였다. 그래서 사진은 붙지 않고
     * CSRF 문구만 떴다. 붙잡는 기준을 주소 유무로 옮겨 그 경계를 고정한다.
     */
    @Test
    void theMemberUploaderOnlyBindsWhereAServerUploadUrlExists() throws IOException {
        String coverUploader = script("diary-cover-photo.js");

        // 업로드 주소가 실려 있는 고르개만 잡는다.
        assertThat(coverUploader)
                .contains("document.querySelectorAll('.diary-cover-photo-input[data-create-url]')");
        // 마크업 생성기 등록은 그 가드보다 앞이라 체험 화면에서도 쓸 수 있다. (8)
        assertThat(coverUploader.indexOf("window.diaryElementRenderers.PHOTO = render;"))
                .isLessThan(coverUploader.indexOf("if (!inputs.length) return;"));

        // 체험 화면의 고르개에는 업로드 주소가 없다. 따라서 회원 업로더가 잡지 않는다.
        for (String template : new String[]{
                "templates/diary/demo-cover.html", "templates/diary/demo-edit.html"}) {
            assertThat(resource(template)).as(template)
                    .contains("data-guest-photo-input")
                    .doesNotContain("data-create-url=@{/diaries/cover-designs")
                    .doesNotContain("data-create-url=@{/diaries/{diaryId}");
        }

        // 회원 페이지 업로더는 원래부터 자기 폼 안으로만 훑는다. (체험 화면에는 그 폼이 없다)
        assertThat(script("diary-photo-picker.js"))
                .contains("document.querySelectorAll('.diary-photo-add')")
                .contains("form.querySelectorAll('.diary-photo-input')");
        assertThat(resource("templates/diary/demo-edit.html"))
                .doesNotContain("diary-photo-add");
    }

    /** 3) 4) 5) 체험 사진 경로에는 CSRF 조회도 업로드도 없다. */
    @Test
    void theGuestPhotoPathNeverAsksForACsrfToken() throws IOException {
        for (String name : new String[]{
                "guest-diary-photo.js", "guest-diary-photo-store.js",
                "guest-diary-editor.js", "guest-diary-cover-editor.js"}) {
            assertThat(script(name)).as(name)
                    .doesNotContain("_csrf")
                    .doesNotContain("csrfToken")
                    .doesNotContain("보안 토큰");
        }
        // 체험 화면에도 CSRF meta/input 을 두지 않았다. (증상을 덮지 않았다는 확인)
        for (String template : new String[]{
                "templates/diary/demo-cover.html", "templates/diary/demo-edit.html"}) {
            assertThat(resource(template)).as(template)
                    .doesNotContain("_csrf");
        }
        // 체험 고르개는 GuestDiaryPhoto 한 경로로만 처리된다.
        assertThat(script("guest-diary-photo.js"))
                .contains("document.querySelectorAll('[data-guest-photo-input]')");
    }

    /**
     * 5) 6) 네 흐름(회원/체험 × 페이지/표지)이 같은 초기 비율 정책을 쓴다.
     *
     * <p>버그: 일반 사진(FULL)만 원본 비율을 보지 않고 늘 정사각 상대값이었다.
     * 세로로 긴 표지에서는 그 상자가 세로가 되어 가로 사진의 좌우가 잘렸다.
     */
    @Test
    void everyPhotoFlowSizesFullPhotosFromTheOriginalRatio() throws IOException {
        // 회원 페이지 / 회원 표지: 두 모습 모두 원본 비율에서 크기를 구한다.
        assertThat(source("controller/diary/DiaryController.java"))
                .contains("double photoRatio = DiaryPhotoFrame.ratioOf(image);")
                .contains("? DiaryPhotoFrame.fullSize(photoRatio,")
                .contains(": DiaryPhotoFrame.polaroidSize(photoRatio,");
        assertThat(source("service/diary/DiaryCoverDesignElementServiceImpl.java"))
                .contains("? DiaryPhotoFrame.fullSize(")
                .contains(": DiaryPhotoFrame.polaroidSize(")
                // 예전처럼 정사각 상대값을 그대로 넣지 않는다.
                .doesNotContain("element.setWidth(PHOTO_SIZE);");

        // 체험 페이지 / 체험 표지: 같은 셈을 JS 로 옮겨 둔 것을 쓴다.
        String guestPhoto = script("guest-diary-photo.js");
        assertThat(guestPhoto)
                .contains("function fullSize(photoRatio, canvasAspect)")
                .contains("? fullSize(ratio, host.canvasAspect)")
                .contains(": polaroidSize(ratio, host.canvasAspect)")
                // 예전처럼 정사각으로 두지 않는다.
                .doesNotContain("{width: PHOTO_WIDTH, height: PHOTO_WIDTH}");

        // 4) 캔버스 비율을 인자로 받는다. 페이지와 표지가 서로 다른 값을 넘긴다.
        assertThat(guestPhoto).contains("height = width * canvasAspect / ratio;");
        assertThat(script("guest-diary-editor.js"))
                .contains("canvasAspect: global.GuestDiaryPhoto.PAGE_CANVAS_ASPECT");
        assertThat(script("guest-diary-cover-editor.js"))
                .contains("canvasAspect: global.GuestDiaryPhoto.COVER_CANVAS_ASPECT");
        // 두 층의 캔버스 비율 값이 같아야 같은 모습이 된다.
        assertThat(guestPhoto)
                .contains("const PAGE_CANVAS_ASPECT = 41 / 38;")
                .contains("const COVER_CANVAS_ASPECT = 3 / 4;");
        assertThat(source("service/diary/DiaryPhotoFrame.java"))
                .contains("PAGE_CANVAS_ASPECT = 41.0 / 38.0")
                .contains("COVER_CANVAS_ASPECT = 3.0 / 4.0");
    }

    /**
     * 8) 이미 저장해 둔 사진은 크기를 다시 세지 않는다.
     * 되살리기는 저장된 width/height 를 그대로 쓴다.
     */
    @Test
    void restoringAnExistingPhotoKeepsItsStoredSize() throws IOException {
        String guestPhoto = script("guest-diary-photo.js");
        String restore = guestPhoto.substring(
                guestPhoto.indexOf("async function restore(element, urls, alt)"));

        // 되살리기에는 크기 계산이 없다. 저장된 값을 그대로 넘긴다.
        assertThat(restore)
                .doesNotContain("fullSize(")
                .doesNotContain("polaroidSize(");
        assertThat(guestPhoto)
                .contains("width: element.width")
                .contains("height: element.height");
        // 크기 계산은 새로 붙일 때만 돈다.
        assertThat(guestPhoto.indexOf("? fullSize(ratio, host.canvasAspect)"))
                .isGreaterThan(guestPhoto.indexOf("async function attachFile("));
    }

    /** 9) 자리/크기/회전/겹침 저장 계약은 그대로다. */
    @Test
    void theGeometrySaveContractIsUnchanged() throws IOException {
        for (String name : new String[]{
                "guest-diary-editor.js", "guest-diary-cover-editor.js"}) {
            assertThat(script(name)).as(name)
                    .contains("patch.positionX = Number(fields.positionX);")
                    .contains("patch.width = Number(fields.width);")
                    .contains("patch.rotation = Number(fields.rotation);")
                    .contains("function moveLayer(elementId, direction)");
        }
        // 조작 엔진은 그대로다. 처음 붙는 크기만 바뀌었다.
        assertThat(script("diary-canvas-drag.js"))
                .contains("item.dataset.positionUrl")
                .contains("item.dataset.sizeUrl")
                .contains("item.dataset.rotationUrl")
                .contains("item.dataset.layerUrl");
    }

    /** 6) 7) 회원의 페이지/표지 사진 업로드 계약은 그대로다. */
    @Test
    void theMemberPhotoUploadIsUnchanged() throws IOException {
        // 표지 사진은 예전처럼 multipart 로 올라간다.
        assertThat(script("diary-cover-photo.js"))
                .contains("const form = new FormData();")
                .contains("form.append('images', file)")
                .contains("method: 'POST'")
                .contains("[csrfHeader]: csrfToken");
        // 페이지 사진도 예전처럼 기존 폼을 그대로 전송한다.
        assertThat(script("diary-photo-picker.js"))
                .contains("form.requestSubmit()")
                .contains(".diary-photo-add");
        assertThat(resource("templates/diary/detail.html"))
                .contains("class=\"diary-photo-add\"")
                .contains("enctype=\"multipart/form-data\"")
                .contains("elements/photo");
        assertThat(resource("templates/diary/cover-design-edit.html"))
                .contains("class=\"diary-cover-photo-input\"")
                .contains("@{/diaries/cover-designs/{id}/elements/photo");
        // 서버 저장 경로도 그대로다.
        assertThat(source("controller/diary/DiaryCoverDesignController.java"))
                .contains("@PostMapping(\"/{designId:\\\\d+}/elements/photo\")");
        assertThat(source("controller/diary/DiaryController.java"))
                .contains("/elements/photo");
    }

    private String script(String name) throws IOException {
        return resource("static/js/" + name);
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/example/travlediary").resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
