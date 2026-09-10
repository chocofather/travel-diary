/* /js/main.js */
document.addEventListener('DOMContentLoaded', () => {
    /* 검색창 토글 */
    const toggleBtn = document.getElementById('search-toggle');
    const form = document.getElementById('search-form');
    const searchBox = toggleBtn?.closest('.search-box');

    if (toggleBtn && form && searchBox) {
        const searchInput = form.querySelector('input[name="q"]');
        const clearBtn = form.querySelector('.search-clear');
        /* 공백만 남은 값은 검색어가 없는 것으로 본다 */
        const hasKeyword = () => (searchInput?.value ?? '').trim() !== '';
        /* 지우기 버튼은 지울 값이 있을 때만 보인다 */
        const syncClearButton = () => {
            if (clearBtn) {
                clearBtn.hidden = (searchInput?.value ?? '') === '';
            }
        };

        const setSearchOpen = (isOpen, restoreFocus = false) => {
            form.classList.toggle('open', isOpen);
            searchBox.classList.toggle('search-open', isOpen);
            toggleBtn.setAttribute('aria-expanded', String(isOpen));
            syncClearButton();
            if (isOpen) {
                form.querySelector('input')?.focus();
            } else if (restoreFocus) {
                toggleBtn.focus();
            }
        };

        searchInput?.addEventListener('input', syncClearButton);

        clearBtn?.addEventListener('click', e => {
            /* 폼 안의 버튼이라 검색으로 새지 않게 막는다 (type=button 이지만 확실히 둔다) */
            e.preventDefault();
            e.stopPropagation();
            if (searchInput) {
                searchInput.value = '';
                searchInput.focus();
            }
            syncClearButton();
        });

        /*
          /search 는 가운데 검색창이 검색어를 들고 있는 주 검색 UI다.
          헤더 검색은 결과 페이지에서 닫힌 채로 시작하고, 브라우저가 값을 되살렸더라도 비운다.
          다른 페이지는 예전처럼 돋보기를 눌렀을 때만 열린다.
        */
        if (window.location.pathname === '/search' && searchInput) {
            searchInput.value = '';
        }
        setSearchOpen(false);

        toggleBtn.addEventListener('click', e => {
            e.preventDefault();
            setSearchOpen(true);
        });

        /*
          열린 검색창의 돋보기는 form 의 submit 버튼이다.
          검색어 없이 눌렀거나 Enter 를 쳤을 때 /search?q= 로 넘어가지 않게 막고,
          그 자리에서 검색창을 닫는다. 값이 공백뿐이면 지워 다음에 깨끗하게 열리게 한다.
        */
        form.addEventListener('submit', e => {
            if (hasKeyword()) {
                return;
            }
            e.preventDefault();
            if (searchInput) {
                searchInput.value = '';
            }
            setSearchOpen(false, true);
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
                    'aria-label', isOpen
                            ? siteMenuToggle.dataset.closeLabel
                            : siteMenuToggle.dataset.openLabel);
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
                    'aria-label', isOpen
                            ? profileToggle.dataset.closeLabel
                            : profileToggle.dataset.openLabel);
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

    /* 언어 메뉴 열고 닫기는 헤더 없는 화면과 공용인 language-menu.js 가 담당한다. */

    /* 이미지 우클릭 방지 */
    document.addEventListener('contextmenu', e => {
        if (e.target.tagName === 'IMG') {
            e.preventDefault();
        }
    });
});
