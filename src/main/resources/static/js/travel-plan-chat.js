/*
  방 채팅 화면.

  연결은 들고 있지 않다. STOMP client 는 travel-plan-realtime.js 하나뿐이고,
  여기서는 그 연결이 내어 준 작은 API 위에서 화면·기록·안 읽은 개수만 다룬다.

  대화 기록의 기준은 언제나 DB 다. 화면은 서버에서 받은 것만 그린다.
  사용자가 쓴 글이므로 DOM 에 넣을 때는 textContent 만 쓴다.
*/
document.addEventListener("DOMContentLoaded", () => {
    const root = document.querySelector("[data-travel-plan-chat]");
    if (!root) return;

    const planId = root.getAttribute("data-chat-plan-id");
    // 내 메시지인지 가리는 데만 쓰는 방 안 번호다. 계정 정보가 아니다.
    const myMemberId = root.getAttribute("data-current-member-id");
    if (!planId) return;

    const toggle = root.querySelector("[data-travel-plan-chat-toggle]");
    const badge = root.querySelector("[data-travel-plan-chat-badge]");
    const panel = document.querySelector("[data-travel-plan-chat-panel]");
    if (!panel) return;

    const body = panel.querySelector("[data-travel-plan-chat-body]");
    const list = panel.querySelector("[data-travel-plan-chat-list]");
    const empty = panel.querySelector("[data-travel-plan-chat-empty]");
    const start = panel.querySelector("[data-travel-plan-chat-start]");
    const jump = panel.querySelector("[data-travel-plan-chat-jump]");
    const form = panel.querySelector("[data-travel-plan-chat-form]");
    const input = panel.querySelector("[data-travel-plan-chat-input]");
    const notice = panel.querySelector("[data-travel-plan-chat-error]");

    /** 맨 위에서 이만큼 안쪽에 들어오면 이전 대화를 미리 가져온다. */
    const OLDER_TRIGGER_PX = 80;

    let loaded = false;
    let loading = false;
    // 이전 대화는 한 번에 한 페이지만 가져온다. 위에서 스크롤이 여러 번 튀어도 겹치지 않는다.
    let loadingOlder = false;
    let hasMoreOlder = false;
    // 앞 페이지를 물어볼 기준. 대화와 투표가 표가 달라 각자 하나씩 든다.
    let beforeMessageId = null;
    let beforePollId = null;
    let sending = false;
    let unreadCount = 0;
    // 아래를 보고 있지 않을 때 도착한 메시지 수. [새 메시지 N개] 에 쓴다.
    let pendingNew = 0;

    function realtime() {
        return window.travelPlanRealtime;
    }

    function isOpen() {
        return !panel.hidden && !panel.classList.contains("is-minimized");
    }

    /** 지금 이 화면이 대화를 실제로 읽고 있는 상태인지. 닫혀 있으면 읽음 처리하지 않는다. */
    function isReading() {
        return isOpen() && document.visibilityState === "visible";
    }

    // ── 안 읽은 개수 ────────────────────────────────────────

    function renderUnread() {
        if (!badge) return;
        badge.textContent = unreadCount > 99 ? "99+" : String(unreadCount);
        badge.hidden = unreadCount <= 0;
    }

    function setUnread(count) {
        unreadCount = Math.max(0, Number(count) || 0);
        renderUnread();
    }

    async function loadUnread() {
        try {
            const response = await fetch(`/travel-plans/${planId}/chat/unread`, {
                headers: { "X-Requested-With": "XMLHttpRequest" }
            });
            if (!response.ok) return;
            setUnread((await response.json()).unreadCount);
        } catch (error) {
            // 개수를 못 읽어도 대화 자체는 쓸 수 있어야 한다.
        }
    }

    /** 지금 화면에 그려진 마지막 메시지까지 읽었다고 서버에 알린다. */
    function markRead() {
        if (!isReading()) return;
        const last = list.lastElementChild;
        if (!last) return;
        const lastId = Number(last.getAttribute("data-message-id"));
        if (!lastId) return;
        // 서버가 이미 그 자리를 읽은 것으로 알고 있으면 UPDATE 하지 않는다.
        realtime()?.markChatRead(lastId);
    }

    // ── 메시지 그리기 ───────────────────────────────────────

    function isMine(message) {
        return myMemberId != null && String(message.memberId) === String(myMemberId);
    }

    function timeTextOf(createdAt) {
        if (!createdAt) return "";
        return new Date(createdAt).toLocaleTimeString("ko-KR", {
            hour: "numeric",
            minute: "2-digit"
        });
    }

    /** 지운 메시지에는 메뉴를 달지 않는다. 남의 메시지에도 달지 않는다. */
    function deleteMenuOf(message) {
        const menu = document.createElement("div");
        menu.className = "travel-plan-chat-menu";
        menu.setAttribute("data-travel-plan-chat-menu", "");

        const button = document.createElement("button");
        button.type = "button";
        button.className = "travel-plan-chat-menu-button";
        button.setAttribute("aria-haspopup", "true");
        button.setAttribute("aria-expanded", "false");
        button.setAttribute("aria-label", "메시지 메뉴");
        button.textContent = "⋯";

        const panelEl = document.createElement("div");
        panelEl.className = "travel-plan-chat-menu-list";
        panelEl.hidden = true;

        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "travel-plan-chat-menu-action";
        remove.textContent = "삭제";
        remove.addEventListener("click", () => {
            panelEl.hidden = true;
            button.setAttribute("aria-expanded", "false");
            if (!window.confirm("이 메시지를 삭제할까요?")) return;
            realtime()?.deleteChatMessage(message.id);
        });

        button.addEventListener("click", event => {
            event.stopPropagation();
            const willOpen = panelEl.hidden;
            closeMenus(null);
            panelEl.hidden = !willOpen;
            button.setAttribute("aria-expanded", String(willOpen));
        });

        panelEl.append(remove);
        menu.append(button, panelEl);
        return menu;
    }

    function messageNode(message) {
        const item = document.createElement("li");
        item.className = "travel-plan-chat-message";
        item.setAttribute("data-message-id", message.messageId);
        if (isMine(message)) item.classList.add("is-mine");
        if (message.deleted) item.classList.add("is-deleted");

        const sender = document.createElement("p");
        sender.className = "travel-plan-chat-sender";
        // 이름도 사용자가 정한 값이라 그대로 글자로만 넣는다.
        sender.textContent = isMine(message)
            ? `${message.displayName} (나)`
            : message.displayName;

        const content = document.createElement("p");
        content.className = "travel-plan-chat-content";
        content.setAttribute("data-travel-plan-chat-content", "");
        // 채팅은 사용자 입력이다. HTML 로 넣으면 태그가 화면에서 실행된다.
        content.textContent = message.deleted
            ? "삭제된 메시지입니다."
            : message.content;

        const time = document.createElement("time");
        time.className = "travel-plan-chat-time";
        if (message.createdAt) {
            time.dateTime = new Date(message.createdAt).toISOString();
        }
        time.textContent = timeTextOf(message.createdAt);

        item.append(sender, content, time);
        if (isMine(message) && !message.deleted) {
            item.append(deleteMenuOf(message));
        }
        return item;
    }

    /*
      "OO님이 새 투표를 만들었어요".

      대화가 아니라 그 사이에 있었던 일이라, 말풍선이 아니라 차분한 알림 줄로 둔다.
      투표 자체는 travel_plan_polls 에 있고 여기에는 옮겨 적지 않는다.
      누르면 투표 센터가 열린다(여는 것은 투표 쪽이 맡는다).
    */
    function pollNoticeNode(item) {
        const node = document.createElement("li");
        node.className = "travel-plan-chat-notice";
        node.setAttribute("data-poll-notice-id", item.pollId);

        const button = document.createElement("button");
        button.type = "button";
        button.className = "travel-plan-chat-notice-body";

        const head = document.createElement("span");
        head.className = "travel-plan-chat-notice-head";
        // 이름도 사용자가 정한 값이라 그대로 글자로만 넣는다.
        head.textContent = `📊 ${item.creatorDisplayName}님이 새 투표를 만들었어요.`;

        const pollTitle = document.createElement("span");
        pollTitle.className = "travel-plan-chat-notice-title";
        pollTitle.textContent = item.pollTitle;

        const time = document.createElement("time");
        time.className = "travel-plan-chat-time";
        if (item.createdAt) {
            time.dateTime = new Date(item.createdAt).toISOString();
        }
        time.textContent = timeTextOf(item.createdAt);

        button.append(head, pollTitle);
        button.addEventListener("click", () => {
            document.dispatchEvent(new CustomEvent("travelplan:poll-center-open", {
                detail: { pollId: item.pollId }
            }));
        });

        node.append(button, time);
        return node;
    }

    /** 대화와 투표 알림이 한 줄기로 온다. 종류에 맞는 줄을 만든다. */
    function itemNode(item) {
        return item.type === "POLL_CREATED" ? pollNoticeNode(item) : messageNode(item);
    }

    /** WebSocket 으로 온 메시지를 타임라인 한 줄과 같은 모양으로 맞춘다. */
    function messageItem(message) {
        return {
            type: "MESSAGE",
            createdAt: message.createdAt,
            messageId: message.id,
            memberId: message.memberId,
            displayName: message.displayName,
            content: message.content,
            deleted: message.deleted
        };
    }

    function nodeOf(messageId) {
        return list.querySelector(`[data-message-id="${messageId}"]`);
    }

    function noticeNodeOf(pollId) {
        return list.querySelector(`[data-poll-notice-id="${pollId}"]`);
    }

    function renderEmptyState() {
        if (empty) empty.hidden = list.childElementCount > 0;
        // 더 가져올 것이 없을 때만 대화의 시작을 알린다.
        if (start) start.hidden = hasMoreOlder || list.childElementCount === 0;
    }

    // ── 스크롤 ──────────────────────────────────────────────

    /** 사용자가 지금 맨 아래를 보고 있는지. 위에서 옛 대화를 읽는 중이면 끌어내리지 않는다. */
    function isAtBottom() {
        return body.scrollHeight - body.scrollTop - body.clientHeight < 40;
    }

    function scrollToBottom() {
        body.scrollTop = body.scrollHeight;
        pendingNew = 0;
        renderJump();
    }

    function renderJump() {
        if (!jump) return;
        jump.hidden = pendingNew <= 0;
        jump.textContent = `새 메시지 ${pendingNew}개`;
    }

    // ── 기록 조회 ───────────────────────────────────────────

    function showError(message) {
        if (!notice) return;
        notice.textContent = message || "처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        notice.hidden = false;
    }

    function clearError() {
        if (!notice) return;
        notice.hidden = true;
        notice.textContent = "";
    }

    /**
     * 대화와 투표 알림을 시간 순서로 함께 받아 온다.
     * 표가 둘이라 기준도 둘이다. 대화 번호만 보내면 그 사이의 투표 알림이 빠진다.
     */
    async function fetchTimeline(beforeMessageId, beforePollId) {
        const query = new URLSearchParams();
        if (beforeMessageId != null) query.set("beforeMessageId", beforeMessageId);
        if (beforePollId != null) query.set("beforePollId", beforePollId);
        const suffix = query.toString() ? `?${query}` : "";

        const response = await fetch(`/travel-plans/${planId}/chat/timeline${suffix}`, {
            headers: { "X-Requested-With": "XMLHttpRequest" }
        });
        if (!response.ok) throw new Error("chat timeline unavailable");
        return response.json();
    }

    /** 다음 앞 페이지를 물어볼 기준. 서버가 알려 준 것을 그대로 들고 있는다. */
    function rememberCursor(payload) {
        hasMoreOlder = !!payload.hasMore;
        // 그 쪽이 끝났으면 null 이 온다. 그때는 다시 묻지 않는다.
        beforeMessageId = payload.nextBeforeMessageId ?? null;
        beforePollId = payload.nextBeforePollId ?? null;
    }

    /** 처음 열 때. 이미 그려 둔 것이 있으면 지우고 서버 내용으로 다시 맞춘다. */
    async function loadRecent() {
        if (loading) return;
        loading = true;
        try {
            const payload = await fetchTimeline(null, null);
            list.replaceChildren(...payload.items.map(itemNode));
            rememberCursor(payload);
            loaded = true;
            renderEmptyState();
            scrollToBottom();
            markRead();
            clearError();
        } catch (error) {
            showError("대화를 불러오지 못했습니다.");
        } finally {
            loading = false;
        }
    }

    /**
     * 위로 올려 이전 대화를 가져온다. 누를 버튼은 없다.
     *
     * <p>앞에 끼워 넣으면 보고 있던 메시지가 그만큼 아래로 밀린다.
     * 늘어난 높이만큼 스크롤을 내려 같은 메시지가 같은 자리에 남게 한다.
     */
    async function loadOlder() {
        // 한 번에 한 페이지만. 더 없으면 다시 묻지 않는다.
        if (loadingOlder || !hasMoreOlder) return;
        loadingOlder = true;
        try {
            const payload = await fetchTimeline(beforeMessageId, beforePollId);
            const before = body.scrollHeight;
            list.prepend(...payload.items.map(itemNode));
            body.scrollTop += body.scrollHeight - before;
            rememberCursor(payload);
            renderEmptyState();
        } catch (error) {
            showError("이전 대화를 불러오지 못했습니다.");
        } finally {
            loadingOlder = false;
        }
    }

    // ── 실시간 수신 ─────────────────────────────────────────

    /** 새 줄 하나를 지금 보고 있는 자리에 맞춰 붙인다. */
    function appendItem(node, countsAsNew) {
        const stick = isAtBottom();
        list.append(node);
        renderEmptyState();
        if (stick) {
            scrollToBottom();
        } else if (countsAsNew) {
            pendingNew += 1;
            renderJump();
        }
    }

    /**
     * 투표가 만들어졌다.
     * 기록을 다시 읽어도 서버가 같은 알림을 같은 자리에 돌려주므로,
     * 여기서는 지금 화면에 한 줄 붙이기만 한다.
     */
    function onPollCreated(poll) {
        if (!poll || poll.id == null) return;
        // 저장 응답과 방 알림이 겹쳐도 알림 줄은 하나다.
        if (!loaded || noticeNodeOf(poll.id)) return;
        appendItem(pollNoticeNode({
            type: "POLL_CREATED",
            createdAt: poll.createdAt,
            pollId: poll.id,
            creatorDisplayName: poll.createdByDisplayName,
            pollTitle: poll.title
        }), true);
    }

    function onMessageCreated(message) {
        if (!message) return;
        // 아직 기록을 읽지 않았다면 그릴 자리가 없다. 열 때 서버에서 통째로 받는다.
        if (loaded && !nodeOf(message.id)) {
            appendItem(messageNode(messageItem(message)), !isMine(message));
        }

        if (isMine(message)) return;
        if (isReading()) {
            markRead();
        } else {
            // 패널이 닫혀 있는 동안 온 메시지를 읽음으로 처리하지 않는다.
            setUnread(unreadCount + 1);
        }
    }

    function onMessageDeleted(messageId) {
        const node = nodeOf(messageId);
        if (!node) return;
        node.classList.add("is-deleted");
        const content = node.querySelector("[data-travel-plan-chat-content]");
        if (content) content.textContent = "삭제된 메시지입니다.";
        // 지운 뒤에는 메뉴가 필요 없다.
        node.querySelector("[data-travel-plan-chat-menu]")?.remove();
    }

    // ── 보내기 ──────────────────────────────────────────────

    function send() {
        const content = input.value.trim();
        if (sending || content === "") return;
        if (!realtime()?.isConnected()) {
            showError("연결이 끊겼습니다. 잠시 후 다시 시도해 주세요.");
            return;
        }

        sending = true;
        clearError();
        const sent = realtime().sendChat(content);
        if (!sent) {
            sending = false;
            showError("연결이 끊겼습니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        // 보낸 내용은 저장이 끝난 뒤 방 알림으로 돌아온다. 미리 그려 두지 않는다.
        input.value = "";
        autoResize();
        sending = false;
        input.focus();
    }

    function autoResize() {
        input.style.height = "auto";
        input.style.height = `${Math.min(input.scrollHeight, 120)}px`;
    }

    // ── 패널 열고 닫기 ──────────────────────────────────────

    function closeMenus(except) {
        list.querySelectorAll("[data-travel-plan-chat-menu]").forEach(menu => {
            const panelEl = menu.querySelector(".travel-plan-chat-menu-list");
            if (!panelEl || panelEl === except) return;
            panelEl.hidden = true;
            menu.querySelector(".travel-plan-chat-menu-button")
                ?.setAttribute("aria-expanded", "false");
        });
    }

    function openPanel() {
        panel.hidden = false;
        panel.classList.remove("is-minimized");
        toggle?.setAttribute("aria-expanded", "true");
        if (!loaded) {
            loadRecent();
        } else {
            scrollToBottom();
            markRead();
        }
        input?.focus();
        /*
          채팅창이 열렸다고 알린다.
          투표 쪽(travel-plan-poll.js)이 이 신호를 듣고 진행 중인 투표를 그때 읽어 온다.
          (내부 변수를 밖에서 들여다보지 않도록 DOM 이벤트로만 알린다)
        */
        document.dispatchEvent(new CustomEvent("travelplan:chat-opened"));
    }

    function closePanel() {
        panel.hidden = true;
        panel.classList.remove("is-minimized");
        toggle?.setAttribute("aria-expanded", "false");
        closeMenus(null);
    }

    toggle?.addEventListener("click", () => {
        if (panel.hidden) {
            openPanel();
        } else {
            closePanel();
        }
    });

    panel.querySelector("[data-travel-plan-chat-close]")
        ?.addEventListener("click", () => closePanel());

    panel.querySelector("[data-travel-plan-chat-minimize]")?.addEventListener("click", () => {
        panel.classList.toggle("is-minimized");
        if (isReading()) markRead();
    });

    jump?.addEventListener("click", () => {
        scrollToBottom();
        markRead();
    });

    body.addEventListener("scroll", () => {
        // 위쪽에 가까워지면 이전 대화를 이어서 가져온다.
        if (body.scrollTop <= OLDER_TRIGGER_PX) loadOlder();

        // 아래까지 내려 읽었으면 그때 읽음으로 본다.
        if (!isAtBottom()) return;
        pendingNew = 0;
        renderJump();
        markRead();
    });

    // 다른 탭에 있는 동안 온 메시지는 돌아왔을 때 읽음으로 본다.
    document.addEventListener("visibilitychange", () => {
        if (isReading()) markRead();
    });

    document.addEventListener("click", () => closeMenus(null));

    // ── 입력 ────────────────────────────────────────────────

    form?.addEventListener("submit", event => {
        event.preventDefault();
        send();
    });

    let composing = false;
    input?.addEventListener("compositionstart", () => {
        composing = true;
    });
    input?.addEventListener("compositionend", () => {
        composing = false;
    });
    input?.addEventListener("input", autoResize);
    input?.addEventListener("keydown", event => {
        // 조합 중의 Enter 는 글자를 확정하는 것이라 전송으로 보지 않는다.
        // 판단 규칙은 일정 편집기와 같은 곳에 있다.
        if (window.travelPlanIme.isComposing(event, composing)) return;
        // Enter 는 전송, Shift+Enter 는 줄바꿈.
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            send();
        }
    });

    // ── 연결 ────────────────────────────────────────────────

    realtime()?.subscribeChat(payload => {
        if (payload.type === "MESSAGE_CREATED") {
            onMessageCreated(payload.message);
            return;
        }
        if (payload.type === "MESSAGE_DELETED") {
            onMessageDeleted(payload.messageId);
        }
    }, payload => {
        if (payload.type === "UNREAD") {
            setUnread(payload.unreadCount);
            return;
        }
        // 보내기/지우기 실패는 나에게만 온다. 입력한 내용은 그대로 남아 있다.
        sending = false;
        showError(payload.message);
    });

    /*
      투표가 만들어졌다는 것은 대화 사이에 있었던 일이라 채팅 흐름에도 한 줄 남는다.
      투표 자체를 여기서 다루지는 않는다. 그것은 투표 센터가 맡는다.
    */
    realtime()?.subscribePolls(payload => {
        if (payload.type === "POLL_CREATED") onPollCreated(payload.poll);
    });

    /*
      끊겨 있던 사이의 채팅은 다시 받을 수 없다.
      지난 알림을 되돌려 주는 장치를 만들지 않고, 그때 서버에서 다시 읽는다.
    */
    realtime()?.onReconnected(() => {
        loadUnread();
        if (isOpen()) loadRecent();
    });

    loadUnread();
});
