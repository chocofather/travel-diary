// 기본 열기/닫기와 키보드 조작은 details/summary가 담당한다.
// course와 post의 동일한 작성자 메뉴, 비로그인 댓글 이동에 함께 쓴다.
(() => {
    const menus = Array.from(document.querySelectorAll('[data-owner-menu]'));

    document.addEventListener('click', event => {
        menus.forEach(menu => {
            if (!menu.contains(event.target)) menu.open = false;
        });
    });

    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        const openMenu = menus.find(menu => menu.open);
        if (!openMenu) return;
        openMenu.open = false;
        openMenu.querySelector('summary').focus();
    });

    menus.forEach(menu => menu.addEventListener('focusout', event => {
        if (!menu.contains(event.relatedTarget)) menu.open = false;
    }));

    document.querySelectorAll('[data-guest-comment-redirect]').forEach(textarea => {
        textarea.addEventListener('click', () => {
            const redirect = encodeURIComponent(window.location.pathname);
            window.location.href = `/login?redirect=${redirect}`;
        });
    });
})();
