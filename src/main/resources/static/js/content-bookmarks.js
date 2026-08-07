document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.content-bookmark-button').forEach(button => {
        button.addEventListener('click', async () => {
            if (typeof isLoggedIn === 'undefined' || !isLoggedIn) {
                const redirect = window.location.pathname + window.location.search;
                window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`;
                return;
            }

            if (button.disabled) return;
            button.disabled = true;

            try {
                const bookmarked = button.dataset.bookmarked === 'true';
                const response = await fetch(button.dataset.bookmarkUrl, {
                    method: bookmarked ? 'DELETE' : 'POST',
                    credentials: 'same-origin',
                    headers: {'Accept': 'application/json'}
                });

                if (response.status === 401) {
                    const redirect = window.location.pathname + window.location.search;
                    window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`;
                    return;
                }
                if (!response.ok) {
                    throw new Error(`북마크 요청에 실패했습니다. (HTTP ${response.status})`);
                }

                updateButton(button, !bookmarked);
            } catch (error) {
                console.error(error);
                window.alert('북마크 상태를 변경하지 못했습니다. 잠시 후 다시 시도해 주세요.');
            } finally {
                button.disabled = false;
            }
        });
    });

    function updateButton(button, bookmarked) {
        button.dataset.bookmarked = String(bookmarked);
        button.classList.toggle('is-bookmarked', bookmarked);
        button.setAttribute('aria-pressed', String(bookmarked));
        const label = button.querySelector('.content-bookmark-label');
        if (label) {
            label.textContent = bookmarked ? '북마크 취소' : '북마크 저장';
        }
    }
});
