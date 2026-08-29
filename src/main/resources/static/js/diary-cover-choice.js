/**
 * 새 여행일기 작성 화면의 표지 고르기.
 *
 * 기본 디자인과 내 디자인 중 하나만 고른다. 어느 쪽을 골랐는지는 hidden 값 하나로 전하고,
 * 내 디자인을 골랐을 때만 디자인 번호가 함께 실린다. (서버는 그 번호가 내 것인지 다시 확인한다)
 *
 * 내 디자인은 사진과 꾸미기를 이미 품고 있으므로 그동안 대표 이미지 자리는 접어 둔다.
 * JS 가 없으면 기본 디자인 그대로 동작한다. (hidden 의 처음 값이 PRESET 이다)
 */
document.addEventListener('DOMContentLoaded', () => {
    const choice = document.querySelector('[data-cover-choice]');
    if (!choice) return;

    const selection = choice.querySelector('[data-cover-selection]');
    const designId = choice.querySelector('[data-cover-design-id]');
    const customHint = choice.querySelector('[data-cover-custom-hint]');
    const imageField = document.querySelector('[data-cover-image-field]');
    const imageInput = imageField?.querySelector('input[type="file"]');
    if (!selection || !designId) return;

    choice.querySelectorAll('[data-cover-tab]').forEach((tab) => {
        tab.addEventListener('click', () => show(tab.dataset.coverTab));
    });

    // 디자인을 고르는 순간 '내 디자인' 쪽으로 확정된다.
    choice.querySelectorAll('[data-cover-design-option]').forEach((option) => {
        option.addEventListener('change', () => {
            if (option.checked) designId.value = option.value;
        });
    });

    function show(type) {
        const custom = type === 'CUSTOM';
        selection.value = custom ? 'CUSTOM' : 'PRESET';
        // 기본 디자인으로 돌아오면 골라 둔 디자인은 함께 놓는다.
        if (!custom) designId.value = '';

        choice.querySelectorAll('[data-cover-tab]').forEach((tab) => {
            const active = tab.dataset.coverTab === type;
            tab.classList.toggle('is-active', active);
            tab.setAttribute('aria-selected', String(active));
        });
        choice.querySelectorAll('[data-cover-panel]').forEach((panel) => {
            panel.hidden = panel.dataset.coverPanel !== type;
        });
        if (customHint) customHint.hidden = !custom;

        /*
          내 디자인일 때는 대표 이미지를 쓰지 않는다.
          자리를 접는 것만으로는 이미 고른 파일이 함께 올라가므로 값도 비운다.
        */
        if (imageField) imageField.hidden = custom;
        if (custom && imageInput) imageInput.value = '';
    }
});
