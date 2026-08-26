package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅 화면의 구조.
 *
 * <p>연결은 하나뿐이고, 사용자가 쓴 글은 글자로만 그리며,
 * 아직 만들지 않은 투표 기능은 자리만 잡아 두고 흉내 내지 않는다.
 */
class TravelPlanChatUiContractTest {

    // ── 진입점과 패널 ───────────────────────────────────────

    @Test
    void thePlannerHasAChatButtonWithAnUnreadBadge() throws IOException {
        String detail = detailHtml();

        assertThat(detail)
                .contains("data-travel-plan-chat-toggle")
                .contains("data-travel-plan-chat-badge")
                // 참여자 / 초대와 같은 상단 보조 액션 줄에 있다
                .contains("class=\"travel-plan-chat\"");
        // 안 읽은 개수는 서버에서 받기 전까지 보이지 않는다
        assertThat(between(detail, "data-travel-plan-chat-badge", "</span>"))
                .contains("aria-label");
        assertThat(between(detail, "class=\"travel-plan-chat-badge\"", "</span>"))
                .contains("hidden");
    }

    @Test
    void theChatButtonIsOnlyForMembersOfTheRoom() throws IOException {
        String detail = detailHtml();

        // 참여자가 아니면 진입점 자체가 그려지지 않는다
        assertThat(between(detail, "class=\"travel-plan-chat\"", "data-travel-plan-chat-toggle"))
                .contains("travelPlan.currentMember != null");
        assertThat(between(detail, "class=\"travel-plan-chat-panel\"",
                "data-travel-plan-chat-panel"))
                .contains("travelPlan.currentMember != null");
    }

    @Test
    void thePanelHasEverythingAConversationNeeds() throws IOException {
        String detail = detailHtml();

        for (String hook : new String[]{
                "data-travel-plan-chat-panel", "data-travel-plan-chat-close",
                "data-travel-plan-chat-minimize", "data-travel-plan-chat-body",
                "data-travel-plan-chat-list", "data-travel-plan-chat-empty",
                "data-travel-plan-chat-jump", "data-travel-plan-chat-form",
                "data-travel-plan-chat-input", "data-travel-plan-chat-send",
                "data-travel-plan-chat-error"}) {
            assertThat(detail).as("panel has %s", hook).contains(hook);
        }
        // 패널은 눌러서 열기 전까지 떠 있지 않다
        assertThat(between(detail, "class=\"travel-plan-chat-panel\"", ">")).contains("hidden");
    }

    @Test
    void hidingThePanelActuallyHidesIt() throws IOException {
        String css = cssFile();

        /*
          패널에 display 를 정해 두면 브라우저 기본 [hidden] 규칙을 덮어써
          hidden 을 걸어도 계속 보인다. 그러면 닫기 버튼이 듣지 않는 것처럼 보이고,
          화면에는 열려 있는데 스크립트는 닫힌 줄 알아 실시간 표시까지 어긋난다.
        */
        assertThat(between(css, ".travel-plan-chat-panel {", "}")).contains("display: flex");
        assertThat(css).contains(".travel-plan-chat-panel[hidden] {\n    display: none;\n}");
    }

    @Test
    void theChatButtonDoesNotStealThePlannerSelector() throws IOException {
        String detail = detailHtml();

        /*
          스케줄러와 실시간은 첫 [data-plan-id] 를 종이로 본다.
          채팅 버튼이 그 앞에서 같은 이름을 달면 일정 동작이 그쪽에 붙어 버린다.
        */
        assertThat(between(detail, "class=\"travel-plan-chat\"", "</div>"))
                .contains("data-chat-plan-id")
                .doesNotContain("data-plan-id=");
        // 종이가 여전히 유일한 data-plan-id 다
        assertThat(countOf(detail, "th:attr=\"data-plan-id=")).isEqualTo(1);
        assertThat(chatJs()).contains("root.getAttribute(\"data-chat-plan-id\")");
    }

    @Test
    void closingIsARealButtonThatKeepsTheStatesInStep() throws IOException {
        String detail = detailHtml();
        String chat = chatJs();

        assertThat(detail).contains("aria-label=\"채팅 닫기\" data-travel-plan-chat-close");
        // 눌리는 것은 실제 button 이고 폼을 보내지 않는다
        assertThat(between(detail, "class=\"travel-plan-chat-header-actions\"",
                "data-travel-plan-chat-close")).contains("type=\"button\"");

        // 닫으면 패널과 상단 버튼 상태가 함께 바뀐다
        assertThat(between(chat, "function closePanel()", "\n    }"))
                .contains("panel.hidden = true")
                .contains("toggle?.setAttribute(\"aria-expanded\", \"false\")");
        assertThat(chat).contains("data-travel-plan-chat-close]\")\n        ?.addEventListener");
    }

