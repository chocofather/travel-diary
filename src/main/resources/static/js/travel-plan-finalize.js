/*
  여행 계획 완료.

  이번 단계에서 하는 일은 "지금 완료할 수 있는가" 를 서버에 물어보는 것까지다.
  실제로 완료하거나 최종본을 만드는 경로는 아직 없다.

  누가 일정을 쓰고 있는지는 화면이 판단하지 않는다.
  서버가 들고 있는 지금 상태로만 알 수 있고, 쓰고 있다고 해서 막지도 않는다.
  누구인지 알려 주고 그래도 할지는 방장이 정한다.
*/
document.addEventListener("DOMContentLoaded", () => {
    /*
      완료된 방으로 화면을 바꾼다.

      함께 하던 일이 모두 끝났으므로 그 진입점을 화면에서 내린다.
      서버는 이미 전부 거부하지만, 눌러 보고 오류로 알게 두지 않는다.
      열어 둔 채팅·투표·초대 창이 있으면 그 창도 함께 닫힌다
      (진입점 뿌리를 감추면 그 안의 패널도 함께 사라지고,
       화면 바깥에 따로 뜨는 창은 아래에서 하나씩 닫는다).

      새로고침하지 않는다. 일정은 읽기 전용으로 바뀌고,
      완료됐다는 것까지만 알린다.
    */
    const COMPLETED_CLOSES = [
        // 채팅 진입점과 열려 있는 채팅창
        "[data-travel-plan-chat]",
        "[data-travel-plan-chat-panel]",
        // 투표 센터. 채팅창 안의 투표 진입점은 채팅창과 함께 사라진다
        "[data-travel-plan-poll-modal]",
        // 초대 버튼과 팝오버
        "[data-travel-plan-invite]",
        // 완료 버튼과 확인 창. 더 쓸 일이 없다
        "[data-travel-plan-finalize]",
        "[data-travel-plan-finalize-modal]"
    ];

    function markCompleted() {
        const planner = document.querySelector("[data-plan-id]");
        // 일정 A/B/C 는 이 표시 하나로 읽기 전용이 된다(기존 동작 그대로).
        if (planner) planner.classList.add("is-completed");

        /*
          hidden 하나로 진입점과 그 안의 패널이 함께 사라진다.
          .travel-plan-chat / .travel-plan-invite / .travel-plan-finalize 는
          display 를 정해 두지 않아 [hidden] 이 그대로 먹고,
          따로 뜨는 창들은 각자 [hidden] 규칙을 이미 갖고 있다.
        */
        COMPLETED_CLOSES.forEach(selector =>
            document.querySelectorAll(selector).forEach(element => {
                element.hidden = true;
                // 닫힌 상태와 표시가 어긋나지 않게 한다.
                element.querySelector("[aria-expanded]")
                    ?.setAttribute("aria-expanded", "false");
            }));

        if (document.querySelector("[data-travel-plan-completed-notice]")) return;
        const notice = document.createElement("p");
        notice.className = "travel-plan-completed-notice";
        notice.setAttribute("data-travel-plan-completed-notice", "");
        notice.setAttribute("role", "status");
        notice.textContent = "여행 계획이 완료되었어요. 이제 일정은 수정할 수 없습니다.";
        planner?.prepend(notice);
    }

    /*
      완료 알림은 방에 있는 모두가 받는다.
      확정 버튼은 방장에게만 있으므로, 아래 준비보다 먼저 붙여 두어야
      멤버 화면도 똑같이 읽기 전용으로 바뀐다.
    */
    document.addEventListener("travelplan:plan-completed", () => markCompleted());

    /*
      여기부터는 확정하는 쪽의 준비다. 방장 화면에만 있다.

      방장은 바뀔 수 있고, 그때 이 markup 은 통째로 새것으로 갈린다.
      그래서 한 번 찾아 두지 않고 갈릴 때마다 다시 찾는다.
      (한 번 담아 두면 넘겨받은 사람 화면에서 버튼이 눌리지 않는다)
    */
    let root = null;
    let modal = null;
    let planId = null;
    let openButton = null;
    let confirmButton = null;
    let warning = null;
    let notice = null;

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

    /*
      방장 전용 자리를 다시 찾아 동작을 붙인다.

      갈아 끼운 markup 은 전부 새 요소라 예전에 붙여 둔 동작이 없다.
      요소가 새것이므로 예전 동작은 그 요소와 함께 사라진다(두 번 붙지 않는다).
      방장이 아니면 이 자리가 비어 있어 아무것도 붙일 것이 없다.
    */
    function bindOwnerControls() {
        root = document.querySelector("[data-travel-plan-finalize]");
        modal = document.querySelector("[data-travel-plan-finalize-modal]");
        planId = root?.getAttribute("data-finalize-plan-id") || null;
        openButton = root?.querySelector("[data-travel-plan-finalize-open]") || null;
        confirmButton = modal?.querySelector("[data-travel-plan-finalize-confirm]") || null;
        warning = modal?.querySelector("[data-travel-plan-finalize-warning]") || null;
        notice = modal?.querySelector("[data-travel-plan-finalize-error]") || null;

        // 넘겨준 사람 화면에서는 여기서 끝난다. 남은 상태도 함께 지운다.
        if (!root || !modal || !planId) {
            checking = false;
            force = false;
            return;
        }

        openButton?.addEventListener("click", () => openModal());
        modal.querySelector("[data-travel-plan-finalize-cancel]")
            ?.addEventListener("click", () => closeModal());

        // 바깥의 어두운 곳을 누르면 닫는다. 창 안쪽 클릭은 그대로 둔다.
        modal.addEventListener("click", event => {
            if (event.target === modal) closeModal();
        });

        confirmButton?.addEventListener("click", () => finalizePlan());
    }

    // 창 바깥의 Esc 는 한 번만 붙여 둔다. 그때의 창을 그때 본다.
    document.addEventListener("keydown", event => {
        if (event.key !== "Escape" || !modal || modal.hidden) return;
        closeModal();
    });

    bindOwnerControls();
    // 방장이 바뀌어 이 자리가 갈렸다. 새 markup 에 동작을 다시 붙인다.
    document.addEventListener("travelplan:owner-actions-updated", () => bindOwnerControls());

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

});
