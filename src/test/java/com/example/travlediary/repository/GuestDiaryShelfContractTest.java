package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비회원 체험의 화면 흐름 계약.
 *
 * <p>고치기 전에는 "체험 시작" 을 누르면 곧바로 페이지 편집기가 열리고, 표지 꾸미기가
 * 그 안의 기능처럼 붙어 있었다. 실제 다이어리 개념과 달라 다음 순서로 바로잡았고,
 * 그 순서를 여기에서 고정한다.
 *
 * <p>책장 → 새 여행일기 → 표지 결정 → 만들어진 다이어리 카드 → 카드를 열어 페이지 작성
 */
class GuestDiaryShelfContractTest {

    /** 3) 4) 책장은 만들어 둔 다이어리를 카드로 보여 주고, 없으면 빈 상태만 남는다. */
    @Test
    void theShelfShowsEitherTheDiaryCardOrAnEmptyState() throws IOException {
        String shelf = script("guest-diary-demo.js");

        assertThat(shelf)
                .contains("const draft = store.getDraft();")
                .contains("show(shelf, draft !== null);")
                .contains("show(empty, draft === null);");

        String template = resource("templates/diary/demo.html");
        assertThat(template)
                .contains("data-guest-shelf-list")
                .contains("data-guest-empty")
                .contains("아직 만든 여행일기가 없습니다.");
        // 예전의 큰 체험 소개 랜딩은 남기지 않는다.
        assertThat(template)
                .doesNotContain("TRY TRAVEL DIARY")
                .doesNotContain("체험 시작하기")
                .doesNotContain("이어서 작성");
    }

    /** 9) 카드는 회원 목록과 같은 시각 체계를 쓴다. 카드를 누르면 페이지 작성으로 들어간다. */
    @Test
    void theCardReusesTheMemberBookshelfMarkupAndOpensTheEditor() throws IOException {
        String template = resource("templates/diary/demo.html");

        // 회원 /diaries 카드와 같은 클래스 구조다.
        assertThat(template)
                .contains("class=\"diary-list-page diary-list-page--shelf\"")
                .contains("class=\"diary-shelf\"")
                .contains("class=\"diary-book\"")
                .contains("class=\"diary-book-inner\"")
                .contains("class=\"diary-book-cover\"")
                .contains("class=\"diary-book-title\"")
                .contains("class=\"diary-book-period\"")
                .contains("class=\"diary-book-pages\"")
                // 카드 전체가 페이지 작성으로 가는 링크다.
                .contains("<a class=\"diary-book-inner\" th:href=\"@{/diaries/demo/edit}\">");

        // 제목/기간/장 수는 사용자가 쓴 값이라 글자로만 넣는다.
        assertThat(script("guest-diary-demo.js"))
                .contains("[data-guest-book-title]').textContent")
                .contains("period.textContent")
                .contains("[data-guest-book-pages]').textContent");
    }

    /** 표지는 책장과 페이지 편집 화면이 같은 조각을 쓴다. 흉내 낸 렌더러를 따로 두지 않는다. */
    @Test
    void bothScreensShareOneCoverRenderer() throws IOException {
        String preview = script("guest-diary-cover-preview.js");

        // 회원 목록과 같은 두 갈래다. 꾸민 표지면 캔버스, 아니면 무지 표지 + 책등.
        assertThat(preview)
                .contains("function customCanvas(coverDesign)")
                .contains("function presetCover()")
                .contains("diary-cover-canvas")
                .contains("diary-cover-surface")
                .contains("diary-book-placeholder")
                .contains("diary-book-spine");
        // 재질 class 규칙은 DiaryCoverStyle.toCssClass 와 같다.
        assertThat(preview).contains("'diary-cover-' + value.toLowerCase().replace(/_/g, '-')");
        // localStorage 값은 믿을 수 없어 아는 모양만 class 로 바꾼다.
        assertThat(preview).contains("const STYLE_CODE = /^[A-Z][A-Z_]*$/;");

        // 표지를 보여 주는 화면은 책장과 표지 편집기뿐이다. 페이지 편집 화면은 쓰지 않는다.
        assertThat(script("guest-diary-demo.js")).contains("GuestDiaryCoverPreview");
        assertThat(resource("templates/diary/demo.html"))
                .contains("/js/guest-diary-cover-preview.js");
        assertThat(resource("templates/diary/demo-edit.html"))
                .doesNotContain("/js/guest-diary-cover-preview.js");
    }