    @Test
    void thePanelFloatsAtTheBottomRightWithoutSpillingOffNarrowScreens() throws IOException {
        String css = cssFile();
        String panel = between(css, ".travel-plan-chat-panel {", "}");

        assertThat(panel)
                .contains("position: fixed")
                .contains("right: 24px")
                .contains("bottom: 24px")
                // 좁은 화면에서도 남은 폭 안에서만 커진다
                .contains("width: min(360px, calc(100vw - 32px))");
        // 옆으로 넘치는 대신 안에서 스크롤한다
        assertThat(between(css, ".travel-plan-chat-body {", "}"))
                .contains("overflow-y: auto")
                .contains("overflow-x: hidden");
    }

    @Test
    void anEmptyRoomSaysSoGently() throws IOException {
        assertThat(detailHtml()).contains("아직 대화가 없어요.");
    }

    // ── 사용자가 쓴 글 ──────────────────────────────────────

    @Test
    void messagesAreOnlyEverPutOnScreenAsText() throws IOException {
        String chat = chatJs();

        // 채팅은 사용자 입력이다. innerHTML 로 넣으면 태그가 화면에서 실행된다
        assertThat(chat)
                .doesNotContain("innerHTML")
                .doesNotContain("insertAdjacentHTML")
                .doesNotContain("outerHTML")
                .doesNotContain("document.write");
        // 본문도 보낸 사람 이름도 글자로만 넣는다
        assertThat(chat)
                .contains("content.textContent =")
                .contains("sender.textContent =");
    }

    @Test
    void noMarkdownOrLinkPreviewSneaksIn() throws IOException {
        String chat = chatJs();

        assertThat(chat)
                .doesNotContain("marked")
                .doesNotContain("markdown")
                .doesNotContain("createElement(\"a\")");
    }

    @Test
    void lineBreaksTypedBySomeoneAreShownAsTyped() throws IOException {
        assertThat(between(cssFile(), ".travel-plan-chat-content {", "}"))
                .contains("white-space: pre-wrap")
                .contains("overflow-wrap: break-word");
    }

    // ── 보내기 ──────────────────────────────────────────────

    @Test
    void enterSendsAndShiftEnterMakesANewLine() throws IOException {
        String chat = chatJs();
        String keydown = between(chat, "input?.addEventListener(\"keydown\"", "});");

        assertThat(keydown)
                .contains("event.key === \"Enter\" && !event.shiftKey")
                .contains("send()");
    }

    @Test
    void koreanBeingAssembledDoesNotSendYet() throws IOException {
        String chat = chatJs();

        // 조합 중의 Enter 는 글자를 확정하는 것이지 전송이 아니다
        assertThat(between(chat, "input?.addEventListener(\"keydown\"", "});"))
                .contains("window.travelPlanIme.isComposing(event, composing)");
        assertThat(chat)
                .contains("compositionstart")
                .contains("compositionend")
                // 조합 알고리즘을 직접 만들지 않는다
                .doesNotContain("charCodeAt")
                .doesNotContain("0xAC00");
    }

    @Test
    void theImeRuleLivesInExactlyOnePlace() throws IOException {
        String scheduler = schedulerJs();
        String chat = chatJs();

        // 일정 편집기와 채팅이 같은 판단을 쓴다. 복사해 두고 서로 달라지지 않게 한다
        assertThat(scheduler).contains("window.travelPlanIme = {");
        assertThat(countOf(scheduler + chat, "event.keyCode === 229")).isEqualTo(1);
        assertThat(scheduler).contains("window.travelPlanIme.isComposing(event, composing)");
        assertThat(chat).contains("window.travelPlanIme.isComposing(event, composing)");
    }

    @Test
    void theSameMessageIsNotSentTwiceByAccident() throws IOException {
        String send = between(chatJs(), "function send()", "\n    }");

        assertThat(send)
                .contains("if (sending || content === \"\") return")
                .contains("sending = true")
                // 성공하면 입력창을 비운다
                .contains("input.value = \"\"");
    }

