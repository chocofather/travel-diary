(() => {
    const randomI18n = document.getElementById('random-travel-i18n')?.dataset;
    if (!randomI18n) return;

    const defaultImageUrl = '/images/default.png';
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const previewNames = {
        domestic: randomI18n.previewDomestic.split('|'),
        overseas: randomI18n.previewOverseas.split('|')
    };

    /** 뽑기 연출 길이와 단계별 문구. 빠르게 시작해 점점 느려진다. */
    const drawDuration = 4200;
    const minTickInterval = 55;
    const maxTickInterval = 430;
    const suspenseDelay = 520;
    const drawPhases = [
        {until: 0.30, label: randomI18n.phaseSelecting},
        {until: 0.60, label: randomI18n.phaseConsidering},
        {until: 0.85, label: randomI18n.phaseAlmost},
        {until: 1, label: randomI18n.phaseReveal}
    ];

    const scopeButtons = Array.from(document.querySelectorAll('[data-random-scope]'));
    const drawButton = document.getElementById('random-draw-button');
    const status = document.getElementById('random-status');
    const stage = document.getElementById('random-stage');
    const stageLabel = document.getElementById('random-stage-label');
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
        status.textContent = randomI18n.statusLoading;
        resultContainer.hidden = true;
        stage.hidden = true;
        stage.setAttribute('aria-hidden', 'true');
        stageName.textContent = '';
    }

    /**
     * 지역명을 빠르게 바꾸다가 점점 느려지는 감속 연출.
     * 마지막에는 잠깐 멈춰 긴장감을 준 뒤 결과를 확정한다.
     */
    function playDrawAnimation(result) {
        if (reducedMotion.matches) {
            return Promise.resolve();
        }

        const names = [...previewNames[selectedScope], result.regionName];
        const startedAt = performance.now();
        let step = 0;

        stage.hidden = false;
        stage.setAttribute('aria-hidden', 'true');
        stage.classList.remove('is-locked');
        stageName.textContent = names[0];
        updateStageLabel(0);

        return new Promise((resolve) => {
            const tick = () => {
                const progress = Math.min((performance.now() - startedAt) / drawDuration, 1);
                updateStageLabel(progress);

                if (progress >= 1) {
                    // 결과 확정 직전 잠깐 멈춰 긴장감을 준다.
                    stageName.textContent = result.regionName;
                    stage.classList.add('is-locked');
                    window.setTimeout(() => {
                        stage.classList.remove('is-locked');
                        hideStage();
                        resolve();
                    }, suspenseDelay);
                    return;
                }

                step += 1;
                stageName.textContent = names[step % names.length];
                // 진행할수록 간격이 길어져(ease-in) 마지막에는 지역명이 또렷하게 보인다.
                const eased = progress * progress;
                const interval = minTickInterval + (maxTickInterval - minTickInterval) * eased;
                window.setTimeout(tick, interval);
            };

            window.setTimeout(tick, minTickInterval);
        });
    }

    function updateStageLabel(progress) {
        if (!stageLabel) return;
        const phase = drawPhases.find((candidate) => progress < candidate.until)
                ?? drawPhases[drawPhases.length - 1];
        if (stageLabel.textContent !== phase.label) {
            stageLabel.textContent = phase.label;
        }
    }

    /** 결과 확정 순간의 작은 축하 효과. (과하지 않게 짧게만 보여준다) */
    function playConfetti(host) {
        if (reducedMotion.matches || !host) return;

        const confetti = document.createElement('div');
        confetti.className = 'random-confetti';
        confetti.setAttribute('aria-hidden', 'true');
        for (let index = 0; index < 14; index += 1) {
            const piece = document.createElement('span');
            piece.className = 'random-confetti-piece';
            piece.style.left = `${6 + (index * 88) / 14}%`;
            piece.style.animationDelay = `${(index % 5) * 60}ms`;
            confetti.append(piece);
        }
        host.append(confetti);
        window.setTimeout(() => confetti.remove(), 1600);
    }

    function renderResult(result, destinations) {
        const fragment = document.createDocumentFragment();
        const hero = createRegionHero(result);
        fragment.append(hero);

        const destinationSection = document.createElement('section');
        destinationSection.className = 'random-destinations';

        const heading = document.createElement('div');
        heading.className = 'random-destinations-heading';
        heading.append(
                createTextElement('h3', '', formatMessage(
                        randomI18n.resultsHeading, result.regionName)),
                createTextElement('p', '', formatMessage(
                        randomI18n.resultsCount, destinations.length))
        );

        const grid = document.createElement('div');
        grid.className = 'random-destination-grid';
        destinations.forEach((destination) => {
            grid.append(createDestinationCard(destination));
        });

        const actions = document.createElement('div');
        actions.className = 'random-destinations-actions';

        // 여행지 상세의 '여행지 더보기'와 같은 목록 필터(type/region)로 이동한다.
        const allDestinationsLink = document.createElement('a');
        allDestinationsLink.className = 'random-all-destinations-link';
        allDestinationsLink.href = buildRegionListUrl(result);
        allDestinationsLink.append(
                createTextElement('span', '', formatMessage(
                        randomI18n.resultsAll, result.regionName)),
                createTextElement('span', 'random-all-destinations-arrow', '→')
        );
        allDestinationsLink.lastElementChild.setAttribute('aria-hidden', 'true');

        const redrawButton = document.createElement('button');
        redrawButton.type = 'button';
        redrawButton.className = 'random-redraw-button';
        redrawButton.textContent = `${randomI18n.redraw} ↻`;
        redrawButton.addEventListener('click', drawTravelRegion);

        actions.append(allDestinationsLink, redrawButton);
        destinationSection.append(heading, grid, actions);
        fragment.append(destinationSection);
        resultContainer.replaceChildren(fragment);
        resultContainer.classList.remove('is-revealed');
        resultContainer.hidden = false;
        status.textContent = formatMessage(
                randomI18n.statusSelected, result.countryName, result.regionName);

        // 결과 카드가 갑자기 나타나지 않도록 다음 프레임에 등장 효과를 켠다.
        window.requestAnimationFrame(() => {
            resultContainer.classList.add('is-revealed');
        });
        playConfetti(hero);
    }

    /** 랜덤 결과 지역으로 필터된 기존 여행지 목록 URL */
    function buildRegionListUrl(result) {
        const scope = normalizedText(result.scope) || selectedScope;
        const params = new URLSearchParams({
            type: scope === 'overseas' ? 'overseas' : 'domestic',
            region: String(result.regionId)
        });
        return `/destinations?${params.toString()}`;
    }

    function createRegionHero(result) {
        const hero = document.createElement('section');
        hero.className = 'random-region-result';
        hero.append(
                createTextElement('p', 'random-region-kicker', randomI18n.heroKicker),
                createTextElement('p', 'random-region-country', result.countryName),
                createTextElement('h2', 'random-region-name', result.regionName),
                createTextElement(
                        'p',
                        'random-region-summary',
                        formatMessage(randomI18n.heroSummary, result.regionName)
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
        image.alt = formatMessage(
                randomI18n.cardImageAlt, destination.destinationName);
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
        content.append(createTextElement(
                'span', 'random-destination-cta', randomI18n.cardDetails));

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
                randomI18n.emptyTitle,
                randomI18n.emptyMessage,
                'is-empty'
        );
    }

    function showErrorState() {
        renderMessageState(
                randomI18n.errorTitle,
                randomI18n.errorMessage,
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
        resultContainer.classList.add('is-revealed');
        resultContainer.hidden = false;
        status.textContent = title;
    }

    function setControlsDisabled(disabled) {
        drawButton.disabled = disabled;
        drawButton.setAttribute('aria-busy', String(disabled));
        drawButton.textContent = disabled
                ? randomI18n.drawLoading
                : `✈ ${randomI18n.drawButton}`;
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
        resultContainer.classList.remove('is-revealed');
        resultContainer.hidden = true;
    }

    function hideStage() {
        stage.hidden = true;
        stage.setAttribute('aria-hidden', 'true');
        stage.classList.remove('is-locked');
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

    function formatMessage(pattern, ...values) {
        return values.reduce(
                (message, value, index) => message.replaceAll(`{${index}}`, String(value)),
                normalizedText(pattern));
    }
})();
