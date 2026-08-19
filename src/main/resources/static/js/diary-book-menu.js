/**
 * 여행일기 목록 카드의 ⋯ 관리 메뉴.
 * 메뉴 자체는 카드 링크 바깥에 있어 표지 클릭을 가로채지 않는다.
 * 설정/삭제 동작은 기존 링크·폼이 그대로 처리하고, 여기서는 열고 닫기만 맡는다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const menus = Array.from(document.querySelectorAll('.diary-book-menu')).map(setupMenu)
        .filter(Boolean);
    if (menus.length === 0) return;

    // 바깥을 누르면 열려 있던 메뉴를 닫는다.
    document.addEventListener('click', (event) => {
        menus.forEach(menu => {
            if (!menu.root.contains(event.target)) menu.close();
        });
    });

    document.addEventListener('keydown', (event) => {
        if (event.key !== 'Escape') return;
        menus.forEach(menu => {
            if (menu.isOpen()) {
                menu.close();
                menu.button.focus();
            }
        });
    });

    function setupMenu(root) {
        const button = root.querySelector('.diary-book-menu-button');
        const panel = root.querySelector('.diary-book-menu-panel');
        if (!button || !panel) return null;

        const menu = {
            root,
            button,
            isOpen: () => !panel.hidden,
            close: () => toggle(false)
        };

        button.addEventListener('click', (event) => {
            // 카드 전체 클릭(상세 이동)으로 번지지 않게 한다.
            event.preventDefault();
            event.stopPropagation();
            const open = panel.hidden;
            // 한 번에 하나만 열어 둔다.
            closeOthers();
            toggle(open);
        });

        // 메뉴 안에서의 클릭은 카드 링크로 번지지 않게 한다. (항목 자체 동작은 그대로)
        panel.addEventListener('click', event => event.stopPropagation());

        panel.addEventListener('keydown', (event) => {
            if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
            event.preventDefault();
            const items = Array.from(panel.querySelectorAll('.diary-book-menu-item'));
            const current = items.indexOf(document.activeElement);
            const step = event.key === 'ArrowDown' ? 1 : -1;
            const next = current < 0 ? 0 : (current + step + items.length) % items.length;
            items[next]?.focus();
        });

        button.addEventListener('keydown', (event) => {
            if (event.key !== 'ArrowDown' || panel.hidden) return;
            event.preventDefault();
            panel.querySelector('.diary-book-menu-item')?.focus();
        });

        toggle(false);
        return menu;

        function toggle(open) {
            panel.hidden = !open;
            root.classList.toggle('is-open', open);
            button.setAttribute('aria-expanded', open ? 'true' : 'false');
        }

        function closeOthers() {
            menus.forEach(other => {
                if (other.root !== root) other.close();
            });
        }
    }
});