    @Test
    void aFailedSendKeepsWhatWasTyped() throws IOException {
        String chat = chatJs();
        String send = between(chat, "function send()", "\n    }");

        // 연결이 없으면 보낸 척하지 않고, 입력한 내용도 지우지 않는다
        assertThat(send).contains("showError(");
        assertThat(between(send, "if (!sent)", "}")).doesNotContain("input.value = \"\"");
        // 실패 사유는 나에게만 온다
        assertThat(chat).contains("showError(payload.message)");
    }

    @Test
    void whatIsSentIsOnlyTheText() throws IOException {
        String realtime = realtimeJs();

        // 보낸 사람은 서버가 정한다
        assertThat(between(realtime, "sendChat(content)", "\n        }"))
                .contains("publishChat(\"send\", { content })")
                .doesNotContain("memberId")
                .doesNotContain("displayName");
    }

    // ── 연결 ────────────────────────────────────────────────

    @Test
    void theChatDoesNotOpenAWebSocketOfItsOwn() throws IOException {
        String chat = chatJs();

        // STOMP client 는 travel-plan-realtime.js 하나뿐이다
        assertThat(chat)
                .doesNotContain("new StompJs.Client")
                .doesNotContain("new WebSocket")
                .doesNotContain("brokerURL");
        assertThat(countOf(realtimeJs(), "new StompJs.Client")).isEqualTo(1);
        // 연결은 그쪽이 내어 준 창구로만 쓴다
        assertThat(chat)
                .contains("realtime()?.subscribeChat(")
                .contains("realtime().sendChat(");
    }

    @Test
    void theChatChannelIsSubscribedEvenWhileThePanelIsClosed() throws IOException {
        String realtime = realtimeJs();

        // 닫혀 있어도 받아야 안 읽은 개수가 쌓인다
        assertThat(realtime).contains("`/topic/travel-plans/${planId}/chat`");
        // 처리 결과는 나에게만 오는 개인 큐로 돌아온다
        assertThat(realtime).contains("/user/queue/travel-plan-chat");
    }

    @Test
    void aMessageArrivesWithoutReloadingThePage() throws IOException {
        String chat = chatJs();

        assertThat(chat)
                .contains("MESSAGE_CREATED")
                .contains("MESSAGE_DELETED")
                .contains("appendItem(messageNode(messageItem(message))")
                // 새로고침으로 따라잡지 않는다
                .doesNotContain("location.reload")
                .doesNotContain("location.href =");
    }

    @Test
    void anOpenPanelShowsANewMessageWithoutPressingTheButtonAgain() throws IOException {
        String chat = chatJs();
        String created = between(chat, "function onMessageCreated(message)", "\n    }");

        // 받은 그 자리에서 목록에 붙인다. 다시 열거나 기록을 다시 읽지 않는다
        assertThat(created).contains("appendItem(messageNode(messageItem(message))");
        assertThat(created)
                .doesNotContain("loadRecent()")
                .doesNotContain("fetchTimeline(");

        // 상단 버튼을 다시 눌러야만 보이던 길로 되돌아가지 않는다
        assertThat(between(chat, "toggle?.addEventListener(\"click\"", "});"))
                .contains("openPanel()")
                .contains("closePanel()");
    }

    @Test
    void everyoneIncludingTheSenderGetsTheSameOneCopy() throws IOException {
        String chat = chatJs();

        // 보낸 사람도 방 알림으로 받는다. 보내면서 미리 그려 두지 않는다
        assertThat(between(chat, "function send()", "\n    }"))
                .doesNotContain("messageNode(")
                .doesNotContain("list.append(");
        // 같은 메시지가 두 번 붙지 않는다
        assertThat(between(chat, "function onMessageCreated(message)", "\n    }"))
                .contains("!nodeOf(message.id)");
    }

    // ── 메시지 생김새 ───────────────────────────────────────

    @Test
    void myMessagesLookLikeEveryoneElsesExceptForTheLabel() throws IOException {
        String chat = chatJs();
        String css = cssFile();

        // 내 메시지만 진한 바탕을 쓰지 않는다
        assertThat(css).doesNotContain(".travel-plan-chat-message.is-mine {");
        // 카카오톡식 좌우 말풍선으로 가르지도 않는다
        assertThat(between(css, ".travel-plan-chat-message {", "}"))
                .doesNotContain("background")
                .doesNotContain("margin-left: auto")
                .doesNotContain("align-self");
        // 누가 썼는지는 이름 옆 "(나)" 로만 구분한다
        assertThat(chat).contains("`${message.displayName} (나)`");
    }

