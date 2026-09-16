/**
 * 다이어리 PDF Phase 1.
 *
 * 읽기 화면에 이미 그려진 내지 한 장을 고정 크기 clone으로 캡처한다. 화면의 종이를
 * 다시 구현하지 않으며, 원본 DOM도 바꾸지 않는다. 표지와 다페이지 병합은 이 단계의 범위가 아니다.
 */
(function (global) {
    'use strict';

    const PAGE_WIDTH = 576;
    const PAGE_HEIGHT = PAGE_WIDTH * 38 / 41;
    const PDF_WIDTH_MM = 200;
    const PDF_HEIGHT_MM = PDF_WIDTH_MM * 38 / 41;
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
        createPagePdf(button);
    });

    async function createPagePdf(button) {
        if (makingPdf) return;
        makingPdf = true;

        const buttons = Array.from(document.querySelectorAll('[data-diary-pdf-button]'));
        const label = button.querySelector('.diary-page-pdf-label');
        const originalLabel = label ? label.textContent : '';
        let host = null;

        buttons.forEach(item => item.disabled = true);
        button.setAttribute('aria-busy', 'true');
        if (label) label.textContent = 'PDF 만드는 중...';

        try {
            const side = button.dataset.diaryPdfButton;
            const pageOrder = button.dataset.diaryPageOrder;
            if (!side || !/^\d+$/.test(pageOrder || '')) {
                throw new Error('PDF로 만들 페이지 정보를 찾지 못했습니다.');
            }

            const source = document.querySelector(
                `[data-diary-pdf-page="${cssEscape(side)}"]`
                + `[data-diary-page-order="${cssEscape(pageOrder)}"]`);
            if (!source || !source.querySelector('.diary-sheet-body')) {
                throw new Error('현재 페이지 화면을 찾지 못했습니다.');
            }

            const capture = createCaptureClone(source);
            host = capture.host;
            const clone = capture.clone;

            await prepareResources(clone);

            const htmlToImage = global.htmlToImage;
            const JsPdf = global.jspdf && global.jspdf.jsPDF;
            if (!htmlToImage || !JsPdf) {
                throw new Error('PDF 라이브러리를 불러오지 못했습니다.');
            }

            const fontEmbedCSS = await withTimeout(
                htmlToImage.getFontEmbedCSS(clone),
                RESOURCE_TIMEOUT_MS,
                'PDF에 글꼴을 포함하지 못했습니다.');
            const png = await withTimeout(
                htmlToImage.toPng(clone, {
                    width: PAGE_WIDTH,
                    height: PAGE_HEIGHT,
                    canvasWidth: PAGE_WIDTH,
                    canvasHeight: PAGE_HEIGHT,
                    pixelRatio: PIXEL_RATIO,
                    skipAutoScale: true,
                    cacheBust: false,
                    fontEmbedCSS
                }),
                RESOURCE_TIMEOUT_MS,
                '페이지 이미지를 만들지 못했습니다.');

            const pdf = new JsPdf({
                orientation: 'landscape',
                unit: 'mm',
                format: [PDF_WIDTH_MM, PDF_HEIGHT_MM],
                compress: true,
                putOnlyUsedFonts: true
            });
            pdf.addImage(png, 'PNG', 0, 0, PDF_WIDTH_MM, PDF_HEIGHT_MM, undefined, 'FAST');

            const title = document.getElementById('diary-detail-title')?.textContent || 'travel_diary';
            const safeTitle = sanitizeFilename(title);
            pdf.save(`${safeTitle}_page_${pageOrder}.pdf`);
        } catch (error) {
            console.error('다이어리 PDF 생성 실패', error);
            const message = error instanceof Error && error.message
                ? error.message
                : '알 수 없는 오류가 발생했습니다.';
            window.alert(`PDF를 만들지 못했습니다.\n${message}`);
        } finally {
            if (host) host.remove();
            buttons.forEach(item => item.disabled = false);
            button.removeAttribute('aria-busy');
            if (label) label.textContent = originalLabel;
            makingPdf = false;
        }
    }

    function createCaptureClone(source) {
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
            width: `${PAGE_WIDTH}px`,
            height: `${PAGE_HEIGHT}px`,
            overflow: 'visible',
            pointerEvents: 'none',
            zIndex: '-2147483647'
        });

        const context = document.createElement('div');
        context.className = captureContextClasses(source.parentElement);
        Object.assign(context.style, {
            position: 'relative',
            display: 'block',
            width: `${PAGE_WIDTH}px`,
            height: `${PAGE_HEIGHT}px`,
            margin: '0',
            border: '0',
            borderRadius: '0',
            boxShadow: 'none',
            overflow: 'visible'
        });

        const clone = source.cloneNode(true);
        clone.style.width = `${PAGE_WIDTH}px`;
        clone.style.height = `${PAGE_HEIGHT}px`;
        clone.style.minWidth = `${PAGE_WIDTH}px`;
        clone.style.maxWidth = 'none';
        clone.style.flex = 'none';
        clone.style.scrollSnapAlign = 'none';
        copyComputedPaperBackground(source, clone);

        context.append(clone);
        host.append(context);
        pageContext.append(host);
        return {host, clone};
    }

    function copyComputedPaperBackground(source, clone) {
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

    function captureContextClasses(parent) {
        if (!parent) return 'diary-book-spread';
        return Array.from(parent.classList)
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
                window.diaryTape.render(item);
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
            .replace(/\s+/g, '_')
            .replace(/_+/g, '_')
            .replace(/^[. ]+|[. ]+$/g, '');
        if (!safe || WINDOWS_RESERVED_NAME.test(safe)) safe = 'travel_diary';
        safe = safe.slice(0, 80).replace(/[. ]+$/g, '');
        return safe || 'travel_diary';
    }

    function cssEscape(value) {
        return global.CSS && typeof global.CSS.escape === 'function'
            ? global.CSS.escape(String(value))
            : String(value).replace(/["\\]/g, '\\$&');
    }

    global.diaryPdfPhase1 = {createPagePdf, sanitizeFilename};
})(window);
