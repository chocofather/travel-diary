/*
  공동 여행계획의 접속 표시.
  일정 편집(travel-plan-scheduler.js)과 책임을 섞지 않으려고 파일을 나눠 둔다.

  하는 일은 넷뿐이다.
    1. STOMP 로 붙는다
    2. 지금 보고 있는 방의 접속 topic 을 구독한다
    3. 들어왔다고 알린다
    4. 받은 목록으로 참여자 줄의 점을 갱신한다
*/
document.addEventListener("DOMContentLoaded", () => {
    const planner = document.querySelector("[data-plan-id]");
    if (!planner || typeof StompJs === "undefined") return;

    const planId = planner.getAttribute("data-plan-id");
    if (!planId) return;

    const rows = document.querySelectorAll("[data-travel-plan-member-row]");
    const countLabel = document.querySelector("[data-travel-plan-online-count]");
    if (rows.length === 0) return;

    // 연결되기 전에는 누가 붙어 있는지 알 수 없다. 아무도 온라인이라고 추측하지 않는다.
    function render(onlineMemberIds, onlineCount) {
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

    const client = new StompJs.Client({
        brokerURL: `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws`,
        // 라이브러리가 알아서 다시 붙는다. 직접 재시도 루프를 만들지 않는다.
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000
    });

    client.onConnect = () => {
        // 다시 붙을 때마다 구독과 인사를 새로 한다.
        // 서버는 연결(sessionId) 단위로 세므로 끊긴 연결이 쌓이지 않는다.
        client.subscribe(`/topic/travel-plans/${planId}/presence`, message => {
            try {
                const payload = JSON.parse(message.body);
                render(payload.onlineMemberIds, payload.onlineCount);
            } catch (error) {
                // 알 수 없는 형식이면 표시를 건드리지 않는다.
            }
        });
        client.publish({ destination: `/app/travel-plans/${planId}/presence/join` });
    };

    client.onWebSocketClose = () => {
        // 끊긴 동안에는 아무도 온라인으로 두지 않는다.
        render([], 0);
    };

    client.activate();

    // 페이지를 떠날 때는 연결을 정리해 서버가 곧바로 알아채게 한다.
    window.addEventListener("pagehide", () => client.deactivate());
});
