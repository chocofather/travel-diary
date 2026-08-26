package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅창 안에서 투표 만들기.
 *
 * <p>연결은 하나뿐이고, 사용자가 쓴 글은 글자로만 그리며,
 * 아직 만들지 않은 투표 참여는 흉내조차 내지 않는다.
 */
class TravelPlanPollUiContractTest {

    // ── 진입점 ──────────────────────────────────────────────

    @Test
    void thePlusButtonOpensASmallToolMenuWithOneItem() throws IOException {
        String detail = detailHtml();
        String tools = between(detail, "class=\"travel-plan-chat-tools\"", "</form>");

        assertThat(tools)
                .contains("data-travel-plan-chat-tool")
                .contains("data-travel-plan-chat-tool-menu")
                .contains("투표 만들기");
        // 지금은 도구가 하나뿐이다. 큰 기능 메뉴를 미리 만들지 않는다
        assertThat(countOf(tools, "travel-plan-chat-tool-action")).isEqualTo(1);
        // 메뉴는 눌러야 열린다
        assertThat(between(tools, "travel-plan-chat-tool-menu\"", ">")).contains("hidden");
    }

    @Test
    void theEntryPointIsOpenToEveryActiveMemberNotJustTheOwner() throws IOException {
        String detail = detailHtml();

        // 방장 전용 조건이 붙지 않는다. 참여자면 누구나 만든다
        assertThat(between(detail, "class=\"travel-plan-chat-tools\"", "</form>"))
                .doesNotContain("OWNER");
        assertThat(between(detail, "class=\"travel-plan-poll-modal\"",
                "data-travel-plan-poll-modal"))
                .contains("travelPlan.currentMember != null")
                .doesNotContain("OWNER");
    }

    // ── 모달 ────────────────────────────────────────────────

    @Test
    void theModalHasEverythingAPollNeeds() throws IOException {
        String detail = detailHtml();

        for (String hook : new String[]{
                "data-travel-plan-poll-modal", "data-travel-plan-poll-form",
                "data-travel-plan-poll-question", "data-travel-plan-poll-options",
                "data-travel-plan-poll-add", "data-travel-plan-poll-selection",
                "data-travel-plan-poll-back", "data-travel-plan-poll-submit",
                "data-travel-plan-poll-error"}) {
            assertThat(detail).as("modal has %s", hook).contains(hook);
        }
        // 열기 전까지 떠 있지 않다
        assertThat(between(detail, "class=\"travel-plan-poll-modal\"", ">")).contains("hidden");
        // 질문 길이는 DB 컬럼(varchar 200)에 맞춘다
        assertThat(between(detail, "class=\"travel-plan-poll-question\"",
                "data-travel-plan-poll-question"))
                .contains("maxlength=\"200\"");
    }

    @Test
    void hidingTheModalActuallyHidesIt() throws IOException {
        String css = cssFile();

        // display 를 정해 두면 브라우저 기본 [hidden] 규칙을 덮어써 계속 보인다
        assertThat(between(css, ".travel-plan-poll-modal {", "}")).contains("display: flex");
        assertThat(css).contains(".travel-plan-poll-modal[hidden] {\n    display: none;\n}");
    }

    @Test
    void theModalStaysInsideNarrowScreens() throws IOException {
        String panel = between(cssFile(), ".travel-plan-poll-modal-panel {", "}");

        assertThat(panel)
                .contains("width: min(420px, 100%)")
                .contains("max-height: min(600px, calc(100vh - 40px))")
                .contains("box-sizing: border-box");
        // 선택지 입력칸도 남은 폭 안에서만 커진다
        assertThat(between(cssFile(), ".travel-plan-poll-question,", "}"))
                .contains("box-sizing: border-box")
                .contains("max-width: 100%")
                .contains("min-width: 0");
    }

    @Test
    void theModalOpensWithTwoEmptyOptionRows() throws IOException {
        String poll = pollJs();

        assertThat(between(poll, "function resetForm()", "\n    }"))
                .contains("optionList.replaceChildren(optionRow(\"\"), optionRow(\"\"))");
        assertThat(poll).contains("const MIN_OPTIONS = 2");
    }

