/**
 * 여행일기 목록의 비동기 검색 / 쪽 이동.
 * 입력에 따라 결과 조각(diary/list :: results)만 받아 목록 자리를 갈아 끼운다.
 * 카드 HTML 은 서버 조각을 그대로 쓰므로 여기서 다시 만들지 않는다.
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

    let timer = 0;
    let composing = false;
    let request = null;
    // 지금 화면에 반영된 상태. 같은 조건이면 다시 부르지 않는다.
    let applied = {keyword: input.value.trim(), page: currentPage()};

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

    // 뒤로/앞으로: 주소의 q/page 를 그대로 되살린다.
    window.addEventListener('popstate', () => {
        const params = new URLSearchParams(window.location.search);
        const keyword = params.get('q') || '';
        input.value = keyword;
        load(keyword, pageOf(params.get('page')), null);
    });

    function schedule() {
        window.clearTimeout(timer);
        timer = window.setTimeout(() => run(input.value, 1, 'replace'), SEARCH_DELAY);
    }

    /** 검색어가 바뀌면 언제나 첫 쪽부터 본다. */
    function run(rawKeyword, page, historyMode, scrollToTop = false) {
        const keyword = (rawKeyword || '').trim();
        if (keyword === applied.keyword && page === applied.page) return;
        load(keyword, page, historyMode, scrollToTop);
    }

    async function load(keyword, page, historyMode, scrollToTop = false) {
        // 앞선 요청의 늦은 응답이 최신 결과를 덮지 않도록 취소한다.
        request?.abort();
        request = new AbortController();

        showLoading(true);
        try {
            const response = await fetch(fragmentUrl + query(keyword, page), {
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
            applied = {keyword, page};
            if (historyMode) updateUrl(keyword, page, historyMode);
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

    function query(keyword, page) {
        const params = new URLSearchParams();
        if (keyword) params.set('q', keyword);
        if (page > 1) params.set('page', String(page));
        const search = params.toString();
        return search ? `?${search}` : '';
    }

    /** 입력 중에는 기록을 쌓지 않고(replace), 쪽 이동만 남긴다(push). */
    function updateUrl(keyword, page, historyMode) {
        const url = window.location.pathname.replace(/\/fragment$/, '') + query(keyword, page);
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

    function currentPage() {
        return pageOf(new URLSearchParams(window.location.search).get('page'));
    }

    function pageOf(value) {
        const page = Number.parseInt(value ?? '1', 10);
        return Number.isNaN(page) || page < 1 ? 1 : page;
    }
});
