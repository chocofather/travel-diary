document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('course-form');
    if (!form) return;

    window.initQuillEditor(
        '#editor',
        'content-input',
        'course-form',
        form.dataset.initialContentId || undefined
    );
    const searchInput = document.getElementById('destination-search-input');
    const searchButton = document.getElementById('destination-search-button');
    const searchResults = document.getElementById('destination-search-results');
    const selectedList = document.getElementById('selected-destinations');
    const hiddenInputs = document.getElementById('destination-id-inputs');
    const message = document.getElementById('course-write-message');
    const submitButton = document.getElementById('course-submit-button');
    const countryIdInput = document.getElementById('course-country-id');
    const countryHelper = document.getElementById('course-country-helper');
    const scopeButtons = Array.from(document.querySelectorAll('[data-country-scope]'));
    const overseasCountryField = document.getElementById('overseas-country-field');
    const overseasCountryInput = document.getElementById('overseas-country-input');
    const countryListbox = document.getElementById('overseas-country-listbox');
    const countryOptions = Array.from(document.querySelectorAll('[data-country-id][role="option"]'));
    const countryOptionEmpty = document.getElementById('country-option-empty');
    const useCountrySelection = form.dataset.countryRestriction === 'true' && countryIdInput !== null;

    const selectedDestinations = Array.from(document.querySelectorAll('[data-initial-destination]'))
        .map(element => ({
            id: Number(element.dataset.destinationId),
            name: element.dataset.name,
            regionName: element.dataset.regionName,
            parentRegionName: element.dataset.parentRegionName,
            thumbnailUrl: element.dataset.thumbnailUrl
        }))
        .filter(destination => Number.isSafeInteger(destination.id) && destination.id > 0);
    let latestResults = [];
    let debounceTimer;
    let activeSearchController;
    let searchRequestNumber = 0;
    let activeCountryScope = '';
    let activeCountryId = countryIdInput?.value || '';
    let activeCountryName = '';
    let highlightedCountryIndex = -1;

    function countryIdOf(destination) {
        const countryId = Number(destination?.countryId);
        return Number.isSafeInteger(countryId) && countryId > 0 ? countryId : null;
    }

    function selectedCourseCountryId() {
        const countryId = Number(countryIdInput?.value);
        return Number.isSafeInteger(countryId) && countryId > 0 ? countryId : null;
    }

    function updateCountrySearchState() {
        if (!useCountrySelection) return;
        const countrySelected = selectedCourseCountryId() !== null;
        searchInput.disabled = !countrySelected;
        searchButton.disabled = !countrySelected;
        countryHelper.hidden = countrySelected;
        if (!countrySelected) {
            activeSearchController?.abort();
            latestResults = [];
            searchResults.replaceChildren();
        }
    }

    function visibleCountryOptions() {
        return countryOptions.filter(option => !option.hidden);
    }

    function closeCountryList() {
        if (!countryListbox || !overseasCountryInput) return;
        countryListbox.hidden = true;
        overseasCountryInput.setAttribute('aria-expanded', 'false');
        overseasCountryInput.removeAttribute('aria-activedescendant');
        countryOptions.forEach(option => option.classList.remove('is-highlighted'));
        highlightedCountryIndex = -1;
    }

    function openCountryList() {
        if (!countryListbox || !overseasCountryInput || overseasCountryField?.hidden) return;
        countryListbox.hidden = false;
        overseasCountryInput.setAttribute('aria-expanded', 'true');
    }

    function extractHangulInitials(value) {
        const initials = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
        return Array.from(String(value ?? ''), character => {
            const codePoint = character.codePointAt(0);
            if (codePoint < 0xAC00 || codePoint > 0xD7A3) return character;
            return initials[Math.floor((codePoint - 0xAC00) / 588)];
        }).join('');
    }

    function filterCountryOptions() {
        const keyword = (overseasCountryInput?.value || '').trim().toLocaleLowerCase('ko-KR');
        countryOptions.forEach(option => {
            const countryName = option.dataset.countryName || option.textContent || '';
            const normalizedName = countryName.toLocaleLowerCase('ko-KR');
            const countryInitials = extractHangulInitials(normalizedName);
            option.hidden = keyword !== ''
                && !normalizedName.includes(keyword)
                && !countryInitials.includes(keyword);
        });
        if (countryOptionEmpty) {
            countryOptionEmpty.hidden = visibleCountryOptions().length > 0;
        }
        highlightedCountryIndex = -1;
        openCountryList();
    }

    function highlightCountryOption(index) {
        const visibleOptions = visibleCountryOptions();
        if (visibleOptions.length === 0) return;
        highlightedCountryIndex = (index + visibleOptions.length) % visibleOptions.length;
        countryOptions.forEach(option => option.classList.remove('is-highlighted'));
        const highlightedOption = visibleOptions[highlightedCountryIndex];
        highlightedOption.classList.add('is-highlighted');
        overseasCountryInput.setAttribute('aria-activedescendant', highlightedOption.id);
        highlightedOption.scrollIntoView({block: 'nearest'});
    }

    function applyCountrySelection(scope, countryId, countryName) {
        activeCountryScope = scope;
        activeCountryId = countryId ? String(countryId) : '';
        activeCountryName = countryName || '';
        countryIdInput.value = activeCountryId;

        scopeButtons.forEach(button => {
            const active = button.dataset.countryScope === scope;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', String(active));
        });

        if (overseasCountryField) {
            overseasCountryField.hidden = scope !== 'overseas';
        }
        if (overseasCountryInput) {
            overseasCountryInput.value = scope === 'overseas' ? activeCountryName : '';
        }
        countryOptions.forEach(option => {
            option.setAttribute('aria-selected', String(option.dataset.countryId === activeCountryId));
            option.hidden = false;
        });
        if (countryOptionEmpty) countryOptionEmpty.hidden = true;
        closeCountryList();

        latestResults = [];
        searchResults.replaceChildren();
        updateCountrySearchState();
        if (selectedCourseCountryId() !== null) {
            searchDestinations();
        } else {
            showMessage('해외 국가를 선택해 주세요.');
        }
    }

    function requestCountryChange(scope, countryId, countryName) {
        const nextCountryId = countryId ? String(countryId) : '';
        if (activeCountryScope === scope && activeCountryId === nextCountryId) return true;

        if (selectedDestinations.length > 0) {
            const confirmed = window.confirm(
                '국가를 변경하면 현재 선택한 여행지가 모두 제거됩니다. 변경하시겠습니까?'
            );
            if (!confirmed) return false;
            selectedDestinations.splice(0, selectedDestinations.length);
            renderSelectedDestinations();
        }

        applyCountrySelection(scope, nextCountryId, countryName);
        return true;
    }

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

        if (useCountrySelection) {
            const selectedCountryId = selectedCourseCountryId();
            if (selectedCountryId === null) {
                showMessage('코스 국가를 먼저 선택해 주세요.', true);
                (activeCountryScope === 'overseas' ? overseasCountryInput : scopeButtons[0])?.focus();
                return;
            }
            const destinationCountryId = countryIdOf(destination);
            if (destinationCountryId === null) {
                showMessage('여행지의 국가 정보를 확인할 수 없습니다.', true);
                return;
            }
            if (selectedCountryId !== destinationCountryId) {
                showMessage('선택한 국가와 여행지의 국가가 일치하지 않습니다.', true);
                return;
            }
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
        const countryId = selectedCourseCountryId();
        if (useCountrySelection && countryId === null) {
            updateCountrySearchState();
            showMessage('코스 국가를 먼저 선택해 주세요.', true);
            return;
        }

        const requestNumber = ++searchRequestNumber;
        activeSearchController?.abort();
        activeSearchController = new AbortController();
        showMessage('여행지를 검색하고 있습니다.');

        try {
            const params = new URLSearchParams({
                q: searchInput.value.trim(),
                size: '20'
            });
            if (useCountrySelection) {
                params.set('countryId', String(countryId));
            }
            const response = await fetch(
                `/api/search?${params.toString()}`,
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

    scopeButtons.forEach(button => {
        button.addEventListener('click', () => {
            const scope = button.dataset.countryScope;
            if (scope === 'domestic') {
                requestCountryChange(scope, button.dataset.countryId, button.dataset.countryName);
                return;
            }
            if (scope === 'overseas' && activeCountryScope === 'overseas') {
                overseasCountryInput?.focus();
                filterCountryOptions();
                return;
            }
            if (scope === 'overseas' && requestCountryChange(scope, '', '')) {
                overseasCountryInput?.focus();
                filterCountryOptions();
            }
        });
    });

    countryOptions.forEach(option => {
        option.addEventListener('click', () => {
            if (!requestCountryChange('overseas', option.dataset.countryId, option.dataset.countryName)) {
                overseasCountryInput.value = activeCountryName;
                closeCountryList();
            }
        });
    });

    overseasCountryInput?.addEventListener('focus', filterCountryOptions);
    overseasCountryInput?.addEventListener('click', filterCountryOptions);
    overseasCountryInput?.addEventListener('input', () => {
        filterCountryOptions();
    });
    overseasCountryInput?.addEventListener('keydown', event => {
        const options = visibleCountryOptions();
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            openCountryList();
            highlightCountryOption(highlightedCountryIndex + 1);
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            openCountryList();
            highlightCountryOption(highlightedCountryIndex - 1);
        } else if (event.key === 'Enter' && !countryListbox.hidden) {
            event.preventDefault();
            const option = options[highlightedCountryIndex] || (options.length === 1 ? options[0] : null);
            option?.click();
        } else if (event.key === 'Escape') {
            event.preventDefault();
            closeCountryList();
            overseasCountryInput.value = activeCountryName;
            countryIdInput.value = activeCountryId;
            updateCountrySearchState();
        }
    });

    document.addEventListener('click', event => {
        if (!event.target.closest('.country-combobox')) {
            closeCountryList();
            if (activeCountryId && activeCountryScope === 'overseas') {
                overseasCountryInput.value = activeCountryName;
            }
        }
    });

    form.addEventListener('submit', event => {
        submitButton.disabled = false;
        if (event.defaultPrevented) return;

        if (useCountrySelection && selectedCourseCountryId() === null) {
            event.preventDefault();
            showMessage('코스 국가를 선택해 주세요.', true);
            (activeCountryScope === 'overseas' ? overseasCountryInput : scopeButtons[0])?.focus();
            return;
        }

        if (selectedDestinations.length === 0) {
            event.preventDefault();
            showMessage('여행지를 한 곳 이상 선택해 주세요.', true);
            return;
        }

        submitButton.disabled = true;
    });

    window.addEventListener('pageshow', () => {
        submitButton.disabled = false;
    });

    renderSelectedDestinations();
    updateCountrySearchState();
    if (!useCountrySelection || selectedCourseCountryId() !== null) {
        searchDestinations();
    } else {
        showMessage('코스 국가를 먼저 선택해 주세요.');
    }
});
