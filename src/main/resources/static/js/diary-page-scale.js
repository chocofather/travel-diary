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
        renderNoteLines(frame, sheet, pageScale);
    }

    function renderNoteLines(frame, sheet, pageScale) {
        if (!sheet.classList.contains('diary-sheet-bg-lined')
            || !sheet.closest('.is-read-mode')) return;

        const layer = sheet.querySelector('.diary-writing-layer');
        const editor = layer?.querySelector('.diary-editor');
        if (!editor) return;

        const lineHeight = Number.parseFloat(getComputedStyle(editor).lineHeight);
        const lineCount = Number.parseInt(
            getComputedStyle(sheet).getPropertyValue('--diary-lines'), 10);
        if (!Number.isFinite(lineHeight) || !Number.isFinite(lineCount)) return;

        const pixelRatio = window.devicePixelRatio || 1;
        const pixel = 1 / pixelRatio;
        const frameRect = frame.getBoundingClientRect();
        const layerRect = layer.getBoundingClientRect();
        let paper = frame.querySelector(':scope > .diary-note-paper');
        if (!paper) {
            paper = document.createElement('div');
            paper.className = 'diary-note-paper';
            paper.setAttribute('aria-hidden', 'true');
            frame.prepend(paper);
        }
        const sheetStyle = getComputedStyle(sheet);
        paper.style.setProperty('--diary-paper-color',
            sheetStyle.getPropertyValue('--diary-paper-color'));
        paper.style.borderRadius = sheetStyle.borderRadius;
        paper.style.transform = `scale(${pageScale})`;

        let lines = frame.querySelector(':scope > .diary-note-lines');
        if (!lines) {
            lines = document.createElement('div');
            lines.className = 'diary-note-lines';
            lines.setAttribute('aria-hidden', 'true');
            frame.append(lines);
        }

        lines.style.top = `${layerRect.top - frameRect.top}px`;
        lines.style.left = `${layerRect.left - frameRect.left}px`;
        lines.style.width = `${layerRect.width}px`;
        lines.style.setProperty('--diary-note-pixel', `${pixel}px`);

        const rows = document.createDocumentFragment();
        for (let index = 1; index <= lineCount; index++) {
            const row = document.createElement('div');
            row.className = 'diary-note-line';
            const screenY = layerRect.top + index * lineHeight * pageScale;
            const snappedTop = Math.round((screenY - pixel) * pixelRatio) / pixelRatio;
            row.style.top = `${snappedTop - layerRect.top}px`;
            rows.append(row);
        }
        lines.replaceChildren(rows);
        frame.classList.add('has-screen-note-lines');
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
