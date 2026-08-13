/* /js/main.js */
document.addEventListener('DOMContentLoaded', () => {
    /* 검색창 토글 */
    const toggleBtn = document.getElementById('search-toggle');
    const form = document.getElementById('search-form');
    const searchBox = toggleBtn?.closest('.search-box');

    if (toggleBtn && form && searchBox) {
        const setSearchOpen = (isOpen, restoreFocus = false) => {
            form.classList.toggle('open', isOpen);
            searchBox.classList.toggle('search-open', isOpen);
            toggleBtn.setAttribute('aria-expanded', String(isOpen));
            if (isOpen) {
                form.querySelector('input')?.focus();
            } else if (restoreFocus) {
                toggleBtn.focus();
            }
        };

        toggleBtn.addEventListener('click', e => {
            e.preventDefault();
            setSearchOpen(true);
        });

        form.addEventListener('keydown', e => {
            if (e.key === 'Escape') {
                setSearchOpen(false, true);
            }
        });

        document.addEventListener('click', e => {
            if (form.classList.contains('open') && !searchBox.contains(e.target)) {
                setSearchOpen(false);
            }
        });
    }

    /* 프로필 클릭 → 마이페이지 이동 */
    const profile = document.getElementById('profile-img');
    if (profile) {
        profile.addEventListener('click', () => {
            location.href = '/mypage';
        });
    }

    /* 이미지 우클릭 방지 */
    document.addEventListener('contextmenu', e => {
        if (e.target.tagName === 'IMG') {
            e.preventDefault();
        }
    });
});
