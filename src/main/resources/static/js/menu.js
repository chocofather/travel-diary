function alignSubmenus() {
    const menuItems = document.querySelectorAll('.main-menu > li');
    const submenuColumns = document.querySelectorAll('.submenu-column');
    const nav = document.querySelector('.main-nav'); // ✅ 기준: relative를 가진 부모
    const navLeft = nav.getBoundingClientRect().left;

    menuItems.forEach((item, index) => {
        const itemLeft = item.getBoundingClientRect().left;
        const relativeLeft = itemLeft - navLeft;

        const submenu = document.querySelector(`.submenu-column[data-index="${index}"]`);
        if (submenu) {
            submenu.style.left = `${relativeLeft - 27}px`;
        }
    });
}

window.addEventListener('DOMContentLoaded', alignSubmenus);
window.addEventListener('resize', alignSubmenus);
