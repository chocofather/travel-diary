package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코스 댓글 사진 업로드 UI 계약.
 * 서버는 multipart 로 List&lt;MultipartFile&gt; images 를 받으므로 화면도 같은 형식으로 보내야 한다.
 */
class CourseCommentImageUiContractTest {

    @Test
    void commentFormSendsUpToThreeFilesUnderTheImagesName() throws IOException {
        String detail = readFile("src/main/resources/templates/course/detail.html");
        String input = between(detail, "id=\"course-comment-image-input\"", ">");

        assertThat(input)
                .contains("name=\"images\"")
                .contains("multiple");
        // 폼 자체도 multipart 로 전송한다
        assertThat(between(detail, "id=\"course-comment-form\"", ">"))
                .contains("enctype=\"multipart/form-data\"");
        // 사진 버튼과 미리보기 영역(기본 숨김)이 함께 있다
        assertThat(detail)
                .contains("for=\"course-comment-image-input\" class=\"image-upload-label\"")
                .contains("class=\"comment-image-preview\" id=\"course-comment-image-preview\" hidden");
    }

    @Test
    void replyFormUsesTheSameFieldNameAndPhotoButtonAsTheCommentForm() throws IOException {
        String script = resource("/static/js/course-comments.js");

        assertThat(script)
                .contains("imageInput.name = 'images'")
                .contains("imageInput.multiple = true")
                .contains("uploadLabel.className = 'image-upload-label'")
                // 답글 폼에도 미리보기 영역을 붙인다
                .contains("preview.className = 'comment-image-preview'");
    }

    @Test
    void submitIsBlockedBeforeSendingMoreThanThreeImages() throws IOException {
        String script = resource("/static/js/course-comments.js");

        assertThat(script)
                .contains("const MAX_COMMENT_IMAGES = 3")
                .contains("사진은 최대 ${MAX_COMMENT_IMAGES}장까지 첨부할 수 있습니다.");
        // 댓글/답글 두 경로 모두 전송 전에 확인한다
        assertThat(script.split("picker\\?\\.exceedsLimit\\(\\)\\) return;", -1)).hasSize(3);
    }

    @Test
    void reopeningTheFilePickerAccumulatesInsteadOfReplacingTheSelection() throws IOException {
        String script = resource("/static/js/course-comments.js");

        assertThat(script)
                // 선택 목록을 따로 들고 DataTransfer 로 input.files 와 동기화한다
                .contains("let selected = []")
                .contains("new DataTransfer()")
                .contains("input.files = transfer.files")
                .contains("addFiles(input.files)")
                // 같은 파일(name|size|lastModified)은 다시 담지 않는다
                .contains("`${file.name}|${file.size}|${file.lastModified}`")
                .contains("keys.has(fileKey(file))")
                .contains("selected = merged.slice(0, MAX_COMMENT_IMAGES)")
                // 썸네일마다 개별 제거 버튼을 붙이고, 비면 영역을 감춘다
                .contains("comment-image-preview-item")
                .contains("comment-image-remove")
                .contains("preview.hidden = selected.length === 0");
    }

    @Test
    void createSendsMultipartWhileOtherRequestsStayJson() throws IOException {
        String script = resource("/static/js/course-comments.js");

        // 등록(댓글/답글)은 FormData 로 보낸다
        assertThat(script.split("const body = new FormData\\(\\);", -1)).hasSize(3);
        assertThat(script)
                .contains("body.append('courseId', String(courseId))")
                .contains("body.append('content'")
                .contains("body.append('replyToCommentId', String(replyForm.dataset.replyToCommentId))")
                .contains("body.append('images', file)")
                // boundary 는 브라우저가 붙여야 하므로 Content-Type 을 직접 지정하지 않는다
                .contains("const sendsFormData = options.body instanceof FormData")
                .contains("sendsFormData ? {'Accept': 'application/json'} : jsonHeaders")
                // 등록 경로에서는 더 이상 JSON 본문을 만들지 않는다
                .doesNotContain("JSON.stringify({courseId")
                // 수정은 기존 JSON 요청 그대로다 (사진 변경 경로는 만들지 않는다)
                .contains("body: JSON.stringify({content: textarea.value})");
    }

    @Test
    void registrationResetsTextareaSelectionAndPreview() throws IOException {
        String script = resource("/static/js/course-comments.js");
        String submit = between(script, "form?.addEventListener('submit'", "contentInput?.addEventListener");

        assertThat(submit)
                .contains("form.reset()")
                .contains("picker?.clear()")
                .contains("lengthOutput.textContent = '0'");
        // 답글 폼을 닫거나 교체할 때도 선택/미리보기를 정리한다
        assertThat(script)
                .contains("function removeReplyForm(replyForm)")
                .contains("imagePickers.get(replyForm)?.clear();")
                .contains("removeReplyForm(list.querySelector('.course-comment-reply-form'))")
                .contains("removeReplyForm(event.target.closest('.course-comment-reply-form'))");
    }