    @Test
    void messagesAreSeparatedInsteadOfRunningTogether() throws IOException {
        String css = cssFile();
        String message = between(css, ".travel-plan-chat-message {", "}");

        // 카드로 만들지 않고 아주 연한 선과 여백으로만 나눈다
        assertThat(message)
                .contains("padding: 10px 2px")
                .contains("border-bottom: 1px solid var(--tp-chat-line)");
        assertThat(css).contains(".travel-plan-chat-message:last-child {");
        // 이름 / 본문 / 시간이 각각 떨어져 보인다
        assertThat(between(css, ".travel-plan-chat-sender {", "}")).contains("margin: 0 0 4px");
        assertThat(between(css, ".travel-plan-chat-time {", "}"))
                .contains("margin-top: 4px")
                .contains("text-align: right");
    }

    // ── 이전 대화 ───────────────────────────────────────────

    @Test
    void thereIsNoLoadOlderButtonAnyMore() throws IOException {
        assertThat(detailHtml())
                .doesNotContain("이전 메시지 보기")
                .doesNotContain("data-travel-plan-chat-more");
        assertThat(chatJs()).doesNotContain("data-travel-plan-chat-more");
        assertThat(cssFile()).doesNotContain(".travel-plan-chat-more");
    }

    @Test
    void scrollingUpBringsTheOlderMessagesIn() throws IOException {
        String chat = chatJs();

        // 위쪽에 가까워지면 알아서 이어 온다
        assertThat(between(chat, "body.addEventListener(\"scroll\"", "});"))
                .contains("if (body.scrollTop <= OLDER_TRIGGER_PX) loadOlder()");
        // 이미 있는 cursor 조회를 그대로 쓴다.
        // 대화와 투표 알림은 표가 달라 기준을 각자 들고 간다
        assertThat(between(chat, "async function loadOlder()", "\n    }"))
                .contains("fetchTimeline(beforeMessageId, beforePollId)")
                .contains("list.prepend(");
    }

    @Test
    void readingOldMessagesDoesNotMakeTheScreenJump() throws IOException {
        String older = between(chatJs(), "async function loadOlder()", "\n    }");

        // 앞에 끼워 넣어 늘어난 높이만큼 스크롤을 내려 같은 자리에 남긴다
        assertThat(older)
                .contains("const before = body.scrollHeight")
                .contains("body.scrollTop += body.scrollHeight - before");
        // 보정이 붙이기보다 나중이다
        assertThat(older.indexOf("list.prepend("))
                .isLessThan(older.indexOf("body.scrollTop += body.scrollHeight - before"));
    }

    @Test
    void theSameOlderPageIsNotFetchedTwice() throws IOException {
        String chat = chatJs();

        // 위에서 스크롤이 여러 번 튀어도 요청은 한 번뿐이고, 더 없으면 묻지 않는다
        assertThat(between(chat, "async function loadOlder()", "\n    }"))
                .contains("if (loadingOlder || !hasMoreOlder) return")
                .contains("loadingOlder = true")
                .contains("loadingOlder = false");
        // 다음 기준은 서버가 알려 준 것을 그대로 쓴다
        assertThat(between(chat, "function rememberCursor(payload)", "\n    }"))
                .contains("hasMoreOlder = !!payload.hasMore")
                .contains("beforeMessageId = payload.nextBeforeMessageId")
                .contains("beforePollId = payload.nextBeforePollId");
    }

    @Test
    void theStartOfTheConversationIsMarkedOnlyWhenThereIsNoMore() throws IOException {
        assertThat(between(chatJs(), "function renderEmptyState()", "\n    }"))
                .contains("start.hidden = hasMoreOlder || list.childElementCount === 0");
        assertThat(detailHtml()).contains("대화의 시작이에요.");
    }

    @Test
    void whatWasMissedWhileDisconnectedIsFetchedAgain() throws IOException {
        String chat = chatJs();

        // 지난 알림을 되돌려 주는 장치를 만들지 않고 그때 서버에서 다시 읽는다
        assertThat(between(chat, "realtime()?.onReconnected(", "});"))
                .contains("loadUnread()")
                .contains("loadRecent()");
    }

    // ── 읽음 / 안 읽은 개수 ─────────────────────────────────

    @Test
    void nothingIsMarkedReadWhileThePanelIsClosed() throws IOException {
        String chat = chatJs();

        assertThat(between(chat, "function isReading()", "\n    }"))
                .contains("isOpen()")
                .contains("document.visibilityState === \"visible\"");
        // 읽음 처리는 반드시 그 확인을 지나서만 일어난다
        assertThat(between(chat, "function markRead()", "\n    }"))
                .contains("if (!isReading()) return");
    }

