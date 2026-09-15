document.addEventListener('DOMContentLoaded', () => {
    const filter = document.getElementById('admin-sticker-filter');
    const results = document.getElementById('admin-sticker-results');
    const error = document.getElementById('admin-sticker-filter-error');
    const reset = filter?.querySelector('.admin-sticker-filter-actions a');
    if (!filter || !results || !error || !reset) return;

    let activeRequest;

    async function refreshResults() {
        activeRequest?.abort();
        const request = new AbortController();
        activeRequest = request;
        const parameters = new URLSearchParams(new FormData(filter));
        const url = new URL(filter.action, window.location.origin);
        url.search = parameters;
        url.searchParams.set('partial', 'true');

        try {
            const response = await fetch(url, {
                headers: {Accept: 'text/html'},
                signal: request.signal
            });
            if (!response.ok) throw new Error('sticker filter request failed');

            results.innerHTML = await response.text();
            error.hidden = true;
            const query = parameters.toString();
            window.history.replaceState(null, '', query ? `${filter.action}?${query}` : filter.action);
        } catch (exception) {
            if (exception.name !== 'AbortError') error.hidden = false;
        } finally {
            if (activeRequest === request) activeRequest = undefined;
        }
    }

    filter.querySelectorAll('select').forEach((select) => {
        select.addEventListener('change', refreshResults);
    });
    filter.addEventListener('submit', (event) => {
        event.preventDefault();
        refreshResults();
    });
    reset.addEventListener('click', (event) => {
        event.preventDefault();
        filter.querySelectorAll('select').forEach((select) => {
            select.value = '';
        });
        refreshResults();
    });
});
