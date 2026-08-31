/**
 * 책장의 잠금.
 *
 * 두 가지를 맡는다.
 *  - 잠긴 책을 눌렀을 때 상세로 넘어가지 않고 그 자리에서 PIN 을 묻는다.
 *  - ⋯ 메뉴의 "PIN 잠금 설정 / 관리" 를 연다.
 *
 * PIN 을 묻고 보내는 일은 모두 diary-pin.js 의 공용 판이 맡는다.
 * 여기서는 어떤 책을 어떤 일로 여는지만 정한다.
 *
 * 카드 링크와 ⋯ 메뉴는 서로 다른 요소다. (메뉴는 링크 바깥에 있다)
 * 그래서 잠금 표시가 메뉴를 가리지 않고, 메뉴를 눌러도 PIN 판이 열리지 않는다.
 *
 * 다만 ⋯ 메뉴는 안쪽 클릭이 카드 링크로 번지지 않도록 전파를 끊는다(diary-book-menu.js).
 * 그래서 메뉴 항목까지 받으려면 올라오는 길이 아니라 내려가는 길(capture)에서 들어야 한다.
 * 목록은 검색으로 통째로 다시 그려지므로 자리는 document 로 둔다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const results = document.getElementById('diary-results');
    const manage = document.getElementById('diary-pin-manage-backdrop');
    /** 지금 관리 판에서 다루고 있는 책 */
    let managing = null;

    document.addEventListener('click', handleClick, true);

    function handleClick(event) {
        // 잠긴 책 열기. 상세로 넘어가기 전에 막고 PIN 을 묻는다.
        const link = event.target.closest('[data-pin-diary-id]');
        if (link && (!results || results.contains(link))) {
            event.preventDefault();
            window.diaryPin?.unlock(link.dataset.pinDiaryId, link.getAttribute('href'), link);
            return;
        }

        // ⋯ 메뉴에서 잠금을 새로 건다.
        const set = event.target.closest('[data-pin-set]');
        if (set) {
            window.diaryPin?.set(set.dataset.pinSet, () => window.location.reload(), set);
            return;
        }

        // ⋯ 메뉴에서 이미 걸린 잠금을 다룬다.
        const manageButton = event.target.closest('[data-pin-manage]');
        if (manageButton) {
            openManage(manageButton.dataset.pinManage, manageButton);
        }
    }

    if (manage) {
        manage.querySelector('[data-pin-manage-change]')?.addEventListener('click', () => {
            const diaryId = managing;
            closeManage();
            window.diaryPin?.change(diaryId, () => window.location.reload());
        });
        manage.querySelector('[data-pin-manage-remove]')?.addEventListener('click', () => {
            const diaryId = managing;
            closeManage();
            window.diaryPin?.remove(diaryId, () => window.location.reload());
        });
        manage.querySelector('[data-pin-manage-cancel]')?.addEventListener('click', closeManage);
        manage.addEventListener('mousedown', (event) => {
            if (event.target === manage) closeManage();
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape' && !manage.hidden) closeManage();
        });
    }

    function openManage(diaryId, opener) {
        if (!manage) return;
        managing = diaryId;
        manage.hidden = false;
        document.body.classList.add('is-pin-open');
        manage.querySelector('[data-pin-manage-modal]')?.focus();
        manage.dataset.opener = opener?.id || '';
    }

    function closeManage() {
        if (!manage) return;
        managing = null;
        manage.hidden = true;
        document.body.classList.remove('is-pin-open');
    }

    /*
      주소창으로 잠긴 다이어리를 열다 책장으로 온 경우.
      어느 책인지와 풀고 나서 돌아갈 자리는 서버가 알려 준다 —
      화면이 만들어 낸 주소가 아니라서 바깥으로 튈 수 없다.
    */
    const pending = document.getElementById('diary-pin-pending');
    const diaryId = pending?.dataset.diaryId;
    if (!diaryId) return;

    window.diaryPin?.unlock(diaryId, pending.dataset.target || `/diaries/${diaryId}`);
    // 새로고침했을 때 판이 다시 뜨지 않게 주소에서 표시만 지운다.
    const url = new URL(window.location.href);
    url.searchParams.delete('locked');
    window.history.replaceState({}, '', url.pathname + url.search);
});
