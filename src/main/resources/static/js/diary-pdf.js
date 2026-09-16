/**
 * 다이어리 전체 PDF.
 *
 * 서버가 이미 소유권과 PIN을 확인해 그리는 표지/내지 DOM을 한 장씩 준비한다. 각 장은 화면용
 * viewport와 transform에서 떼어 낸 고정 A5 clone으로 캡처하고, 바로 PDF에 넣은 뒤 해제한다.
 */
(function (global) {
    'use strict';

    const PAGE_WIDTH = 720;
    const PAGE_HEIGHT = PAGE_WIDTH * 210 / 148;
    const COVER_WIDTH = 480;
    const COVER_HEIGHT = COVER_WIDTH * 210 / 148;
    const PDF_WIDTH_MM = 148;
    const PDF_HEIGHT_MM = 210;
    const PIXEL_RATIO = 2;
    const RESOURCE_TIMEOUT_MS = 20000;
    const FONT_SAMPLE = '가을 햇살 아래 경복궁을 걷다 0123456789';
    const INVALID_FILENAME_CHARACTERS = /[<>:"/\\|?*\u0000-\u001f]/g;
    const WINDOWS_RESERVED_NAME = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\..*)?$/i;

    let makingPdf = false;

    document.addEventListener('click', (event) => {
        const button = event.target.closest('[data-diary-pdf-button]');
        if (!button) return;
        event.preventDefault();
        createDiaryPdf(button);
    });

    async function createDiaryPdf(button) {
        if (makingPdf) return;
        makingPdf = true;

        const label = button.querySelector('.diary-pdf-download-label');
        const originalLabel = label ? label.textContent : '';
        button.disabled = true;
        button.setAttribute('aria-busy', 'true');

        try {
            const pageCount = nonNegativeInteger(button.dataset.diaryPdfPageCount, '내지 수');
            const totalSpreads = nonNegativeInteger(
                button.dataset.diaryPdfTotalSpreads, '펼침 수');
            const totalPdfPages = pageCount + 1;
            if (pageCount > 0 && totalSpreads < 1) {
                throw new Error('다이어리 펼침 정보를 찾지 못했습니다.');
            }

            const htmlToImage = global.htmlToImage;
            const JsPdf = global.jspdf && global.jspdf.jsPDF;
            if (!htmlToImage || !JsPdf) {
                throw new Error('PDF 라이브러리를 불러오지 못했습니다.');
            }

            const pdf = new JsPdf({
                orientation: 'portrait',
                unit: 'mm',
                format: [PDF_WIDTH_MM, PDF_HEIGHT_MM],
                compress: true,
                putOnlyUsedFonts: true
            });

            let completed = 0;
            updateProgress(label, completed + 1, totalPdfPages);
            await captureCover(button, pdf, htmlToImage);
            completed += 1;

            let capturedDiaryPages = 0;
            const spreadsToFetch = pageCount === 0 ? 0 : totalSpreads;
            for (let spread = 0; spread < spreadsToFetch; spread += 1) {
                const spreadHost = await fetchSpread(button.dataset.diaryPdfSpreadUrl, spread);
                try {
                    const sheets = Array.from(
                        spreadHost.querySelectorAll('.diary-sheet[data-diary-page-order]'))
                        .filter(sheet => /^\d+$/.test(sheet.dataset.diaryPageOrder || ''))
                        .sort((first, second) => Number(first.dataset.diaryPageOrder)
                            - Number(second.dataset.diaryPageOrder));

                    for (const sheet of sheets) {
                        if (capturedDiaryPages >= pageCount) break;
                        updateProgress(label, completed + 1, totalPdfPages);
                        await appendCapturedPage(pdf, sheet, PAGE_WIDTH, PAGE_HEIGHT,
                            htmlToImage, true);
                        capturedDiaryPages += 1;
                        completed += 1;
                        sheet.remove();
                    }
                } finally {
                    spreadHost.remove();
                }
            }

            if (capturedDiaryPages !== pageCount || completed !== totalPdfPages) {
                throw new Error('모든 다이어리 페이지를 불러오지 못했습니다.');
            }

            const safeTitle = sanitizeFilename(
                button.dataset.diaryTitle || document.getElementById('diary-detail-title')?.textContent);
            pdf.save(`${safeTitle}.pdf`);
        } catch (error) {
            console.error('다이어리 PDF 생성 실패', error);
            const message = error instanceof Error && error.message
                ? error.message
                : '알 수 없는 오류가 발생했습니다.';
            window.alert(`PDF를 만들지 못했습니다.\n${message}`);
        } finally {
            button.disabled = false;
            button.removeAttribute('aria-busy');
            if (label) label.textContent = originalLabel;
            makingPdf = false;
        }
    }

    async function captureCover(button, pdf, htmlToImage) {
        const templateId = button.dataset.diaryPdfCoverTemplate;
        const template = templateId ? document.getElementById(templateId) : null;
        if (!(template instanceof HTMLTemplateElement)) {
            throw new Error('다이어리 표지를 찾지 못했습니다.');
        }

        const host = createSourceHost(COVER_WIDTH, COVER_HEIGHT);
        try {
            host.append(template.content.cloneNode(true));
            const context = host.querySelector('[data-diary-pdf-cover-context]');
            const source = host.querySelector('[data-diary-pdf-cover]');
            if (!context || !source) {
                throw new Error('다이어리 표지를 준비하지 못했습니다.');
            }
            setFixedSize(context, COVER_WIDTH, COVER_HEIGHT);
            setFixedSize(source, COVER_WIDTH, COVER_HEIGHT);
            await appendCapturedPage(pdf, source, COVER_WIDTH, COVER_HEIGHT,
                htmlToImage, false);
        } finally {
            host.remove();
        }
    }

    async function fetchSpread(spreadUrl, spread) {
        if (!spreadUrl) throw new Error('다이어리 페이지 주소를 찾지 못했습니다.');
        const url = new URL(spreadUrl, document.baseURI);
        url.searchParams.set('spread', String(spread));
        const response = await withTimeout(fetch(url, {
            credentials: 'same-origin',
            headers: {
                'Accept': 'text/html',
                'X-Requested-With': 'XMLHttpRequest'
            }
        }), RESOURCE_TIMEOUT_MS, '다이어리 페이지를 불러오는 시간이 초과되었습니다.');
        if (!response.ok) {
            throw new Error(`${spread + 1}번째 펼침을 불러오지 못했습니다.`);
        }

        const html = await response.text();
        const board = new DOMParser().parseFromString(html, 'text/html')
            .getElementById('diary-read-board');
        if (!board) throw new Error(`${spread + 1}번째 펼침 화면을 찾지 못했습니다.`);
        board.removeAttribute('id');

        const host = createSourceHost(PAGE_WIDTH * 2 + 40, PAGE_HEIGHT);
        host.append(board);
        return host;
    }

    function createSourceHost(width, height) {
        const pageContext = document.querySelector('.diary-detail-page');
        if (!pageContext) throw new Error('다이어리 화면을 찾지 못했습니다.');

        const host = document.createElement('div');
        host.className = 'diary-pdf-source-host';
        host.setAttribute('aria-hidden', 'true');
        Object.assign(host.style, {
            position: 'fixed',
            left: '-100000px',
            top: '0',
            width: `${width}px`,
            height: `${height}px`,
            overflow: 'visible',
            pointerEvents: 'none',
            zIndex: '-2147483647'
        });
        pageContext.append(host);
        return host;
    }

    async function appendCapturedPage(pdf, source, width, height, htmlToImage, addPage) {
        const capture = createCaptureClone(source, width, height);
        let png = null;
        try {
            await prepareResources(capture.clone);
            const fontEmbedCSS = await withTimeout(
                htmlToImage.getFontEmbedCSS(capture.clone),
                RESOURCE_TIMEOUT_MS,
                'PDF에 글꼴을 포함하지 못했습니다.');
            png = await withTimeout(
                htmlToImage.toPng(capture.clone, {
                    width,
                    height,
                    canvasWidth: width,
                    canvasHeight: height,
                    pixelRatio: PIXEL_RATIO,
                    skipAutoScale: true,
                    cacheBust: false,
                    fontEmbedCSS
                }),
                RESOURCE_TIMEOUT_MS,
                '페이지 이미지를 만들지 못했습니다.');

            if (addPage) pdf.addPage([PDF_WIDTH_MM, PDF_HEIGHT_MM], 'portrait');
            pdf.addImage(png, 'PNG', 0, 0, PDF_WIDTH_MM, PDF_HEIGHT_MM, undefined, 'FAST');
        } finally {
            png = null;
            capture.dispose();
        }
    }

    function createCaptureClone(source, width, height) {
        const pageContext = source.closest('.diary-detail-page');
        if (!pageContext) {
            throw new Error('다이어리 페이지 스타일 기준을 찾지 못했습니다.');
        }

        const host = document.createElement('div');
        host.className = 'diary-pdf-capture-host';
        host.setAttribute('aria-hidden', 'true');
        Object.assign(host.style, {
            position: 'fixed',
            left: '-100000px',
            top: '0',
            width: `${width}px`,
            height: `${height}px`,
            overflow: 'visible',
            pointerEvents: 'none',
            zIndex: '-2147483647'
        });

        const context = document.createElement('div');
        context.className = captureContextClasses(source);
        Object.assign(context.style, {
            position: 'relative',
            display: 'block',
            width: `${width}px`,
            height: `${height}px`,
            margin: '0',
            border: '0',
            borderRadius: '0',
            boxShadow: 'none',
            overflow: 'visible'
        });

        const clone = source.cloneNode(true);
        setFixedSize(clone, width, height);
        clone.style.transform = 'none';
        clone.style.setProperty('--diary-page-scale', '1');
        clone.style.flex = 'none';
        clone.style.scrollSnapAlign = 'none';
        copyComputedBackground(source, clone);

        context.append(clone);
        host.append(context);
        pageContext.append(host);
        return {host, clone, dispose: () => host.remove()};
    }

    function setFixedSize(element, width, height) {
        element.style.width = `${width}px`;
        element.style.height = `${height}px`;
        element.style.minWidth = `${width}px`;
        element.style.maxWidth = 'none';
        element.style.minHeight = `${height}px`;
        element.style.maxHeight = 'none';
        element.style.margin = '0';
        element.style.transform = 'none';
    }

    function copyComputedBackground(source, clone) {
        const computed = global.getComputedStyle(source);
        clone.style.backgroundImage = computed.backgroundImage;
        clone.style.backgroundColor = computed.backgroundColor;
        clone.style.backgroundPosition = computed.backgroundPosition;
        clone.style.backgroundSize = computed.backgroundSize;
        clone.style.backgroundRepeat = computed.backgroundRepeat;
        clone.style.backgroundOrigin = computed.backgroundOrigin;
        clone.style.backgroundClip = computed.backgroundClip;
        clone.style.backgroundAttachment = computed.backgroundAttachment;
        clone.style.backgroundBlendMode = computed.backgroundBlendMode;
    }

    function captureContextClasses(source) {
        const classes = new Set(source.parentElement
            ? Array.from(source.parentElement.classList)
            : []);
        const spread = source.closest('.diary-book-spread');
        if (spread) Array.from(spread.classList).forEach(name => classes.add(name));
        return Array.from(classes)
            .filter(name => !name.startsWith('is-flipping') && !name.startsWith('is-slide-'))
            .join(' ');
    }

    async function prepareResources(clone) {
        renderMaskingTapes(clone);

        if (!document.fonts) {
            throw new Error('이 브라우저에서는 글꼴 로딩 상태를 확인할 수 없습니다.');
        }
        await document.fonts.ready;
        await waitForUsedCustomFonts(clone);
        await waitForImages(clone);
        await waitForBackgroundImages(clone);
        await waitForFrame();
        await waitForFrame();
    }

    function renderMaskingTapes(clone) {
        const tapes = Array.from(clone.querySelectorAll('.diary-sticker[data-tape-center]'));
        if (tapes.length && (!global.diaryTape || typeof global.diaryTape.render !== 'function')) {
            throw new Error('마스킹테이프 렌더러를 불러오지 못했습니다.');
        }

        tapes.forEach((item) => {
            if (!item.classList.contains('is-tape-repeat')) {
                global.diaryTape.render(item);
            }
            if (!item.querySelector('.diary-tape')) {
                throw new Error('마스킹테이프 렌더링이 끝나지 않았습니다.');
            }
        });
    }

    async function waitForUsedCustomFonts(clone) {
        const faces = Array.from(document.fonts);
        const facesByFamily = new Map();
        faces.forEach((face) => {
            const family = normalizeFontFamily(face.family);
            if (family && !facesByFamily.has(family)) facesByFamily.set(family, face);
        });

        const requiredFamilies = new Set();
        [clone, ...clone.querySelectorAll('*')].forEach((element) => {
            const family = normalizeFontFamily(global.getComputedStyle(element).fontFamily);
            if (facesByFamily.has(family)) requiredFamilies.add(family);
        });

        await Promise.all(Array.from(requiredFamilies).map(async (family) => {
            const face = facesByFamily.get(family);
            const descriptor = `${face.style || 'normal'} ${face.weight || '400'} 16px "${family}"`;
            const loaded = await withTimeout(
                document.fonts.load(descriptor, FONT_SAMPLE),
                RESOURCE_TIMEOUT_MS,
                `글꼴 ${family} 로딩 시간이 초과되었습니다.`);
            if (!loaded.length || !document.fonts.check(descriptor, FONT_SAMPLE)) {
                throw new Error(`글꼴 ${family}을(를) 불러오지 못했습니다.`);
            }
        }));

        await document.fonts.ready;
    }

    function normalizeFontFamily(value) {
        const first = String(value || '').split(',')[0].trim();
        return first.replace(/^['"]|['"]$/g, '');
    }

    async function waitForImages(clone) {
        const images = Array.from(clone.querySelectorAll('img'));
        await Promise.all(images.map((image) => withTimeout(
            waitForImage(image),
            RESOURCE_TIMEOUT_MS,
            `이미지 로딩 시간이 초과되었습니다: ${image.currentSrc || image.src}`)));
    }

    async function waitForImage(image) {
        image.loading = 'eager';
        if (!image.complete) {
            await new Promise((resolve, reject) => {
                image.addEventListener('load', resolve, {once: true});
                image.addEventListener('error', () => reject(
                    new Error(`이미지를 불러오지 못했습니다: ${image.currentSrc || image.src}`)),
                {once: true});
            });
        }
        if (!image.naturalWidth || !image.naturalHeight) {
            throw new Error(`이미지가 비어 있습니다: ${image.currentSrc || image.src}`);
        }
        if (typeof image.decode === 'function') {
            try {
                await image.decode();
            } catch (error) {
                throw new Error(`이미지를 해석하지 못했습니다: ${image.currentSrc || image.src}`,
                    {cause: error});
            }
        }
    }

    async function waitForBackgroundImages(clone) {
        const urls = collectCssImageUrls(clone);
        await Promise.all(Array.from(urls).map(url => withTimeout(
            loadImageUrl(url),
            RESOURCE_TIMEOUT_MS,
            `장식 이미지 로딩 시간이 초과되었습니다: ${url}`)));
    }

    function collectCssImageUrls(root) {
        const urls = new Set();
        const elements = [root, ...root.querySelectorAll('*')];
        elements.forEach((element) => {
            [null, '::before', '::after'].forEach((pseudo) => {
                const style = global.getComputedStyle(element, pseudo);
                [style.backgroundImage, style.maskImage, style.webkitMaskImage,
                    style.borderImageSource].forEach(value => addCssUrls(urls, value));
            });
        });
        return urls;
    }

    function addCssUrls(urls, value) {
        const pattern = /url\((['"]?)(.*?)\1\)/g;
        let match;
        while ((match = pattern.exec(value || '')) !== null) {
            if (!match[2] || match[2].startsWith('data:') || match[2].startsWith('blob:')) continue;
            urls.add(new URL(match[2], document.baseURI).href);
        }
    }

    async function loadImageUrl(url) {
        const image = new Image();
        image.decoding = 'async';
        await new Promise((resolve, reject) => {
            image.addEventListener('load', resolve, {once: true});
            image.addEventListener('error', () => reject(
                new Error(`장식 이미지를 불러오지 못했습니다: ${url}`)), {once: true});
            image.src = url;
        });
        if (!image.naturalWidth || !image.naturalHeight) {
            throw new Error(`장식 이미지가 비어 있습니다: ${url}`);
        }
        if (typeof image.decode === 'function') await image.decode();
    }

    function updateProgress(label, completed, total) {
        if (label) label.textContent = `PDF 만드는 중... ${completed} / ${total}`;
    }

    function nonNegativeInteger(value, label) {
        if (!/^\d+$/.test(value || '')) throw new Error(`${label} 정보를 찾지 못했습니다.`);
        return Number.parseInt(value, 10);
    }

    function waitForFrame() {
        return new Promise(resolve => global.requestAnimationFrame(() => resolve()));
    }

    function withTimeout(promise, timeout, message) {
        return new Promise((resolve, reject) => {
            const timer = global.setTimeout(() => reject(new Error(message)), timeout);
            Promise.resolve(promise).then(
                value => {
                    global.clearTimeout(timer);
                    resolve(value);
                },
                error => {
                    global.clearTimeout(timer);
                    reject(error);
                });
        });
    }

    function sanitizeFilename(value) {
        let safe = String(value || '')
            .normalize('NFC')
            .replace(INVALID_FILENAME_CHARACTERS, '_')
            .replace(/\s+/g, ' ')
            .replace(/_+/g, '_')
            .replace(/^[. ]+|[. ]+$/g, '');
        if (!safe || WINDOWS_RESERVED_NAME.test(safe)) safe = 'travel-diary';
        safe = safe.slice(0, 80).replace(/[. ]+$/g, '');
        return safe || 'travel-diary';
    }

    global.diaryPdfExport = {createDiaryPdf, sanitizeFilename};
})(window);
