/*
 * 체험 여행일기를 내 여행일기로 가져오기.
 *
 * 서버는 가져올 내용을 알지 못한다. 체험 여행일기는 이 브라우저의 localStorage 와
 * IndexedDB 에만 있기 때문이다. 그래서 여기에서 자기 저장소를 읽어 보여 주고,
 * 저장을 누르면 내용과 사진 원본을 한 요청으로 올린다.
 *
 * 순서가 중요하다. 체험 내용은 서버가 "전부 저장했다" 고 답한 뒤에만 지운다.
 * 실패하거나 답을 받지 못하면 그대로 남겨 다시 시도할 수 있게 한다.
 */
(function (global) {
    'use strict';

    const store = global.TravelDiaryGuestDraftStore;
    const photoStore = global.TravelDiaryGuestPhotoStore;
    const preview = global.GuestDiaryCoverPreview;
    const intent = global.GuestDiaryImportIntent;
    const page = document.querySelector('[data-guest-import]');
    if (!store || !page) {
        return;
    }

    const card = page.querySelector('[data-guest-import-card]');
    const actions = page.querySelector('[data-guest-import-actions]');
    const empty = page.querySelector('[data-guest-import-empty]');
    const incomplete = page.querySelector('[data-guest-import-incomplete]');
    const undated = page.querySelector('[data-guest-import-undated]');
    const status = page.querySelector('[data-guest-import-status]');
    const error = page.querySelector('[data-guest-import-error]');
    const confirm = page.querySelector('[data-guest-import-confirm]');

    const autoPanel = page.querySelector('[data-guest-import-auto]');

    const draft = store.getDraft();
    /** 한 번에 하나만 보낸다. 빠르게 두 번 눌러도 요청이 두 번 나가지 않는다. */
    let sending = false;
    /** 묻지 않고 바로 저장하는 중인지. 실패하면 이 자리에서 확인 화면으로 돌아간다. */
    let automatic = false;

    render();
    wireActions();
    // 자동 저장은 이 파일 맨 끝에서 시작한다. (저장을 맡을 GuestDiaryImport 가 준비된 뒤여야 한다)

    /**
     * 체험 화면에서 "로그인하고 저장하기" 를 누르고 온 경우에만 묻지 않고 바로 저장한다.
     *
     * <p>판단 기준은 체험 여행일기가 남아 있는지가 아니라 그때 남긴 쪽지다.
     * 남아 있다는 것과 저장하겠다는 뜻은 다르기 때문이다. 그래서 그냥 로그인했거나,
     * 목록의 안내를 보고 스스로 찾아온 경우에는 예전처럼 확인 화면이 뜬다.
     *
     * <p>보낼 수 없는 상태(제목·기간이 비었거나 날짜 없는 장이 있는 경우)면 시작하지 않고
     * 쪽지도 그대로 둔다. 화면이 안내하는 자리에서 마저 채우고 돌아오면 그때 이어서 저장한다.
     */
    function startAutomaticImport() {
        const requested = intent?.read();
        if (!requested || !draft || requested.draftId !== draft.draftId
            || requested.mode !== intent.MODE_AUTO) {
            // 이 여행일기를 가리키지 않는 쪽지는 남겨 둘 이유가 없다. (체험 내용은 그대로 둔다)
            if (requested) intent.clear();
            return;
        }
        if (!readyToSend()) {
            return;
        }

        /*
         * 시작하기로 정한 뒤에 쪽지를 지운다. 실패하고 새로고침해도 자동 저장이
         * 되풀이되지 않게 하기 위해서다. 체험 내용(draft·사진)은 여기에서 건드리지 않는다.
         */
        intent.clear();
        automatic = true;
        showAutomatic(true);
        global.GuestDiaryImport.start();
    }

    /** 지금 그대로 보낼 수 있는 상태인지. 확인 화면이 보는 것과 같은 기준이다. */
    function readyToSend() {
        return draft !== null
            && store.isComplete(draft)
            && store.pagesMissingDate(draft).length === 0;
    }

    /** 자동 저장 중의 화면. 물어볼 것이 없으므로 확인 카드와 버튼을 감춘다. */
    function showAutomatic(running) {
        show(autoPanel, running);
        show(card, !running);
        show(actions, !running);
        const heading = page.querySelector('[data-guest-import-heading]');
        const subtitle = page.querySelector('[data-guest-import-subtitle]');
        if (running && heading) heading.textContent = heading.dataset.headingAuto;
        if (running && subtitle) subtitle.textContent = subtitle.dataset.subtitleAuto;
    }

    function render() {
        const found = draft !== null;
        /*
         * 제목/여행 기간이 비어 있는 체험 여행일기는 그대로 저장할 수 없다.
         * 있는 것처럼 보여 주고 저장 단계에서 거절당하는 대신, 지금 상태를 그대로 알리고
         * 채울 자리로 보낸다. 체험 내용은 하나도 지우지 않는다.
         */
        const complete = found && store.isComplete(draft);
        // 장마다 날짜가 있어야 저장된다. 회원 페이지와 같은 기준이라 여기에서 먼저 본다.
        const dated = complete && store.pagesMissingDate(draft).length === 0;
        show(card, found);
        show(incomplete, found && !complete);
        show(undated, complete && !dated);
        show(actions, dated);
        show(empty, !found);
        if (!found) return;

        // 표지는 책장 카드와 같은 조각으로 그린다. (꾸민 표지면 그 모습 그대로 줄어든다)
        preview?.render(
            page.querySelector('.diary-book'),
            page.querySelector('[data-guest-import-cover]'),
            draft);

        // 사용자가 쓴 값이라 글자로만 넣는다. 비어 있으면 비어 있다고 보여 준다.
        page.querySelector('[data-guest-import-title]').textContent =
            draft.title || '제목 없음';
        page.querySelector('[data-guest-import-period]').textContent =
            draft.startDate && draft.endDate
                ? draft.startDate + ' ~ ' + draft.endDate : '';
        page.querySelector('[data-guest-import-pages]').textContent =
            draft.pages.length + '장';
        show(page.querySelector('[data-guest-import-photos]'), usedPhotoRefs().length > 0);
    }

    /** 지금 이 여행일기가 쓰고 있는 사진. 모든 장과 표지를 함께 보고 중복은 한 번만 센다. */
    function usedPhotoRefs() {
        const refs = new Set();
        const collect = (elements) => (elements || []).forEach((item) => {
            if (item.elementType === 'PHOTO' && item.photoRef) refs.add(item.photoRef);
        });
        draft.pages.forEach((item) => collect(item.elements));
        if (draft.coverDesign) collect(draft.coverDesign.elements);
        return Array.from(refs);
    }

    function wireActions() {
        confirm?.addEventListener('click', () => global.GuestDiaryImport.start());

        /*
         * [나중에 하기]. 체험 내용(draft·사진)은 그대로 둔다.
         * 다만 "저장하러 로그인한다" 던 쪽지는 여기에서 거둔다. 지금 하지 않겠다는 뜻이므로
         * 다음에 이 화면에 들어왔을 때 묻지 않고 저장되면 안 된다.
         * (링크는 그대로 /diaries 로 데려간다)
         */
        page.querySelector('[data-guest-import-later]')
            ?.addEventListener('click', () => intent?.clear());
    }

    /**
     * 자동 저장이 실패한 뒤의 화면.
     *
     * <p>쪽지는 이미 거뒀으므로 새로고침해도 다시 자동으로 보내지 않는다. 대신 확인 화면으로
     * 돌아가 [다시 시도] 로 같은 저장을 한 번 더 부를 수 있게 한다. 지운 것은 아무것도 없다.
     */
    function backToConfirm() {
        if (!automatic) return;
        automatic = false;
        showAutomatic(false);
        show(page.querySelector('[data-guest-import-kept]'), true);
        if (confirm) {
            confirm.dataset.label = confirm.dataset.labelRetry;
            confirm.textContent = confirm.dataset.labelRetry;
        }
    }

    /* ===== 보내기 ===== */

    /**
     * 내용과 사진 원본을 한 요청으로 올린다.
     *
     * <p>사진은 올라오는 차례에 기대지 않고 이름표로 짝을 짓는다.
     * 필요한 원본이 하나라도 없으면 보내기 전에 멈춘다. 반쯤 저장되는 것보다 낫다.
     */
    async function send() {
        const refs = usedPhotoRefs();
        const form = new FormData();
        const photoParts = {};

        for (let index = 0; index < refs.length; index += 1) {
            const partName = 'photo' + index;
            const found = photoStore ? await photoStore.getPhoto(refs[index]) : null;
            if (!found || !found.ok || !found.photo.blob) {
                throw new Error('사진 원본을 찾지 못했습니다. 체험 여행일기를 열어 사진을 다시 넣어 주세요.');
            }
            photoParts[refs[index]] = partName;
            form.append(partName, found.photo.blob, found.photo.fileName || partName);
        }

        // 보내는 것은 체험 내용과 사진 짝짓기 표뿐이다. 누구의 것이 될지는 서버가 정한다.
        form.append('manifest', JSON.stringify(Object.assign({}, draft, {
            photoParts: photoParts
        })));
        form.append('importToken', page.dataset.importToken);

        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            throw new Error('보안 토큰을 확인할 수 없어 저장하지 못했습니다.');
        }

        const response = await fetch(page.dataset.importUrl, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {'Accept': 'application/json', [csrfHeader]: csrfToken},
            body: form
        });

        let payload = null;
        if ((response.headers.get('Content-Type') || '').includes('application/json')) {
            payload = await response.json();
        }
        if (!response.ok || !payload || payload.success !== true) {
            throw new Error((payload && payload.message)
                || '여행일기를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
        if (typeof payload.diaryId !== 'number') {
            throw new Error('여행일기를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
        return payload.diaryId;
    }

    /**
     * 저장이 끝난 뒤의 정리.
     *
     * <p>서버가 전부 저장했다고 답한 뒤에만 부른다. 사진 원본을 먼저 지우고 draft 를 비운다.
     * 순서가 반대면 draftId 를 잃어 어떤 사진을 지워야 하는지 알 수 없게 된다.
     *
     * <p>여기에서 실패해도 저장 자체는 이미 끝났다. 실패했다고 알리지 않는다.
     * 남은 값은 다음에 체험 화면을 열 때 주인 없는 사진으로 정리된다.
     */
    async function discardGuestData() {
        try {
            global.GuestDiaryPhoto?.releaseAll();
            if (photoStore) await photoStore.deletePhotosForDraft(draft.draftId);
            store.clear();
            intent?.clear();
        } catch (ignored) {
            // 정리 실패가 저장 성공을 덮지 않게 한다.
        }
    }

    function busy(isBusy) {
        sending = isBusy;
        if (!confirm) return;
        confirm.disabled = isBusy;
        confirm.textContent = isBusy ? confirm.dataset.labelBusy : confirm.dataset.label;
    }

    function show(element, visible) {
        if (element) element.hidden = !visible;
    }

    function say(element, message) {
        if (!element) return;
        element.textContent = message || '';
        element.hidden = !message;
    }

    global.GuestDiaryImport = {
        draft: () => draft,

        /**
         * 저장 한 번.
         *
         * <p>이미 보내는 중이면 아무것도 하지 않는다. 서버도 같은 표로 두 번 저장하지 않지만,
         * 요청을 두 번 만들 이유가 없다.
         */
        async start() {
            if (sending || !draft) return;
            // 제목/기간이 비어 있거나 날짜 없는 장이 있으면 보내지 않는다.
            // 서버도 같은 이유로 거절한다. (이 확인이 서버 검증을 대신하지는 않는다)
            if (!store.isComplete(draft)) return;
            if (store.pagesMissingDate(draft).length > 0) return;
            busy(true);
            say(error, '');
            say(status, '여행일기를 저장하는 중입니다…');

            let diaryId;
            try {
                diaryId = await send();
            } catch (failure) {
                // 실패했다. 체험 여행일기는 그대로 남겨 다시 시도할 수 있게 한다.
                busy(false);
                say(status, '');
                say(error, failure.message);
                backToConfirm();
                return;
            }

            // 서버가 전부 저장했다. 이제서야 이 브라우저의 체험 내용을 정리한다.
            await discardGuestData();
            say(status, '저장했습니다. 내 여행일기로 이동합니다…');
            global.location.href = page.dataset.diariesUrl
                + '?imported=' + encodeURIComponent(String(diaryId));
        }
    };

    // 저장을 맡을 자리가 준비됐다. 묻지 않고 저장할 경우인지 여기에서 정한다.
    startAutomaticImport();
})(window);
