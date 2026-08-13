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

function loadBoardList(page, sort, updateHistory = true) {
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
            if (updateHistory) {
                window.history.pushState(null, '', `${window.location.pathname}?${params.toString()}`);
            }
        })
        .catch(error => {
            console.error(error);
            alert('게시판 목록을 불러오지 못했습니다.');
        });
}

function changeBoardCountry(countryId) {
    const params = new URLSearchParams(window.location.search);
    params.set('boardType', 'course');
    params.set('scope', 'overseas');
    params.set('page', '1');
    if (countryId) {
        params.set('countryId', countryId);
    } else {
        params.delete('countryId');
    }
    window.location.assign(`/board/list?${params.toString()}`);
}

function changeBoardScope(event, scope) {
    event.preventDefault();
    const allowedScopes = new Set(['all', 'domestic', 'overseas']);
    const nextScope = allowedScopes.has(scope) ? scope : 'all';
    const params = new URLSearchParams(window.location.search);
    params.set('boardType', 'course');
    params.set('scope', nextScope);
    params.set('page', '1');
    params.delete('countryId');
    window.location.assign(`/board/list?${params.toString()}`);
}

document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(window.location.search);
    updateBoardSortState(params.get('sort'));

    const field = document.querySelector('.board-country-select-wrap');
    const input = document.getElementById('board-country-input');
    const listbox = document.getElementById('board-country-listbox');
    const options = document.querySelectorAll('.board-country-option');
    if (field && input && listbox) {
        window.CountryCombobox?.init({
            root: field.querySelector('.board-country-combobox'),
            input,
            listbox,
            options,
            empty: document.getElementById('board-country-option-empty'),
            getSelectedName: () => field.dataset.selectedCountryName || '',
            onSelect: countryId => {
                changeBoardCountry(countryId);
                return true;
            }
        });
    }
});

window.addEventListener('popstate', () => {
    const params = new URLSearchParams(window.location.search);
    loadBoardList(params.get('page'), params.get('sort'), false);
});
