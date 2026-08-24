document.addEventListener("DOMContentLoaded", () => {
    const planner = document.querySelector("[data-plan-id]");
    if (!planner) return;

    // 화면 전체에서 열려 있는 편집기는 항상 하나뿐이다.
    // 추가 슬롯과 기존 일정 수정도 서로 동시에 열리지 않는다.
    let activeLine = null;
    // 저장 중에는 focus-out 이 한 번 더 저장하지 않도록 잠근다.
    let submitting = false;

    // 대안 편집기도 같은 규칙을 따른다. 열려 있는 것은 A 쪽이든 대안 쪽이든 하나뿐이다.
    let activeAlt = null;

    function formOf(line) {
        return line.querySelector("[data-travel-plan-slot-form], [data-travel-plan-item-form]");
    }

    function textareaOf(line) {
        // 대안 편집기의 textarea 까지 집히지 않게 A 일정 폼 안에서만 찾는다.
        const form = formOf(line);
        return form ? form.querySelector("textarea") : null;
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
        closeAlt();
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

    // ── 대안(B/C) ───────────────────────────────────────────────
    // A 아래에 접힌 목록이 하나 있고, 그 안에서 각 대안이 그 자리에서 편집기가 된다.

    function altFormOf(node) {
        return node.querySelector(
            "[data-travel-plan-alt-form], [data-travel-plan-alt-new-form]");
    }

    function altViewOf(node) {
        return node.querySelector("[data-travel-plan-alt-view]");
    }

    // 편집기를 닫는다. 저장된 대안은 원래 값으로 되돌리고, 새 대안은 비운다.
    function closeAlt() {
        if (!activeAlt) return;
        const form = altFormOf(activeAlt);
        const view = altViewOf(activeAlt);
        if (form) {
            // 저장된 대안은 원래 값으로, 새 대안은 빈 칸으로 돌아간다.
            form.reset();
            form.hidden = true;
        }
        if (view) view.hidden = false;
        activeAlt.classList.remove("is-editing");
        activeAlt = null;
    }

    function openAlt(node) {
        if (activeAlt === node) return;
        closeActive();
        closeAlt();
        const form = altFormOf(node);
        if (!form) return;
        const view = altViewOf(node);
        if (view) view.hidden = true;
        form.hidden = false;
        node.classList.add("is-editing");
        activeAlt = node;
        const textarea = form.querySelector("textarea");
        autoResize(textarea);
        const first = form.querySelector("input[type=\"text\"]") || textarea;
        first?.focus();
    }

    function listOf(line) {
        return line.querySelector("[data-travel-plan-alt-list]");
    }

    function toggleOf(line) {
        return line.querySelector("[data-travel-plan-alt-toggle]");
    }

    function expand(line, shouldOpen) {
        const list = listOf(line);
        if (!list) return;
        list.hidden = !shouldOpen;
        toggleOf(line)?.setAttribute("aria-expanded", String(shouldOpen));
        // 접으면 그 안에서 열려 있던 편집기도 함께 닫는다.
        if (!shouldOpen) closeAlt();
    }

    planner.querySelectorAll("[data-travel-plan-item]").forEach(line => {
        toggleOf(line)?.addEventListener("click", () => {
            const list = listOf(line);
            expand(line, list ? list.hidden : false);
        });

        // ⋯ 메뉴의 "대안 추가" 는 목록을 펼치고 새 입력만 열어 준다.
        line.querySelector("[data-travel-plan-alt-add]")?.addEventListener("click", () => {
            closeMenus(null);
            expand(line, true);
            const slot = line.querySelector("[data-travel-plan-alt-new]");
            if (slot) openAlt(slot);
        });

        // 저장된 B/C 와 아직 비어 있는 새 대안 칸이 같은 규칙을 쓴다.
        const slots = "[data-travel-plan-alt], [data-travel-plan-alt-new]";
        line.querySelectorAll(slots).forEach(node => {
            altViewOf(node)?.addEventListener("click", () => openAlt(node));
            node.querySelector("[data-travel-plan-alt-cancel]")
                ?.addEventListener("click", () => closeAlt());

            const textarea = altFormOf(node)?.querySelector("textarea");
            if (!textarea) return;
            textarea.addEventListener("input", () => autoResize(textarea));
            textarea.addEventListener("keydown", event => {
                if (event.key === "Escape") {
                    event.preventDefault();
                    closeAlt();
                    return;
                }
                // A 일정과 같다. Enter 는 저장, Shift+Enter 는 줄바꿈.
                if (event.key === "Enter" && !event.shiftKey) {
                    event.preventDefault();
                    if (textarea.value.trim() === "") return;
                    submitting = true;
                    altFormOf(node).requestSubmit();
                }
            });
        });
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

    // ── 초대 (OWNER 상단 보조 액션) ──────────────────────────────
    // 플래너 종이 바깥의 상단 줄에 있으므로 document 에서 찾는다.
    const invite = document.querySelector("[data-travel-plan-invite]");
    if (invite) {
        const toggle = invite.querySelector("[data-travel-plan-invite-toggle]");
        const panel = invite.querySelector("[data-travel-plan-invite-panel]");
        const url = invite.querySelector("[data-travel-plan-invite-url]");
        const copy = invite.querySelector("[data-travel-plan-invite-copy]");

        function openPanel(shouldOpen) {
            if (!panel) return;
            panel.hidden = !shouldOpen;
            toggle?.setAttribute("aria-expanded", String(shouldOpen));
        }

        toggle?.addEventListener("click", event => {
            event.stopPropagation();
            openPanel(panel ? panel.hidden : false);
        });
        // 패널 안을 눌렀다고 닫히지 않게 한다.
        panel?.addEventListener("click", event => event.stopPropagation());
        document.addEventListener("click", () => openPanel(false));

        copy?.addEventListener("click", () => {
            if (!url) return;
            // 복사가 막혀 있어도 사용자가 직접 고를 수 있게 먼저 선택해 둔다.
            url.select();
            url.setSelectionRange(0, url.value.length);
            navigator.clipboard?.writeText(url.value)
                .then(() => { copy.textContent = "복사됨"; })
                .catch(() => { copy.textContent = "직접 복사해 주세요"; });
        });

        // 방금 발급한 링크는 이 화면에서만 볼 수 있으므로 바로 펼쳐 준다.
        if (url) openPanel(true);
    }

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
