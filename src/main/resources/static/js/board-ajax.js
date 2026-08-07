const BOARD_SORT_TYPES = new Set(['latest', 'oldest', 'views', 'comments', 'bookmarks']);

function normalizeBoardSort(sort) {
    const normalized = typeof sort === 'string' ? sort.toLowerCase() : 'latest';
    return BOARD_SORT_TYPES.has(normalized) ? normalized : 'latest';
}

function updateBoardSortState(sort) {
    const activeSort = normalizeBoardSort(sort);
    document.querySelectorAll('.board-sort-button[data-sort]').forEach(button => {
        const isActive = button.dataset.sort === activeSort;
        button.classList.toggle('active', isActive);
        button.setAttribute('aria-pressed', String(isActive));
    });
}

function loadBoardList(page, sort) {
    const params = new URLSearchParams(window.location.search);
    params.set('page', Math.max(Number(page) || 1, 1).toString());
    const activeSort = normalizeBoardSort(sort);
    params.set('sort', activeSort);

    if (!params.has('size')) {
        params.set('size', '10');
    }

    fetch(`/board/fragment?${params.toString()}`)
        .then(res => {
            if (!res.ok) {
                throw new Error(`게시판 목록 요청 실패: ${res.status}`);
            }
            return res.text();
        })
        .then(html => {
            document.getElementById('board-fragment-container').innerHTML = html;
            updateBoardSortState(activeSort);
        })
        .catch(error => {
            console.error(error);
            alert('게시판 목록을 불러오지 못했습니다.');
        });
}

document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(window.location.search);
    updateBoardSortState(params.get('sort'));
});
