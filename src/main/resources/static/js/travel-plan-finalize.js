/*
  여행 계획 완료.

  이번 단계에서 하는 일은 "지금 완료할 수 있는가" 를 서버에 물어보는 것까지다.
  실제로 완료하거나 최종본을 만드는 경로는 아직 없다.

  누가 일정을 쓰고 있는지는 화면이 판단하지 않는다.
  서버가 들고 있는 지금 상태로만 알 수 있고, 쓰고 있다고 해서 막지도 않는다.
  누구인지 알려 주고 그래도 할지는 방장이 정한다.
*/
document.addEventListener("DOMContentLoaded", () => {
    const root = document.querySelector("[data-travel-plan-finalize]");
    const modal = document.querySelector("[data-travel-plan-finalize-modal]");
    if (!root || !modal) return;

    const planId = root.getAttribute("data-finalize-plan-id");
    if (!planId) return;

    const openButton = root.querySelector("[data-travel-plan-finalize-open]");
    const confirmButton = modal.querySelector("[data-travel-plan-finalize-confirm]");
    const warning = modal.querySelector("[data-travel-plan-finalize-warning]");
    const notice = modal.querySelector("[data-travel-plan-finalize-error]");

    let checking = false;
    /** 쓰고 있는 사람이 있는 것을 알고도 하겠다고 한 상태. */
    let force = false;

    function showError(message) {
        if (!notice) return;
        notice.textContent = message || "확인하지 못했습니다. 잠시 후 다시 시도해 주세요.";
        notice.hidden = false;
    }

    function clearMessages() {
        if (notice) {
            notice.hidden = true;
            notice.textContent = "";
        }
        if (warning) {
            warning.hidden = true;
            warning.textContent = "";
        }
    }

    /**
     * 쓰고 있는 사람을 사람 말로 옮긴다.
     * 한 명이면 그 이름, 여럿이면 첫 사람과 나머지 수로 줄여 쓴다.
     */
    function editingSentence(names) {
        const [first, ...rest] = names;
        const who = rest.length === 0 ? `${first}님` : `${first}님 외 ${rest.length}명`;
        return `${who}이 현재 일정을 편집 중입니다.\n`
            + "지금 완료하면 저장하지 않은 편집 내용은 사라질 수 있습니다.";
    }

    function renderWarning(names) {
        force = true;
        if (warning) {
            // 이름도 사용자가 정한 값이라 글자로만 넣는다.
            warning.textContent = editingSentence(names);
            warning.hidden = false;
        }
        if (confirmButton) confirmButton.textContent = "그래도 완료";
    }

    function renderReady() {
        force = false;
        if (confirmButton) confirmButton.textContent = "여행 계획 확정";
    }

    function csrfHeaders() {
        const token = document.querySelector("meta[name=\"_csrf\"]")?.content;
        const header = document.querySelector("meta[name=\"_csrf_header\"]")?.content;
        const headers = { "Content-Type": "application/json" };
        if (token && header) headers[header] = token;
        return headers;
    }

    /*
      지금 상태를 서버에 물어본다.

      쓰고 있는 사람이 있으면 경고를 띄우고 버튼을 "그래도 완료" 로 바꾼다.
      막지 않는다. 판단은 방장이 한다.
    */
    async function check() {
        if (checking) return null;
        checking = true;
        if (confirmButton) confirmButton.disabled = true;
        clearMessages();

        try {
            const response = await fetch(`/travel-plans/${planId}/finalize/check`, {
                method: "POST",
                headers: csrfHeaders()
            });
            if (!response.ok) {
                showError(null);
                return null;
            }
            const payload = await response.json();
            if (payload.activeEditorExists) {
                renderWarning(payload.activeEditorDisplayNames || []);
            } else {
                renderReady();
            }
            return payload;
        } catch (error) {
            showError(null);
            return null;
        } finally {
            checking = false;
            if (confirmButton) confirmButton.disabled = false;
        }
    }

    function openModal() {
        clearMessages();
        renderReady();
        modal.hidden = false;
        confirmButton?.focus();
        // 열자마자 지금 누가 쓰고 있는지 확인해, 누르기 전에 알 수 있게 한다.
        check();
    }

    function closeModal() {
        modal.hidden = true;
        clearMessages();
        renderReady();
    }

    openButton?.addEventListener("click", () => openModal());
    modal.querySelector("[data-travel-plan-finalize-cancel]")
        ?.addEventListener("click", () => closeModal());

    // 바깥의 어두운 곳을 누르면 닫는다. 창 안쪽 클릭은 그대로 둔다.
    modal.addEventListener("click", event => {
        if (event.target === modal) closeModal();
    });

    document.addEventListener("keydown", event => {
        if (event.key !== "Escape" || modal.hidden) return;
        closeModal();
    });

    /*
      방장이 하겠다고 눌렀다.

      경고를 보고 누른 것인지(force)를 함께 보낸다.
      서버는 물어본 결과를 믿지 않고 이 시점의 자격과 편집 상태를 다시 본다.
    */
    async function finalizePlan() {
        if (checking) return;
        checking = true;
        if (confirmButton) confirmButton.disabled = true;

        try {
            const response = await fetch(
                `/travel-plans/${planId}/finalize?force=${force}`,
                { method: "POST", headers: csrfHeaders() });
            if (!response.ok) {
                const payload = await response.json().catch(() => null);
                showError(payload?.message);
                return;
            }
            /*
              완료됐다. 이 화면도 더 이상 고칠 수 없는 상태가 된다.
              같은 방의 다른 사람은 방 알림으로 같은 처리를 받는다.
            */
            closeModal();
            markCompleted();
        } catch (error) {
            showError(null);
        } finally {
            checking = false;
            if (confirmButton) confirmButton.disabled = false;
        }
    }

    confirmButton?.addEventListener("click", () => finalizePlan());

    /*
      완료된 방으로 화면을 바꾼다.

      새로고침하지 않는다. 지금 열려 있던 편집기를 닫고, 더 이상 열리지 않게 한다.
      완료 화면 자체는 다음 단계라 여기서는 고칠 수 없다는 것까지만 알린다.
    */
    function markCompleted() {
        const planner = document.querySelector("[data-plan-id]");
        if (planner) planner.classList.add("is-completed");
        // 상단의 완료 버튼도 더 쓸 일이 없다.
        root.hidden = true;

        if (document.querySelector("[data-travel-plan-completed-notice]")) return;
        const notice = document.createElement("p");
        notice.className = "travel-plan-completed-notice";
        notice.setAttribute("data-travel-plan-completed-notice", "");
        notice.setAttribute("role", "status");
        notice.textContent = "여행 계획이 완료되었어요. 이제 일정은 수정할 수 없습니다.";
        planner?.prepend(notice);
    }

    // 다른 사람이 완료했을 때도 같은 처리를 받는다.
    document.addEventListener("travelplan:plan-completed", () => markCompleted());
});
