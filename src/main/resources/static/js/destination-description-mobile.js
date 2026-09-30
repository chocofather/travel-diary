/* 원문과 문단은 그대로 두고 스마트폰에서 긴 소개의 표시 높이만 제한한다. */
(function () {
    const description = document.getElementById('destination-description');
    const toggle = document.getElementById('destination-description-toggle');
    if (!description || !toggle) return;
    const mobile = window.matchMedia('(max-width: 600px)');

    function refresh() {
        const expanded = toggle.getAttribute('aria-expanded') === 'true';
        // 문단 간격까지 포함한 실제 6줄 클램프 높이와 원문 높이를 비교한다.
        description.classList.toggle('is-collapsible', mobile.matches);
        description.classList.remove('is-expanded');
        const long = mobile.matches && description.scrollHeight > description.clientHeight + 1;
        description.classList.toggle('is-collapsible', long);
        description.classList.toggle('is-expanded', long && expanded);
        toggle.hidden = !long;
        if (!long) {
            description.classList.remove('is-expanded');
            toggle.setAttribute('aria-expanded', 'false');
        }
        toggle.textContent = toggle.getAttribute(toggle.getAttribute('aria-expanded') === 'true'
            ? 'data-collapse-label' : 'data-expand-label');
    }

    toggle.addEventListener('click', () => {
        const expanded = toggle.getAttribute('aria-expanded') !== 'true';
        description.classList.toggle('is-expanded', expanded);
        toggle.setAttribute('aria-expanded', String(expanded));
        refresh();
        if (!expanded && description.getBoundingClientRect().top < 0) description.scrollIntoView({block: 'start'});
    });
    window.addEventListener('resize', refresh);
    if (typeof ResizeObserver !== 'undefined') new ResizeObserver(refresh).observe(description);
    document.fonts?.ready.then(refresh);
    refresh();
})();
