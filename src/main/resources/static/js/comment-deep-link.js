(function exposeCommentDeepLink(global) {
    const highlightClass = 'is-deep-link-target';
    const highlightDurationMs = 2000;

    function readTargetCommentId() {
        const raw = new URLSearchParams(global.location.search).get('commentId');
        if (!raw || !/^\d+$/.test(raw)) return null;
        const commentId = Number(raw);
        return Number.isSafeInteger(commentId) && commentId > 0 ? commentId : null;
    }

    function scrollBehavior() {
        return global.matchMedia?.('(prefers-reduced-motion: reduce)').matches
            ? 'auto'
            : 'smooth';
    }

    function scrollToSection(section) {
        section?.scrollIntoView({behavior: scrollBehavior(), block: 'start'});
    }

    function focusTarget(container, dataAttribute, commentId) {
        if (!container || !Number.isSafeInteger(commentId)) return false;
        const target = container.querySelector(`[${dataAttribute}="${commentId}"]`);
        if (!target) return false;

        global.requestAnimationFrame(() => {
            target.scrollIntoView({behavior: scrollBehavior(), block: 'center'});
            target.classList.add(highlightClass);
            global.setTimeout(() => target.classList.remove(highlightClass), highlightDurationMs);
        });
        return true;
    }

    global.TravelDiaryCommentDeepLink = Object.freeze({
        readTargetCommentId,
        scrollToSection,
        focusTarget
    });
})(window);
