/**
 * 관리자 여행지 이미지 관리 - 등록된 사진 카드의 출처 영역(접기/펼치기).
 *
 * 출처 입력은 <details> 안에 있어 접어도 입력칸이 그대로 남는다(값·일괄 적용·저장 모두 그대로).
 * 여기서는 접힌 상태 때문에 놓치기 쉬운 것만 돕는다.
 */
document.addEventListener("DOMContentLoaded", () => {
    const sections = Array.from(document.querySelectorAll("[data-image-source-details]"));
    if (!sections.length) return;

    // 접힌 영역 안의 입력이 브라우저 검증에 걸리면 그 영역을 펼쳐, 브라우저가 오류 위치로 이동할 수 있게 한다.
    document.addEventListener("invalid", event => {
        const details = event.target.closest?.("[data-image-source-details]");
        if (details && !details.open) details.open = true;
    }, true);

    sections.forEach(details => {
        const form = details.querySelector("form");
        if (!form) return;
        // 접은 뒤에도 저장하지 않은 수정이 있다는 것을 요약줄에서 알 수 있게 한다.
        const markDirty = event => {
            if (event.isTrusted) details.dataset.dirty = "true";
        };
        form.addEventListener("input", markDirty);
        form.addEventListener("change", markDirty);
    });

    // 서버가 저장을 거절한 카드: 방금 입력한 값이 돌아와 있으니 저장 안 된 수정으로 표시하고 오류 위치를 보여 준다.
    const failed = document.querySelector(".admin-destination-image-card.is-source-error [data-image-source-details]");
    if (failed) {
        failed.open = true;
        failed.dataset.dirty = "true";
        const form = failed.querySelector("form");
        // URL 칸은 공통 규칙으로 다시 검사해 어느 칸이 문제인지 표시한다.
        form?.querySelectorAll("[data-image-source-url]").forEach(input =>
            input.dispatchEvent(new Event("change", {bubbles: true})));
        // 페이지를 다 그린 뒤에 포커스를 옮긴다(읽는 도중에는 브라우저가 포커스 이동을 무시할 수 있다).
        const reveal = () => {
            const invalid = form?.querySelector(":invalid");
            if (invalid) {
                invalid.focus();
                form.reportValidity();
            } else {
                failed.querySelector(".admin-image-source-error")?.focus();
            }
        };
        if (document.readyState === "complete") reveal();
        else window.addEventListener("load", reveal, {once: true});
    }
});
