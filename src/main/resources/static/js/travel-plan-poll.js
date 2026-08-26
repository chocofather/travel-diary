/*
  투표 센터.

  진행 중·지난 투표를 보는 곳과 새 투표를 만드는 곳이 창 하나 안에 있다.
  창 위에 창을 겹치지 않고, 두 화면이 같은 자리에서 서로 바뀐다.

  연결은 들고 있지 않다. STOMP client 는 travel-plan-realtime.js 하나뿐이고,
  저장은 기존 HTTP POST 로 한다.
  투표 질문과 선택지도 사용자가 쓴 글이므로 DOM 에 넣을 때는 textContent 만 쓴다.
*/
document.addEventListener("DOMContentLoaded", () => {
    const root = document.querySelector("[data-travel-plan-chat]");
    const modal = document.querySelector("[data-travel-plan-poll-modal]");
    if (!root || !modal) return;

    const planId = root.getAttribute("data-chat-plan-id");
    if (!planId) return;

    /** 선택지 최소/최대. 서버도 같은 수로 다시 본다. */
    const MIN_OPTIONS = 2;
    const MAX_OPTIONS = 10;

    const entry = document.querySelector("[data-travel-plan-poll-entry]");
    const entryCount = document.querySelector("[data-travel-plan-poll-count]");
    const tool = document.querySelector("[data-travel-plan-chat-tool]");
    const toolMenu = document.querySelector("[data-travel-plan-chat-tool-menu]");
    const createFromTool = document.querySelector("[data-travel-plan-poll-open]");

    const title = modal.querySelector("[data-travel-plan-poll-modal-title]");
    const listView = modal.querySelector("[data-travel-plan-poll-list-view]");
    const detailView = modal.querySelector("[data-travel-plan-poll-detail-view]");
    const detailBody = modal.querySelector("[data-travel-plan-poll-detail]");
    const createView = modal.querySelector("[data-travel-plan-poll-create-view]");
    const list = modal.querySelector("[data-travel-plan-poll-list]");
    const empty = modal.querySelector("[data-travel-plan-poll-empty]");
    const tabs = Array.from(modal.querySelectorAll("[data-travel-plan-poll-tab]"));

    const form = modal.querySelector("[data-travel-plan-poll-form]");
    const question = modal.querySelector("[data-travel-plan-poll-question]");
    const optionList = modal.querySelector("[data-travel-plan-poll-options]");
    const addButton = modal.querySelector("[data-travel-plan-poll-add]");
    const submitButton = modal.querySelector("[data-travel-plan-poll-submit]");
    const notice = modal.querySelector("[data-travel-plan-poll-error]");

    const EMPTY_TEXT = {
        OPEN: "진행 중인 투표가 아직 없어요.",
        CLOSED: "지난 투표가 아직 없어요."
    };

    let activeTab = "OPEN";
    let submitting = false;
    let voting = false;
    // 지금 상세로 열어 둔 투표. 닫혀 있으면 null.
    let openedPollId = null;
    // 탭마다 한 번 읽어 두고, 새 투표가 생기면 그때 고쳐 그린다.
    const loadedTabs = new Set();
    const pollsByTab = { OPEN: [], CLOSED: [] };

    function realtime() {
        return window.travelPlanRealtime;
    }

    // ── 도구 메뉴 ───────────────────────────────────────────

    function closeToolMenu() {
        if (!toolMenu) return;
        toolMenu.hidden = true;
        tool?.setAttribute("aria-expanded", "false");
    }

    tool?.addEventListener("click", event => {
        event.stopPropagation();
        if (!toolMenu) return;
        const willOpen = toolMenu.hidden;
        toolMenu.hidden = !willOpen;
        tool.setAttribute("aria-expanded", String(willOpen));
    });

    document.addEventListener("click", () => closeToolMenu());

    // ── 선택지 줄 ───────────────────────────────────────────

    /**
     * 선택지 한 줄.
     * 화면의 몇 번째 줄인지는 저장할 때의 순서로만 쓴다. DB id 로 삼지 않는다.
     */
    function optionRow(value) {
        const row = document.createElement("li");
        row.className = "travel-plan-poll-option-row";
        row.setAttribute("data-travel-plan-poll-option-row", "");

        const number = document.createElement("span");
        number.className = "travel-plan-poll-option-number";
        number.setAttribute("data-travel-plan-poll-option-number", "");

        const input = document.createElement("input");
        input.type = "text";
        input.className = "travel-plan-poll-option";
        input.autocomplete = "off";
        input.maxLength = 200;
        input.placeholder = "선택지 입력...";
        input.value = value || "";
        input.setAttribute("aria-label", "선택지");
        input.setAttribute("data-travel-plan-poll-option", "");

        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "travel-plan-poll-option-remove";
        remove.setAttribute("aria-label", "선택지 삭제");
        remove.setAttribute("data-travel-plan-poll-option-remove", "");
        remove.textContent = "×";
        remove.addEventListener("click", () => {
            // 두 개까지는 남겨 둔다. 하나만 남으면 고를 것이 없다.
            if (rows().length <= MIN_OPTIONS) return;
            row.remove();
            renderOptionControls();
        });

        row.append(number, input, remove);
        return row;
    }

    function rows() {
        return Array.from(optionList.querySelectorAll("[data-travel-plan-poll-option-row]"));
    }

    function renderOptionControls() {
        const current = rows();
        current.forEach((row, index) => {
            // 01, 02 ... 화면에서도 순서가 보이게 한다.
            const number = row.querySelector("[data-travel-plan-poll-option-number]");
            if (number) number.textContent = String(index + 1).padStart(2, "0");
            // 최소 개수일 때는 지울 수 없다는 것이 보이게 둔다.
            const remove = row.querySelector("[data-travel-plan-poll-option-remove]");
            if (remove) remove.disabled = current.length <= MIN_OPTIONS;
        });
        // 상한에 닿으면 더 만들 수 없다.
        if (addButton) addButton.hidden = current.length >= MAX_OPTIONS;
    }

    addButton?.addEventListener("click", () => {
        if (rows().length >= MAX_OPTIONS) return;
        const row = optionRow("");
        optionList.append(row);
        renderOptionControls();
        row.querySelector("[data-travel-plan-poll-option]")?.focus();
    });

    // ── 두 화면 ─────────────────────────────────────────────

    function showError(message) {
        if (!notice) return;
        notice.textContent = message || "투표를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.";
        notice.hidden = false;
    }

    function clearError() {
        if (!notice) return;
        notice.hidden = true;
        notice.textContent = "";
    }

    /** 열 때마다 빈 상태로 시작한다. 지난번에 쓰다 만 값이 남지 않는다. */
    function resetForm() {
        if (question) question.value = "";
        optionList.replaceChildren(optionRow(""), optionRow(""));
        renderOptionControls();
        modal.querySelectorAll("[data-travel-plan-poll-selection]").forEach(radio => {
            radio.checked = radio.value === "SINGLE";
        });
        clearError();
    }

    function showListView() {
        openedPollId = null;
        listView.hidden = false;
        detailView.hidden = true;
        createView.hidden = true;
        if (title) title.textContent = "투표";
    }

    function showDetailView() {
        listView.hidden = true;
        detailView.hidden = false;
        createView.hidden = true;
        if (title) title.textContent = "투표";
    }

    function showCreateView() {
        resetForm();
        openedPollId = null;
        listView.hidden = true;
        detailView.hidden = true;
        createView.hidden = false;
        if (title) title.textContent = "새 투표 만들기";
        question?.focus();
    }

    function openModal(createFirst) {
        closeToolMenu();
        modal.hidden = false;
        if (createFirst) {
            showCreateView();
        } else {
            showListView();
        }
        // 열 때 지금 상태를 한 번 맞춘다.
        loadTab(activeTab, true);
    }

    function closeModal() {
        modal.hidden = true;
        showListView();
        resetForm();
    }

    entry?.addEventListener("click", () => openModal(false));
    createFromTool?.addEventListener("click", () => openModal(true));
    modal.querySelector("[data-travel-plan-poll-close]")
        ?.addEventListener("click", () => closeModal());
    modal.querySelector("[data-travel-plan-poll-create-open]")
        ?.addEventListener("click", () => showCreateView());
    // 취소와 [← 투표 목록] 은 같은 곳으로 간다. 창 전체를 닫지 않는다.
    modal.querySelectorAll("[data-travel-plan-poll-back]").forEach(button => {
        button.addEventListener("click", () => showListView());
    });

    // 바깥의 어두운 곳을 누르면 닫는다. 창 안쪽 클릭은 그대로 둔다.
    modal.addEventListener("click", event => {
        if (event.target === modal) closeModal();
    });

    document.addEventListener("keydown", event => {
        if (event.key !== "Escape" || modal.hidden) return;
        closeModal();
    });

    // ── 목록 ────────────────────────────────────────────────

    const SELECTION_LABELS = {
        SINGLE: "하나만 선택",
        MULTIPLE: "여러 개 선택"
    };

    /*
      목록 한 줄.
      선택지는 여기서 펼치지 않는다. 무엇을 정하는지와 얼마나 참여했는지까지다.
      선택지와 표는 눌러서 상세로 들어갔을 때 읽는다.
    */
    function pollNode(poll) {
        const item = document.createElement("li");
        item.setAttribute("data-poll-id", poll.id);

        const card = document.createElement("button");
        card.type = "button";
        card.className = "travel-plan-poll-card";

        const text = document.createElement("span");
        text.className = "travel-plan-poll-card-body";

        const cardTitle = document.createElement("span");
        cardTitle.className = "travel-plan-poll-card-title";
        // 사용자가 쓴 글이다. 글자로만 넣어 태그가 실행되지 않게 한다.
        cardTitle.textContent = poll.title;

        const author = document.createElement("span");
        author.className = "travel-plan-poll-card-author";
        author.textContent = poll.createdByDisplayName;

        const meta = document.createElement("span");
        meta.className = "travel-plan-poll-card-meta";
        if (poll.status === "CLOSED") {
            // 끝난 투표는 참여 인원 대신 결과만 보여 준다.
            const label = document.createElement("span");
            label.className = "travel-plan-poll-card-result-label";
            label.textContent = "결과";
            const winner = document.createElement("span");
            winner.textContent = poll.winnerSummary || "투표 결과 없음";
            meta.append(label, winner);
        } else {
            meta.textContent =
                `${poll.votedMemberCount} / ${poll.activeMemberCount}명 투표`;
        }

        const chevron = document.createElement("span");
        chevron.className = "travel-plan-poll-card-chevron";
        chevron.setAttribute("aria-hidden", "true");
        chevron.textContent = "›";

        text.append(cardTitle, author, meta);
        card.append(text, chevron);
        card.addEventListener("click", () => openDetail(poll.id));

        item.append(card);
        return item;
    }

    function renderTabs() {
        tabs.forEach(tab => {
            const name = tab.getAttribute("data-travel-plan-poll-tab");
            const isActive = name === activeTab;
            tab.classList.toggle("is-active", isActive);
            tab.setAttribute("aria-selected", String(isActive));
        });
        modal.querySelectorAll("[data-travel-plan-poll-tab-count]").forEach(node => {
            const name = node.getAttribute("data-travel-plan-poll-tab-count");
            node.textContent = String(pollsByTab[name].length);
        });
        // 채팅 머리글의 작은 숫자는 진행 중인 투표 개수다.
        if (entryCount) {
            const open = pollsByTab.OPEN.length;
            entryCount.textContent = String(open);
            entryCount.hidden = open === 0;
        }
    }

    function renderList() {
        const polls = pollsByTab[activeTab];
        list.replaceChildren(...polls.map(pollNode));
        if (empty) {
            empty.textContent = EMPTY_TEXT[activeTab];
            empty.hidden = polls.length > 0;
        }
        renderTabs();
    }

    async function loadTab(name, force) {
        if (loadedTabs.has(name) && !force) {
            renderList();
            return;
        }
        try {
            const path = name === "OPEN" ? "open" : "closed";
            const response = await fetch(`/travel-plans/${planId}/polls/${path}`, {
                headers: { "X-Requested-With": "XMLHttpRequest" }
            });
            if (!response.ok) return;
            pollsByTab[name] = (await response.json()).polls || [];
            loadedTabs.add(name);
        } catch (error) {
            // 못 읽어도 채팅 자체는 그대로 쓸 수 있어야 한다.
        }
        renderList();
    }

    tabs.forEach(tab => {
        tab.addEventListener("click", () => {
            activeTab = tab.getAttribute("data-travel-plan-poll-tab");
            renderList();
            loadTab(activeTab, false);
        });
    });

    /**
     * 새 투표가 생겼다.
     *
     * <p>알림에 실려 온 값을 그대로 목록에 끼우지 않는다.
     * 목록 한 줄에는 참여 인원이 필요한데 그것은 서버가 세는 값이라,
     * 저장 응답이든 방 알림이든 "다시 읽어라" 는 신호로만 쓴다.
     * 같은 투표가 두 번 들어가지 않는 것도 이렇게 하면 저절로 지켜진다.
     */
    function onPollCreated() {
        loadTab("OPEN", true);
    }

    // ── 상세 ────────────────────────────────────────────────

    function detailHeadOf(poll) {
        const head = document.createElement("div");
        head.className = "travel-plan-poll-detail-head";

        const detailTitle = document.createElement("p");
        detailTitle.className = "travel-plan-poll-detail-title";
        detailTitle.textContent = poll.title;

        const meta = document.createElement("p");
        meta.className = "travel-plan-poll-detail-meta";
        const label = SELECTION_LABELS[poll.selectionType] || "";
        meta.textContent = poll.status === "CLOSED" || !label
            ? poll.createdByDisplayName
            : `${poll.createdByDisplayName} · ${label}`;

        head.append(detailTitle, meta);

        if (poll.status !== "CLOSED") {
            const joined = document.createElement("p");
            joined.className = "travel-plan-poll-detail-joined";
            joined.textContent =
                `현재 ${poll.votedMemberCount} / ${poll.activeMemberCount}명 참여`;
            head.append(joined);
        }
        return head;
    }

    /**
     * 진행 중인 투표의 선택지 한 줄.
     * 하나만 고르는 투표는 radio, 여러 개는 checkbox 다.
     */
    function optionChoiceOf(poll, option) {
        const row = document.createElement("label");
        row.className = "travel-plan-poll-choice-row";

        const input = document.createElement("input");
        input.type = poll.selectionType === "MULTIPLE" ? "checkbox" : "radio";
        input.name = "travelPlanPollChoice";
        input.value = option.id;
        input.checked = (poll.selectedOptionIds || [])
            .some(selected => String(selected) === String(option.id));
        input.setAttribute("data-travel-plan-poll-choice", "");

        const text = document.createElement("span");
        text.className = "travel-plan-poll-choice-text";
        text.textContent = option.content;

        const count = document.createElement("span");
        count.className = "travel-plan-poll-choice-count";
        // 결과를 바로 볼 수 있는 투표라 지금까지의 표를 함께 보여 준다.
        count.textContent = `${option.voteCount}표`;

        row.append(input, text, count);
        return row;
    }

    /** 끝난 투표는 읽기 전용이다. 고르는 칸을 두지 않는다. */
    function resultRowOf(option) {
        const row = document.createElement("li");
        row.className = "travel-plan-poll-result-row";

        const text = document.createElement("span");
        text.textContent = option.content;

        const count = document.createElement("span");
        count.className = "travel-plan-poll-choice-count";
        count.textContent = `${option.voteCount}표`;

        row.append(text, count);
        return row;
    }

    function renderDetail(poll) {
        openedPollId = poll.id;
        const parts = [detailHeadOf(poll)];

        if (poll.status === "CLOSED") {
            const heading = document.createElement("p");
            heading.className = "travel-plan-poll-label";
            heading.textContent = "투표 결과";

            const results = document.createElement("ol");
            results.className = "travel-plan-poll-results";
            (poll.options || []).forEach(option => results.append(resultRowOf(option)));

            const finalHeading = document.createElement("p");
            finalHeading.className = "travel-plan-poll-label";
            finalHeading.textContent = "최종 선택";

            const winner = document.createElement("p");
            winner.className = "travel-plan-poll-winner";
            winner.textContent = poll.winnerSummary || "투표 결과 없음";

            parts.push(heading, results, finalHeading, winner);
        } else {
            const choices = document.createElement("div");
            choices.className = "travel-plan-poll-choices";
            choices.setAttribute("data-travel-plan-poll-choices", "");
            (poll.options || []).forEach(option =>
                choices.append(optionChoiceOf(poll, option)));

            const error = document.createElement("p");
            error.className = "travel-plan-poll-error";
            error.hidden = true;
            error.setAttribute("data-travel-plan-poll-vote-error", "");

            const actions = document.createElement("div");
            actions.className = "travel-plan-poll-actions";
            const submitVote = document.createElement("button");
            submitVote.type = "button";
            submitVote.className = "travel-plan-poll-submit";
            submitVote.setAttribute("data-travel-plan-poll-vote", "");
            submitVote.textContent = "투표하기";
            submitVote.addEventListener("click", () => vote(poll.id));
            actions.append(submitVote);

            parts.push(choices, error, actions);
        }

        detailBody.replaceChildren(...parts);
    }

    async function fetchDetail(pollId) {
        const response = await fetch(`/travel-plans/${planId}/polls/${pollId}`, {
            headers: { "X-Requested-With": "XMLHttpRequest" }
        });
        if (!response.ok) throw new Error("poll unavailable");
        return response.json();
    }

    async function openDetail(pollId) {
        try {
            const poll = await fetchDetail(pollId);
            renderDetail(poll);
            showDetailView();
        } catch (error) {
            // 못 읽으면 목록에 그대로 머문다.
        }
    }

    /** 표가 바뀌었다는 알림을 받으면 지금 보고 있는 상세를 서버에서 다시 읽는다. */
    async function refreshDetail() {
        if (openedPollId == null) return;
        try {
            renderDetail(await fetchDetail(openedPollId));
        } catch (error) {
            // 못 읽어도 보고 있던 화면은 그대로 둔다.
        }
    }

    function showVoteError(message) {
        const error = detailBody.querySelector("[data-travel-plan-poll-vote-error]");
        if (!error) return;
        error.textContent = message || "투표하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        error.hidden = false;
    }

    async function vote(pollId) {
        if (voting) return;
        const chosen = Array.from(
                detailBody.querySelectorAll("[data-travel-plan-poll-choice]:checked"))
            .map(input => Number(input.value));

        voting = true;
        const button = detailBody.querySelector("[data-travel-plan-poll-vote]");
        if (button) button.disabled = true;
        try {
            const response = await fetch(`/travel-plans/${planId}/polls/${pollId}/vote`, {
                method: "POST",
                headers: csrfHeaders(),
                body: JSON.stringify({ optionIds: chosen })
            });
            if (!response.ok) {
                const payload = await response.json().catch(() => null);
                showVoteError(payload?.message);
                return;
            }
            /*
              저장된 뒤의 값은 서버가 돌려준 그대로 쓴다.
              화면에서 표를 스스로 +1 하면 내 알림이 겹쳐 두 번 오를 수 있다.
            */
            renderDetail(await response.json());
            // 목록의 참여 인원도 함께 맞춘다.
            loadTab("OPEN", true);
        } catch (error) {
            showVoteError(null);
        } finally {
            voting = false;
            const current = detailBody.querySelector("[data-travel-plan-poll-vote]");
            if (current) current.disabled = false;
        }
    }

    // ── 저장 ────────────────────────────────────────────────

    /** 화면에 적힌 값 그대로. 빈 줄은 서버가 덜어 낸다. */
    function readForm() {
        return {
            question: question ? question.value : "",
            selectionType:
                modal.querySelector("[data-travel-plan-poll-selection]:checked")?.value || "SINGLE",
            options: rows().map(row =>
                row.querySelector("[data-travel-plan-poll-option]")?.value || "")
        };
    }

    function csrfHeaders() {
        const token = document.querySelector("meta[name=\"_csrf\"]")?.content;
        const header = document.querySelector("meta[name=\"_csrf_header\"]")?.content;
        const headers = { "Content-Type": "application/json" };
        if (token && header) headers[header] = token;
        return headers;
    }

    async function submit() {
        if (submitting) return;
        submitting = true;
        if (submitButton) submitButton.disabled = true;
        clearError();

        try {
            const response = await fetch(`/travel-plans/${planId}/polls`, {
                method: "POST",
                headers: csrfHeaders(),
                body: JSON.stringify(readForm())
            });
            if (!response.ok) {
                // 만들기 화면과 입력한 값을 그대로 두고 사유만 알린다.
                const payload = await response.json().catch(() => null);
                showError(payload?.message);
                return;
            }
            // 방금 만든 것을 바로 볼 수 있게 목록으로 돌아간다. 창은 닫지 않는다.
            activeTab = "OPEN";
            showListView();
            onPollCreated();
        } catch (error) {
            showError(null);
        } finally {
            submitting = false;
            if (submitButton) submitButton.disabled = false;
        }
    }

    form?.addEventListener("submit", event => {
        event.preventDefault();
        submit();
    });

    /*
      한글 조합 중의 Enter 는 글자를 확정하는 것이지 제출이 아니다.
      판단 규칙은 일정 편집기·채팅과 같은 곳에 있다.
    */
    let composing = false;
    modal.addEventListener("compositionstart", () => {
        composing = true;
    });
    modal.addEventListener("compositionend", () => {
        composing = false;
    });
    form?.addEventListener("keydown", event => {
        if (event.key !== "Enter") return;
        if (window.travelPlanIme.isComposing(event, composing)) {
            // 조합 중에는 폼이 저절로 보내지지 않게 막기만 한다.
            event.preventDefault();
        }
    });

    // ── 연결 ────────────────────────────────────────────────

    /*
      만들어졌거나 표가 바뀌었다는 알림. 만든 사람·투표한 사람에게도 이 길로 온다.

      알림에는 숫자가 없다. 화면이 스스로 표를 올리면
      내 저장 응답과 내 알림이 겹쳐 두 번 오를 수 있어서,
      알림은 "다시 읽어라" 는 신호로만 쓴다.
    */
    realtime()?.subscribePolls(payload => {
        if (payload.type === "POLL_CREATED") {
            onPollCreated();
            return;
        }
        if (payload.type !== "POLL_VOTED") return;
        // 목록을 보고 있으면 참여 인원이, 상세를 보고 있으면 표가 바뀐다.
        if (loadedTabs.has("OPEN")) loadTab("OPEN", true);
        if (String(openedPollId) === String(payload.pollId)) refreshDetail();
    });

    // 채팅의 "새 투표를 만들었어요" 를 누르면 여기가 열린다.
    document.addEventListener("travelplan:poll-center-open", event => {
        const pollId = event.detail?.pollId;
        openModal(false);
        // 어떤 투표인지 알면 그 상세로 바로 들어간다.
        if (pollId != null) openDetail(pollId);
    });

    // 끊겨 있던 사이에 만들어진 투표는 다시 받을 수 없다. 그때 서버에서 다시 읽는다.
    realtime()?.onReconnected(() => {
        loadedTabs.clear();
        loadTab(activeTab, true);
    });

    // 머리글의 숫자는 채팅창을 열 때 한 번 맞춘다.
    document.addEventListener("travelplan:chat-opened", () => {
        if (!loadedTabs.has("OPEN")) loadTab("OPEN", true);
    });
});
