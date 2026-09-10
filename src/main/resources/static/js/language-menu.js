/* 언어 메뉴: native details를 유지하고 바깥 클릭·Esc 닫기만 보완한다.
   사이트 헤더와 헤더가 없는 인증 화면(로그인)이 같은 동작을 쓰도록 공용 스크립트로 둔다. */
(() => {
    const menus = Array.from(document.querySelectorAll('.language-menu'));
    if (!menus.length) {
        return;
    }

    document.addEventListener('click', event => {
        menus.forEach(menu => {
            if (menu.open && !menu.contains(event.target)) {
                menu.removeAttribute('open');
            }
        });
    });

    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') {
            return;
        }
        const openMenu = menus.find(menu => menu.open);
        if (!openMenu) {
            return;
        }
        openMenu.removeAttribute('open');
        openMenu.querySelector('summary')?.focus();
    });
})();