    @Test
    void writeFormMatchesTheCommunityCommentCardDesign() throws IOException {
        String css = resource("/static/css/course-detail.css");
        String postCss = resource("/static/css/post-detail.css");

        String courseCard = between(css, ".course-comment-form {", "}");
        String postCard = between(postCss, ".post-comment-form {", "}");
        for (String rule : new String[]{"gap: 12px", "padding: 16px",
                "border: 1px solid #ddd", "border-radius: 10px"}) {
            assertThat(courseCard).as("course card %s", rule).contains(rule);
            assertThat(postCard).as("post card %s", rule).contains(rule);
        }
        assertThat(css)
                .contains(".course-comments .image-upload-label")
                .contains(".course-comments .comment-image-preview")
                .contains(".course-comments .comment-image-preview[hidden]")
                .contains(".course-comments .comment-image-preview-item")
                .contains(".course-comments .comment-image-remove");
    }

    @Test
    void commentBodyRendersImageUrlsAndSkipsTheAreaWhenThereAreNone() throws IOException {
        String script = resource("/static/js/course-comments.js");
        String renderer = between(script, "function makeCommentImages(comment)", "function renderComment");

        assertThat(renderer)
                // 서버가 내려준 imageUrls 를 그대로 쓴다 (등록 직후 응답/새로고침 조회 모두 같은 필드)
                .contains("comment.imageUrls")
                // 사진이 없으면 영역 자체를 만들지 않는다
                .contains("if (imageUrls.length === 0) return null;")
                // 기존 createElement 방식을 유지한다 (innerHTML 사용 안 함)
                .contains("document.createElement('img')")
                .contains("comment-images content-comment-images")
                .contains("comment-image content-comment-image")
                .doesNotContain("innerHTML");
        // 본문 아래, 액션 위에 붙는다 (원댓글/답글 모두 같은 renderComment 를 쓴다)
        assertThat(script)
                .contains("body.append(meta, content);")
                .contains("if (images) body.append(images);")
                .contains("body.append(actions);");
    }

    @Test
    void deletedAndModeratedCommentsNeverRenderImages() throws IOException {
        String script = resource("/static/js/course-comments.js");
        String deletedBranch = between(script, "if (comment.deleted) {", "return item;");

        // 삭제·조치 댓글은 플레이스홀더만 남기고 끝난다 (기존 정책 유지)
        assertThat(deletedBranch)
                .contains("관리자에 의해 조치된 댓글입니다.")
                .contains("삭제된 댓글입니다.")
                .doesNotContain("makeCommentImages");
    }

    @Test
    void enlargedImageModalCyclesInsideOneCommentAndClosesEveryUsualWay() throws IOException {
        String detail = readFile("src/main/resources/templates/course/detail.html");
        String script = resource("/static/js/course-comments.js");

        assertThat(detail)
                .contains("id=\"course-comment-image-modal\"")
                .contains("class=\"comment-image-nav prev\"")
                .contains("class=\"comment-image-nav next\"")
                .contains("id=\"course-comment-modal-img\"")
                .contains("class=\"close-btn\"");
        assertThat(script)
                // 클릭한 댓글의 사진 목록 안에서만 순환한다
                .contains("const group = target.closest('.comment-images');")
                .contains("index = (nextIndex + images.length) % images.length;")
                // 클릭한 사진부터 보여준다 (주소가 아니라 위치로 찾는다)
                .contains("show(Math.max(items.indexOf(target), 0));")
                // 사진이 한 장이면 좌/우 버튼을 감춘다
                .contains("modal.classList.toggle('is-single', images.length <= 1);")
                // 좌/우 버튼 클릭이 닫기로 전파되지 않는다
                .contains("event.stopPropagation();")
                // 확대 이미지 / 배경 / 닫기 버튼 클릭 모두 닫는다
                .contains("event.target === modalImage")
                .contains("event.target === modal")
                .contains("event.target.closest('.close-btn')")
                // 키보드 이동/닫기 (닫혀 있으면 간섭하지 않는다)
                .contains("event.key === 'Escape'")
                .contains("event.key === 'ArrowLeft'")
                .contains("event.key === 'ArrowRight'")
                .contains("if (!isOpen()) return;");
    }

    @Test
    void commentImageAndModalStylesAreScopedToTheCommentSection() throws IOException {
        String css = resource("/static/css/course-detail.css");

        assertThat(css)
                .contains(".course-comments .comment-images")
                .contains(".course-comments .comment-images .comment-image")
                .contains(".course-comments .comment-images .comment-image:hover")
                .contains(".course-comments .image-modal")
                .contains(".course-comments .image-modal .comment-image-nav")
                .contains(".course-comments .image-modal.is-single .comment-image-nav");
        // 원본 비율을 지키면서 화면을 넘지 않는다
        String modalImage = between(css, ".course-comments .image-modal img.modal-content", "}");
        assertThat(modalImage)
                .contains("max-width: 90%")
                .contains("max-height: 90%");
        // 모바일에서도 닫기/이동 버튼이 보이게 위치를 조정한다
        String mobile = between(css, "/* 좁은 화면에서도 닫기/이동 버튼이 사진에 가리지 않게 안쪽으로 당긴다 */",
                ".course-comments .image-modal .comment-image-nav.prev");
        assertThat(mobile)
                .contains(".course-comments .image-modal .close-btn")
                .contains("width: 40px");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readFile(String path) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)),
                StandardCharsets.UTF_8);
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
