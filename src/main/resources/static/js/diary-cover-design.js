/**
 * 표지 디자인 만들기/편집 화면의 작은 도우미.
 *
 * 1) 배경색은 고르지 않아도 되는 값이다. type=color 는 늘 값을 갖고 있어 "고르지 않음"을
 *    표현하지 못하므로, 실제로 보내는 값은 hidden 하나로 두고 여기서 채우거나 비운다.
 * 2) 편집 화면에서는 고른 값을 곧바로 미리보기에 비춘다. 저장은 저장 버튼으로만 한다.
 *
 * JS 가 없어도 폼은 그대로 동작한다. (hidden 에 서버가 그려 준 값이 그대로 남는다)
 */
document.addEventListener('DOMContentLoaded', () => {
    const field = document.querySelector('[data-cover-color-field]');
    const preview = document.querySelector('[data-cover-preview] .diary-cover-canvas');

    setupColorField(field, preview);
    setupStylePicker(preview);

    /** 색 고르개 ↔ 실제로 저장되는 hidden 값 */
    function setupColorField(colorField, canvas) {
        if (!colorField) return;
        const value = colorField.querySelector('[data-cover-color-value]');
        const picker = colorField.querySelector('[data-cover-color-input]');
        const clear = colorField.querySelector('[data-cover-color-clear]');
        if (!value || !picker) return;

        markChosen(colorField, value.value);

        picker.addEventListener('input', () => {
            value.value = picker.value;
            markChosen(colorField, value.value);
            paintColor(canvas, value.value);
        });

        clear?.addEventListener('click', () => {
            // 비우면 표지 스타일의 원래 색으로 돌아간다
            value.value = '';
            markChosen(colorField, '');
            paintColor(canvas, '');
        });
    }

    /** 바탕 표지를 고르면 미리보기의 재질 클래스만 갈아 끼운다 */
    function setupStylePicker(canvas) {
        if (!canvas) return;
        document.querySelectorAll('input[name="baseCoverStyle"][data-cover-style-class]')
            .forEach(option => option.addEventListener('change', () => {
                if (!option.checked) return;
                canvas.classList.forEach(name => {
                    if (name.startsWith('diary-cover-') && name !== 'diary-cover-canvas') {
                        canvas.classList.remove(name);
                    }
                });
                canvas.classList.add(option.dataset.coverStyleClass);
            }));
    }

    function paintColor(canvas, color) {
        if (!canvas) return;
        if (color) {
            canvas.style.setProperty('--diary-cover-color', color);
        } else {
            canvas.style.removeProperty('--diary-cover-color');
        }
    }

    /** 색을 골랐는지 여부만 표시해 둔다. (버튼 노출/문구는 CSS 가 맡는다) */
    function markChosen(colorField, color) {
        colorField.classList.toggle('has-color', !!color);
    }
});