    @Test
    void aMessageThatArrivesWhileClosedRaisesTheBadge() throws IOException {
        String created = between(chatJs(), "function onMessageCreated(message)", "\n    }");

        assertThat(created)
                .contains("if (isMine(message)) return")
                .contains("setUnread(unreadCount + 1)")
                .contains("markRead()");
    }

    @Test
    void scrollingDoesNotWriteToTheDatabaseEveryTime() throws IOException {
        String chat = chatJs();

        // 아래까지 내려온 순간에만 읽음을 알린다
        assertThat(between(chat, "body.addEventListener(\"scroll\"", "});"))
                .contains("if (!isAtBottom()) return")
                .contains("markRead()");
    }

    // ── 스크롤 ──────────────────────────────────────────────

    @Test
    void readingOldMessagesIsNotInterrupted() throws IOException {
        String chat = chatJs();

        // 위에서 옛 대화를 읽는 중이면 끌어내리지 않고 알림만 띄운다
        assertThat(between(chat, "function appendItem(node, countsAsNew)", "\n    }"))
                .contains("const stick = isAtBottom()")
                .contains("pendingNew += 1");
        assertThat(chat).contains("jump.textContent = `새 메시지 ${pendingNew}개`");
        // 스크롤 라이브러리를 들이지 않는다
        assertThat(chat).doesNotContain("scrollIntoView(");
    }

    // ── 지우기 ──────────────────────────────────────────────

    @Test
    void onlyMyOwnMessageGetsAMenu() throws IOException {
        String chat = chatJs();

        assertThat(between(chat, "item.append(sender, content, time);", "return item;"))
                .contains("if (isMine(message) && !message.deleted)")
                .contains("deleteMenuOf(message)");
        // 상시 보이는 빨간 휴지통을 두지 않는다
        assertThat(between(chat, "function deleteMenuOf(message)", "return menu;"))
                .contains("button.textContent = \"⋯\"")
                .contains("remove.textContent = \"삭제\"");
        assertThat(between(cssFile(), ".travel-plan-chat-menu-button {", "}"))
                .contains("opacity: 0");
    }

    @Test
    void aDeletedMessageLeavesAMarkInItsPlace() throws IOException {
        String deleted = between(chatJs(), "function onMessageDeleted(messageId)", "\n    }");

        assertThat(deleted)
                .contains("content.textContent = \"삭제된 메시지입니다.\"")
                // 목록에서 빼면 앞뒤 대화가 당겨진다
                .doesNotContain("node.remove()")
                // 지운 뒤에는 메뉴가 필요 없다
                .contains("[data-travel-plan-chat-menu]");
    }

    // ── 다음 단계 자리 ──────────────────────────────────────

    @Test
    void theVoteEntryPointSitsLeftOfTheChatInput() throws IOException {
        String detail = detailHtml();
        String tools = between(detail, "class=\"travel-plan-chat-tools\"", "</div>");

        // 입력창 왼쪽 도구 자리에서 투표 만들기를 연다
        assertThat(tools)
                .contains("class=\"travel-plan-chat-tool\"")
                .contains("data-travel-plan-chat-tool")
                .doesNotContain("disabled");
        assertThat(detail).contains("data-travel-plan-poll-open");
    }

    @Test
    void theChatOnlyShowsThatAPollHappenedAndLeavesTheRestToThePollCentre() throws IOException {
        String chat = chatJs();

        /*
          투표가 만들어졌다는 것은 대화 사이에 있었던 일이라 채팅 흐름에 한 줄 남는다.
          하지만 투표를 다루지는 않는다. 목록·만들기·마감은 투표 센터가 맡는다.
        */
        assertThat(chat)
                .contains("travelplan:chat-opened")
                .contains("POLL_CREATED")
                // 누르면 투표 센터가 열린다. 여는 것도 저쪽이 맡는다
                .contains("travelplan:poll-center-open");
        assertThat(chat)
                .doesNotContain("/polls")
                .doesNotContain("selectionType")
                .doesNotContain("options")
                .doesNotContain("vote");
    }

    private String detailHtml() throws IOException {
        return resource("/templates/travelplan/detail.html");
    }

    private String chatJs() throws IOException {
        return resource("/static/js/travel-plan-chat.js");
    }

    private String realtimeJs() throws IOException {
        return resource("/static/js/travel-plan-realtime.js");
    }

    private String schedulerJs() throws IOException {
        return resource("/static/js/travel-plan-scheduler.js");
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
