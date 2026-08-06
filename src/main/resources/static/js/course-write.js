document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('course-form');
    if (!form) return;

    const editor = window.initToastEditor('#editor', 'content-input', 'course-form');
    const contentInput = document.getElementById('content-input');
    const searchInput = document.getElementById('destination-search-input');
    const searchButton = document.getElementById('destination-search-button');
    const searchResults = document.getElementById('destination-search-results');
    const selectedList = document.getElementById('selected-destinations');
    const hiddenInputs = document.getElementById('destination-id-inputs');
    const message = document.getElementById('course-write-message');
    const submitButton = document.getElementById('course-submit-button');

    const selectedDestinations = [];
    let latestResults = [];
    let debounceTimer;
    let activeSearchController;
    let searchRequestNumber = 0;

    function showMessage(text, isError = false) {
        message.textContent = text;
        message.classList.toggle('is-error', isError);
    }

    function createImage(url, alt) {
        const image = document.createElement('img');
        image.src = url || '/images/default.png';
        image.alt = alt;
        image.loading = 'lazy';
        image.addEventListener('error', () => {
            if (!image.src.endsWith('/images/default.png')) {
                image.src = '/images/default.png';
            }
        });
        return image;
    }

    function regionText(destination) {
        return [destination.parentRegionName, destination.regionName]
            .filter(value => value && value.trim())
            .join(' ');
    }

    function renderSearchResults(results) {
        searchResults.replaceChildren();
        if (results.length === 0) {
            const empty = document.createElement('p');
            empty.className = 'picker-empty';
            empty.textContent = '검색 결과가 없습니다.';
            searchResults.append(empty);
            return;
        }

        const selectedIds = new Set(selectedDestinations.map(destination => Number(destination.id)));
        results.forEach(destination => {
            const card = document.createElement('article');
            card.className = 'destination-result-card';

            const image = createImage(destination.thumbnailUrl, `${destination.name || '여행지'} 썸네일`);
            image.className = 'destination-card-image';

            const info = document.createElement('div');
            info.className = 'destination-card-info';
            const name = document.createElement('h3');
            name.textContent = destination.name || '이름 없는 여행지';
            info.append(name);

            const region = regionText(destination);
            if (region) {
                const regionElement = document.createElement('p');
                regionElement.className = 'destination-card-region';
                regionElement.textContent = `지역 ${region}`;
                info.append(regionElement);
            }
            if (destination.shortDescription && destination.shortDescription.trim()) {
                const description = document.createElement('p');
                description.className = 'destination-card-description';
                description.textContent = destination.shortDescription;
                info.append(description);
            }

            const addButton = document.createElement('button');
            addButton.type = 'button';
            addButton.className = 'destination-add-button';
            addButton.textContent = selectedIds.has(Number(destination.id)) ? '추가됨' : '추가';
            addButton.disabled = selectedIds.has(Number(destination.id));
            addButton.addEventListener('click', () => addDestination(destination));

            card.append(image, info, addButton);
            searchResults.append(card);
        });
    }

    function renderSelectedDestinations() {
        selectedList.replaceChildren();
        hiddenInputs.replaceChildren();

        if (selectedDestinations.length === 0) {
            const empty = document.createElement('li');
            empty.className = 'picker-empty selected-empty';
            empty.textContent = '선택한 여행지가 없습니다.';
            selectedList.append(empty);
        }

        selectedDestinations.forEach((destination, index) => {
            const item = document.createElement('li');
            item.className = 'selected-destination-card';

            const order = document.createElement('span');
            order.className = 'selected-order';
            order.textContent = String(index + 1);

            const image = createImage(destination.thumbnailUrl, `${destination.name || '여행지'} 썸네일`);
            image.className = 'selected-card-image';

            const info = document.createElement('div');
            info.className = 'selected-card-info';
            const name = document.createElement('strong');
            name.textContent = destination.name || '이름 없는 여행지';
            const region = document.createElement('span');
            region.textContent = regionText(destination) || '지역 정보 없음';
            info.append(name, region);

            const controls = document.createElement('div');
            controls.className = 'selected-card-controls';
            controls.append(
                createControlButton('위로', index === 0, () => moveDestination(index, -1)),
                createControlButton('아래로', index === selectedDestinations.length - 1,
                    () => moveDestination(index, 1)),
                createControlButton('제거', false, () => removeDestination(index), 'remove')
            );

            item.append(order, image, info, controls);
            selectedList.append(item);

            const hidden = document.createElement('input');
            hidden.type = 'hidden';
            hidden.name = 'destinationIds';
            hidden.value = String(destination.id);
            hiddenInputs.append(hidden);
        });

        renderSearchResults(latestResults);
    }

    function createControlButton(label, disabled, action, extraClass = '') {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = label;
        button.disabled = disabled;
        button.className = `order-control ${extraClass}`.trim();
        button.addEventListener('click', action);
        return button;
    }

    function addDestination(destination) {
        if (selectedDestinations.some(selected => Number(selected.id) === Number(destination.id))) {
            showMessage('이미 선택한 여행지입니다.', true);
            return;
        }
        selectedDestinations.push(destination);
        showMessage(`${destination.name || '여행지'}을(를) 코스에 추가했습니다.`);
        renderSelectedDestinations();
    }

    function moveDestination(index, offset) {
        const targetIndex = index + offset;
        if (targetIndex < 0 || targetIndex >= selectedDestinations.length) return;
        [selectedDestinations[index], selectedDestinations[targetIndex]] =
            [selectedDestinations[targetIndex], selectedDestinations[index]];
        renderSelectedDestinations();
    }

    function removeDestination(index) {
        const [removed] = selectedDestinations.splice(index, 1);
        showMessage(`${removed.name || '여행지'}을(를) 코스에서 제거했습니다.`);
        renderSelectedDestinations();
    }

    async function searchDestinations() {
        const requestNumber = ++searchRequestNumber;
        activeSearchController?.abort();
        activeSearchController = new AbortController();
        showMessage('여행지를 검색하고 있습니다.');

        try {
            const response = await fetch(
                `/api/search?q=${encodeURIComponent(searchInput.value.trim())}&size=20`,
                {signal: activeSearchController.signal, headers: {'Accept': 'application/json'}}
            );
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const results = await response.json();
            if (requestNumber !== searchRequestNumber) return;
            latestResults = Array.isArray(results) ? results : [];
            renderSearchResults(latestResults);
            showMessage(`${latestResults.length}개의 여행지를 찾았습니다.`);
        } catch (error) {
            if (error.name === 'AbortError') return;
            if (requestNumber !== searchRequestNumber) return;
            latestResults = [];
            renderSearchResults(latestResults);
            showMessage('여행지 검색 중 오류가 발생했습니다.', true);
        }
    }

    searchInput.addEventListener('input', () => {
        window.clearTimeout(debounceTimer);
        debounceTimer = window.setTimeout(searchDestinations, 250);
    });
    searchInput.addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            event.preventDefault();
            window.clearTimeout(debounceTimer);
            searchDestinations();
        }
    });
    searchButton.addEventListener('click', () => {
        window.clearTimeout(debounceTimer);
        searchDestinations();
    });

    form.addEventListener('submit', event => {
        submitButton.disabled = false;
        if (event.defaultPrevented) return;

        if (selectedDestinations.length === 0) {
            event.preventDefault();
            showMessage('여행지를 한 곳 이상 선택해 주세요.', true);
            return;
        }

        const markdown = editor?.getMarkdown().trim() || '';
        if (!markdown) {
            event.preventDefault();
            showMessage('코스 소개를 입력해 주세요.', true);
            editor?.focus();
            return;
        }
        contentInput.value = editor.getHTML();
        submitButton.disabled = true;
    });

    window.addEventListener('pageshow', () => {
        submitButton.disabled = false;
    });

    renderSelectedDestinations();
    searchDestinations();
});
