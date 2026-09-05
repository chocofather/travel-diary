/**
 * 축제·행사 외국어 TourAPI 자동입력.
 *
 * <p>여행지 화면(/js/admin-kto-tour-autofill.js)과 같은 흐름을 쓴다 —
 * 언어마다 foreign-match 로 외국어 contentId 를 찾고, foreign-detail 로 값을 읽어
 * <b>빈 칸만</b> 채운다. 관리자가 이미 적어 둔 번역은 절대 덮어쓰지 않는다.
 *
 * <p>축제는 외국어 locationBasedList2 에 나오지 않아 서버가 국문 제목 키워드 검색으로 찾는다.
 * 그래서 좌표 대신 국문 KTO contentId 를 넘기고, 좌표 복구는 서버가 한다.
 * 국문 contentId 는 좌표를 되찾는 용도뿐이며 foreign-detail 에는 넘기지 않는다 —
 * 외국어 contentId 는 매칭 결과가 알려 준다.
 *
 * <p>본문은 /js/admin-translation-editors.js 가 만든 Quill 인스턴스에 직접 넣어
 * 화면과 제출값이 어긋나지 않게 한다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const panel = document.querySelector('[data-festival-foreign-autofill]');
    const status = panel?.querySelector('[data-festival-foreign-status]');
    const titleInput = document.getElementById('festival-title');
    if (!panel || !status || !titleInput) return;

    // 번역 탭과 같은 canonical 코드. 서버도 이 값만 받는다.
    const LANGUAGES = [
        {code: 'en', label: '영어'},
        {code: 'ja', label: '일본어'},
        {code: 'zh-CN', label: '간체'},
        {code: 'zh-TW', label: '번체'}
    ];
    // 외국어 상세 응답 필드 → 번역 탭 입력칸 표시자. 이름이 1:1 로 대응한다.
    const FESTIVAL_FIELDS = [
        {field: 'eventPlace', marker: 'data-translation-event-place'},
        {field: 'address', marker: 'data-translation-address'},
        {field: 'playTime', marker: 'data-translation-play-time'},
        {field: 'useTime', marker: 'data-translation-use-time'},
        {field: 'sponsor1', marker: 'data-translation-sponsor1'},
        {field: 'sponsor2', marker: 'data-translation-sponsor2'}
    ];

    let requestGeneration = 0;

    // 국문 TourAPI 축제를 고르면 바로 이어서 돈다. 버튼을 따로 누르지 않는다.
    // 수정 화면은 이 신호가 없으므로 저장된 번역을 그대로 두고 다시 부르지 않는다.
    document.addEventListener('festival:korean-autofill-applied', event => {
        const contentId = event.detail?.contentId;
        if (!contentId) return;
        fillForeignTranslations(contentId);
    });

    async function fillForeignTranslations(koreanContentId) {
        const koreanTitle = titleInput.value.trim();
        if (!koreanTitle || !koreanContentId) {
            // 국문 TourAPI 축제가 아니면(수기 등록) 외국어 자동입력 대상이 아니다.
            return;
        }

        const generation = ++requestGeneration;
        setStatus('외국어 축제·행사 정보를 불러오는 중...');
        // 한 언어가 실패해도 나머지 언어는 그대로 진행한다.
        const settled = await Promise.allSettled(LANGUAGES.map(
            language => fillLanguage(language, koreanTitle, koreanContentId, generation)));
        if (generation !== requestGeneration) return;

        const filled = [];
        const missing = [];
        const failed = [];
        settled.forEach((result, index) => {
            const label = LANGUAGES[index].label;
            const state = result.status === 'fulfilled' ? result.value : 'error';
            if (state === 'filled') filled.push(label);
            else if (state === 'none') missing.push(label);
            else failed.push(label);
        });
        setStatus(summarize(filled, missing, failed), filled.length === 0);
    }

    /** 한 언어의 매칭 + 상세 조회. 하나라도 채웠으면 filled 를 준다. */
    async function fillLanguage(language, koreanTitle, koreanContentId, generation) {
        const matchParams = new URLSearchParams({
            language: language.code,
            title: koreanTitle,
            festival: 'true',
            koreanContentId: koreanContentId
        });
        // 국문 검색에서 이미 좌표를 들고 있으면 서버의 좌표 재조회를 아낀다.
        const coordinates = resolveKoreanCoordinates();
        if (coordinates) {
            matchParams.set('mapX', coordinates.mapX);
            matchParams.set('mapY', coordinates.mapY);
        }

        const matched = await requestJson(`/admin/api/kto/tour/foreign-match?${matchParams}`);
        if (generation !== requestGeneration) return 'none';
        if (matched.status !== 'MATCHED' || !matched.matched || !matched.matched.contentId) {
            return 'none';
        }

        // 외국어 contentId·유형 코드는 매칭 결과 값을 그대로 쓴다. 국문 contentId 를 넘기지 않는다.
        const detailParams = new URLSearchParams({
            language: language.code,
            contentId: matched.matched.contentId
        });
        if (hasText(matched.matched.contentTypeId)) {
            detailParams.set('contentTypeId', matched.matched.contentTypeId);
        }
        const detail = await requestJson(`/admin/api/kto/tour/foreign-detail?${detailParams}`);
        if (generation !== requestGeneration) return 'none';

        const titleFilled = fillIfEmpty(
            findByLanguage('data-translation-title', language.code), detail.title);
        const contentFilled = fillEditorIfEmpty(language.code, detail.overview);
        const fieldsFilled = FESTIVAL_FIELDS
            .map(({field, marker}) =>
                fillIfEmpty(findByLanguage(marker, language.code), detail[field]))
            .some(Boolean);
        return titleFilled || contentFilled || fieldsFilled ? 'filled' : 'none';
    }

    /** 자리 번호가 아니라 언어 코드로 입력칸을 찾는다. */
    function findByLanguage(marker, languageCode) {
        return document.querySelector(`[${marker}="${languageCode}"]`);
    }

    /** 현재 값이 비어 있을 때만 채운다. API 값이 비어 있으면 아무 것도 하지 않는다. */
    function fillIfEmpty(element, value) {
        if (!element || element.value.trim()) return false;
        if (!hasText(value)) return false;
        element.value = String(value).trim();
        element.dispatchEvent(new Event('input', {bubbles: true}));
        element.dispatchEvent(new Event('change', {bubbles: true}));
        return true;
    }

    /**
     * 언어별 본문 편집기. 글도 이미지도 없을 때만 채운다.
     * Quill 에 직접 넣어 화면과 제출값이 함께 바뀌게 한다.
     */
    function fillEditorIfEmpty(languageCode, value) {
        if (!hasText(value)) return false;
        const editorElement = findByLanguage('data-translation-editor', languageCode);
        const quill = editorElement?.quillEditorInstance;
        if (!quill) return false;
        const editorIsEmpty = !quill.getText().trim() && !quill.root.querySelector('img[src]');
        if (!editorIsEmpty) return false;

        const container = document.createElement('div');
        String(value).split(/\r?\n/).forEach(line => {
            if (!line.trim()) return;
            const paragraph = document.createElement('p');
            paragraph.textContent = line.trim();
            container.append(paragraph);
        });
        if (!container.childElementCount) return false;
        const delta = quill.clipboard.convert({html: container.innerHTML, text: ''});
        quill.setContents(delta, 'silent');
        // 제출 직전에도 편집기가 hidden input 을 다시 채우지만, 지금 값도 맞춰 둔다.
        const contentInput = findByLanguage('data-translation-content', languageCode);
        if (contentInput) {
            contentInput.value = quill.getSemanticHTML();
        }
        return true;
    }

    /** 국문 검색 결과가 좌표를 남겨 두었을 때만 쓴다. 저장하지 않는다. */
    function resolveKoreanCoordinates() {
        const mapX = panel.dataset.festivalKtoMapX;
        const mapY = panel.dataset.festivalKtoMapY;
        return hasText(mapX) && hasText(mapY)
            ? {mapX: String(mapX).trim(), mapY: String(mapY).trim()}
            : null;
    }

    async function requestJson(url) {
        const response = await fetch(url, {headers: {Accept: 'application/json'}});
        if (!response.ok) {
            throw new Error(`request failed: ${response.status}`);
        }
        return response.json();
    }

    function summarize(filled, missing, failed) {
        const parts = [];
        if (filled.length) parts.push(`${filled.join('·')} 정보를 빈 항목에 입력했습니다.`);
        if (missing.length) parts.push(`${missing.join('·')}는 일치하는 축제·행사가 없습니다.`);
        if (failed.length) parts.push(`${failed.join('·')}는 불러오지 못했습니다.`);
        return parts.join(' ');
    }

    function setStatus(message, isError = false) {
        status.textContent = message;
        status.classList.toggle('is-error', isError);
    }

    function hasText(value) {
        return value !== null && value !== undefined && String(value).trim() !== '';
    }
});