    @Test
    void optionRowsAreAddedAndRemovedWithinTheLimits() throws IOException {
        String poll = pollJs();

        assertThat(poll).contains("const MAX_OPTIONS = 10");
        // 두 개까지는 남겨 둔다
        assertThat(between(poll, "remove.addEventListener(\"click\"", "});"))
                .contains("if (rows().length <= MIN_OPTIONS) return");
        // 상한에 닿으면 더 만들 수 없다
        assertThat(between(poll, "addButton?.addEventListener(\"click\"", "});"))
                .contains("if (rows().length >= MAX_OPTIONS) return");
        assertThat(between(poll, "function renderOptionControls()", "\n    }"))
                .contains("addButton.hidden = current.length >= MAX_OPTIONS");
    }

    @Test
    void theRowPositionIsNeverTreatedAsADatabaseId() throws IOException {
        String poll = pollJs();

        // 화면의 몇 번째 줄인지는 저장할 때의 순서로만 쓴다
        assertThat(between(poll, "function readForm()", "\n    }"))
                .contains("options: rows().map(")
                .doesNotContain("id:");
        assertThat(poll).doesNotContain("data-option-id");
    }

    @Test
    void closingTheModalThrowsAwayWhatWasTyped() throws IOException {
        String poll = pollJs();

        assertThat(between(poll, "function closeModal()", "\n    }"))
                .contains("modal.hidden = true")
                .contains("resetForm()");
        // Esc 로도 닫힌다
        assertThat(between(poll, "document.addEventListener(\"keydown\"", "});"))
                .contains("event.key !== \"Escape\" || modal.hidden")
                .contains("closeModal()");
        // 바깥의 어두운 곳을 눌러도 닫힌다
        assertThat(between(poll, "modal.addEventListener(\"click\"", "});"))
                .contains("if (event.target === modal) closeModal()");
    }

    // ── 저장 ────────────────────────────────────────────────

    @Test
    void theModalSavesThroughHttpNotTheWebSocket() throws IOException {
        String poll = pollJs();

        assertThat(between(poll, "async function submit()", "\n    }"))
                .contains("fetch(`/travel-plans/${planId}/polls`")
                .contains("method: \"POST\"");
        // 투표 만들기를 WebSocket 으로 보내지 않는다
        assertThat(poll)
                .doesNotContain("new StompJs.Client")
                .doesNotContain("new WebSocket")
                .doesNotContain("client.publish");
    }

