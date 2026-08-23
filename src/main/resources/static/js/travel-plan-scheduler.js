document.addEventListener("DOMContentLoaded", () => {
    const planner = document.querySelector("[data-plan-id]");
    if (!planner) return;

    // 화면 전체에서 열려 있는 입력칸은 항상 하나뿐이다.
    let activeSlot = null;
    // 저장 중에는 focus-out 이 한 번 더 저장하지 않도록 잠근다.
    let submitting = false;

    function textareaOf(slot) {
        return slot.querySelector("textarea");
    }

    function closeActive() {
        if (!activeSlot) return;
        const form = activeSlot.querySelector("[data-travel-plan-slot-form]");
        const textarea = textareaOf(activeSlot);
        if (textarea) textarea.value = "";
        if (form) form.hidden = true;
        activeSlot.classList.remove("is-editing");
        activeSlot = null;
    }

    function open(slot) {
        if (activeSlot === slot) return;
        closeActive();
        const form = slot.querySelector("[data-travel-plan-slot-form]");
        if (!form) return;
        form.hidden = false;
        slot.classList.add("is-editing");
        activeSlot = slot;
        textareaOf(slot)?.focus();
    }

    function save(slot) {
        const textarea = textareaOf(slot);
        const form = slot.querySelector("[data-travel-plan-slot-form]");
        if (!textarea || !form) return;
        // 공백만 있으면 저장하지 않고 그냥 닫는다.
        if (textarea.value.trim() === "") {
            closeActive();
            return;
        }
        submitting = true;
        form.requestSubmit();
    }

    planner.querySelectorAll("[data-travel-plan-slot]").forEach(slot => {
        slot.addEventListener("click", () => open(slot));

        const textarea = textareaOf(slot);
        if (!textarea) return;

        textarea.addEventListener("keydown", event => {
            if (event.key === "Escape") {
                event.preventDefault();
                closeActive();
                return;
            }
            // Enter 는 저장, Shift+Enter 는 줄바꿈.
            if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                save(slot);
            }
        });

        textarea.addEventListener("blur", () => {
            if (submitting) return;
            if (textarea.value.trim() === "") {
                closeActive();
                return;
            }
            save(slot);
        });
    });

    // 저장에 실패해 서버가 열어 둔 슬롯이 있으면 그 자리에서 이어 쓴다.
    const reopened = planner.querySelector(
        "[data-travel-plan-slot]:has([data-travel-plan-slot-form]:not([hidden]))");
    if (reopened) {
        activeSlot = reopened;
        reopened.classList.add("is-editing");
        const textarea = textareaOf(reopened);
        textarea?.focus();
        textarea?.setSelectionRange(textarea.value.length, textarea.value.length);
    }
});
