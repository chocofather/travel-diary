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

    /* 전체 메뉴 (좁은 화면) */
    const siteMenuToggle = document.getElementById('site-menu-toggle');
    const siteMenu = document.getElementById('site-menu');

    if (siteMenuToggle && siteMenu) {
        const setSiteMenuOpen = (isOpen, restoreFocus = false) => {
            siteMenu.hidden = !isOpen;
            siteMenuToggle.setAttribute('aria-expanded', String(isOpen));
            siteMenuToggle.setAttribute(
                    'aria-label', isOpen ? '전체 메뉴 닫기' : '전체 메뉴 열기');
            if (!isOpen && restoreFocus) {
                siteMenuToggle.focus();
            }
        };

        siteMenuToggle.addEventListener('click', () => {
            setSiteMenuOpen(siteMenu.hidden);
        });

        /* 바깥을 누르면 닫는다 */
        document.addEventListener('click', e => {
            if (siteMenu.hidden) return;
            if (siteMenu.contains(e.target) || siteMenuToggle.contains(e.target)) return;
            setSiteMenuOpen(false);
        });

        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && !siteMenu.hidden) {
                setSiteMenuOpen(false, true);
            }
        });

        /* 넓은 화면으로 돌아가면 메뉴가 헤더에 다시 펼쳐지므로 판은 닫아 둔다 */
        window.addEventListener('resize', () => {
            if (!siteMenu.hidden && window.innerWidth > 1199) {
                setSiteMenuOpen(false);
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

    /* 언어 메뉴: native details를 유지하고 바깥 클릭·Esc 닫기만 보완한다. */
    const languageMenu = document.querySelector('.language-menu');

    if (languageMenu) {
        document.addEventListener('click', e => {
            if (languageMenu.open && !languageMenu.contains(e.target)) {
                languageMenu.removeAttribute('open');
            }
        });

        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && languageMenu.open) {
                languageMenu.removeAttribute('open');
                languageMenu.querySelector('summary')?.focus();
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
