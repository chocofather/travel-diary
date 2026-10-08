/**
 * 관리자 여행지 이미지 관리 - 등록된 사진 선택 삭제.
 *
 * 카드의 '삭제 선택' 체크박스는 form 속성으로 선택 삭제 폼에 묶여 있어, 체크한 사진 ID만 imageIds 로 보낸다.
 * 여기서는 선택 수 표시, 전체 선택/해제, 삭제 확인, 중복 제출 방지만 맡는다.
 * 대표 이미지 처리는 서버가 단건 삭제와 같은 규칙(남은 첫 사진이 대표)으로 한다.
 */
document.addEventListener("DOMContentLoaded", () => {
    const form = document.querySelector("[data-image-bulk-delete]");
    if (!form) return;

    const submitButton = form.querySelector("[data-image-bulk-delete-submit]");
    const selectAllButton = form.querySelector("[data-image-bulk-delete-all]");
    const clearButton = form.querySelector("[data-image-bulk-delete-clear]");
    const choices = () => Array.from(document.querySelectorAll("[data-image-delete-select]"));
    const selected = () => choices().filter(choice => choice.checked);
    let submitting = false;

    function update() {
        const all = choices();
        const count = selected().length;
        submitButton.disabled = submitting || count === 0;
        submitButton.textContent = submitting ? "삭제 중..." : count ? `선택 삭제 (${count})` : "선택 삭제";
        selectAllButton.disabled = submitting || count === all.length;
        clearButton.disabled = submitting || count === 0;
        all.forEach(choice => choice.closest(".admin-destination-image-card")
            ?.classList.toggle("is-delete-selected", choice.checked));
    }

    function setAll(checked) {
        if (submitting) return;
        choices().forEach(choice => { choice.checked = checked; });
        update();
    }

    function confirmMessage(targets) {
        const message = `선택한 이미지 ${targets.length}장을 삭제하시겠습니까?`;
        if (!targets.some(choice => choice.dataset.imageMain === "true")) return message;
        return targets.length === choices().length
            ? `${message}\n등록된 사진이 모두 삭제되어 대표 이미지가 없어집니다.`
            : `${message}\n대표 이미지가 포함되어 있어, 삭제 후 남은 사진 중 첫 번째 사진이 대표 이미지가 됩니다.`;
    }

    document.addEventListener("change", event => {
        if (event.target.matches?.("[data-image-delete-select]")) update();
    });
    selectAllButton.addEventListener("click", () => setAll(true));
    clearButton.addEventListener("click", () => setAll(false));

    form.addEventListener("submit", event => {
        const targets = selected();
        if (submitting || targets.length === 0 || !window.confirm(confirmMessage(targets))) {
            event.preventDefault();
            return;
        }
        // 첫 제출 즉시 잠근다. 체크박스는 비활성화하면 전송 값에서 빠지므로 그대로 둔다.
        submitting = true;
        update();
    });

    // 오류 화면에서 뒤로 돌아오면(페이지 캐시) 다시 삭제할 수 있게 풀어 준다.
    window.addEventListener("pageshow", event => {
        if (!event.persisted) return;
        submitting = false;
        update();
    });

    update();
});
