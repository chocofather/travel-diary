(() => {
    const BOOKMARK_SELECTOR = '[data-travel-info-bookmark]';
    const LOGIN_URL = '/login';

    document.addEventListener('click', async (event) => {
        if (!(event.target instanceof Element)) {
            return;
        }

        const button = event.target.closest(BOOKMARK_SELECTOR);
        if (!button) {
            return;
        }

        event.preventDefault();
        event.stopPropagation();

        if (typeof isLoggedIn === 'undefined' || !isLoggedIn) {
            window.location.assign(LOGIN_URL);
            return;
        }
        if (button.disabled) {
            return;
        }

        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (!csrfToken || !csrfHeader) {
            window.alert('북마크 처리에 실패했습니다.');
            return;
        }

        const bookmarked = button.dataset.bookmarked === 'true';
        button.disabled = true;

        try {
            const response = await fetch(button.dataset.bookmarkUrl, {
                method: bookmarked ? 'DELETE' : 'POST',
                credentials: 'same-origin',
                headers: {
                    'Accept': 'application/json',
                    [csrfHeader]: csrfToken
                }
            });

            if (response.status === 401) {
                window.location.assign(LOGIN_URL);
                return;
            }
            if (!response.ok) {
                throw new Error(`북마크 요청 실패: ${response.status}`);
            }

            updateButton(button, !bookmarked);
        } catch (error) {
            console.error(error);
            window.alert('북마크 처리에 실패했습니다.');
        } finally {
            button.disabled = false;
        }
    });

    function updateButton(button, bookmarked) {
        button.dataset.bookmarked = String(bookmarked);
        button.classList.toggle('is-bookmarked', bookmarked);
        button.setAttribute('aria-pressed', String(bookmarked));
        button.setAttribute('aria-label', bookmarked ? '여행정보 저장 취소' : '여행정보 저장');

        const label = button.querySelector('.travel-info-bookmark-label');
        if (label) {
            label.textContent = bookmarked ? '저장됨' : '저장';
        }
    }
})();
