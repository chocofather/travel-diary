package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 체험 여행일기를 저장하러 로그인했다가 돌아오는 길의 계약.
 *
 * <p>지키는 것은 네 가지다. 체험 내용이 인증 과정에서 그대로 남는다는 것,
 * 쪽지에 내용을 복사하지 않는다는 것, 돌아갈 자리가 고정 내부 경로 하나라는 것,
 * 그리고 쪽지가 없는 평소 로그인은 예전 그대로라는 것.
 */
class GuestDiaryImportIntentContractTest {

    /** 1) "로그인하고 저장하기" 는 쪽지를 남기고 가져오기 자리로 간다. */
    @Test
    void theSaveButtonLeavesAnIntentAndHeadsForTheImportPage() throws IOException {
        assertThat(script("guest-diary-editor.js"))
                .contains("global.GuestDiaryImportIntent.startLogin(store.getDraft()?.draftId);");

        String intent = script("guest-diary-import-intent.js");
        assertThat(intent)
                .contains("const KEY = 'travelDiary.guestDiaryImportIntent.v1';")
                .contains("const IMPORT_PATH = '/diaries/import';")
                .contains("startLogin(draftId)")
                .contains("remember(draftId);")
                .contains("global.location.href = IMPORT_PATH;");
    }

    /**
     * 2) 5) 6) 인증을 마치면 원래 가려던 자리로 돌아온다.
     *
     * <p>가져오기 자리는 로그인해야 열리므로, 서버가 평소처럼 로그인 화면으로 보내면서
     * 그 주소를 기억해 둔다. 일반 로그인·소셜 로그인·소셜 신규가입이 모두 같은 성공 처리를
     * 쓰므로 한 장치로 이어진다. 새 복귀 장치를 만들지 않았다는 것을 고정한다.
     */
    @Test
    void everyAuthenticationPathGoesThroughTheSameSuccessHandler() throws IOException {
        // 로그인해야 열리는 자리로 밀려나면 서버가 그 주소를 기억한다.
        assertThat(source("config/SecurityConfig.java"))
                .contains("response.sendRedirect(\"/login?redirect=\" + request.getRequestURI());")
                .contains("\"/diaries/**\",  // 개인 여행일기는 본인만 접근");

        // 기억해 둔 자리와 redirect 파라미터를 모두 성공 처리에서 쓴다.
        assertThat(source("config/CustomLoginSuccessHandler.java"))
                .contains("String savedRedirect = savedRequestRedirect(request, response);")
                .contains("request.getParameter(\"redirect\")");

        // 소셜 로그인과 소셜 신규가입도 같은 성공 처리를 부른다.
        assertThat(source("config/SocialOAuth2LoginSuccessHandler.java"))
                .contains("customLoginSuccessHandler.onAuthenticationSuccess(");
        assertThat(source("service/user/SocialSignupAuthenticationService.java"))
                .contains("customLoginSuccessHandler.onAuthenticationSuccess(request, response, authentication);");
    }

    /**
     * 3) 13) 쪽지가 없는 평소 로그인은 예전 그대로다.
     * 인증 쪽 Java 코드를 이번 작업으로 고치지 않았다.
     */
    @Test
    void anOrdinaryLoginIsUntouched() throws IOException {
        String handler = source("config/CustomLoginSuccessHandler.java");

        // 성공 처리에 체험 여행일기를 아는 코드가 없다.
        assertThat(handler)
                .doesNotContain("guest")
                .doesNotContain("Guest")
                .doesNotContain("/diaries/import")
                .doesNotContain("localStorage");
        // 갈 곳이 없으면 예전처럼 첫 화면이다.
        assertThat(handler).contains("response.sendRedirect(\"/\");");

        // 로그인 화면의 기본값도 그대로다. (쪽지가 있을 때만 JS 가 덮어쓴다)
        assertThat(resource("templates/login.html"))
                .contains("<input type=\"hidden\" name=\"redirect\" "
                        + "th:value=\"${param.redirect ?: '/'}\">");
        assertThat(script("guest-diary-import-intent.js"))
                .contains("if (field.value && field.value !== '/') return;")
                .contains("if (!read()) return;");
    }

