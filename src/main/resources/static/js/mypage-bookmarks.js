document.addEventListener('DOMContentLoaded', () => {
    const status = document.querySelector('[data-bookmark-status]');
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    document.addEventListener('click', async event => {
        if (!(event.target instanceof Element)) return;

        const button = event.target.closest('[data-bookmark-remove]');
        if (!button || button.disabled) return;

        const deleteUrl = button.dataset.bookmarkDeleteUrl;
        if (!deleteUrl || !csrfToken || !csrfHeader) {
            showStatus('북마크를 해제하지 못했습니다. 잠시 후 다시 시도해 주세요.', true);
            return;
        }

        button.disabled = true;
        try {
            const response = await fetch(deleteUrl, {
                method: 'DELETE',
                credentials: 'same-origin',
                headers: {
                    'Accept': 'application/json',
                    [csrfHeader]: csrfToken
                }
            });

            if (response.status === 401) {
                const redirect = window.location.pathname + window.location.search;
                window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`);
                return;
            }
            if (!response.ok) {
                throw new Error(`북마크 해제 실패: ${response.status}`);
            }

            button.closest('[data-bookmark-item]')?.remove();
            showStatus('북마크를 해제했습니다.', false);
            handleEmptyPage();
        } catch (error) {
            console.error(error);
            showStatus('북마크를 해제하지 못했습니다. 잠시 후 다시 시도해 주세요.', true);
            button.disabled = false;
        }
    });

    function handleEmptyPage() {
        if (document.querySelector('[data-bookmark-item]')) return;

        const pageContainer = document.querySelector('[data-bookmark-page]');
        const currentPage = Math.max(
            Number.parseInt(pageContainer?.dataset.currentPage || '1', 10), 1);
        if (currentPage > 1) {
            const section = pageContainer?.dataset.currentSection || 'destination';
            const url = new URL('/mypage/bookmarks', window.location.origin);
            url.searchParams.set('section', section);
            if (section === 'community') {
                url.searchParams.set('type', pageContainer?.dataset.currentType || 'all');
            } else {
                url.searchParams.set('scope', pageContainer?.dataset.currentScope || 'all');
            }
            url.searchParams.set('page', String(currentPage - 1));
            window.location.assign(url.toString());
            return;
        }

        document.querySelector('[data-bookmark-list]')?.remove();
        const emptyState = document.querySelector('[data-bookmark-empty]');
        if (emptyState) emptyState.hidden = false;
        document.querySelector('[data-bookmark-pagination]')?.remove();
    }

    function showStatus(message, error) {
        if (!status) return;
        status.textContent = message;
        status.classList.toggle('is-error', error);
        status.hidden = false;
    }
});
