/**
 * 여행일기 목록의 비동기 검색 / 정렬 / 쪽 이동.
 * 입력에 따라 결과 조각(diary/list :: results)만 받아 목록 자리를 갈아 끼운다.
 * 카드 HTML 은 서버 조각을 그대로 쓰므로 여기서 다시 만들지 않는다.
 * 검색어(q) · 정렬(sort) · 쪽(page)은 언제나 함께 다니고 주소에도 그대로 남는다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('diary-search-form');
    const input = form?.querySelector('.diary-search-input');
    const spinner = form?.querySelector('.diary-search-spinner');
    let results = document.getElementById('diary-results');
    if (!form || !input || !results) return;

    /** 조합 중간 글자(ㅈ → 제 → 제주)로 찾지 않을 만큼만 기다린다. */
    const SEARCH_DELAY = 280;
    const fragmentUrl = form.dataset.fragmentUrl;
    if (!fragmentUrl) return;

    // 정렬 고르기. 목록 조각 바깥에 있어 갈아 끼워도 그대로 남는다.
    const sortBox = document.getElementById('diary-sort');
    const sortButton = document.getElementById('diary-sort-button');
    const sortMenu = document.getElementById('diary-sort-menu');
    const sortValue = document.getElementById('diary-sort-value');
    /** 주소에서 생략하는 기본 정렬. 어떤 값인지는 서버가 알려 준다. */
    const DEFAULT_SORT = sortBox?.dataset.defaultSort || '';

    let timer = 0;
    let composing = false;
    let request = null;
    let sort = shownSort();
    // 지금 화면에 반영된 상태. 같은 조건이면 다시 부르지 않는다.
    let applied = {keyword: input.value.trim(), sort, page: currentPage()};

    // 버튼을 다시 누르면 닫힌다. (바깥 클릭/Esc 는 아래에서 함께 처리한다)
    sortButton?.addEventListener('click', () => toggleSort(sortMenu.hidden));

    document.addEventListener('click', (event) => {
        if (!sortBox || !sortMenu || sortMenu.hidden) return;
        if (!sortBox.contains(event.target)) toggleSort(false);
    });
    document.addEventListener('keydown', (event) => {
        if (event.key !== 'Escape' || !sortMenu || sortMenu.hidden) return;
        toggleSort(false);
        sortButton?.focus();
    });

    // 정렬을 바꾸면 검색어는 그대로 두고 첫 쪽부터 다시 본다.
    sortMenu?.querySelectorAll('.diary-sort-option').forEach((option) => {
        option.addEventListener('click', () => {
            toggleSort(false);
            sortButton?.focus();
            if (option.dataset.sort === sort) return;
            showSort(option.dataset.sort);
            run(input.value, 1, 'push');
        });
    });

    // Enter 로 폼이 통째로 전송돼 화면이 새로 그려지지 않게 한다.
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        run(input.value, 1, 'replace');
    });

    // 한글은 조합이 끝난 뒤에 찾는다.
    input.addEventListener('compositionstart', () => {
        composing = true;
    });
    input.addEventListener('compositionend', () => {
        composing = false;
        schedule();
    });

    input.addEventListener('input', () => {
        if (composing) return;
        schedule();
    });
    // 검색창 기본 ✕ 버튼. 지우는 즉시 전체 목록으로 되돌린다.
    input.addEventListener('search', () => {
        window.clearTimeout(timer);
        run(input.value, 1, 'replace');
    });

    // 쪽 이동도 화면을 새로 고치지 않는다. (결과가 갈려도 계속 듣도록 문서에 건다)
    document.addEventListener('click', (event) => {
        const link = event.target.closest('#diary-results .diary-pagination a[href]');
        if (!link || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();

        const target = new URL(link.href, window.location.origin);
        // 쪽 링크는 지금 검색어/정렬을 그대로 달고 있다.
        showSort(target.searchParams.get('sort') || DEFAULT_SORT);
        // 쪽 이동은 사용자가 스스로 옮긴 것이라 뒤로 가기로 돌아올 수 있게 남긴다.
        run(target.searchParams.get('q') || '', Number(target.searchParams.get('page') || 1),
            'push', true);
    });

    // 결과가 없을 때의 '전체 여행일기 보기'도 같은 방식으로 처리한다.
    document.addEventListener('click', (event) => {
        const reset = event.target.closest('#diary-results .diary-empty-reset');
        if (!reset || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        input.value = '';
        run('', 1, 'replace');
    });

    // 뒤로/앞으로: 주소의 q/sort/page 를 그대로 되살린다. (검색창·정렬 표시까지 함께 맞춘다)
    window.addEventListener('popstate', () => {
        const params = new URLSearchParams(window.location.search);
        const keyword = params.get('q') || '';
        input.value = keyword;
        showSort(params.get('sort') || DEFAULT_SORT);
        load(keyword, pageOf(params.get('page')), null);
    });

    function schedule() {
        window.clearTimeout(timer);
        timer = window.setTimeout(() => run(input.value, 1, 'replace'), SEARCH_DELAY);
    }

    /** 검색어나 정렬이 바뀌면 언제나 첫 쪽부터 본다. */
    function run(rawKeyword, page, historyMode, scrollToTop = false) {
        const keyword = (rawKeyword || '').trim();
        if (keyword === applied.keyword && page === applied.page && sort === applied.sort) return;
        load(keyword, page, historyMode, scrollToTop);
    }

    async function load(keyword, page, historyMode, scrollToTop = false) {
        // 요청을 보낸 순간의 정렬로 끝까지 간다. (도중에 또 바꾸면 그쪽 요청이 이어받는다)
        const order = sort;
        // 앞선 요청의 늦은 응답이 최신 결과를 덮지 않도록 취소한다.
        request?.abort();
        request = new AbortController();

        showLoading(true);
        try {
            const response = await fetch(fragmentUrl + query(keyword, page, order), {
                credentials: 'same-origin',
                headers: {'Accept': 'text/html'},
                signal: request.signal
            });
            if (response.status === 401) {
                window.location.href = '/login?redirect='
                    + encodeURIComponent(window.location.pathname + window.location.search);
                return;
            }
            if (!response.ok) throw new Error('목록을 불러오지 못했습니다');

            render(await response.text());
            applied = {keyword, sort: order, page};
            if (historyMode) updateUrl(keyword, page, order, historyMode);
            if (scrollToTop) scrollToResults();
        } catch (error) {
            // 취소된 요청은 사용자 오류가 아니다.
            if (error.name !== 'AbortError') window.alert(error.message);
        } finally {
            // 뒤이어 시작된 요청이 있으면 그쪽이 표시를 이어 간다.
            if (!request || request.signal.aborted) return;
            showLoading(false);
        }
    }

    /** 서버가 돌려준 결과 조각으로 목록 자리를 통째로 바꾼다. */
    function render(html) {
        const fresh = new DOMParser().parseFromString(html, 'text/html')
            .getElementById('diary-results');
        if (!fresh) return;

        results.replaceWith(fresh);
        results = fresh;
        // 새로 그려진 카드의 ⋯ 메뉴를 다시 연결한다.
        window.diaryBookMenu?.refresh();
    }

    /** 기본 정렬은 주소를 깔끔하게 두려고 붙이지 않는다. (서버도 없으면 기본으로 본다) */
    function query(keyword, page, order) {
        const params = new URLSearchParams();
        if (keyword) params.set('q', keyword);
        if (order && order !== DEFAULT_SORT) params.set('sort', order);
        if (page > 1) params.set('page', String(page));
        const search = params.toString();
        return search ? `?${search}` : '';
    }

    /** 입력 중에는 기록을 쌓지 않고(replace), 정렬/쪽 이동만 남긴다(push). */
    function updateUrl(keyword, page, order, historyMode) {
        const url = window.location.pathname.replace(/\/fragment$/, '')
            + query(keyword, page, order);
        if (historyMode === 'push') {
            window.history.pushState({}, '', url);
        } else {
            window.history.replaceState({}, '', url);
        }
    }

    function showLoading(loading) {
        if (spinner) spinner.hidden = !loading;
        form.classList.toggle('is-loading', loading);
    }

    function scrollToResults() {
        const top = results.getBoundingClientRect().top + window.scrollY - 90;
        window.scrollTo({top: Math.max(top, 0), behavior: 'smooth'});
    }

    function toggleSort(open) {
        if (!sortMenu || !sortButton) return;
        sortMenu.hidden = !open;
        sortButton.classList.toggle('is-open', open);
        sortButton.setAttribute('aria-expanded', open ? 'true' : 'false');
    }

    /** 지금 화면이 보여 주는 정렬. (서버가 표시해 둔 항목을 그대로 읽는다) */
    function shownSort() {
        return sortMenu?.querySelector('.diary-sort-option.is-current')?.dataset.sort
            || DEFAULT_SORT;
    }

    /** 고른 정렬을 버튼 이름/체크 표시에 반영한다. 목록에 없는 값이면 기본 정렬로 본다. */
    function showSort(order) {
        if (!sortMenu) return;
        const options = Array.from(sortMenu.querySelectorAll('.diary-sort-option'));
        const chosen = options.find((option) => option.dataset.sort === order)
            || options.find((option) => option.dataset.sort === DEFAULT_SORT);
        if (!chosen) return;

        sort = chosen.dataset.sort;
        if (sortValue) sortValue.textContent = chosen.textContent.trim();
        options.forEach((option) => {
            const current = option === chosen;
            option.classList.toggle('is-current', current);
            option.setAttribute('aria-selected', current ? 'true' : 'false');
        });
    }

    function currentPage() {
        return pageOf(new URLSearchParams(window.location.search).get('page'));
    }

    function pageOf(value) {
        const page = Number.parseInt(value ?? '1', 10);
        return Number.isNaN(page) || page < 1 ? 1 : page;
    }
});
