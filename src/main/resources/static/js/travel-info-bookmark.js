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
            window.alert(text(button, 'failedMessage'));
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
            window.alert(text(button, 'failedMessage'));
        } finally {
            button.disabled = false;
        }
    });

    function updateButton(button, bookmarked) {
        button.dataset.bookmarked = String(bookmarked);
        button.classList.toggle('is-bookmarked', bookmarked);
        button.setAttribute('aria-pressed', String(bookmarked));
        const ariaLabel = bookmarked
            ? text(button, 'ariaRemove')
            : text(button, 'ariaSave');
        if (ariaLabel) {
            button.setAttribute('aria-label', ariaLabel);
        }

        const label = button.querySelector('.travel-info-bookmark-label');
        const labelText = bookmarked
            ? text(button, 'labelSaved')
            : text(button, 'labelSave');
        if (label && labelText) {
            label.textContent = labelText;
        }
    }

    /**
     * 버튼이 들고 있는 현재 언어 문구. 목록·상세 화면이 messages 로 실어 준다.
     * 값이 없으면 화면에 이미 있는 문구를 그대로 두고 건드리지 않는다.
     */
    function text(button, key) {
        const value = button.dataset[key];
        return value && value.trim() ? value : '';
    }
})();
