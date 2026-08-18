/**
 * 읽기 화면의 '편집하기' → 편집할 페이지 고르기.
 * 목록은 서버가 그려 두고, 여기서는 열고 닫기와 포커스만 다룬다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const button = document.getElementById('diary-page-picker-button');
    const backdrop = document.getElementById('diary-page-picker-backdrop');
    const dialog = document.getElementById('diary-page-picker');
    const closeButton = document.getElementById('diary-page-picker-close');
    if (!button || !backdrop || !dialog) return;

    function toggle(open) {
        backdrop.hidden = !open;
        button.setAttribute('aria-expanded', open ? 'true' : 'false');
        if (open) {
            // 첫 페이지 항목(없으면 닫기 버튼)으로 포커스를 옮긴다.
            const first = dialog.querySelector('.diary-page-picker-item, .diary-mode-button');
            (first || closeButton)?.focus();
        } else {
            button.focus();
        }
    }

    button.addEventListener('click', () => toggle(backdrop.hidden));
    closeButton?.addEventListener('click', () => toggle(false));
    // 배경(모달 밖)을 누르면 닫는다.
    backdrop.addEventListener('click', (event) => {
        if (event.target === backdrop) toggle(false);
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && !backdrop.hidden) toggle(false);
    });
});
