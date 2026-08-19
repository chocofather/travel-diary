/**
 * 페이지 설정의 종이 색상 고르기.
 * 고른 값은 hidden(paperColor)에만 담고 저장은 기존 페이지 설정 폼이 그대로 한다.
 * 고르는 동안에는 지금 편집 중인 종이에 바로 비춰 보여 주고,
 * 저장하지 않고 설정을 닫으면 원래 색으로 되돌린다.
 */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.diary-paper-color').forEach(setupPaperColor);

    function setupPaperColor(root) {
        const value = root.querySelector('.diary-paper-color-value');
        const picker = root.querySelector('.diary-paper-color-picker');
        const swatches = Array.from(root.querySelectorAll('.diary-paper-swatch'));
        // 미리보기 대상은 지금 편집 중인 종이 한 장이다.
        const sheet = document.querySelector('.diary-book-single .diary-sheet');
        const settings = root.closest('.diary-page-settings');
        if (!value || swatches.length === 0) return;

        // 저장돼 있던 색. 설정을 닫으면 이 값으로 되돌린다.
        const savedColor = value.value;

        select(savedColor);
        settings?.addEventListener('toggle', () => {
            if (!settings.open) select(savedColor);
        });

        swatches.forEach(swatch => swatch.addEventListener('click', () => {
            select(swatch.dataset.paperColor || '');
        }));
        picker?.addEventListener('input', () => select(normalize(picker.value)));

        /** 고른 색을 폼 값·버튼 표시·종이 미리보기에 함께 반영한다. */
        function select(color) {
            const paperColor = normalize(color);
            value.value = paperColor;

            swatches.forEach(swatch => {
                const chosen = (swatch.dataset.paperColor || '') === paperColor;
                swatch.classList.toggle('is-selected', chosen);
                swatch.setAttribute('aria-pressed', chosen ? 'true' : 'false');
            });
            if (picker && paperColor) picker.value = paperColor.toLowerCase();

            // 색만 바꾼다. 무늬(LINED/GRID/DOT)와 종이 질감은 그대로 얹혀 있다.
            if (!sheet) return;
            if (paperColor) {
                sheet.style.setProperty('--diary-paper-color', paperColor);
            } else {
                sheet.style.removeProperty('--diary-paper-color');
            }
        }

        /** 서버가 받는 형식(#RRGGBB)으로만 맞춘다. 그 밖의 값은 기본 종이색으로 본다. */
        function normalize(color) {
            const trimmed = (color || '').trim();
            return /^#[0-9a-fA-F]{6}$/.test(trimmed) ? trimmed.toUpperCase() : '';
        }
    }
});
