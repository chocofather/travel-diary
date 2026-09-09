document.addEventListener('DOMContentLoaded', () => {
    const article = document.querySelector(
        '[data-title-content-translation][data-translation-url]');
    const button = article?.querySelector('[data-translation-toggle]');
    const title = article?.querySelector('[data-translation-title]');
    const content = article?.querySelector('[data-translation-content]');
    const status = article?.querySelector('[data-translation-status]');
    if (!article || !button || !title || !content || !status) return;

    const messages = {
        show: article.dataset.translationShow || '번역 보기',
        original: article.dataset.translationOriginal || '원문 보기',
        loading: article.dataset.translationLoading || '번역 중…',
        retry: article.dataset.translationRetry || '번역이 처리 중입니다. 잠시 후 다시 시도해주세요.',
        rateLimited: article.dataset.translationRateLimited || '번역 요청이 많습니다. 잠시 후 다시 시도해주세요.',
        failed: article.dataset.translationFailed || '지금은 번역을 사용할 수 없습니다.'
    };
    const originalTitle = title.textContent;
    const originalContentNodes = Array.from(content.childNodes, node => node.cloneNode(true));
    button.dataset.translationState = 'original';

    function restoreOriginal() {
        title.textContent = originalTitle;
        content.replaceChildren(...originalContentNodes.map(node => node.cloneNode(true)));
        button.dataset.translationState = 'original';
        button.textContent = messages.show;
        status.hidden = true;
        status.textContent = '';
    }

    function replaceWithSanitizedHtml(html) {
        const parsed = new DOMParser().parseFromString(html, 'text/html');
        const nodes = Array.from(parsed.body.childNodes,
            node => document.importNode(node, true));
        content.replaceChildren(...nodes);
    }

    async function requestTranslation(attempt) {
        try {
            const response = await fetch(article.dataset.translationUrl, {
                method: 'GET',
                cache: 'no-store',
                headers: {'Accept': 'application/json'}
            });
            if (!response.ok) {
                const error = new Error(messages.failed);
                error.status = response.status;
                throw error;
            }
            const result = await response.json();
            if (result.status === 'PROCESSING') {
                if (attempt >= 3) {
                    button.disabled = false;
                    button.textContent = messages.show;
                    status.textContent = messages.retry;
                    status.hidden = false;
                    return;
                }
                const waitSeconds = Math.max(1, Number(result.retryAfterSeconds || 1));
                window.setTimeout(() => requestTranslation(attempt + 1),
                    Math.min(waitSeconds, 30) * 1000);
                return;
            }

            title.textContent = result.translatedTitle == null
                ? originalTitle : result.translatedTitle;
            if (result.translatedContent == null) {
                content.replaceChildren(...originalContentNodes.map(node => node.cloneNode(true)));
            } else {
                replaceWithSanitizedHtml(result.translatedContent);
            }
            button.dataset.translationState = 'translated';
            button.textContent = messages.original;
            button.disabled = false;
            status.hidden = true;
            status.textContent = '';
        } catch (error) {
            button.disabled = false;
            button.textContent = messages.show;
            status.textContent = error.status === 429
                ? messages.rateLimited : messages.failed;
            status.hidden = false;
        }
    }

    button.addEventListener('click', () => {
        if (button.dataset.translationState === 'translated') {
            restoreOriginal();
            return;
        }
        button.disabled = true;
        button.textContent = messages.loading;
        status.hidden = true;
        void requestTranslation(0);
    });
});
