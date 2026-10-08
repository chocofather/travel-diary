/**
 * 관리자 여행지 이미지 관리 - 등록된 사진 공통 선택 관리(머리말).
 *
 * 카드의 '선택' 체크박스 하나를 선택 삭제와 출처 일괄 적용이 같이 쓴다.
 * 체크박스는 form 속성으로 선택 삭제 폼에 묶여 있어, 체크한 사진 ID만 imageIds 로 보낸다.
 * 여기서는 선택 수 표시, 전체 선택/해제, 출처 일괄 적용 영역으로 이동, 삭제 확인, 중복 제출 방지만 맡는다.
 * 출처 일괄 적용 자체(검증·저장)는 admin-destination-image-bulk-source.js 가 같은 선택으로 처리한다.
 * 대표 이미지 처리는 서버가 단건 삭제와 같은 규칙(남은 첫 사진이 대표)으로 한다.
 */
document.addEventListener("DOMContentLoaded", () => {
    const form = document.querySelector("[data-image-bulk-delete]");
    if (!form) return;

    const submitButton = form.querySelector("[data-image-bulk-delete-submit]");
    const selectAllButton = form.querySelector("[data-image-bulk-delete-all]");
    const clearButton = form.querySelector("[data-image-bulk-delete-clear]");
    const countLabel = form.querySelector("[data-image-selection-count]");
    const sourceButton = form.querySelector("[data-image-bulk-source-open]");
    const sourcePanel = document.querySelector("[data-image-bulk='existing']");
    const choices = () => Array.from(document.querySelectorAll("[data-image-delete-select]"));
    const selected = () => choices().filter(choice => choice.checked);
    let submitting = false;

    function update() {
        const all = choices();
        const targets = selected();
        const count = targets.length;
        // Commons 사진은 출처를 고칠 수 없어 출처 일괄 적용 대상에서만 빠진다
        const sourceCount = targets.filter(choice => choice.dataset.imageCommons !== "true").length;
        submitButton.disabled = submitting || count === 0;
        submitButton.textContent = submitting ? "삭제 중..." : "선택 삭제";
        selectAllButton.disabled = submitting || count === all.length;
        clearButton.disabled = submitting || count === 0;
        if (countLabel) countLabel.textContent = `${count}장 선택`;
        if (sourceButton) sourceButton.disabled = submitting || !sourcePanel || sourceCount === 0;
        all.forEach(choice => choice.closest(".admin-destination-image-card")
            ?.classList.toggle("is-selected", choice.checked));
    }

    // 출처 일괄 적용 영역도 선택 수를 다시 세도록 바뀐 체크박스마다 change 를 알린다
    function setAll(checked) {
        if (submitting) return;
        choices().forEach(choice => {
            if (choice.checked === checked) return;
            choice.checked = checked;
            choice.dispatchEvent(new Event("change", {bubbles: true}));
        });
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
    // 카드의 사진 영역을 눌러도 같은 체크박스를 토글한다(카드 전체가 아니라 사진 영역만).
    // 체크박스(라벨)를 직접 누른 경우는 브라우저가 이미 바꾸므로 여기서 다시 바꾸지 않는다.
    document.addEventListener("click", event => {
        const preview = event.target.closest?.(".admin-destination-image-grid .admin-destination-image-preview");
        if (!preview || submitting || event.target.closest("label, a, button, input, select")) return;
        const choice = preview.querySelector("[data-image-delete-select]");
        if (!choice) return;
        choice.checked = !choice.checked;
        choice.dispatchEvent(new Event("change", {bubbles: true}));
    });
    selectAllButton.addEventListener("click", () => setAll(true));
    clearButton.addEventListener("click", () => setAll(false));
    // 공통 출처 입력 영역으로 옮겨 간다. 적용 대상은 지금 선택 그대로다
    sourceButton?.addEventListener("click", () => {
        if (!sourcePanel) return;
        sourcePanel.scrollIntoView({behavior: "smooth", block: "start"});
        sourcePanel.querySelector("[data-bulk-field]")?.focus({preventScroll: true});
    });

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
