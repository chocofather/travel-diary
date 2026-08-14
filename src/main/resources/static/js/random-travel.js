(() => {
    const defaultImageUrl = '/images/default.png';
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const previewNames = {
        domestic: ['서울', '부산', '제주', '강원', '인천'],
        overseas: ['오사카', '파리', '방콕', '로마', '뉴욕']
    };

    const scopeButtons = Array.from(document.querySelectorAll('[data-random-scope]'));
    const drawButton = document.getElementById('random-draw-button');
    const status = document.getElementById('random-status');
    const stage = document.getElementById('random-stage');
    const stageName = document.getElementById('random-stage-name');
    const resultContainer = document.getElementById('random-result');

    if (!drawButton || !status || !stage || !stageName
            || !resultContainer || scopeButtons.length === 0) {
        return;
    }

    let selectedScope = 'domestic';
    let isDrawing = false;
    const previousRegionIds = {
        domestic: null,
        overseas: null
    };

    scopeButtons.forEach((button) => {
        button.addEventListener('click', () => {
            if (isDrawing) return;
            const nextScope = button.dataset.randomScope;
            if (!Object.hasOwn(previousRegionIds, nextScope)) return;

            selectedScope = nextScope;
            scopeButtons.forEach((scopeButton) => {
                const active = scopeButton === button;
                scopeButton.classList.toggle('is-active', active);
                scopeButton.setAttribute('aria-pressed', String(active));
            });
            resetPresentation();
        });
    });

    drawButton.addEventListener('click', drawTravelRegion);

    function buildRequestUrl() {
        const params = new URLSearchParams({scope: selectedScope});
        const previousRegionId = previousRegionIds[selectedScope];
        if (previousRegionId !== null) {
            params.set('excludeRegionId', String(previousRegionId));
        }
        return `/api/random-recommend?${params.toString()}`;
    }

    async function drawTravelRegion() {
        if (isDrawing) return;

        isDrawing = true;
        setControlsDisabled(true);
        prepareLoadingState();

        try {
            const response = await fetch(buildRequestUrl(), {
                method: 'GET',
                headers: {'Accept': 'application/json'}
            });
            if (response.status === 204) {
                showEmptyState();
                return;
            }
            if (!response.ok) {
                throw new Error('random recommendation failed');
            }

            const result = await response.json();
            const destinations = validateResult(result);
            await playDrawAnimation(result);
            renderResult(result, destinations);
            previousRegionIds[selectedScope] = result.regionId;
        } catch (error) {
            hideStage();
            showErrorState();
        } finally {
            isDrawing = false;
            setControlsDisabled(false);
        }
    }

    function prepareLoadingState() {
        status.textContent = '여행 지역을 고르고 있어요.';
        resultContainer.hidden = true;
        stage.hidden = true;
        stage.setAttribute('aria-hidden', 'true');
        stageName.textContent = '';
    }

    function playDrawAnimation(result) {
        if (reducedMotion.matches) {
            return Promise.resolve();
        }

        const duration = 1200;
        const interval = 150;
        const totalSteps = Math.floor(duration / interval) - 1;
        const names = [...previewNames[selectedScope], result.regionName];
        let step = 0;

        stage.hidden = false;
        stage.setAttribute('aria-hidden', 'true');
        stageName.textContent = names[0];

        return new Promise((resolve) => {
            const timer = window.setInterval(() => {
                step += 1;
                if (step >= totalSteps) {
                    window.clearInterval(timer);
                    stageName.textContent = result.regionName;
                    window.setTimeout(() => {
                        hideStage();
                        resolve();
                    }, interval);
                    return;
                }
                stageName.textContent = names[step % names.length];
            }, interval);
        });
    }

    function renderResult(result, destinations) {
        const fragment = document.createDocumentFragment();
        fragment.append(createRegionHero(result));

        const destinationSection = document.createElement('section');
        destinationSection.className = 'random-destinations';

        const heading = document.createElement('div');
        heading.className = 'random-destinations-heading';
        heading.append(
                createTextElement('h3', '', `${result.regionName}에서 둘러볼 여행지`),
                createTextElement('p', '', `${destinations.length}곳을 골라봤어요.`)
        );

        const grid = document.createElement('div');
        grid.className = 'random-destination-grid';
        destinations.forEach((destination) => {
            grid.append(createDestinationCard(destination));
        });

        const redrawButton = document.createElement('button');
        redrawButton.type = 'button';
        redrawButton.className = 'random-redraw-button';
        redrawButton.textContent = '다시 뽑기 ↻';
        redrawButton.addEventListener('click', drawTravelRegion);

        destinationSection.append(heading, grid, redrawButton);
        fragment.append(destinationSection);
        resultContainer.replaceChildren(fragment);
        resultContainer.hidden = false;
        status.textContent = `${result.countryName} · ${result.regionName} 지역을 골랐어요.`;
    }

    function createRegionHero(result) {
        const hero = document.createElement('section');
        hero.className = 'random-region-result';
        hero.append(
                createTextElement('p', 'random-region-kicker', '이번에 떠날 곳은'),
                createTextElement('p', 'random-region-country', result.countryName),
                createTextElement('h2', 'random-region-name', result.regionName),
                createTextElement(
                        'p',
                        'random-region-summary',
                        `Travel Diary가 ${result.regionName}에서 가볼 만한 여행지를 골랐어요.`
                )
        );
        return hero;
    }

    function createDestinationCard(destination) {
        const card = document.createElement('a');
        card.className = 'random-destination-card';
        card.href = destination.detailUrl;

        const imageWrap = document.createElement('div');
        imageWrap.className = 'random-destination-image-wrap';
        const image = document.createElement('img');
        image.className = 'random-destination-image';
        image.src = normalizedText(destination.imageUrl) || defaultImageUrl;
        image.alt = `${destination.destinationName} 대표 이미지`;
        image.addEventListener('error', () => {
            image.src = defaultImageUrl;
        }, {once: true});
        imageWrap.append(image);

        const content = document.createElement('div');
        content.className = 'random-destination-content';
        const regionName = normalizedText(destination.regionName);
        if (regionName) {
            content.append(createTextElement('p', 'random-destination-location', regionName));
        }
        content.append(createTextElement(
                'h4', 'random-destination-name', destination.destinationName));

        const description = normalizedText(destination.shortDescription);
        if (description) {
            content.append(createTextElement(
                    'p', 'random-destination-description', description));
        }
        content.append(createTextElement('span', 'random-destination-cta', '자세히 보기'));

        card.append(imageWrap, content);
        return card;
    }

    function validateResult(result) {
        if (!result
                || result.regionId == null
                || result.countryId == null
                || !normalizedText(result.regionName)
                || !normalizedText(result.countryName)
                || !Array.isArray(result.recommendedDestinations)) {
            throw new Error('invalid random recommendation');
        }

        const destinations = result.recommendedDestinations.slice(0, 8);
        if (destinations.length === 0 || destinations.some((destination) =>
            destination.destinationId == null
            || !normalizedText(destination.destinationName)
            || !normalizedText(destination.detailUrl).startsWith('/destinations/'))) {
            throw new Error('invalid random destinations');
        }
        return destinations;
    }

    function showEmptyState() {
        hideStage();
        renderMessageState(
                '조건에 맞는 여행지를 찾지 못했어요.',
                '다른 범위로 다시 시도해 주세요.',
                'is-empty'
        );
    }

    function showErrorState() {
        renderMessageState(
                '여행지를 불러오지 못했어요.',
                '잠시 후 다시 시도해 주세요.',
                'is-error'
        );
    }

    function renderMessageState(title, message, stateClass) {
        const messageBox = document.createElement('div');
        messageBox.className = `random-message ${stateClass}`;
        messageBox.append(
                createTextElement('strong', '', title),
                createTextElement('p', '', message)
        );
        resultContainer.replaceChildren(messageBox);
        resultContainer.hidden = false;
        status.textContent = title;
    }

    function setControlsDisabled(disabled) {
        drawButton.disabled = disabled;
        drawButton.setAttribute('aria-busy', String(disabled));
        drawButton.textContent = disabled ? '여행 지역을 고르는 중…' : '✈ 여행지 뽑기';
        scopeButtons.forEach((button) => {
            button.disabled = disabled;
        });
        const redrawButton = resultContainer.querySelector('.random-redraw-button');
        if (redrawButton) redrawButton.disabled = disabled;
    }

    function resetPresentation() {
        status.textContent = '';
        hideStage();
        resultContainer.replaceChildren();
        resultContainer.hidden = true;
    }

    function hideStage() {
        stage.hidden = true;
        stage.setAttribute('aria-hidden', 'true');
        stageName.textContent = '';
    }

    function createTextElement(tagName, className, text) {
        const element = document.createElement(tagName);
        if (className) element.className = className;
        element.textContent = normalizedText(text);
        return element;
    }

    function normalizedText(value) {
        return typeof value === 'string' ? value.trim() : '';
    }
})();
