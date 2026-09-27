// FAQ 카테고리 필터가 가로로 넘치는 화면(모바일)에서 선택한 카테고리가 보이도록 가로 위치만 맞춘다.
// 세로 스크롤은 건드리지 않는다.
document.addEventListener('DOMContentLoaded', () => {
    const list = document.querySelector('.support-faq-filter-list');
    const active = list?.querySelector('.support-faq-filter-link.is-active');
    if (!list || !active || list.scrollWidth <= list.clientWidth) return;
    const item = active.parentElement;
    list.scrollLeft = item.offsetLeft - (list.clientWidth - item.offsetWidth) / 2;
});
