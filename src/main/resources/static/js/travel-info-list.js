(() => {
    const FILTER_SELECTOR = '.travel-info-filter-pill[data-filter-name]';
    const RESET_SELECTOR = '[data-travel-info-reset]';
    const RESULTS_SELECTOR = '#travel-info-results';
    const CATEGORY_FILTER_SELECTOR = '#travel-info-category-filter';
    const CATEGORY_FILTER_TEMPLATE_SELECTOR = '#travel-info-category-filter-template';
    const PAGINATION_LINK_SELECTOR = '#travel-info-results .travel-info-pagination a';
    const SEARCH_FORM_SELECTOR = '[data-travel-info-search]';
    const SEARCH_INPUT_SELECTOR = '[data-travel-info-search-input]';
    const SEARCH_CLEAR_SELECTOR = '[data-travel-info-search-clear]';
    const SORT_SELECTOR = '[data-travel-info-sort]';
    const PRIMARY_FILTER_NAME = 'primary';
    const CATEGORY_FILTER_NAME = 'categoryId';
    const CONTENT_TYPE_PARAMETER_NAME = 'contentType';
    const FESTIVAL_CONTENT_TYPE = 'FESTIVAL';
    const GENERAL_CONTENT_TYPE = 'GENERAL';
    const KEYWORD_PARAMETER_NAME = 'keyword';
    const SORT_PARAMETER_NAME = 'sort';
    const SORT_VIEWS = 'views';
    const KEYWORD_MAX_LENGTH = 100;
    const SEARCH_DEBOUNCE_MS = 200;
    let activeController = null;
    let searchTimer = null;
    let selectedUrl = new URL(window.location.href);

    function canIntercept(event, control) {
        return event.button === 0
            && !event.defaultPrevented
            && !event.metaKey
            && !event.ctrlKey
            && !event.shiftKey
            && !event.altKey
            && (control.tagName !== 'A' || !control.target || control.target === '_self');
    }

    function cleanUrl(url) {
        const keyword = normalizeKeyword(url.searchParams.get(KEYWORD_PARAMETER_NAME) || '');
        if (keyword) {
            url.searchParams.set(KEYWORD_PARAMETER_NAME, keyword);
        } else {
            url.searchParams.delete(KEYWORD_PARAMETER_NAME);
        }
        if (url.searchParams.get('page') === '1') {
            url.searchParams.delete('page');
        }
        if (url.searchParams.get('size') === '12') {
            url.searchParams.delete('size');
        }
        if (url.searchParams.get(SORT_PARAMETER_NAME) === SORT_VIEWS) {
            url.searchParams.set(SORT_PARAMETER_NAME, SORT_VIEWS);
        } else {
            url.searchParams.delete(SORT_PARAMETER_NAME);
        }
        return url;
    }

    function normalizeKeyword(value) {
        return Array.from(value.trim()).slice(0, KEYWORD_MAX_LENGTH).join('');
    }

    function searchUrl(value) {
        const url = new URL(selectedUrl.href);
        const keyword = normalizeKeyword(value);

        if (keyword) {
            url.searchParams.set(KEYWORD_PARAMETER_NAME, keyword);
        } else {
            url.searchParams.delete(KEYWORD_PARAMETER_NAME);
        }
        url.searchParams.delete('page');
        return cleanUrl(url);
    }

    function filterUrl(control) {
        if (control.dataset.filterName === PRIMARY_FILTER_NAME) {
            return primaryFilterUrl(control);
        }
        const url = new URL(selectedUrl.href);
        const name = control.dataset.filterName;
        const value = control.dataset.filterValue;

        if (value) {
            url.searchParams.set(name, value);
        } else {
            url.searchParams.delete(name);
        }
        url.searchParams.delete('page');
        return cleanUrl(url);
    }

    function primaryFilterUrl(control, baseUrl = selectedUrl) {
        const url = new URL(baseUrl.href);
        const primaryValue = control.dataset.filterValue;
        const currentContentType = url.searchParams.get(CONTENT_TYPE_PARAMETER_NAME)
            === FESTIVAL_CONTENT_TYPE
            ? FESTIVAL_CONTENT_TYPE
            : GENERAL_CONTENT_TYPE;
        const nextContentType = primaryValue === FESTIVAL_CONTENT_TYPE
            ? FESTIVAL_CONTENT_TYPE
            : GENERAL_CONTENT_TYPE;

        if (nextContentType === FESTIVAL_CONTENT_TYPE) {
            url.searchParams.delete('scope');
            url.searchParams.set(CONTENT_TYPE_PARAMETER_NAME, FESTIVAL_CONTENT_TYPE);
        } else {
            url.searchParams.set(CONTENT_TYPE_PARAMETER_NAME, GENERAL_CONTENT_TYPE);
            if (primaryValue) {
                url.searchParams.set('scope', primaryValue);
            } else {
                url.searchParams.delete('scope');
            }
        }
        if (currentContentType !== nextContentType) {
            url.searchParams.delete(CATEGORY_FILTER_NAME);
        }
        url.searchParams.delete('page');
        return cleanUrl(url);
    }

    function isPositiveCategoryId(value) {
        return /^\d+$/.test(value) && Number(value) > 0;
    }

    function categoryFilterUrl(control) {
        const url = new URL(selectedUrl.href);
        const value = control.dataset.filterValue;
        const selectedCategoryIds = new Set(
            url.searchParams.getAll(CATEGORY_FILTER_NAME).filter(isPositiveCategoryId)
        );

        if (!value) {
            selectedCategoryIds.clear();
        } else if (selectedCategoryIds.has(value)) {
            selectedCategoryIds.delete(value);
        } else {
            selectedCategoryIds.add(value);
        }

        url.searchParams.delete(CATEGORY_FILTER_NAME);
        selectedCategoryIds.forEach((categoryId) => {
            url.searchParams.append(CATEGORY_FILTER_NAME, categoryId);
        });
        url.searchParams.delete('page');
        return cleanUrl(url);
    }

    function sortUrl(control) {
        const url = new URL(selectedUrl.href);
        if (control.dataset.sortValue === SORT_VIEWS) {
            url.searchParams.set(SORT_PARAMETER_NAME, SORT_VIEWS);
        } else {
            url.searchParams.delete(SORT_PARAMETER_NAME);
        }
        url.searchParams.delete('page');
        return cleanUrl(url);
    }

    function syncFilterUi(url) {
        const pills = Array.from(document.querySelectorAll(FILTER_SELECTOR));
        syncPrimaryFilterUi(pills, url);

        const categoryPills = pills.filter(
            (pill) => pill.dataset.filterName === CATEGORY_FILTER_NAME
        );
        const selectedCategoryIds = new Set(
            url.searchParams.getAll(CATEGORY_FILTER_NAME).filter(isPositiveCategoryId)
        );
        categoryPills.forEach((pill) => {
            const value = pill.dataset.filterValue;
            const isActive = value
                ? selectedCategoryIds.has(value)
                : selectedCategoryIds.size === 0;
            pill.classList.toggle('is-active', isActive);
            pill.setAttribute('aria-pressed', String(isActive));
        });
    }

    function syncPrimaryFilterUi(pills, url) {
        const primaryPills = pills.filter((pill) => pill.dataset.filterName === PRIMARY_FILTER_NAME);
        const contentType = url.searchParams.get(CONTENT_TYPE_PARAMETER_NAME) === FESTIVAL_CONTENT_TYPE
            ? FESTIVAL_CONTENT_TYPE
            : GENERAL_CONTENT_TYPE;
        const activeValue = contentType === FESTIVAL_CONTENT_TYPE
            ? FESTIVAL_CONTENT_TYPE
            : url.searchParams.get('scope') || '';

        primaryPills.forEach((pill) => {
            const isActive = pill.dataset.filterValue === activeValue;
            pill.classList.toggle('is-active', isActive);
            if (isActive) {
                pill.setAttribute('aria-current', 'true');
            } else {
                pill.removeAttribute('aria-current');
            }

            const nextUrl = primaryFilterUrl(pill, url);
            pill.href = nextUrl.pathname + nextUrl.search;
        });
    }

    function syncSearchUi(url) {
        const input = document.querySelector(SEARCH_INPUT_SELECTOR);
        const clearButton = document.querySelector(SEARCH_CLEAR_SELECTOR);
        if (!input) {
            return;
        }

        const keyword = normalizeKeyword(url.searchParams.get(KEYWORD_PARAMETER_NAME) || '');
        if (input.value !== keyword) {
            input.value = keyword;
        }
        if (clearButton) {
            clearButton.hidden = !keyword;
        }
    }

    function syncSortUi(url) {
        const options = Array.from(document.querySelectorAll(SORT_SELECTOR));
        const activeSort = url.searchParams.get(SORT_PARAMETER_NAME) === SORT_VIEWS
            ? SORT_VIEWS
            : 'latest';
        options.forEach((option) => {
            const isActive = option.dataset.sortValue === activeSort;
            option.classList.toggle('is-active', isActive);
            if (isActive) {
                option.setAttribute('aria-current', 'true');
            } else {
                option.removeAttribute('aria-current');
            }

            const nextUrl = new URL(url.href);
            if (option.dataset.sortValue === SORT_VIEWS) {
                nextUrl.searchParams.set(SORT_PARAMETER_NAME, SORT_VIEWS);
            } else {
                nextUrl.searchParams.delete(SORT_PARAMETER_NAME);
            }
            nextUrl.searchParams.delete('page');
            cleanUrl(nextUrl);
            option.href = nextUrl.pathname + nextUrl.search;
        });
    }

    function syncUi(url) {
        syncFilterUi(url);
        syncSearchUi(url);
        syncSortUi(url);
    }

    function clearSearchTimer() {
        if (searchTimer !== null) {
            window.clearTimeout(searchTimer);
            searchTimer = null;
        }
    }

    function runSearch() {
        const input = document.querySelector(SEARCH_INPUT_SELECTOR);
        if (!input) {
            return;
        }
        clearSearchTimer();
        loadResults(searchUrl(input.value), 'push');
    }

    function scheduleSearch() {
        clearSearchTimer();
        searchTimer = window.setTimeout(() => {
            searchTimer = null;
            runSearch();
        }, SEARCH_DEBOUNCE_MS);
    }

    function setLoading(loading) {
        const results = document.querySelector(RESULTS_SELECTOR);
        if (!results) {
            return;
        }
        results.classList.toggle('is-loading', loading);
        results.setAttribute('aria-busy', String(loading));
    }

    function showMessage(message) {
        const messageElement = document.getElementById('travel-info-async-message');
        if (!messageElement) {
            return;
        }
        messageElement.textContent = message;
        messageElement.hidden = !message;
    }

    function replaceResults(html) {
        const currentResults = document.querySelector(RESULTS_SELECTOR);
        const currentCategoryFilter = document.querySelector(CATEGORY_FILTER_SELECTOR);
        const template = document.createElement('template');
        template.innerHTML = html.trim();
        const nextResults = template.content.querySelector(RESULTS_SELECTOR);
        const categoryFilterTemplate = template.content.querySelector(CATEGORY_FILTER_TEMPLATE_SELECTOR);
        const nextCategoryFilter = categoryFilterTemplate?.content.querySelector(CATEGORY_FILTER_SELECTOR);

        if (!currentResults || !nextResults || !currentCategoryFilter || !nextCategoryFilter) {
            throw new Error('여행정보 결과 영역을 찾을 수 없습니다.');
        }
        currentCategoryFilter.replaceWith(nextCategoryFilter);
        currentResults.replaceWith(nextResults);
    }

    async function loadResults(requestedUrl, historyMode) {
        const url = cleanUrl(new URL(requestedUrl.href));
        const controller = new AbortController();

        if (activeController) {
            activeController.abort();
        }
        activeController = controller;
        selectedUrl = url;
        syncUi(url);
        showMessage('');
        setLoading(true);

        try {
            const response = await fetch(url.pathname + url.search, {
                method: 'GET',
                headers: {
                    'X-Requested-With': 'XMLHttpRequest'
                },
                signal: controller.signal
            });
            if (!response.ok) {
                throw new Error(`목록 요청 실패: ${response.status}`);
            }

            replaceResults(await response.text());
            if (historyMode === 'push') {
                window.history.pushState({}, '', url.pathname + url.search);
            }
            syncUi(url);
        } catch (error) {
            if (error.name === 'AbortError') {
                return;
            }
            showMessage('목록을 불러오지 못했습니다. 페이지를 다시 불러옵니다.');
            window.setTimeout(() => window.location.assign(url.href), 250);
        } finally {
            if (activeController === controller) {
                activeController = null;
                setLoading(false);
            }
        }
    }

    document.addEventListener('click', (event) => {
        if (!(event.target instanceof Element)) {
            return;
        }
        const filter = event.target.closest(FILTER_SELECTOR);
        const reset = event.target.closest(RESET_SELECTOR);
        const pageLink = event.target.closest(PAGINATION_LINK_SELECTOR);
        const sortOption = event.target.closest(SORT_SELECTOR);
        const anchor = filter || reset || pageLink || sortOption;

        if (!anchor || !canIntercept(event, anchor)) {
            return;
        }
        event.preventDefault();

        if (reset) {
            clearSearchTimer();
            loadResults(new URL('/travel-info', window.location.origin), 'push');
            return;
        }
        if (filter) {
            clearSearchTimer();
            const url = filter.dataset.filterName === CATEGORY_FILTER_NAME
                ? categoryFilterUrl(filter)
                : filterUrl(filter);
            loadResults(url, 'push');
            return;
        }
        if (sortOption) {
            clearSearchTimer();
            loadResults(sortUrl(sortOption), 'push');
            return;
        }
        clearSearchTimer();
        loadResults(new URL(pageLink.href), 'push');
    });

    const searchForm = document.querySelector(SEARCH_FORM_SELECTOR);
    const searchInput = document.querySelector(SEARCH_INPUT_SELECTOR);
    const searchClearButton = document.querySelector(SEARCH_CLEAR_SELECTOR);

    if (searchForm && searchInput) {
        searchForm.addEventListener('submit', (event) => {
            event.preventDefault();
            runSearch();
        });

        searchInput.addEventListener('input', () => {
            scheduleSearch();
        });

        searchInput.addEventListener('compositionstart', () => {
            clearSearchTimer();
        });

        searchInput.addEventListener('compositionupdate', () => {
            scheduleSearch();
        });

        searchInput.addEventListener('compositionend', () => {
            scheduleSearch();
        });
    }

    if (searchClearButton && searchInput) {
        searchClearButton.addEventListener('click', () => {
            searchInput.value = '';
            runSearch();
            searchInput.focus();
        });
    }

    window.addEventListener('popstate', () => {
        clearSearchTimer();
        loadResults(new URL(window.location.href), 'none');
    });

    syncUi(selectedUrl);
})();
