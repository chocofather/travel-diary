/*
 * 비회원 체험 다이어리 만들기.
 *
 * 회원의 "새 여행일기"와 같은 자리이고, 저장만 브라우저 draft 로 간다.
 * 서버로 나가는 요청이 없어 이 파일에는 fetch 도 form 전송도 없다.
 *
 * 회원과 같은 개념으로 여행일기 기본정보(제목 / 여행 시작일 / 여행 종료일)를 먼저 받는다.
 * 기본정보가 갖춰지지 않으면 표지 단계로 넘어가지 않는다. 빈 값을 그럴듯한 말로 채워
 * 만들어 두면 나중에 로그인해서 저장할 때 서버가 거절하기 때문이다.
 *
 * 표지를 고르는 두 갈래가 만들기 뒤를 가른다.
 *   기본 디자인 → 다이어리를 만들고 바로 책장으로
 *   직접 꾸미기 → 다이어리 골격을 만든 뒤 표지 편집기로 (표지를 적용하면 책장으로)
 *
 * 어느 쪽이든 다이어리는 여기에서 만들어진다. 페이지 편집기가 몰래 만들지 않는다.
 *
 * 예전에 만들어 둔 체험 다이어리는 기본정보 없이 시작됐을 수 있다. 그때는
 * ?mode=complete 로 이 화면에 다시 들어와 제목과 기간만 채운다. 표지·페이지·사진은
 * 건드리지 않는다.
 */
