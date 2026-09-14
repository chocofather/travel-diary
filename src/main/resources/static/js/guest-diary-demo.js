/*
 * 비회원용 "나의 여행일기" 책장.
 *
 * 회원 /diaries 와 같은 역할이다. 만들어 둔 체험 다이어리를 카드로 보여 주고,
 * 카드를 누르면 그 다이어리의 페이지 작성으로 들어간다.
 * 다른 것은 저장소뿐이라 여기에는 서버로 나가는 요청이 없다.
 *
 * 체험 다이어리는 한 권뿐이라, 이미 있는데 새로 만들려 하면 바로 덮어쓰지 않고 먼저 묻는다.
 * 그때는 localStorage 만 비우면 사진 원본이 주인 없이 남으므로, 그 draft 의 사진도 함께 지운다.
 */
(function (global) {
    'use strict';

    const store = global.TravelDiaryGuestDraftStore;
    const preview = global.GuestDiaryCoverPreview;
    const page = document.querySelector('[data-guest-shelf]');
    if (!store || !preview || !page) {
        return;
    }

    const shelf = page.querySelector('[data-guest-shelf-list]');
    const empty = page.querySelector('[data-guest-empty]');
    const unavailable = page.querySelector('[data-guest-unavailable]');
    const incomplete = page.querySelector('[data-guest-incomplete]');
    const replacePrompt = document.querySelector('[data-guest-replace-prompt]');
    const deletePrompt = document.querySelector('[data-guest-delete-prompt]');
    const intent = global.GuestDiaryImportIntent;

    const photoStore = global.TravelDiaryGuestPhotoStore;

    render();
    wireActions();
    cleanupOrphanPhotos();

    /*
     * 주인 없는 사진 정리.
     * 브라우저가 도중에 닫히면 draft 에서는 빠졌는데 원본만 남을 수 있다.
     * 지금 draft 가 쓰는 photoRef 목록을 주고 그 밖의 것만 지운다.
     * 훑는 범위는 언제나 이 draftId 안이라 다른 다이어리의 사진은 건드리지 않는다.
     */
    function cleanupOrphanPhotos() {
        const draft = store.getDraft();
        if (!draft || !photoStore) return;
        photoStore.cleanupOrphans(draft.draftId, usedPhotoRefs(draft));
    }

    /** 지금 draft 가 쓰고 있는 사진. 모든 장과 표지를 함께 본다. */
    function usedPhotoRefs(draft) {
        const refs = [];
        const collect = (elements) => (elements || []).forEach((item) => {
            if (item.photoRef) refs.push(item.photoRef);
        });
        draft.pages.forEach((item) => collect(item.elements));
        if (draft.coverDesign) collect(draft.coverDesign.elements);
        return refs;
    }

    /** 책장 한 칸. draft 가 없으면 빈 상태만 남는다. */
    function render() {
        const draft = store.getDraft();
        show(shelf, draft !== null);
        show(empty, draft === null);
        if (!draft) {
            // 지운 뒤에는 남아 있던 안내도 함께 거둔다.
            show(incomplete, false);
            return;
        }

        const book = page.querySelector('[data-guest-book]');
        const cover = page.querySelector('[data-guest-book-cover]');
        // 표지는 회원 목록과 같은 조각으로 그린다. (꾸민 표지면 그 모습 그대로 줄어든다)
        preview.render(book, cover, draft);

        /*
         * 제목과 기간은 draft 에 실제로 들어 있는 값만 보여 준다.
         * 없는 값을 그럴듯한 말로 채우면 저장할 때가 되어서야 비어 있던 것을 알게 된다.
         */
        const complete = store.isComplete(draft);
        show(incomplete, !complete);

        // 사용자가 쓴 값이라 글자로만 넣는다.
        page.querySelector('[data-guest-book-title]').textContent =
            draft.title || '제목 없음';
        const period = page.querySelector('[data-guest-book-period]');
        // 회원 목록 카드와 같은 모양 (2026-09-14 ~ 2026-09-18)
        period.textContent = draft.startDate && draft.endDate
            ? draft.startDate + ' ~ ' + draft.endDate : '';
        page.querySelector('[data-guest-book-pages]').textContent =
            draft.pages.length + '장';
    }

    function wireActions() {
        /*
         * [+ 새 여행일기]. 이미 한 권 있으면 바로 만들지 않고 교체할지 먼저 묻는다.
         * 없으면 링크 그대로 표지 고르기 화면으로 간다.
         */
        page.querySelectorAll('[data-guest-action="new-diary"]').forEach((link) => {
            link.addEventListener('click', (event) => {
                if (!store.hasDraft()) return;
                event.preventDefault();
                show(replacePrompt, true);
            });
        });

        replacePrompt?.querySelector('[data-guest-action="cancel-replace"]')
            ?.addEventListener('click', () => show(replacePrompt, false));

        /*
         * 새로 만들기를 고른 뒤에야 기존 체험 다이어리를 지운다.
         * 사진 원본(IndexedDB)을 먼저 지우고 draft 를 비운다. 순서가 반대면 draftId 를 잃어
         * 어떤 사진을 지워야 하는지 알 수 없게 된다.
         */
        replacePrompt?.querySelector('[data-guest-action="confirm-replace"]')
            ?.addEventListener('click', (event) => {
                event.preventDefault();
                const target = event.currentTarget.href;
                discardDraft().then((cleared) => {
                    if (cleared && target) global.location.href = target;
                });
            });

        replacePrompt?.addEventListener('click', (event) => {
            if (event.target === replacePrompt) show(replacePrompt, false);
        });

        /*
         * ⋯ 메뉴의 [체험 여행일기 삭제]. 만들기로 이어지는 교체와 달리 지우고 끝난다.
         * 여기에서도 바로 지우지 않고 먼저 묻는다.
         */
        page.querySelector('[data-guest-action="delete-diary"]')
            ?.addEventListener('click', () => show(deletePrompt, true));

        deletePrompt?.querySelector('[data-guest-action="cancel-delete"]')
            ?.addEventListener('click', () => show(deletePrompt, false));

        deletePrompt?.querySelector('[data-guest-action="confirm-delete"]')
            ?.addEventListener('click', () => {
                discardDraft().then((cleared) => {
                    if (!cleared) return;
                    // 지웠으면 빈 상태로 되돌린다. 갈 곳이 따로 없다.
                    show(deletePrompt, false);
                    render();
                });
            });

        deletePrompt?.addEventListener('click', (event) => {
            if (event.target === deletePrompt) show(deletePrompt, false);
        });

        document.addEventListener('keydown', (event) => {
            if (event.key !== 'Escape') return;
            show(replacePrompt, false);
            show(deletePrompt, false);
        });
    }

    /**
     * 지금 체험 다이어리를 버린다. 사진 원본까지 함께 정리한다.
     *
     * <p>지우는 차례가 있다. 사진 원본(IndexedDB)을 먼저 지우고 draft 를 비운다.
     * 순서가 반대면 draftId 를 잃어 어떤 사진을 지워야 하는지 알 수 없게 된다.
     * 사진 정리가 실패해도 삭제 자체는 진행한다. 남은 원본은 다음 책장 진입 때
     * 주인 없는 사진으로 정리되므로, 사용자에게 실패처럼 보일 일이 아니다.
     *
     * <p>지우는 것은 이 브라우저의 체험 내용뿐이다. 회원 여행일기나 서버 파일은
     * 여기에서 닿지 않는다. (이 화면에는 서버로 나가는 요청 자체가 없다)
     *
     * @return 지웠으면 true. 저장소를 쓸 수 없으면 false
     */
    async function discardDraft() {
        const draft = store.getDraft();

        if (draft && photoStore) {
            // 화면에서 쓰던 blob: 주소도 함께 돌려준다.
            global.GuestDiaryPhoto?.releaseAll();
            try {
                await photoStore.deletePhotosForDraft(draft.draftId);
            } catch (ignored) {
                // 사진 정리 실패가 삭제를 막지 않는다.
            }
        }
        if (!store.clear()) {
            show(replacePrompt, false);
            show(deletePrompt, false);
            show(unavailable, true);
            return false;
        }
        // 저장하러 로그인하려던 쪽지도 거둔다. 가리킬 체험 여행일기가 없어졌다.
        intent?.clear();
        return true;
    }

    function show(element, visible) {
        if (element) element.hidden = !visible;
    }
})(window);
