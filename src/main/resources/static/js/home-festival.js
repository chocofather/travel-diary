(() => {
    const rail = document.getElementById('home-festival-list');
    if (!rail) return;
    const buttons = document.querySelectorAll('[data-festival-direction]');
    const update = () => {
        const end = rail.scrollWidth - rail.clientWidth;
        buttons.forEach(button => {
            button.disabled = Number(button.dataset.festivalDirection) < 0
                ? rail.scrollLeft <= 1 : rail.scrollLeft >= end - 1;
        });
    };
    buttons.forEach(button => button.addEventListener('click', () => {
        const gap = parseFloat(getComputedStyle(rail).gap) || 0;
        rail.scrollBy({
            left: Number(button.dataset.festivalDirection) * (rail.clientWidth + gap),
            behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
        });
    }));
    rail.addEventListener('scroll', update, {passive: true});
    window.addEventListener('resize', update, {passive: true});
    update();
})();