    /** 6) 만들기 화면은 제공 디자인과 직접 꾸미기 두 갈래를 준다. */
    @Test
    void theCreateScreenOffersPresetAndCustomCovers() throws IOException {
        String template = resource("templates/diary/demo-new.html");

        assertThat(template)
                .contains("data-guest-cover-tab=\"PRESET\"")
                .contains("data-guest-cover-tab=\"CUSTOM\"")
                // 제공 표지·노트 종류는 회원 화면과 같은 조각을 그대로 쓴다.
                .contains("~{diary/cover-style :: picker(${coverStyles}, null)}")
                .contains("~{diary/notebook-type :: picker(${notebookTypes}, null)}")
                // 회원 생성 화면과 같은 기본정보를 받는다.
                .contains("data-guest-title")
                .contains("data-guest-start-date")
                .contains("data-guest-end-date");
        // 사진(대표 이미지) 올리기는 체험에 없다.
        assertThat(template)
                .doesNotContain("type=\"file\"")
                .doesNotContain("coverImage");
    }

    /**
     * 7) 제공 디자인을 고르면 draft 를 만들고 책장으로 간다.
     * 8) 직접 꾸미기를 고르면 골격을 만든 뒤 표지 편집기로 간다.
     */
    @Test
    void thePresetGoesToTheShelfAndTheCustomGoesToTheCoverEditor() throws IOException {
        String create = script("guest-diary-new.js");

        // 고른 값은 회원 다이어리와 같은 의미의 자리에 담긴다.
        assertThat(create)
                .contains("coverType: coverChoice")
                .contains("coverStyle: chosenCoverStyle()")
                .contains("notebookType: chosenNotebookType()")
                // 제공 표지는 code 로만 고른다. 서버 행 번호를 흉내 내지 않는다.
                .contains("input[name=\"coverStyle\"]:checked")
                .doesNotContain("designId");

        assertThat(create).contains(
                "global.location.href = coverChoice === 'CUSTOM'\n"
                        + "            ? page.dataset.coverUrl\n"
                        + "            : page.dataset.shelfUrl;");
    }

    /**
     * 9) 10) 표지는 다이어리 한 권의 속성이라 들어오는 자리도 나가는 자리도 책장 하나다.
     * 새로 만드는 중이든 카드의 "표지 수정" 이든 끝나면 책장으로 돌아간다.
     */
    @Test
    void theCoverEditorAlwaysReturnsToTheShelf() throws IOException {
        assertThat(resource("templates/diary/demo-cover.html"))
                .contains("th:href=\"@{/diaries/demo}\">표지 적용하기</a>")
                .contains("th:href=\"@{/diaries/demo}\">← 나의 여행일기로</a>")
                // 돌아갈 자리가 한 곳뿐이라 어디에서 왔는지 물을 것이 없다.
                .doesNotContain("${param.")
                .doesNotContain("coverReturnUrl");
        assertThat(source("controller/diary/GuestDiaryDemoController.java"))
                .doesNotContain("@RequestParam")
                .doesNotContain("coverReturnUrl");

        // 표지 편집기로 들어가는 자리는 두 곳뿐이고 둘 다 파라미터를 붙이지 않는다.
        assertThat(resource("templates/diary/demo-new.html"))
                .contains("th:data-cover-url=\"@{/diaries/demo/cover}\"");
        assertThat(resource("templates/diary/demo.html"))
                .contains("th:href=\"@{/diaries/demo/cover}\">표지 수정</a>");
    }

    /** 카드의 ⋯ 메뉴는 회원 목록과 같은 조각·같은 스크립트를 쓴다. */
    @Test
    void theCardMenuReusesTheMemberMarkupAndDoesNotOpenTheCard() throws IOException {
        String template = resource("templates/diary/demo.html");

        // 회원 카드와 같은 클래스라 같은 스크립트가 그대로 붙는다.
        assertThat(template)
                .contains("class=\"diary-book-menu\"")
                .contains("class=\"diary-book-menu-button\"")
                .contains("class=\"diary-book-menu-panel\"")
                .contains("class=\"diary-book-menu-item\"")
                .contains("/js/diary-book-menu.js");
        // 메뉴는 카드 링크 바깥에 있다. (링크가 닫힌 뒤에 온다)
        assertThat(template.indexOf("class=\"diary-book-menu\""))
                .isGreaterThan(template.indexOf("</a>"));
        // 그 스크립트가 카드 진입으로 번지지 않게 막는다.
        assertThat(script("diary-book-menu.js"))
                .contains("event.stopPropagation();")
                .contains("panel.addEventListener('click', event => event.stopPropagation());");
    }

