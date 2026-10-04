/**
 * 페이지 설정의 종이 색상 고르기.
 * 고른 값은 hidden(paperColor)에만 담고 저장은 기존 페이지 설정 폼이 그대로 한다.
 * 고르는 동안에는 지금 편집 중인 종이에 바로 비춰 보여 주고,
 * 저장하지 않고 설정을 닫으면 원래 색으로 되돌린다.
 *
 * 페이지 설정(details)은 바깥을 누르면 닫는다. 브라우저는 details 를 바깥 클릭으로 닫아 주지 않아서,
 * 열린 채 포커스만 빠지면 패널이 부모(.diary-page-actions)의 옅은 투명도를 물려받아 반투명하게 남았다.
 */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.diary-paper-color').forEach(setupPaperColor);

    /*
      설정 바깥을 누르면 open 을 끈다. (toggle 이벤트가 나서 미리 본 종이색도 원래대로 돌아간다)
      설정 안(토글 버튼, 날짜·배경·색 견본·직접 선택)을 누른 것은 그대로 둔다. 토글 버튼은 브라우저가 열고 닫는다.
      다른 패널이 click 전파를 막아도 닫히도록 capture 단계에서 보되, 이 리스너는 전파를 막지 않는다.
    */
    const pageSettings = Array.from(document.querySelectorAll('.diary-page-settings'));
    if (pageSettings.length > 0) {
        document.addEventListener('click', (event) => {
            pageSettings.forEach((settings) => {
                if (settings.open && !settings.contains(event.target)) settings.open = false;
            });
        }, true);
    }

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
            if (sheet.classList.contains('diary-sheet-bg-lined')) {
                window.diaryPageScale?.refresh(sheet.closest('[data-diary-sheet-viewport]'));
            }
        }

        /** 서버가 받는 형식(#RRGGBB)으로만 맞춘다. 그 밖의 값은 기본 종이색으로 본다. */
        function normalize(color) {
            const trimmed = (color || '').trim();
            return /^#[0-9a-fA-F]{6}$/.test(trimmed) ? trimmed.toUpperCase() : '';
        }
    }
});
