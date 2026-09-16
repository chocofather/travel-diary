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

    // 갱신 뒤에 추가된 radio 도 같은 동작을 하도록 선택 상자에서 한 번만 듣는다.
    choice.addEventListener('change', (event) => {
        const option = event.target.closest('[data-cover-design-option]');
        if (option?.checked) designId.value = option.value;
    });

    let designList = choice.querySelector('[data-cover-design-list]');
    const refreshUrl = designList?.dataset.coverDesignRefreshUrl;
    let designIdsBeforeCreate = new Set();
    let waitingForCreatedDesign = false;
    let leftForEditor = false;
    let refreshing = false;

    /*
      미저장 신규 화면은 링크가 새 탭으로 연다. 여기서는 기존 번호만 기억한다.
      원래 탭은 이동하지 않으므로 제목·기간·파일 input 등 작성 중인 form 값은 그대로 남는다.
    */
    choice.addEventListener('click', (event) => {
        if (!event.target.closest('[data-cover-design-create]')) return;
        designIdsBeforeCreate = new Set(designOptions().map((option) => option.value));
        waitingForCreatedDesign = true;
        leftForEditor = false;
    });

    // 새 탭을 다녀온 뒤에만 목록을 다시 받는다. 단순 focus 때마다 조회하지 않는다.
    window.addEventListener('blur', () => {
        if (waitingForCreatedDesign) leftForEditor = true;
    });
    document.addEventListener('visibilitychange', () => {
        if (!waitingForCreatedDesign) return;
        if (document.hidden) {
            leftForEditor = true;
            return;
        }
        refreshDesignsAfterReturn();
    });
    window.addEventListener('focus', refreshDesignsAfterReturn);

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
          이미 고른 파일은 기본 디자인으로 돌아올 때 다시 쓸 수 있게 유지하고,
          커스텀 표지를 선택한 동안만 제출 대상에서 제외한다.
        */
        if (imageField) imageField.hidden = custom;
        if (imageInput) imageInput.disabled = custom;
    }

    function designOptions() {
        return Array.from(choice.querySelectorAll('[data-cover-design-option]'));
    }

    async function refreshDesignsAfterReturn() {
        if (!waitingForCreatedDesign || !leftForEditor || refreshing || !refreshUrl || !designList) {
            return;
        }

        refreshing = true;
        const selectedBeforeRefresh = designId.value;
        try {
            const response = await fetch(refreshUrl, {
                credentials: 'same-origin',
                cache: 'no-store',
                headers: {'X-Requested-With': 'XMLHttpRequest'}
            });
            if (!response.ok) throw new Error('표지 디자인 목록을 불러오지 못했습니다.');

            const template = document.createElement('template');
            template.innerHTML = (await response.text()).trim();
            const updatedList = template.content.querySelector('[data-cover-design-list]');
            if (!updatedList) throw new Error('표지 디자인 목록 응답이 올바르지 않습니다.');

            designList.replaceWith(updatedList);
            designList = updatedList;
            updatedList.querySelectorAll('.diary-sticker[data-tape-center]')
                .forEach((item) => window.diaryTape?.render(item));

            const options = designOptions();
            const added = options.filter((option) => !designIdsBeforeCreate.has(option.value));
            if (added.length === 1) {
                added[0].checked = true;
                designId.value = added[0].value;
            } else if (selectedBeforeRefresh) {
                const selected = options.find((option) => option.value === selectedBeforeRefresh);
                if (selected) selected.checked = true;
            }

            waitingForCreatedDesign = false;
            leftForEditor = false;
        } catch (error) {
            // 기존 목록과 선택은 그대로 둔다. 다음에 탭을 다시 다녀오면 다시 시도할 수 있다.
            console.warn(error);
        } finally {
            refreshing = false;
        }
    }
});
