/**
 * 달력의 달 이동(이전/다음·연/월 고르기·오늘)을 화면 새로고침 없이 처리한다.
 * 서버가 그린 달력 조각(diary/calendar :: board)만 받아 통째로 갈아 끼우므로
 * 날짜/공휴일/절기/여행일기 HTML 을 여기서 다시 만들지 않는다.
 *
 * 연/월 고르기 팝오버도 같은 조각 안에 있어, 이동 뒤에도 그대로 동작한다.
 *
 * 조각이 통째로 바뀌므로 이벤트는 문서에 걸어 둔다. (다시 연결할 필요가 없다)
 * 스크립트가 없거나 실패하면 이전/다음/오늘은 그대로 링크로 동작한다.
 */
document.addEventListener('DOMContentLoaded', () => {
    let board = document.getElementById('diary-calendar-board');
    const error = document.getElementById('diary-calendar-error');
    if (!board) return;

    const fragmentUrl = board.dataset.fragmentUrl;
    if (!fragmentUrl) return;

    let request = null;

    // 이전/다음/오늘은 이미 옳은 주소를 들고 있는 링크라 주소만 가져다 쓴다.
    document.addEventListener('click', (event) => {
        const link = event.target.closest(
            '#diary-calendar-board .diary-calendar-move, #diary-calendar-board .diary-calendar-today');
        if (!link || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();

        // 이미 이번 달을 보고 있으면 '오늘' 은 아무 일도 하지 않는다. (오류 아님)
        if (link.classList.contains('is-current')) return;
        load(monthOf(link.href), 'push');
    });

    // 연/월 버튼: 누른 메뉴만 열고 다른 메뉴는 닫는다. (같은 버튼을 다시 누르면 닫힘)
    document.addEventListener('click', (event) => {
        const button = event.target.closest('#diary-calendar-board .diary-calendar-pick-button');
        if (button) {
            const open = button.getAttribute('aria-expanded') === 'true';
            closeMenus();
            if (!open) openMenu(button);
            return;
        }
        // 메뉴 밖을 누르면 닫는다. (달력 안쪽이든 바깥이든)
        if (!event.target.closest('#diary-calendar-board .diary-calendar-popover')) closeMenus();
    });

    // 연도를 고르면 월은 그대로, 월을 고르면 연도는 그대로 둔다.
    document.addEventListener('click', async (event) => {
        const option = event.target.closest('#diary-calendar-board .diary-calendar-option');
        if (!option) return;

        const [year, month] = (board.dataset.month || '').split('-');
        if (!year || !month) return;

        const buttonId = openButton()?.id;
        closeMenus();
        // 고르는 즉시 기존 비동기 달 이동을 그대로 탄다.
        await load(option.dataset.year
                ? `${option.dataset.year}-${month}`
                : `${year}-${String(option.dataset.month).padStart(2, '0')}`,
            'push');
        // 달력이 갈리면서 눌렀던 항목이 사라지므로 초점을 다시 그 버튼에 둔다.
        if (buttonId) document.getElementById(buttonId)?.focus({preventScroll: true});
    });

    // Esc 로 닫고 눌렀던 버튼으로 초점을 돌려준다.
    document.addEventListener('keydown', (event) => {
        if (event.key !== 'Escape') return;
        const open = openButton();
        if (!open) return;
        closeMenus();
        open.focus();
    });

    function openMenu(button) {
        const menu = document.getElementById(button.getAttribute('aria-controls'));
        if (!menu) return;

        button.setAttribute('aria-expanded', 'true');
        menu.hidden = false;

        // 지금 보고 있는 값이 바로 보이게 메뉴 안쪽만 움직인다. (화면은 그대로)
        const current = menu.querySelector('.diary-calendar-option.is-current');
        if (!current) return;
        menu.scrollTop = current.offsetTop - (menu.clientHeight - current.offsetHeight) / 2;
        current.focus({preventScroll: true});
    }

    function closeMenus() {
        document.querySelectorAll('#diary-calendar-board .diary-calendar-pick-button')
            .forEach((button) => {
                button.setAttribute('aria-expanded', 'false');
                const menu = document.getElementById(button.getAttribute('aria-controls'));
                if (menu) menu.hidden = true;
            });
    }

    function openButton() {
        return document.querySelector(
            '#diary-calendar-board .diary-calendar-pick-button[aria-expanded="true"]');
    }

    // 뒤로/앞으로: 주소의 month 를 그대로 되살린다. (기록을 새로 쌓지 않는다)
    window.addEventListener('popstate', () => {
        load(new URLSearchParams(window.location.search).get('month'), null);
    });

    async function load(month, historyMode) {
        // 빠르게 여러 번 누르면 앞선 요청의 늦은 응답이 최신 달력을 덮을 수 있어 취소한다.
        request?.abort();
        const controller = new AbortController();
        request = controller;

        showLoading(true);
        try {
            const response = await fetch(fragmentUrl + query(month), {
                credentials: 'same-origin',
                headers: {'Accept': 'text/html'},
                signal: controller.signal
            });
            if (response.status === 401) {
                window.location.href = '/login?redirect='
                    + encodeURIComponent(window.location.pathname + window.location.search);
                return;
            }
            if (!response.ok) throw new Error('달력을 불러오지 못했습니다');

            render(await response.text());
            if (historyMode) updateUrl(board.dataset.month, historyMode);
            showError(false);
        } catch (failure) {
            // 취소된 요청은 사용자 오류가 아니다. (보고 있던 달력도 그대로 둔다)
            if (failure.name !== 'AbortError') showError(true);
        } finally {
            // 뒤이어 시작된 요청이 있으면 그쪽이 표시를 이어 간다.
            if (request === controller) showLoading(false);
        }
    }

    /** 서버가 돌려준 달력 조각으로 달력 자리를 통째로 바꾼다. */
    function render(html) {
        const fresh = new DOMParser().parseFromString(html, 'text/html')
            .getElementById('diary-calendar-board');
        if (!fresh) throw new Error('달력을 불러오지 못했습니다');

        board.replaceWith(fresh);
        board = fresh;
    }

    function query(month) {
        // month 가 없으면(오늘) 서버가 이번 달로 본다.
        return month ? `?month=${encodeURIComponent(month)}` : '';
    }

    /** 사용자가 스스로 옮긴 달이라 뒤로 가기로 돌아올 수 있게 남긴다. */
    function updateUrl(month, historyMode) {
        const url = window.location.pathname.replace(/\/fragment$/, '') + query(month);
        if (historyMode === 'push') {
            window.history.pushState({}, '', url);
        } else {
            window.history.replaceState({}, '', url);
        }
    }

    /** 링크 주소(?month=YYYY-MM)에서 옮겨 갈 달만 꺼낸다. */
    function monthOf(href) {
        return new URL(href, window.location.origin).searchParams.get('month');
    }

    /** 달력이 사라지지 않게 아주 옅게만 흐려 둔다. */
    function showLoading(loading) {
        board.classList.toggle('is-loading', loading);
    }

    function showError(failed) {
        if (error) error.hidden = !failed;
    }
});
