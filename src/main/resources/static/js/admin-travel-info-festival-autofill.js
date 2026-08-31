document.addEventListener('DOMContentLoaded', () => {
    const FESTIVAL_TYPE = 'FESTIVAL';
    const panel = document.querySelector('[data-kto-festival-autofill]');
    if (!panel) return;

    const contentType = document.getElementById('travel-info-content-type');
    const scope = document.getElementById('travel-info-scope');
    const title = document.getElementById('travel-info-title');
    const category = document.getElementById('travel-info-category');
    const startDate = panel.querySelector('[data-kto-festival-search-start-date]');
    const endDate = panel.querySelector('[data-kto-festival-search-end-date]');
    const searchButton = panel.querySelector('[data-kto-festival-search-button]');
    const status = panel.querySelector('[data-kto-festival-status]');
    const results = panel.querySelector('[data-kto-festival-results]');
    const editorElement = document.getElementById('travel-info-editor');
    const periodList = document.getElementById('travel-info-period-list');
    const addPeriodButton = document.getElementById('add-travel-info-period');

    if (!contentType || !scope || !title || !category || !startDate || !endDate
        || !searchButton || !status || !results || !editorElement || !periodList) return;

    const managedValues = new Map();
    let lastSelectedContentId = null;
    let searchRequestGeneration = 0;
    let detailRequestGeneration = 0;

    contentType.addEventListener('change', syncPanelVisibility);
    searchButton.addEventListener('click', searchFestivals);
    syncPanelVisibility();

    function syncPanelVisibility() {
        panel.hidden = contentType.value !== FESTIVAL_TYPE;
        if (panel.hidden) {
            searchRequestGeneration += 1;
            detailRequestGeneration += 1;
            searchButton.disabled = false;
        }
    }

    async function searchFestivals() {
        const normalizedStartDate = startDate.value.trim();
        const normalizedEndDate = endDate.value.trim();
        results.replaceChildren();

        if (!normalizedStartDate) {
            setStatus('검색 시작일을 선택해 주세요.', true);
            startDate.focus();
            return;
        }
        if (normalizedEndDate && normalizedEndDate < normalizedStartDate) {
            setStatus('검색 종료일은 시작일보다 빠를 수 없습니다.', true);
            endDate.focus();
            return;
        }

        const requestGeneration = ++searchRequestGeneration;
        setSearchLoading(true, 'TourAPI에서 축제·행사를 검색하고 있습니다.');
        try {
            const params = new URLSearchParams({
                eventStartDate: normalizedStartDate,
                pageNo: '1',
                numOfRows: '10'
            });
            if (normalizedEndDate) params.set('eventEndDate', normalizedEndDate);

            const response = await fetch(`/admin/api/kto/festivals/search?${params.toString()}`, {
                headers: {Accept: 'application/json'}
            });
            const payload = await response.json();
            if (requestGeneration !== searchRequestGeneration) return;
            if (!response.ok) {
                throw new Error(payload.message || '축제·행사를 검색하지 못했습니다.');
            }
            renderCandidates(Array.isArray(payload.items) ? payload.items : []);
        } catch (error) {
            if (requestGeneration !== searchRequestGeneration) return;
            setStatus(error.message || '축제·행사를 검색하지 못했습니다.', true);
        } finally {
            if (requestGeneration === searchRequestGeneration) searchButton.disabled = false;
        }
    }

    function renderCandidates(items) {
        results.replaceChildren();
        if (!items.length) {
            setStatus('검색 결과가 없습니다.');
            return;
        }

        setStatus(`${items.length}개의 축제·행사 후보를 찾았습니다.`);
        items.forEach(item => results.append(createCandidate(item)));
    }

    function createCandidate(item) {
        const card = document.createElement('article');
        card.className = 'admin-kto-festival-candidate';

        const media = document.createElement('div');
        media.className = 'admin-kto-festival-candidate-media';
        const thumbnailUrl = item.firstImage || item.firstImage2;
        const safeThumbnailUrl = safeHttpUrl(thumbnailUrl);
        if (safeThumbnailUrl) {
            const image = document.createElement('img');
            image.src = safeThumbnailUrl;
            image.alt = '';
            image.loading = 'lazy';
            media.append(image);
        } else {
            const placeholder = document.createElement('span');
            placeholder.textContent = '이미지 없음';
            media.append(placeholder);
        }

        const body = document.createElement('div');
        body.className = 'admin-kto-festival-candidate-body';
        const candidateTitle = document.createElement('strong');
        candidateTitle.textContent = hasText(item.title) ? item.title : '제목 없음';
        body.append(candidateTitle);

        const period = formatPeriod(item.eventStartDate, item.eventEndDate);
        if (period) body.append(createMetaText(period));
        if (hasText(item.address)) body.append(createMetaText(item.address));
        if (hasText(item.categoryName)) {
            const categoryBadge = document.createElement('span');
            categoryBadge.className = 'admin-kto-festival-category-badge';
            categoryBadge.textContent = item.categoryName;
            body.append(categoryBadge);
        }

        const selectButton = document.createElement('button');
        selectButton.type = 'button';
        selectButton.className = 'admin-btn is-small is-primary';
        selectButton.textContent = '선택';
        selectButton.disabled = !hasText(item.contentId);
        selectButton.addEventListener('click', () => loadDetail(item));

        card.append(media, body, selectButton);
        return card;
    }

    async function loadDetail(item) {
        if (!hasText(item.contentId)) return;
        const requestGeneration = ++detailRequestGeneration;
        setStatus(`${item.title || '선택한 행사'} 상세 정보를 불러오고 있습니다.`);

        try {
            const params = new URLSearchParams({contentId: item.contentId});
            const response = await fetch(`/admin/api/kto/festivals/detail?${params.toString()}`, {
                headers: {Accept: 'application/json'}
            });
            const detail = await response.json();
            if (requestGeneration !== detailRequestGeneration
                || contentType.value !== FESTIVAL_TYPE) return;
            if (!response.ok) {
                throw new Error(detail.message || '축제·행사 상세 정보를 불러오지 못했습니다.');
            }

            const categoryResolved = applyAutofill(detail);
            const replacingPrevious = lastSelectedContentId !== null
                && lastSelectedContentId !== item.contentId;
            lastSelectedContentId = item.contentId;
            if (!categoryResolved && hasText(detail.categoryName)) {
                setStatus(`${detail.categoryName} 카테고리를 찾지 못했습니다. 카테고리를 직접 선택해 주세요.`, true);
                return;
            }
            setStatus(`${detail.title || item.title || '선택한 행사'} 정보를 ${replacingPrevious
                ? '새 후보 기준으로 갱신했습니다.'
                : '빈 항목에 입력했습니다.'}`);
        } catch (error) {
            if (requestGeneration !== detailRequestGeneration) return;
            setStatus(error.message || '축제·행사 상세 정보를 불러오지 못했습니다.', true);
        }
    }

    function applyAutofill(detail) {
        setManagedField('scope', scope, 'DOMESTIC');
        setManagedField('title', title, detail.title);
        setManagedPeriods(detail.eventStartDate, detail.eventEndDate);

        const categoryOption = findFestivalCategory(detail.categoryName);
        if (categoryOption) setManagedField('categoryId', category, categoryOption.value);

        const html = buildFestivalHtml(detail);
        setManagedEditor(html);
        return Boolean(categoryOption);
    }

    function setManagedField(fieldKey, element, nextValue) {
        if (!element || !hasText(nextValue)) return false;
        const normalizedValue = String(nextValue).trim();
        const currentValue = element.value.trim();
        const mayReplace = !currentValue || (managedValues.has(fieldKey)
            && currentValue === managedValues.get(fieldKey));
        if (!mayReplace) return false;

        element.value = normalizedValue;
        managedValues.set(fieldKey, element.value.trim());
        element.dispatchEvent(new Event('input', {bubbles: true}));
        element.dispatchEvent(new Event('change', {bubbles: true}));
        return true;
    }

    function setManagedPeriods(nextStartDate, nextEndDate) {
        let row = periodList.querySelector('[data-period-row]');
        if (!row && addPeriodButton) {
            addPeriodButton.click();
            row = periodList.querySelector('[data-period-row]');
        }
        if (!row) return;

        const inputs = row.querySelectorAll('input[type="date"]');
        setManagedField('periodStartDate', inputs[0], nextStartDate);
        setManagedField('periodEndDate', inputs[1], nextEndDate);
    }

    function findFestivalCategory(categoryName) {
        if (!hasText(categoryName)) return null;
        const expectedName = normalizeCategoryName(categoryName);
        return Array.from(category.options).find(option =>
            option.dataset.contentType === FESTIVAL_TYPE
            && normalizeCategoryName(option.textContent) === expectedName
        ) || null;
    }

    function normalizeCategoryName(value) {
        return String(value || '').trim().replace(/\s+\(숨김\)$/, '');
    }

    function setManagedEditor(html) {
        if (!hasText(html)) return false;
        const quill = editorElement.quillEditorInstance;
        if (!quill) return false;

        const currentHtml = quill.getSemanticHTML();
        const editorIsEmpty = !quill.getText().trim() && !quill.root.querySelector('img[src]');
        const mayReplace = editorIsEmpty || (managedValues.has('content')
            && currentHtml === managedValues.get('content'));
        if (!mayReplace) return false;

        const delta = quill.clipboard.convert({html, text: ''});
        quill.setContents(delta, 'silent');
        managedValues.set('content', quill.getSemanticHTML());
        return true;
    }

    function buildFestivalHtml(detail) {
        const container = document.createElement('div');

        if (hasText(detail.overview)) {
            appendHeading(container, '행사 소개');
            String(detail.overview).split(/\r?\n/).forEach(line => {
                if (!line.trim()) return;
                const paragraph = document.createElement('p');
                paragraph.textContent = line.trim();
                container.append(paragraph);
            });
        }

        const infoContainer = document.createElement('div');
        appendInfoRow(infoContainer, '행사장소', detail.eventPlace);
        appendInfoRow(infoContainer, '주소', detail.address);
        appendInfoRow(infoContainer, '행사시간', detail.playTime);
        appendInfoRow(infoContainer, '이용요금', detail.useTimeFestival);
        appendInfoRow(infoContainer, '주최', detail.sponsor1);
        appendInfoRow(infoContainer, '주관', detail.sponsor2);
        appendInfoRow(infoContainer, '문의', buildContactText(detail));
        appendHomepageRow(infoContainer, detail.eventHomepage || detail.homepage);

        if (infoContainer.childElementCount > 0) {
            appendHeading(container, '행사 정보');
            Array.from(infoContainer.children).forEach(child => container.append(child));
        }
        return container.innerHTML;
    }

    function appendHeading(container, text) {
        const heading = document.createElement('h2');
        heading.textContent = text;
        container.append(heading);
    }

    function appendInfoRow(container, label, value) {
        if (!hasText(value)) return;
        const paragraph = document.createElement('p');
        const labelElement = document.createElement('strong');
        labelElement.textContent = `${label}: `;
        const valueElement = document.createElement('span');
        valueElement.textContent = String(value).trim();
        paragraph.append(labelElement, valueElement);
        container.append(paragraph);
    }

    function appendHomepageRow(container, value) {
        const homepage = safeHttpUrl(value);
        if (!homepage) return;
        const paragraph = document.createElement('p');
        const labelElement = document.createElement('strong');
        labelElement.textContent = '홈페이지: ';
        const link = document.createElement('a');
        link.href = homepage;
        link.textContent = homepage;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        paragraph.append(labelElement, link);
        container.append(paragraph);
    }

    function buildContactText(detail) {
        const seenContacts = new Set();
        const contacts = [];

        addContact(detail.tel, '');
        addContact(detail.sponsor1Tel, hasText(detail.sponsor1) ? `${detail.sponsor1} 문의` : '주최 문의');
        addContact(detail.sponsor2Tel, hasText(detail.sponsor2) ? `${detail.sponsor2} 문의` : '주관 문의');
        return contacts.join(' · ');

        function addContact(value, label) {
            if (!hasText(value)) return;
            const normalizedValue = String(value).trim();
            const deduplicationKey = normalizedValue.replace(/\D/g, '') || normalizedValue.toLowerCase();
            if (seenContacts.has(deduplicationKey)) return;
            seenContacts.add(deduplicationKey);
            contacts.push(label ? `${label} ${normalizedValue}` : normalizedValue);
        }
    }

    function formatPeriod(first, second) {
        if (!hasText(first)) return hasText(second) ? second : '';
        if (!hasText(second) || first === second) return first;
        return `${first} ~ ${second}`;
    }

    function createMetaText(value) {
        const element = document.createElement('span');
        element.className = 'admin-kto-festival-candidate-meta';
        element.textContent = value;
        return element;
    }

    function safeHttpUrl(value) {
        if (!hasText(value)) return null;
        try {
            const url = new URL(String(value).trim());
            if ((url.protocol !== 'http:' && url.protocol !== 'https:') || url.username || url.password) {
                return null;
            }
            return url.toString();
        } catch {
            return null;
        }
    }

    function hasText(value) {
        return value !== null && value !== undefined && String(value).trim() !== '';
    }

    function setSearchLoading(loading, message) {
        searchButton.disabled = loading;
        setStatus(message);
    }

    function setStatus(message, isError = false) {
        status.textContent = message;
        status.classList.toggle('is-error', isError);
    }
});
