/**
 * 다이어리 한 장은 항상 같은 720px A5 캔버스로 배치하고, 바깥 viewport 크기에 맞춰
 * 종이 전체만 균일하게 축소한다. 본문·사진·스티커가 같은 transform을 공유하므로
 * 읽기 펼침이나 모바일에서도 내부 줄바꿈과 상대좌표는 다시 계산되지 않는다.
 */
(() => {
    const VIEWPORT_SELECTOR = '[data-diary-sheet-viewport]';
    const FALLBACK_BASE_WIDTH = 720;
    const observed = new WeakSet();

    function baseWidthOf(frame) {
        const page = frame.closest('.diary-detail-page');
        if (!page) return FALLBACK_BASE_WIDTH;
        return Number.parseFloat(
            getComputedStyle(page).getPropertyValue('--diary-page-base-width'))
            || FALLBACK_BASE_WIDTH;
    }

    function scale(frame) {
        const sheet = frame.querySelector(':scope > .diary-sheet');
        if (!sheet) return;

        const baseWidth = baseWidthOf(frame);
        const frameWidth = frame.getBoundingClientRect().width;
        if (baseWidth <= 0 || frameWidth <= 0) return;

        const pageScale = Math.min(1, frameWidth / baseWidth);
        sheet.style.setProperty('--diary-page-scale', pageScale.toFixed(6));
    }

    const resizeObserver = typeof ResizeObserver === 'function'
        ? new ResizeObserver(entries => entries.forEach(entry => scale(entry.target)))
        : null;

    function refresh(root = document) {
        const frames = [];
        if (root instanceof Element && root.matches(VIEWPORT_SELECTOR)) frames.push(root);
        root.querySelectorAll?.(VIEWPORT_SELECTOR).forEach(frame => frames.push(frame));

        frames.forEach(frame => {
            scale(frame);
            if (resizeObserver && !observed.has(frame)) {
                observed.add(frame);
                resizeObserver.observe(frame);
            }
        });
    }

    window.diaryPageScale = {refresh};

    document.addEventListener('DOMContentLoaded', () => {
        refresh();
        if (!resizeObserver) window.addEventListener('resize', () => refresh());
    });
})();