(function (global) {
    'use strict';

    const store = global.TravelDiaryGuestDraftStore;
    const page = document.querySelector('[data-guest-new]');
    if (!store || !page) {
        return;
    }

    const params = new URLSearchParams(global.location.search);
    const existing = store.getDraft();
    /** 덜 채워진 체험 다이어리를 보완하러 온 경우. 보완할 것이 있어야 성립한다. */
    const completing = params.get('mode') === 'complete' && existing !== null;
    /*
     * 보완을 마친 뒤 돌아갈 자리.
     *
     * 주소를 받지 않고 이름표만 받는다. 이름표에 해당하는 자리는 이 화면이 들고 있는
     * 내부 경로 중 하나뿐이라, 주소를 지어내도 바깥으로 나가는 길이 생기지 않는다.
     * 모르는 이름표는 책장으로 본다.
     */
    const RETURN_TARGETS = {
        edit: 'editUrl',
        import: 'importUrl',
        shelf: 'shelfUrl'
    };
    const returnUrl = page.dataset[RETURN_TARGETS[params.get('returnTo')] || 'shelfUrl']
        || page.dataset.shelfUrl;

    const form = page.querySelector('[data-guest-form]');
    const error = page.querySelector('[data-guest-error]');
    const submit = page.querySelector('[data-guest-action="create"]');
    const titleInput = page.querySelector('[data-guest-title]');
    const startInput = page.querySelector('[data-guest-start-date]');
    const endInput = page.querySelector('[data-guest-end-date]');
    let coverChoice = 'PRESET';

    prepareMode();
    wireCoverTabs();
    form.addEventListener('submit', (event) => {
        // 값은 서버가 아니라 draft 로 간다. 다만 required 검사는 먼저 지나온 뒤다.
        event.preventDefault();
        save();
    });

    /**
     * 보완하러 온 경우의 화면.
     *
     * 표지는 이미 정해져 있으므로 고르는 자리를 접고, 남은 기본정보만 채우게 한다.
     * 지금 draft 에 들어 있는 값이 있으면 그대로 띄워 준다. (빈 값을 지어내지 않는다)
     */
    function prepareMode() {
        if (!completing) {
            return;
        }
        page.querySelector('[data-guest-heading]').textContent =
            page.querySelector('[data-guest-heading]').dataset.headingComplete;
        const subtitle = page.querySelector('[data-guest-subtitle]');
        page.querySelector('[data-guest-subtitle-text]').textContent =
            subtitle.dataset.subtitleComplete;

        titleInput.value = existing.title || '';
        startInput.value = existing.startDate || '';
        endInput.value = existing.endDate || '';
        checkOption('notebookType', existing.notebookType);

        // 표지는 이미 만들어 둔 것을 그대로 쓴다. 여기에서 다시 고르게 하지 않는다.
        const coverChoiceField = page.querySelector('[data-guest-cover-choice]');
        if (coverChoiceField) coverChoiceField.hidden = true;
        submit.textContent = submit.dataset.labelComplete;
    }

    /** 기본 디자인 / 직접 꾸미기. 고른 갈래에 따라 만들기 버튼의 말이 바뀐다. */
    function wireCoverTabs() {
        const tabs = Array.from(page.querySelectorAll('[data-guest-cover-tab]'));
        tabs.forEach((tab) => {
            tab.addEventListener('click', () => {
                coverChoice = tab.dataset.guestCoverTab;
                tabs.forEach((other) => {
                    const active = other === tab;
                    other.classList.toggle('is-active', active);
                    other.setAttribute('aria-selected', String(active));
                });
                page.querySelectorAll('[data-guest-cover-panel]').forEach((panel) => {
                    panel.hidden = panel.dataset.guestCoverPanel !== coverChoice;
                });
                submit.textContent = coverChoice === 'CUSTOM'
                    ? submit.dataset.labelCustom : submit.dataset.labelPreset;
            });
        });
    }

    /** 고른 기본 표지. 아무것도 고르지 않았으면 첫 칸(기본 아이보리)이 이미 눌려 있다. */
    function chosenCoverStyle() {
        const checked = page.querySelector('input[name="coverStyle"]:checked');
        return checked ? checked.value : 'DEFAULT';
    }

    function chosenNotebookType() {
        const checked = page.querySelector('input[name="notebookType"]:checked');
        return checked ? checked.value : 'CLASSIC';
    }

    /** 보완 화면에서 지금 draft 가 쓰고 있는 값에 표시를 되돌려 놓는다. */
    function checkOption(name, value) {
        if (!value) return;
        const option = page.querySelector(
            'input[name="' + name + '"][value="' + value + '"]');
        if (option) option.checked = true;
    }

    /** 화면이 받은 기본정보. 저장소가 보는 것과 같은 모양으로 모은다. */
    function basics() {
        return {
            title: titleInput.value.trim(),
            startDate: startInput.value,
            endDate: endInput.value
        };
    }

    function save() {
        const entered = basics();
        /*
         * required 로 막히지 않는 것(종료일이 시작일보다 빠른 경우 등)이 있어 여기에서 한 번 더 본다.
         * 그러고도 저장소가 같은 규칙을 다시 보므로, 이 화면을 건너뛴 값은 draft 에 들어가지 않는다.
         */
        const invalid = store.basicsError(entered);
        if (invalid) {
            say(invalid);
            return;
        }
        say('');

        if (completing) {
            completeDraft(entered);
            return;
        }
        createDraft(entered);
    }

    /**
     * 새 체험 다이어리 한 권.
     *
     * 체험 다이어리는 언제나 한 권이다. createDraft 가 기존 값을 덮어쓰므로
     * 여기까지 온 것은 책장에서 이미 교체를 확인했거나 처음 만드는 경우다.
     */
    function createDraft(entered) {
        const created = store.createDraft({
            title: entered.title,
            startDate: entered.startDate,
            endDate: entered.endDate,
            notebookType: chosenNotebookType(),
            // 직접 꾸미기는 표지 편집기가 coverDesign 을 채우며 CUSTOM 으로 바꾼다.
            coverType: coverChoice,
            coverStyle: chosenCoverStyle()
        });
        if (!created.ok) {
            fail(created);
            return;
        }

        // 회원 다이어리처럼 첫 장 한 권으로 시작한다. (책장 카드가 1장으로 보인다)
        const firstPage = store.addPage({pageDate: entered.startDate});
        if (!firstPage.ok) {
            fail(firstPage);
            return;
        }

        global.location.href = coverChoice === 'CUSTOM'
            ? page.dataset.coverUrl
            : page.dataset.shelfUrl;
    }

    /**
     * 덜 채워진 체험 다이어리 보완.
     *
     * 기본정보만 채운다. 표지(coverType / coverStyle / coverDesign)와 페이지·사진은
     * 이 요청에 담지 않으므로 저장소에서도 그대로 남는다.
     */
    function completeDraft(entered) {
        const updated = store.updateDraft({
            title: entered.title,
            startDate: entered.startDate,
            endDate: entered.endDate,
            notebookType: chosenNotebookType()
        });
        if (!updated.ok) {
            fail(updated);
            return;
        }
        global.location.href = returnUrl;
    }

    /** 안내 한 줄. 빈 말이면 자리를 접는다. */
    function say(message) {
        if (!error) return;
        error.textContent = message || '';
        error.hidden = !message;
    }

    /**
     * 저장소가 거절했을 때.
     *
     * 이유를 알려 주면 그 말을 그대로 쓰고, 저장소를 쓸 수 없는 환경처럼 이유가 없는
     * 실패면 원래 자리에 적혀 있던 안내를 보여 준다.
     */
    function fail(result) {
        say(result.message
            || '체험 다이어리를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.');
    }
})(window);
