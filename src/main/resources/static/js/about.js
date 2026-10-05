// 소개 페이지 내부 탐색만 부드럽게 이동한다. 전역 스크롤 설정은 바꾸지 않는다.
document.querySelectorAll('.about-jump a[href^="#"]').forEach((link) => {
    link.addEventListener('click', (event) => {
        const target = document.getElementById(link.hash.slice(1));
        if (!target || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        history.pushState(null, '', link.hash);
        target.setAttribute('tabindex', '-1');
        target.focus({ preventScroll: true });
        target.scrollIntoView({
            behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
            block: 'start'
        });
    });
});
