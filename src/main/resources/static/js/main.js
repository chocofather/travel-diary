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

    /* 프로필 메뉴 */
    const profileToggle = document.getElementById('profile-menu-toggle');
    const profileMenu = document.getElementById('profile-menu');
    const profileMenuContainer = profileToggle?.closest('.profile-menu-container');

    if (profileToggle && profileMenu && profileMenuContainer) {
        const setProfileMenuOpen = (isOpen, restoreFocus = false) => {
            profileMenu.hidden = !isOpen;
            profileToggle.setAttribute('aria-expanded', String(isOpen));
            profileToggle.setAttribute(
                    'aria-label', isOpen ? '프로필 메뉴 닫기' : '프로필 메뉴 열기');
            if (!isOpen && restoreFocus) {
                profileToggle.focus();
            }
        };

        profileToggle.addEventListener('click', () => {
            setProfileMenuOpen(profileMenu.hidden);
        });

        document.addEventListener('click', e => {
            if (!profileMenu.hidden && !profileMenuContainer.contains(e.target)) {
                setProfileMenuOpen(false);
            }
        });

        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && !profileMenu.hidden) {
                setProfileMenuOpen(false, true);
            }
        });
    }

    /* 이미지 우클릭 방지 */
    document.addEventListener('contextmenu', e => {
        if (e.target.tagName === 'IMG') {
            e.preventDefault();
        }
    });
});