    /** 8) 가져오기 자리는 로그인 사용자만 들어온다. 체험 경로처럼 열어 두지 않았다. */
    @Test
    void theImportPageIsAuthenticatedOnly() throws IOException {
        String security = source("config/SecurityConfig.java");

        // 열어 둔 체험 경로에 가져오기 자리가 들어 있지 않다.
        // (쿼리를 허용하지만 열리는 경로는 여전히 체험 화면 네 개뿐이다)
        assertThat(security).contains("\"^/diaries/demo(?:/new|/edit|/cover)?(?:\\\\?.*)?$\"");
        /*
          SecurityConfig 가 가져오기를 말하는 곳은 CSRF 목록 한 줄뿐이다.
          permitAll 쪽에 이름이 없으므로 /diaries/** 인증 규칙을 그대로 받는다.
        */
        assertThat(security.lines()
                .filter(line -> line.contains("/diaries/import"))
                .map(String::strip)
                .toList())
                .containsExactly("\"^/diaries/import$\", HttpMethod.POST.name()),");
        assertThat(security).contains("\"/diaries/**\",  // 개인 여행일기는 본인만 접근");

        // 화면과 저장 둘 다 로그인 사용자 전용이다. 열어 둔 경로에 들어 있지 않다.
        String controller = source("controller/diary/GuestDiaryImportController.java");
        assertThat(controller)
                .contains("@GetMapping(\"/diaries/import\")")
                .contains("@PostMapping(\"/diaries/import\")")
                // 소유자는 요청 값이 아니라 로그인 정보로만 정해진다.
                .contains("@AuthenticationPrincipal CustomUserDetails userDetails")
                .contains("userDetails.getId()")
                .doesNotContain("@RequestParam(\"userId\")");
        // 저장 요청에도 CSRF 가 걸린다.
        assertThat(security).contains("\"^/diaries/import$\", HttpMethod.POST.name()");
    }

    /**
     * 12) 13) 돌아갈 자리는 고정 내부 경로 하나뿐이다.
     * 체험 내용이나 사용자가 준 값을 주소로 쓰지 않는다.
     */
    @Test
    void theReturnTargetIsAFixedInternalPath() throws IOException {
        String intent = script("guest-diary-import-intent.js");

        // 쪽지에 적힌 값을 주소로 쓰지 않는다. 상수 하나만 쓴다.
        assertThat(intent)
                .contains("field.value = IMPORT_PATH;")
                .doesNotContain("parsed.redirect")
                .doesNotContain("location.search")
                .doesNotContain("document.referrer");
        // 바깥 주소를 만들 재료가 없다.
        assertThat(intent)
                .doesNotContain("http://")
                .doesNotContain("https://")
                .doesNotContain("//' +");

        // 기존 안전 검사도 그대로다. (성공 처리는 언제나 이 검사를 지난다)
        assertThat(source("config/CustomLoginSuccessHandler.java"))
                .contains("InternalRedirectValidator.normalize");
        assertThat(source("config/InternalRedirectValidator.java"))
                .contains("candidate.startsWith(\"//\")")
                .contains("uri.isAbsolute()");
    }

    /** 13) 쪽지에는 어느 다이어리였는지와 언제였는지만 남는다. */
    @Test
    void theIntentCarriesNoDiaryContent() throws IOException {
        String intent = script("guest-diary-import-intent.js");
        String remember = intent.substring(intent.indexOf("function remember(draftId)"));

        assertThat(remember.substring(0, remember.indexOf("function clear()")))
                .contains("draftId: draftId")
                .contains("requestedAt: new Date().toISOString()")
                // 제목·본문·사진·photoRef 같은 내용은 담지 않는다.
                .doesNotContain("title")
                .doesNotContain("pages")
                .doesNotContain("photoRef")
                .doesNotContain("coverDesign");

        // 서버 세션이나 주소로 내용을 옮기지도 않는다.
        assertThat(intent)
                .doesNotContain("fetch(")
                .doesNotContain("document.cookie")
                .doesNotContain("sessionStorage");
    }

    /**
     * 10) 11) 15) 16) 체험 내용은 인증 앞뒤로 그대로 남는다.
     * 지우는 것은 쪽지뿐이고, draft 와 사진은 건드리지 않는다.
     */
    @Test
    void theGuestDataSurvivesEveryStepOfAuthentication() throws IOException {
        // 쪽지 모듈은 자기 key 만 지운다.
        String intent = script("guest-diary-import-intent.js");
        assertThat(intent)
                .contains("store.removeItem(KEY)")
                .doesNotContain("TravelDiaryGuestDraftStore")
                .doesNotContain("TravelDiaryGuestPhotoStore")
                .doesNotContain("indexedDB");

        /*
          가져오기 화면은 저장이 끝난 뒤에만 체험 내용을 지운다.
          지우는 코드가 성공 처리 안에만 있고, 보내기 전에는 없다는 것을 자리로 고정한다.
        */
        String importScript = script("guest-diary-import.js");
        assertThat(importScript)
                .contains("store.getDraft()")
                .contains("async function discardGuestData()")
                .contains("await photoStore.deletePhotosForDraft(draft.draftId);")
                .contains("store.clear();");
        // 보내기(send)에는 지우는 코드가 없다.
        String send = importScript.substring(
                importScript.indexOf("async function send()"),
                importScript.indexOf("async function discardGuestData()"));
        assertThat(send)
                .doesNotContain("store.clear()")
                .doesNotContain("deletePhotosForDraft");
        // 쪽지만 먼저 지운다. 다음 로그인이 이 화면으로 다시 끌려오지 않게 하려는 것뿐이다.
        assertThat(importScript).contains("intent?.clear();");

        // 로그인 화면에 붙인 것도 값을 채우는 일뿐이다.
        assertThat(script("guest-diary-import-login.js"))
                .contains("applyToLoginForm()")
                .doesNotContain("clear()");
    }