    /** 11) 페이지 편집기는 다이어리를 만들지 않는다. 없으면 책장으로 돌려보낸다. */
    @Test
    void theEditorNeverCreatesADiaryAndSendsVisitorsBackToTheShelf() throws IOException {
        String editor = script("guest-diary-editor.js");

        assertThat(editor)
                .contains("if (!draft) {\n        backToShelf();")
                .contains("global.location.replace(page.dataset.shelfUrl);")
                // 다이어리 자체를 만드는 호출이 없다.
                .doesNotContain("store.createDraft");
        assertThat(resource("templates/diary/demo-edit.html"))
                .contains("th:data-shelf-url=\"@{/diaries/demo}\"");
    }

    /** 3) 이미 한 권 있으면 바로 덮어쓰지 않고 먼저 묻는다. */
    @Test
    void creatingASecondDiaryAsksBeforeReplacingTheFirst() throws IOException {
        String shelf = script("guest-diary-demo.js");

        assertThat(shelf)
                .contains("if (!store.hasDraft()) return;")
                .contains("event.preventDefault();")
                .contains("show(replacePrompt, true);")
                // 지우는 것은 새로 만들기를 고른 뒤다.
                .contains("data-guest-action=\"confirm-replace\"");

        assertThat(resource("templates/diary/demo.html"))
                .contains("비회원 체험에서는 여행일기 1개만 만들 수 있어요")
                .contains("새로 만들면 현재 체험 여행일기는 삭제됩니다.")
                .contains("data-guest-action=\"cancel-replace\"");
    }

    /** 12) 이미 만들어 둔 draft 는 구조 변경 뒤에도 그대로 쓰인다. */
    @Test
    void anExistingDraftKeepsItsPagesAndCover() throws IOException {
        // 책장은 읽기만 한다. 열면서 초기화하지 않는다.
        assertThat(script("guest-diary-demo.js"))
                .doesNotContain("store.createDraft")
                .doesNotContain("store.addPage");
        // 지우는 경로는 "새로 만들기" 확인 하나뿐이다.
        assertThat(script("guest-diary-demo.js").split("store.clear\\(", -1).length - 1)
                .isEqualTo(1);
        // 저장해 둔 값은 읽을 때 모양만 맞추고 버리지 않는다.
        assertThat(script("guest-diary-draft-store.js"))
                .contains("coverDesign: normalizeCoverDesign(raw.coverDesign)")
                .contains("pages: pages.slice(0, MAX_PAGES)");
    }

    /** 13) 14) 체험 흐름 전체에 서버 저장 요청이 없다. */
    @Test
    void noStepOfTheGuestFlowTalksToTheServer() throws IOException {
        for (String name : new String[]{
                "guest-diary-demo.js", "guest-diary-new.js",
                "guest-diary-cover-preview.js"}) {
            assertThat(script(name)).as(name)
                    .doesNotContain("fetch(")
                    .doesNotContain("XMLHttpRequest")
                    .doesNotContain("$.ajax")
                    .doesNotContain("FormData")
                    .doesNotContain("/uploads");
        }
        /*
          만들기 화면은 회원 화면과 같은 폼을 쓰되(required 검사를 그대로 받기 위해서다)
          보낼 곳이 없다. action 도 method 도 두지 않아 서버로 나가지 않는다.
        */
        assertThat(resource("templates/diary/demo-new.html"))
                .contains("<form class=\"diary-form\" data-guest-form>")
                .doesNotContain("th:action")
                .doesNotContain("method=\"post\"");

        // 컨트롤러는 화면만 내려준다. 저장용 endpoint 를 열지 않는다.
        String controller = source("controller/diary/GuestDiaryDemoController.java");
        assertThat(controller)
                .contains("return \"diary/demo\";")
                .contains("return \"diary/demo-new\";")
                .contains("return \"diary/demo-edit\";")
                .contains("return \"diary/demo-cover\";")
                .doesNotContain("@PostMapping")
                .doesNotContain("@PutMapping")
                .doesNotContain("@DeleteMapping");
    }

    /** 14) 회원의 목록/생성/표지 흐름은 그대로다. */
    @Test
    void theMemberDiaryFlowIsUnchanged() throws IOException {
        assertThat(resource("templates/diary/list.html"))
                .contains("th:href=\"@{/diaries/{id}(id=${diary.id})}\"")
                .contains("@{/diaries/new}");
        assertThat(resource("templates/diary/new.html"))
                .contains("th:action=\"@{/diaries}\"")
                .contains("name=\"coverSelectionType\"")
                .contains("name=\"customCoverDesignId\"")
                .contains("name=\"coverImage\"");
        // 회원 화면이 쓰는 조각은 그대로 있고, 체험 화면이 같은 조각을 함께 쓴다.
        assertThat(resource("templates/diary/cover-style.html"))
                .contains("th:fragment=\"picker(styles, selected)\"");
        assertThat(resource("templates/diary/notebook-type.html"))
                .contains("th:fragment=\"picker(types, selected)\"");
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