    @Test
    void theSaveCarriesTheCsrfTokenAndTheRouteRequiresIt() throws IOException {
        String poll = pollJs();
        String security = Files.readString(
                Path.of("src/main/java/com/example/travlediary/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);

        assertThat(between(poll, "function csrfHeaders()", "\n    }"))
                .contains("meta[name=\\\"_csrf\\\"]")
                .contains("meta[name=\\\"_csrf_header\\\"]");
        // requireCsrfProtectionMatcher 는 여기 적힌 것만 보호한다
        assertThat(security).contains("\"^/travel-plans/[0-9]+/polls$\", HttpMethod.POST.name()");
    }

    @Test
    void nothingInTheBodyDecidesWhoTheCreatorIs() throws IOException {
        String poll = pollJs();

        // 보내는 것은 질문·선택 방식·선택지뿐이다
        assertThat(between(poll, "function readForm()", "\n    }"))
                .doesNotContain("memberId")
                .doesNotContain("creator")
                .doesNotContain("status");
        // 폼이 받을 수 있는 것도 그 셋뿐이다. 작성자나 방·상태를 실을 자리가 없다
        assertThat(com.example.travlediary.dto.TravelPlanPollCreateForm.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("question", "selectionType", "options");
    }

    @Test
    void aFailedSaveKeepsTheModalAndWhatWasTyped() throws IOException {
        String submit = between(pollJs(), "async function submit()", "\n    }");

        assertThat(submit).contains("showError(payload?.message)");
        // 실패했을 때는 만들기 화면에 그대로 머문다. 목록으로 넘어가지 않는다
        assertThat(submit.indexOf("showError(payload?.message)"))
                .isLessThan(submit.indexOf("showListView()"));
        // 연타로 두 번 저장되지 않는다
        assertThat(submit)
                .contains("if (submitting) return")
                .contains("submitButton.disabled = true");
    }

    @Test
    void aSuccessfulSaveDoesNotReloadThePage() throws IOException {
        String poll = pollJs();

        assertThat(between(poll, "async function submit()", "\n    }"))
                .contains("onPollCreated()");
        assertThat(poll)
                .doesNotContain("location.reload")
                .doesNotContain("location.href =");
    }

    @Test
    void koreanBeingAssembledDoesNotSubmitTheModal() throws IOException {
        String poll = pollJs();

        // 조합 중의 Enter 는 글자를 확정하는 것이지 제출이 아니다
        assertThat(between(poll, "form?.addEventListener(\"keydown\"", "});"))
                .contains("window.travelPlanIme.isComposing(event, composing)")
                .contains("event.preventDefault()");
        assertThat(poll)
                .contains("compositionstart")
                .contains("compositionend")
                // 조합 알고리즘을 직접 만들지 않는다
                .doesNotContain("charCodeAt")
                .doesNotContain("0xAC00");
    }

    // ── 진행 중인 투표 ──────────────────────────────────────

    @Test
    void theChatNoLongerCarriesAFixedPollShelf() throws IOException {
        String detail = detailHtml();

        // 투표가 채팅 세로 공간을 차지하지 않는다. 채팅창은 다시 대화만 담는다
        assertThat(detail)
                .doesNotContain("data-travel-plan-poll-area")
                .doesNotContain("진행 중인 투표</");
        assertThat(cssFile()).doesNotContain(".travel-plan-poll-area");

        // 채팅 머리글 -> 대화 -> 입력창. 그 사이에 투표 자리는 없다
        assertThat(detail.indexOf("travel-plan-chat-header"))
                .isLessThan(detail.indexOf("data-travel-plan-chat-body"));
    }

    @Test
    void theHeaderKeepsASmallWayIntoThePollCentre() throws IOException {
        String detail = detailHtml();
        String header = between(detail, "class=\"travel-plan-chat-header-actions\"", "</header>");

        // 크고 강한 배지가 아니라 작은 보조 액션이다
        assertThat(header)
                .contains("data-travel-plan-poll-entry")
                .contains("data-travel-plan-poll-count");
        // 진행 중인 투표가 없으면 숫자는 보이지 않지만 진입점은 남는다
        assertThat(between(detail, "travel-plan-poll-entry-count\"", ">")).contains("hidden");
        assertThat(between(header, "data-travel-plan-poll-entry>", "</button>")).contains("투표");
    }

    @Test
    void manyPollsScrollInsideThePollCentreInsteadOfGrowingForever() throws IOException {
        String body = between(cssFile(), ".travel-plan-poll-body {", "}");

        assertThat(body)
                .contains("overflow-y: auto")
                .contains("overflow-x: hidden");
        // 창 자체도 화면 밖으로 자라지 않는다
        assertThat(between(cssFile(), ".travel-plan-poll-modal-panel {", "}"))
                .contains("max-height");
    }

    // ── 목록과 만들기가 한 창 안에 ──────────────────────────

    @Test
    void bothViewsLiveInTheOneModalWithoutStacking() throws IOException {
        String detail = detailHtml();
        String poll = pollJs();

        assertThat(detail)
                .contains("data-travel-plan-poll-list-view")
                .contains("data-travel-plan-poll-create-view");
        // 창은 하나뿐이다. 창 위에 창을 겹치지 않는다
        assertThat(countOf(detail, "role=\"dialog\"")).isEqualTo(1);
        assertThat(countOf(detail, "class=\"travel-plan-poll-modal\"")).isEqualTo(1);
        // 두 화면이 같은 자리에서 서로 바뀐다
        assertThat(between(poll, "function showCreateView()", "\n    }"))
                .contains("listView.hidden = true")
                .contains("createView.hidden = false");
        assertThat(between(poll, "function showListView()", "\n    }"))
                .contains("listView.hidden = false")
                .contains("createView.hidden = true");
    }

    @Test
    void theTwoEntryPointsLandOnTheRightView() throws IOException {
        String poll = pollJs();

        // 머리글의 [투표 N] 은 목록으로, 입력창의 [+ -> 투표 만들기] 는 만들기로
        assertThat(poll)
                .contains("entry?.addEventListener(\"click\", () => openModal(false))")
                .contains("createFromTool?.addEventListener(\"click\", () => openModal(true))");
        assertThat(between(poll, "function openModal(createFirst)", "\n    }"))
                .contains("showCreateView()")
                .contains("showListView()");
    }

    @Test
    void theListHasTabsForRunningAndFinishedPolls() throws IOException {
        String detail = detailHtml();
        String poll = pollJs();

        assertThat(detail)
                .contains("data-travel-plan-poll-tab=\"OPEN\"")
                .contains("data-travel-plan-poll-tab=\"CLOSED\"")
                .contains("진행 중")
                .contains("지난 투표");
        // 마감 기능이 아직 없어 비어 있는 것이 정상이다. 가짜 데이터를 만들지 않는다
        assertThat(poll).contains("CLOSED: \"지난 투표가 아직 없어요.\"");
        assertThat(poll).contains("/polls/${path}");
    }

    @Test
    void afterCreatingItGoesBackToTheListWithoutClosing() throws IOException {
        String submit = between(pollJs(), "async function submit()", "\n    }");

        // 방금 만든 것을 바로 볼 수 있게 목록으로 돌아간다
        assertThat(submit)
                .contains("activeTab = \"OPEN\"")
                .contains("showListView()")
                .contains("onPollCreated()");
        // 창 자체를 닫아 버리지 않는다
        assertThat(submit).doesNotContain("closeModal()");
    }

    @Test
    void theRunningListCardStaysCompact() throws IOException {
        String poll = pollJs();
        String card = between(poll, "function pollNode(poll)", "return item;");

        // 질문 · 작성자 · 참여 현황까지다
        assertThat(card)
                .contains("cardTitle.textContent = poll.title")
                .contains("author.textContent = poll.createdByDisplayName")
                .contains("${poll.votedMemberCount} / ${poll.activeMemberCount}명 투표");
        // 목록에서는 선택지를 펼치지 않는다. 그것은 상세의 몫이다
        assertThat(card)
                .doesNotContain("poll.options")
                .doesNotContain("option.content");
    }

    @Test
    void theFinishedListCardShowsOnlyTheResult() throws IOException {
        String card = between(pollJs(), "function pollNode(poll)", "return item;");

        assertThat(card)
                .contains("poll.status === \"CLOSED\"")
                .contains("label.textContent = \"결과\"")
                .contains("poll.winnerSummary || \"투표 결과 없음\"");
        // 끝난 투표에는 참여 인원을 쓰지 않는다
        assertThat(card.indexOf("poll.winnerSummary"))
                .isLessThan(card.indexOf("명 투표"));
    }

    @Test
    void theCardOpensTheDetailInTheSameModal() throws IOException {
        String poll = pollJs();
        String detail = detailHtml();

        assertThat(between(poll, "function pollNode(poll)", "return item;"))
                .contains("card.addEventListener(\"click\", () => openDetail(poll.id))");
        // 창을 하나 더 띄우지 않는다. 같은 창 안에서 화면만 바뀐다
        assertThat(detail).contains("data-travel-plan-poll-detail-view");
        assertThat(countOf(detail, "role=\"dialog\"")).isEqualTo(1);
        assertThat(between(poll, "function showDetailView()", "\n    }"))
                .contains("listView.hidden = true")
                .contains("detailView.hidden = false");
    }

    // ── 투표하기 ────────────────────────────────────────────

    @Test
    void theDetailUsesRadiosOrCheckboxesToMatchThePoll() throws IOException {
        String choice = between(pollJs(), "function optionChoiceOf(poll, option)", "return row;");

        assertThat(choice)
                .contains("poll.selectionType === \"MULTIPLE\" ? \"checkbox\" : \"radio\"")
                // 이미 고른 것은 미리 체크되어 있다
                .contains("poll.selectedOptionIds || []")
                // 결과를 바로 볼 수 있는 투표라 지금까지의 표도 함께 보여 준다
                .contains("${option.voteCount}표");
    }

    @Test
    void aFinishedPollIsReadOnly() throws IOException {
        String poll = pollJs();
        String result = between(poll, "function resultRowOf(option)", "return row;");

        // 끝난 투표에는 고르는 칸도 [투표하기] 도 없다
        assertThat(result)
                .doesNotContain("input")
                .doesNotContain("radio")
                .doesNotContain("checkbox");
        assertThat(between(poll, "if (poll.status === \"CLOSED\") {", "} else {"))
                .doesNotContain("투표하기");
    }

    @Test
    void votingGoesThroughHttpAndKeepsTheModalOpen() throws IOException {
        String vote = between(pollJs(), "async function vote(pollId)", "\n    }");

        assertThat(vote)
                .contains("/polls/${pollId}/vote")
                .contains("method: \"POST\"")
                .contains("csrfHeaders()")
                // 저장한 뒤에도 상세에 그대로 머문다
                .contains("renderDetail(await response.json())")
                .doesNotContain("closeModal()");
        // 연타로 두 번 보내지 않는다
        assertThat(vote).contains("if (voting) return");
    }

    @Test
    void theScreenNeverGuessesTheNumbers() throws IOException {
        String poll = pollJs();

        /*
          저장 응답과 내 알림이 겹쳐 표가 두 번 오르면 안 된다.
          숫자는 언제나 서버가 준 값을 그대로 그린다.
        */
        assertThat(poll)
                .doesNotContain("voteCount + 1")
                .doesNotContain("voteCount++")
                .doesNotContain("votedMemberCount + 1");
        assertThat(between(poll, "if (payload.type !== \"POLL_VOTED\") return;", "});"))
                .contains("loadTab(\"OPEN\", true)")
                .contains("refreshDetail()");
    }

    @Test
    void aFailedVoteSaysWhyAndLeavesTheChoicesAlone() throws IOException {
        String vote = between(pollJs(), "async function vote(pollId)", "\n    }");

        assertThat(vote).contains("showVoteError(payload?.message)");
        assertThat(vote.indexOf("showVoteError(payload?.message)"))
                .isLessThan(vote.indexOf("renderDetail("));
    }

    @Test
    void nobodyIsToldWhoVotedForWhat() throws IOException {
        String poll = pollJs();

        // 보여 주는 것은 내 선택 · 선택지별 합계 · 참여 인원까지다
        assertThat(poll)
                .doesNotContain("voters")
                .doesNotContain("votedBy")
                .doesNotContain("voterNames");
    }

    @Test
    void closingAPollIsStillNotPartOfThis() throws IOException {
        String poll = pollJs();
        String detail = detailHtml();

        // 마감·삭제·수정은 다음 단계다
        assertThat(poll)
                .doesNotContain("/close")
                .doesNotContain("deadline");
        assertThat(detail)
                .doesNotContain("투표 마감")
                .doesNotContain("투표 삭제");
    }

    @Test
    void pollTextIsOnlyEverPutOnScreenAsText() throws IOException {
        String poll = pollJs();

        // 질문과 선택지도 사용자가 쓴 글이다
        assertThat(poll)
                .doesNotContain("innerHTML")
                .doesNotContain("insertAdjacentHTML")
                .doesNotContain("outerHTML")
                .doesNotContain("document.write");
    }

    // ── 실시간 ──────────────────────────────────────────────

    @Test
    void aNewPollAppearsWithoutOpeningTheChatAgain() throws IOException {
        String poll = pollJs();

        assertThat(poll).contains("POLL_CREATED");
        assertThat(between(poll, "realtime()?.subscribePolls(", "});"))
                .contains("onPollCreated()");
    }

    @Test
    void thePollChannelRidesOnTheOneExistingConnection() throws IOException {
        String realtime = realtimeJs();

        assertThat(realtime).contains("`/topic/travel-plans/${planId}/polls`");
        assertThat(countOf(realtime, "new StompJs.Client")).isEqualTo(1);
        // 투표는 받기만 한다. 보내는 목적지는 없다
        assertThat(realtime).doesNotContain("/app/travel-plans/${planId}/polls");
    }

    @Test
    void theSamePollIsNeverShownTwice() throws IOException {
        String poll = pollJs();

        /*
          저장 응답과 방 알림 둘 다 도착해도 카드는 하나다.
          알림에 실려 온 값을 목록에 끼우지 않고 서버에서 다시 읽기 때문이다.
        */
        assertThat(between(poll, "function onPollCreated()", "\n    }"))
                .contains("loadTab(\"OPEN\", true)");
        assertThat(poll).doesNotContain("pollsByTab.OPEN.unshift");
    }

    @Test
    void theChatNoticeIsAlsoShownOnlyOncePerPoll() throws IOException {
        String chat = resource("/static/js/travel-plan-chat.js");
        String created = between(chat, "function onPollCreated(poll)", "\n    }");

        // 알림 줄도 pollId 로 가른다
        assertThat(created).contains("noticeNodeOf(poll.id)");
        assertThat(chat).contains("node.setAttribute(\"data-poll-notice-id\", item.pollId)");
    }

    @Test
    void theNoticeSaysWhoMadeItAndWhatItAsks() throws IOException {
        String chat = resource("/static/js/travel-plan-chat.js");
        String notice = between(chat, "function pollNoticeNode(item)", "return node;");

        assertThat(notice)
                .contains("님이 새 투표를 만들었어요.")
                .contains("pollTitle.textContent = item.pollTitle")
                // 말풍선이 아니라 알림 줄이다
                .contains("travel-plan-chat-notice");
        // 사용자가 쓴 글이라 글자로만 넣는다
        assertThat(notice).doesNotContain("innerHTML");
        // 누르면 투표 센터가 열린다
        assertThat(notice).contains("travelplan:poll-center-open");
    }

    @Test
    void theNoticeComesBackFromTheServerNotJustFromTheLiveEvent() throws IOException {
        String chat = resource("/static/js/travel-plan-chat.js");

        /*
          실시간으로 받은 순간에만 보이고 다시 열면 사라지면 안 된다.
          기록도 같은 타임라인에서 오고 같은 줄을 그린다.
        */
        assertThat(chat)
                .contains("chat/timeline")
                .contains("item.type === \"POLL_CREATED\" ? pollNoticeNode(item)");
        assertThat(between(chat, "async function loadRecent()", "\n    }"))
                .contains("payload.items.map(itemNode)");
    }

    @Test
    void openPollsAreFetchedWhenTheChatIsOpenedNotOnPageLoad() throws IOException {
        String poll = pollJs();
        String detail = detailHtml();

        assertThat(poll)
                .contains("travelplan:chat-opened")
                .contains("/polls/${path}");
        // 상세 화면에 투표를 미리 싣지 않는다
        assertThat(detail).doesNotContain("th:each=\"poll");
        // 채팅창을 열 때 머리글 숫자를 한 번 맞춘다
        assertThat(between(poll, "document.addEventListener(\"travelplan:chat-opened\"", "});"))
                .contains("if (!loadedTabs.has(\"OPEN\")) loadTab(\"OPEN\", true)");
        // 탭은 한 번 읽어 두고 다시 묻지 않는다
        assertThat(between(poll, "async function loadTab(name, force)", "\n    }"))
                .contains("if (loadedTabs.has(name) && !force)");
    }

    @Test
    void whatWasMissedWhileDisconnectedIsFetchedAgain() throws IOException {
        assertThat(between(pollJs(), "realtime()?.onReconnected(", "});"))
                .contains("loadedTabs.clear()")
                .contains("loadTab(activeTab, true)");
    }

    // ── 색 계층 ─────────────────────────────────────────────

    @Test
    void eachAreaHasItsOwnSurfaceInsteadOfOneBeigeForEverything() throws IOException {
        String css = cssFile();
        String tokens = between(css, ":root {", "}");

        // 색을 여기저기 박아 두지 않고 이름 하나로 모아 둔다
        for (String token : new String[]{
                "--tp-page:", "--tp-paper:", "--tp-chat-surface:", "--tp-chat-composer:",
                "--tp-poll-surface:", "--tp-poll-accent:"}) {
            assertThat(tokens).as("색 이름 %s", token).contains(token);
        }
        // 채팅과 투표가 같은 한 가지 바탕으로 뭉개지지 않는다
        assertThat(between(css, ".travel-plan-chat-panel {", "}"))
                .contains("var(--tp-chat-surface)");
        assertThat(between(css, ".travel-plan-poll-modal-panel {", "}"))
                .contains("var(--tp-poll-surface)");
        // 대화와 입력줄도 아주 조금 다르다
        assertThat(between(css, ".travel-plan-chat-form {", "}"))
                .contains("var(--tp-chat-composer)");
    }

    @Test
    void thePollAccentIsUsedOnlyWhereSomethingNeedsPointingOut() throws IOException {
        String css = cssFile();

        // 배지 / 탭 밑줄 / 선택지 번호 정도에만 쓴다
        assertThat(between(css, ".travel-plan-poll-entry-count {", "}"))
                .contains("var(--tp-poll-accent)");
        assertThat(between(css, ".travel-plan-poll-tab.is-active {", "}"))
                .contains("border-bottom-color: var(--tp-poll-accent)");
        assertThat(between(css, ".travel-plan-poll-choice-count {", "}"))
                .contains("var(--tp-poll-accent)");
        // 창 전체를 파랗게 칠하지 않는다. 카드 안은 흰 종이 그대로다
        assertThat(between(css, ".travel-plan-poll-card {", "}"))
                .contains("background: #fff");
    }

    @Test
    void theShapeOfTheMainButtonStillMatchesTheRestOfTheSite() throws IOException {
        String css = cssFile();
        String submit = between(css, ".travel-plan-poll-cancel,\n.travel-plan-poll-submit {", "}");

        // 투표만 다른 브랜드 색을 새로 만들지 않는다. 기존 기본 버튼과 같은 갈색이다
        assertThat(css).contains(
                ".travel-plan-poll-submit {\n"
                        + "    border-color: #6f6350;\n"
                        + "    background: #6f6350;");
        assertThat(between(css, ".travel-plan-form-submit {", "}")).contains("#6f6350");
        // 크기·모서리도 사이트 기본과 같은 값이다
        assertThat(submit).contains("border-radius: 8px").contains("font-size: 14px");
    }

    @Test
    void thePollCentreSitsAboveTheChatAndCoversIt() throws IOException {
        String css = cssFile();

        // 채팅 패널(40)보다 위라 채팅의 작은 자식창처럼 보이지 않는다
        assertThat(between(css, ".travel-plan-chat-panel {", "}")).contains("z-index: 40");
        assertThat(between(css, ".travel-plan-poll-modal {", "}"))
                .contains("z-index: 60")
                .contains("position: fixed")
                .contains("inset: 0")
                // 뒤의 페이지와 채팅을 함께 덮어 그동안 눌리지 않게 한다
                .contains("background: rgba(63, 52, 38, 0.32)");
    }

    private String detailHtml() throws IOException {
        return resource("/templates/travelplan/detail.html");
    }

    private String pollJs() throws IOException {
        return resource("/static/js/travel-plan-poll.js");
    }

    private String realtimeJs() throws IOException {
        return resource("/static/js/travel-plan-realtime.js");
    }

    private String cssFile() throws IOException {
        return resource("/static/css/travel-plan.css");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int countOf(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0;
             index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
