/*
  공동 여행계획의 실시간 연결.
  STOMP 연결은 이 파일 하나가 들고 있고, 그 위에서 두 가지를 구독한다.

    presence : 누가 접속해 있는지
    schedule : 어떤 DAY 가 바뀌었는지

  브라우저 하나가 /ws 에 두 번 붙지 않도록 client 는 하나만 만든다.
  일정 편집(travel-plan-scheduler.js)과는 DOM 이벤트로만 이야기한다.
*/
document.addEventListener("DOMContentLoaded", () => {
    const planner = document.querySelector("[data-plan-id]");
    if (!planner || typeof StompJs === "undefined") return;

    const planId = planner.getAttribute("data-plan-id");
    if (!planId) return;

    // ── 접속 표시 ───────────────────────────────────────────
    const rows = document.querySelectorAll("[data-travel-plan-member-row]");
    const countLabel = document.querySelector("[data-travel-plan-online-count]");

    // 연결되기 전에는 누가 붙어 있는지 알 수 없다. 아무도 온라인이라고 추측하지 않는다.
    function renderPresence(onlineMemberIds, onlineCount) {
        const online = new Set((onlineMemberIds || []).map(String));
        rows.forEach(row => {
            const isOnline = online.has(row.getAttribute("data-member-id"));
            row.classList.toggle("is-online", isOnline);
            const dot = row.querySelector("[data-travel-plan-presence-dot]");
            if (!dot) return;
            const label = isOnline ? "온라인" : "오프라인";
            dot.setAttribute("title", label);
            dot.setAttribute("aria-label", label);
        });
        if (countLabel) {
            countLabel.textContent = `${onlineCount || 0}명 접속 중`;
            countLabel.hidden = false;
        }
    }

    // ── 일정 갱신 ───────────────────────────────────────────
    // DAY 마다 마지막 요청 번호를 기억한다.
    // 빠르게 여러 번 바뀌면 응답이 뒤섞여 도착할 수 있어, 최신 요청의 응답만 반영한다.
    const dayRequests = new Map();
    // 편집 중이라 미뤄 둔 DAY. 편집이 끝나면 그때 새로 고친다.
    const pendingDays = new Set();
    let pendingResync = false;

    function dayElement(dayId) {
        return document.querySelector(`[data-travel-plan-day-id="${dayId}"]`);
    }

    /*
      새로 받은 markup 으로 그 자리를 갈아 끼운다.
      갈아 끼운 요소는 전부 새것이라 동작이 붙어 있지 않으므로,
      편집 쪽(travel-plan-scheduler.js)이 다시 붙일 수 있게 그 요소를 함께 알린다.
    */
    function replaceWith(target, html) {
        const holder = document.createElement("div");
        holder.innerHTML = html.trim();
        const fresh = holder.firstElementChild;
        if (!fresh) return;
        target.replaceWith(fresh);
        document.dispatchEvent(new CustomEvent("travelplan:schedule-updated", {
            detail: { root: fresh }
        }));
    }

    /** 그 DAY 안에서 지금 무언가 편집 중인지. 편집 중이면 덮어쓰지 않는다. */
    function isEditing(element) {
        return !!element && !!element.querySelector(".is-editing");
    }

    function anyEditing() {
        return !!document.querySelector("[data-travel-plan-days] .is-editing");
    }

    async function refreshDay(dayId) {
        const element = dayElement(dayId);
        if (!element) return;
        // 작성 중이던 내용을 날리지 않도록 편집이 끝날 때까지 미룬다.
        if (isEditing(element)) {
            pendingDays.add(String(dayId));
            return;
        }

        const sequence = (dayRequests.get(String(dayId)) || 0) + 1;
        dayRequests.set(String(dayId), sequence);

        const response = await fetch(`/travel-plans/${planId}/days/${dayId}/fragment`, {
            headers: { "X-Requested-With": "XMLHttpRequest" }
        });
        if (!response.ok) return;
        const html = await response.text();

        // 늦게 도착한 예전 응답이 최신 화면을 덮지 않게 한다.
        if (dayRequests.get(String(dayId)) !== sequence) return;
        const target = dayElement(dayId);
        if (!target || isEditing(target)) {
            pendingDays.add(String(dayId));
            return;
        }
        replaceWith(target, html);
    }

    /** 끊겨 있던 동안 놓친 변경은 다시 받을 수 없어 전체를 한 번에 맞춘다. */
    async function resyncSchedule() {
        const container = document.querySelector("[data-travel-plan-days]");
        if (!container) return;
        if (anyEditing()) {
            pendingResync = true;
            return;
        }

        const response = await fetch(`/travel-plans/${planId}/schedule/fragment`, {
            headers: { "X-Requested-With": "XMLHttpRequest" }
        });
        if (!response.ok) return;
        const html = await response.text();

        const target = document.querySelector("[data-travel-plan-days]");
        if (!target || anyEditing()) {
            pendingResync = true;
            return;
        }
        replaceWith(target, html);
    }

    // 편집이 끝나면 미뤄 둔 갱신을 그때 반영한다.
    document.addEventListener("travelplan:editor-idle", () => {
        if (pendingResync) {
            pendingResync = false;
            resyncSchedule();
            return;
        }
        const days = Array.from(pendingDays);
        pendingDays.clear();
        days.forEach(refreshDay);
    });

    // ── 작성 중 상태 ────────────────────────────────────────
    // 서버가 붙잡아 준 자리만 편집기를 열 수 있다. 화면이 혼자 판단하지 않는다.
    const pendingLocks = new Map();
    // lockKey -> 원격에서 작성 중인 사람. 내 자리는 여기 넣지 않는다.
    const remoteLocks = new Map();
    /** 내가 지금 붙잡고 있는 자리. 한 번에 하나뿐이다. */
    let heldLock = null;
    let requestSeq = 0;

    function addKey(dayId) {
        return `ADD:${dayId}`;
    }

    function itemKey(itemId) {
        return `ITEM:${itemId}`;
    }

    function lineOf(lock) {
        if (!lock) return null;
        return lock.mode === "ADD"
            ? document.querySelector(
                `[data-travel-plan-day-id="${lock.dayId}"] [data-travel-plan-slot]`)
            : document.querySelector(`[data-item-id="${lock.itemId}"]`);
    }

    /** 다른 사람이 쓰고 있다는 표시와 작성 중 글자를 그 자리에 보여 준다. */
    function renderRemote(lock) {
        const line = lineOf(lock);
        if (!line) return;
        // 내 편집기와 구분되는 class 를 쓴다.
        // (.is-editing 으로 표시하면 정식 갱신이 영원히 미뤄진다)
        line.classList.add("is-remote-editing");

        let note = line.querySelector("[data-travel-plan-remote-note]");
        if (!note) {
            note = document.createElement("p");
            note.className = "travel-plan-remote-note";
            note.setAttribute("data-travel-plan-remote-note", "");
            const body = line.querySelector(".travel-plan-line-body") || line;
            body.prepend(note);
        }
        const label = lock.mode === "ADD" ? "일정 작성 중" : "편집 중";
        const draft = (lock.content || "").trim();
        note.textContent = draft
            ? `${lock.displayName}님이 ${label}\n${draft}`
            : `${lock.displayName}님이 ${label}`;
    }

    function clearRemote(lock) {
        const line = lineOf(lock);
        if (!line) return;
        line.classList.remove("is-remote-editing");
        line.querySelector("[data-travel-plan-remote-note]")?.remove();
    }

    function applyLock(lock) {
        if (!lock || !lock.lockKey) return;
        // 내가 붙잡고 있는 자리는 내 입력칸이 이미 있으므로 덮어 그리지 않는다.
        if (heldLock === lock.lockKey) return;
        remoteLocks.set(lock.lockKey, lock);
        renderRemote(lock);
    }

    function dropLock(lock) {
        if (!lock || !lock.lockKey) return;
        remoteLocks.delete(lock.lockKey);
        clearRemote(lock);
    }

    function clearAllRemote() {
        Array.from(remoteLocks.values()).forEach(clearRemote);
        remoteLocks.clear();
    }

    function handleEditorEvent(payload) {
        if (payload.type === "SNAPSHOT") {
            // 끊겨 있던 사이의 옛 표시가 남지 않게 통째로 다시 그린다.
            clearAllRemote();
            (payload.locks || []).forEach(applyLock);
            return;
        }
        if (payload.type === "LOCKED" || payload.type === "DRAFT") {
            applyLock(payload.lock);
            return;
        }
        if (payload.type === "UNLOCKED") {
            dropLock(payload.lock);
            // 취소였다면 원본이 다시 보여야 하므로 그 DAY 를 서버에서 다시 읽는다.
            if (payload.lock?.dayId) refreshDay(payload.lock.dayId);
        }
    }

    function handleLockReply(payload) {
        if (payload.type === "SNAPSHOT") {
            handleEditorEvent(payload);
            return;
        }
        const waiting = pendingLocks.get(payload.requestId);
        if (!waiting) return;
        pendingLocks.delete(payload.requestId);
        if (payload.granted) {
            heldLock = payload.lock?.lockKey || null;
            remoteLocks.delete(heldLock);
            // 방 전체 알림이 이 답보다 먼저 도착했을 수 있다.
            // 그때 내 화면에 붙은 "편집 중" 표시를 내 자리로 확정되는 지금 걷어낸다.
            clearRemote(payload.lock);
        }
        waiting(payload);
    }

    /*
      편집 쪽(travel-plan-scheduler.js)이 쓰는 창구.
      연결은 이 파일 하나가 들고 있으므로 여기로만 오간다.
    */
    window.travelPlanRealtime = {
        /** 서버가 자리를 내줄 때까지 기다린다. 내주지 않으면 편집기를 열지 않는다. */
        requestLock(dayId, itemId) {
            if (!client.connected) return Promise.resolve({ granted: false });
            const requestId = `lock-${++requestSeq}`;
            return new Promise(resolve => {
                pendingLocks.set(requestId, resolve);
                client.publish({
                    destination: `/app/travel-plans/${planId}/editor/lock`,
                    body: JSON.stringify({ requestId, dayId, itemId: itemId ?? null })
                });
                // 답이 오지 않으면 열지 않는다.
                window.setTimeout(() => {
                    if (!pendingLocks.has(requestId)) return;
                    pendingLocks.delete(requestId);
                    resolve({ granted: false });
                }, 3000);
            });
        },

        sendDraft(content) {
            if (!heldLock || !client.connected) return;
            client.publish({
                destination: `/app/travel-plans/${planId}/editor/draft`,
                body: JSON.stringify({ lockKey: heldLock, content })
            });
        },

        releaseLock() {
            const lockKey = heldLock;
            heldLock = null;
            if (!lockKey || !client.connected) return;
            client.publish({
                destination: `/app/travel-plans/${planId}/editor/unlock`,
                body: JSON.stringify({ lockKey })
            });
        },

        /** 다른 사람이 붙잡고 있는 자리인지. 열기 전에 화면이 먼저 확인한다. */
        isLockedByOther(dayId, itemId) {
            const key = itemId ? itemKey(itemId) : addKey(dayId);
            return key !== heldLock && remoteLocks.has(key);
        },

        refreshDay
    };

    // ── 연결 ────────────────────────────────────────────────
    const client = new StompJs.Client({
        brokerURL: `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws`,
        // 라이브러리가 알아서 다시 붙는다. 직접 재시도 루프를 만들지 않는다.
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000
    });

    // 처음 그린 화면은 이미 최신이라 따라잡을 것이 없다. 다시 붙었을 때만 맞춘다.
    let connectedBefore = false;

    client.onConnect = () => {
        // 다시 붙을 때마다 구독과 인사를 새로 한다.
        // 서버는 연결(sessionId) 단위로 세므로 끊긴 연결이 쌓이지 않는다.
        client.subscribe(`/topic/travel-plans/${planId}/presence`, message => {
            try {
                const payload = JSON.parse(message.body);
                renderPresence(payload.onlineMemberIds, payload.onlineCount);
            } catch (error) {
                // 알 수 없는 형식이면 표시를 건드리지 않는다.
            }
        });

        // 변경 알림은 "이 DAY 를 다시 읽어라" 는 신호일 뿐이다. 내용은 서버에서 다시 가져온다.
        client.subscribe(`/topic/travel-plans/${planId}/schedule`, message => {
            try {
                const payload = JSON.parse(message.body);
                (payload.affectedDayIds || []).forEach(refreshDay);
            } catch (error) {
                // 알 수 없는 형식이면 화면을 건드리지 않는다.
            }
        });

        // 작성 중 상태. 방 전체 알림과, 내 잠금 요청의 답이 오는 개인 큐 두 갈래다.
        client.subscribe(`/topic/travel-plans/${planId}/editor`, message => {
            try {
                handleEditorEvent(JSON.parse(message.body));
            } catch (error) {
                // 알 수 없는 형식이면 화면을 건드리지 않는다.
            }
        });
        client.subscribe("/user/queue/travel-plan-editor", message => {
            try {
                handleLockReply(JSON.parse(message.body));
            } catch (error) {
                // 알 수 없는 형식이면 화면을 건드리지 않는다.
            }
        });

        client.publish({ destination: `/app/travel-plans/${planId}/presence/join` });

        if (connectedBefore) {
            resyncSchedule();
            // 끊겨 있던 사이에 사라진 옛 "편집 중" 표시가 남지 않게 지금 상태를 다시 받는다.
            heldLock = null;
            clearAllRemote();
            client.publish({ destination: `/app/travel-plans/${planId}/editor/sync` });
        }
        connectedBefore = true;
    };

    client.onWebSocketClose = () => {
        // 끊긴 동안에는 아무도 온라인으로 두지 않는다.
        renderPresence([], 0);
    };

    client.activate();

    // 페이지를 떠날 때는 연결을 정리해 서버가 곧바로 알아채게 한다.
    window.addEventListener("pagehide", () => client.deactivate());
});
