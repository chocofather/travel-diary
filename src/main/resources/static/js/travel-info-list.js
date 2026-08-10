(() => {
    const FILTER_SELECTOR = '.travel-info-filter-pill[data-filter-name]';
    const RESET_SELECTOR = '[data-travel-info-reset]';
    const RESULTS_SELECTOR = '#travel-info-results';
    const PAGINATION_LINK_SELECTOR = '#travel-info-results .travel-info-pagination a';
    const SINGLE_FILTER_NAMES = ['scope'];
    const CATEGORY_FILTER_NAME = 'categoryId';
    let activeController = null;
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
        if (url.searchParams.get('page') === '1') {
            url.searchParams.delete('page');
        }
        if (url.searchParams.get('size') === '12') {
            url.searchParams.delete('size');
        }
        return url;
    }

    function filterUrl(control) {
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

    function syncFilterUi(url) {
        const pills = Array.from(document.querySelectorAll(FILTER_SELECTOR));

        SINGLE_FILTER_NAMES.forEach((name) => {
            const group = pills.filter((pill) => pill.dataset.filterName === name);
            const allowedValues = new Set(group.map((pill) => pill.dataset.filterValue));
            const requestedValue = url.searchParams.get(name) || '';
            const activeValue = allowedValues.has(requestedValue) ? requestedValue : '';

            group.forEach((pill) => {
                const isActive = pill.dataset.filterValue === activeValue;
                pill.classList.toggle('is-active', isActive);
                if (isActive) {
                    pill.setAttribute('aria-current', 'true');
                } else {
                    pill.removeAttribute('aria-current');
                }

                const nextUrl = new URL(url.href);
                if (pill.dataset.filterValue) {
                    nextUrl.searchParams.set(name, pill.dataset.filterValue);
                } else {
                    nextUrl.searchParams.delete(name);
                }
                nextUrl.searchParams.delete('page');
                cleanUrl(nextUrl);
                pill.href = nextUrl.pathname + nextUrl.search;
            });
        });

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
        const template = document.createElement('template');
        template.innerHTML = html.trim();
        const nextResults = template.content.querySelector(RESULTS_SELECTOR);

        if (!currentResults || !nextResults) {
            throw new Error('여행정보 결과 영역을 찾을 수 없습니다.');
        }
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
        syncFilterUi(url);
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
            syncFilterUi(url);
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
        const anchor = filter || reset || pageLink;

        if (!anchor || !canIntercept(event, anchor)) {
            return;
        }
        event.preventDefault();

        if (reset) {
            loadResults(new URL('/travel-info', window.location.origin), 'push');
            return;
        }
        if (filter) {
            const url = filter.dataset.filterName === CATEGORY_FILTER_NAME
                ? categoryFilterUrl(filter)
                : filterUrl(filter);
            loadResults(url, 'push');
            return;
        }
        loadResults(new URL(pageLink.href), 'push');
    });

    window.addEventListener('popstate', () => {
        loadResults(new URL(window.location.href), 'none');
    });

    syncFilterUi(selectedUrl);
})();
