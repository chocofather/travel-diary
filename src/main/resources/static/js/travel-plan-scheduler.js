document.addEventListener("DOMContentLoaded", () => {
    const planner = document.querySelector("[data-plan-id]");
    if (!planner) return;

    // 화면 전체에서 열려 있는 편집기는 항상 하나뿐이다.
    // 추가 슬롯과 기존 일정 수정도 서로 동시에 열리지 않는다.
    let activeLine = null;
    // 저장 중에는 focus-out 이 한 번 더 저장하지 않도록 잠근다.
    let submitting = false;

    function formOf(line) {
        return line.querySelector("[data-travel-plan-slot-form], [data-travel-plan-item-form]");
    }

    function textareaOf(line) {
        return line.querySelector("textarea");
    }

    function isItem(line) {
        return line.hasAttribute("data-travel-plan-item");
    }

    // 입력 높이를 내용에 맞춘다. 줄 안에서 편집하는 느낌을 유지하기 위한 것이다.
    function autoResize(textarea) {
        if (!textarea) return;
        textarea.style.height = "auto";
        textarea.style.height = `${textarea.scrollHeight}px`;
    }

    function closeActive() {
        if (!activeLine) return;
        const form = formOf(activeLine);
        const textarea = textareaOf(activeLine);
        if (isItem(activeLine)) {
            // 수정 취소는 원래 내용으로 되돌린다.
            const content = activeLine.querySelector("[data-travel-plan-item-content]");
            if (textarea && content) textarea.value = content.textContent.trim();
            if (content) content.hidden = false;
        } else if (textarea) {
            textarea.value = "";
        }
        if (form) form.hidden = true;
        activeLine.classList.remove("is-editing");
        activeLine = null;
    }

    function open(line) {
        if (activeLine === line) return;
        closeActive();
        const form = formOf(line);
        if (!form) return;
        if (isItem(line)) {
            const content = line.querySelector("[data-travel-plan-item-content]");
            if (content) content.hidden = true;
        }
        form.hidden = false;
        line.classList.add("is-editing");
        activeLine = line;
        const textarea = textareaOf(line);
        autoResize(textarea);
        textarea?.focus();
        textarea?.setSelectionRange(textarea.value.length, textarea.value.length);
    }

    function originalContentOf(line) {
        const content = line.querySelector("[data-travel-plan-item-content]");
        return content ? content.textContent.trim() : "";
    }

    function save(line) {
        const textarea = textareaOf(line);
        const form = formOf(line);
        if (!textarea || !form) return;

        const value = textarea.value.trim();
        // 공백만 있으면 저장하지 않는다. 수정이면 원래 내용을 되살린다.
        if (value === "") {
            closeActive();
            return;
        }
        // 내용이 그대로면 불필요한 UPDATE 를 보내지 않는다.
        if (isItem(line) && value === originalContentOf(line)) {
            closeActive();
            return;
        }
        submitting = true;
        form.requestSubmit();
    }

    function bind(line) {
        const textarea = textareaOf(line);
        if (!textarea) return;

        textarea.addEventListener("input", () => autoResize(textarea));

        textarea.addEventListener("keydown", event => {
            if (event.key === "Escape") {
                event.preventDefault();
                closeActive();
                return;
            }
            // Enter 는 저장, Shift+Enter 는 줄바꿈.
            if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                save(line);
            }
        });

        textarea.addEventListener("blur", () => {
            if (submitting) return;
            save(line);
        });
    }

    // 빈 슬롯: 줄을 누르면 그 자리가 입력 상태가 된다.
    planner.querySelectorAll("[data-travel-plan-slot]").forEach(line => {
        line.addEventListener("click", () => open(line));
        bind(line);
    });

    // 기존 일정: 내용을 누르면 그 줄이 편집 상태가 된다.
    planner.querySelectorAll("[data-travel-plan-item]").forEach(line => {
        line.querySelector("[data-travel-plan-item-content]")
            ?.addEventListener("click", () => open(line));
        bind(line);
    });

    // ⋯ 메뉴는 한 번에 하나만 열어 둔다.
    function closeMenus(except) {
        planner.querySelectorAll("[data-travel-plan-menu-list]").forEach(list => {
            if (list === except) return;
            list.hidden = true;
            list.parentElement?.querySelector("[data-travel-plan-menu-button]")
                ?.setAttribute("aria-expanded", "false");
        });
    }

    planner.querySelectorAll("[data-travel-plan-menu-button]").forEach(button => {
        button.addEventListener("click", event => {
            event.stopPropagation();
            const list = button.parentElement?.querySelector("[data-travel-plan-menu-list]");
            if (!list) return;
            const willOpen = list.hidden;
            // 편집 중이던 입력칸과 메뉴가 함께 열려 있지 않게 한다.
            if (willOpen) closeActive();
            closeMenus(list);
            list.hidden = !willOpen;
            button.setAttribute("aria-expanded", String(willOpen));
        });
    });

    document.addEventListener("click", () => closeMenus(null));

    // 저장에 실패해 서버가 열어 둔 슬롯이 있으면 그 자리에서 이어 쓴다.
    const reopened = planner.querySelector(
        "[data-travel-plan-slot]:has([data-travel-plan-slot-form]:not([hidden]))");
    if (reopened) {
        activeLine = reopened;
        reopened.classList.add("is-editing");
        const textarea = textareaOf(reopened);
        textarea?.focus();
        textarea?.setSelectionRange(textarea.value.length, textarea.value.length);
    }
});