    /** 9) 가져올 것이 없으면 화면을 죽이지 않고 안내만 한다. */
    @Test
    void anEmptyBrowserGetsAFriendlyNoticeInsteadOfAnError() throws IOException {
        assertThat(script("guest-diary-import.js"))
                .contains("const found = draft !== null;")
                .contains("show(empty, !found);");
        assertThat(resource("templates/diary/import.html"))
                .contains("가져올 체험 여행일기가 없습니다.")
                // 이 브라우저에만 남는다는 개념을 문구에 담는다. (다른 기기는 비어 있는 게 정상)
                .contains("체험 여행일기는 만들던 브라우저에만 남아 있어요.")
                .contains("나의 여행일기로");
    }

    /** 10) "나중에 하기" 는 체험 내용을 지우지 않는다. */
    @Test
    void theLaterButtonKeepsEverything() throws IOException {
        assertThat(resource("templates/diary/import.html"))
                .contains("data-guest-import-later>나중에 하기</a>")
                // 그냥 내 여행일기로 가는 링크다.
                .contains("<a class=\"diary-form-cancel\" th:href=\"@{/diaries}\"");
        /*
          체험 내용(draft·사진)은 그대로 둔다. 거두는 것은 "저장하러 로그인한다" 던 쪽지뿐이다.
          지금 하지 않겠다는 뜻이므로 다음에 이 화면에 들어왔을 때 묻지 않고 저장되면 안 된다.
        */
        String later = section(script("guest-diary-import.js"),
                "page.querySelector('[data-guest-import-later]')", "function backToConfirm()");
        assertThat(later)
                .contains("intent?.clear()")
                .doesNotContain("store.clear()")
                .doesNotContain("deletePhotosForDraft");

        // 그래서 나중에 다시 들어올 길이 목록 위에 필요하다.
        assertThat(resource("templates/diary/list.html"))
                .contains("data-guest-import-notice")
                .contains("이 브라우저에 체험 중 작성한 여행일기가 있습니다.")
                .contains("th:href=\"@{/diaries/import}\">가져오기</a>");
        // 남은 것이 없으면 아무것도 보여 주지 않는다.
        assertThat(script("guest-diary-import-notice.js"))
                .contains("notice.hidden = !store.hasDraft();");
    }

    /** 16) 쪽지는 수명이 있고, 만료돼도 체험 내용은 살아남는다. */
    @Test
    void theIntentExpiresButTheDraftDoesNot() throws IOException {
        String intent = script("guest-diary-import-intent.js");

        assertThat(intent)
                .contains("const MAX_AGE_MS = 60 * 60 * 1000;")
                .contains("Date.now() - requestedAt > MAX_AGE_MS");
        // 만료는 "자동 이동에 쓰지 않는다" 일 뿐이다. 여기에서 아무것도 지우지 않는다.
        String read = intent.substring(intent.indexOf("function read()"),
                intent.indexOf("function remember(draftId)"));
        assertThat(read.substring(read.indexOf("MAX_AGE_MS)")))
                .doesNotContain("clear()");
    }

    /** 14) 저장 버튼이 실제 가져오기에 이어져 있다. */
    @Test
    void theConfirmButtonIsWiredToTheRealImport() throws IOException {
        String importScript = script("guest-diary-import.js");

        assertThat(importScript)
                .contains("global.GuestDiaryImport = {")
                .contains("async start()")
                .contains("diaryId = await send();")
                .contains("method: 'POST'")
                .contains("[csrfHeader]: csrfToken");
        assertThat(resource("templates/diary/import.html"))
                .contains("data-guest-import-confirm")
                // 준비 중이라는 안내는 더 이상 두지 않는다.
                .doesNotContain("저장 기능은 준비 중입니다.");
    }

    private String script(String name) throws IOException {
        return resource("static/js/" + name);
    }

    /** 파일의 한 토막. 시작 표시부터 다음 표시 직전까지만 본다. */
    private String section(String content, String from, String until) {
        int start = content.indexOf(from);
        int end = content.indexOf(until, start + 1);
        assertThat(start).as("시작 표시를 찾지 못했습니다: " + from).isNotNegative();
        assertThat(end).as("끝 표시를 찾지 못했습니다: " + until).isGreaterThan(start);
        return content.substring(start, end);
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
