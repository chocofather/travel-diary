/**
 * 여행정보 구조화 콘텐츠의 이미지 슬라이더.
 *
 * <p>넘기기는 CSS scroll-snap 가로 스크롤이 맡는다. 휴대폰 손가락 넘기기는 브라우저 그대로이고,
 * 이 스크립트는 번호·이전/다음 버튼·← → 키만 더한다. 스크립트가 없어도 가로로 넘겨 모든 사진을 볼 수 있다.
 *
 * <p>[data-structured-slider] 마다 따로 초기화하고 상태(현재 번호)도 각자 가진다.
 * 한 페이지에 슬라이더가 여러 개 있어도 서로 영향을 주지 않는다. 자동 넘김은 없다.
 */
(function () {
    'use strict';

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

    function pad(number) {
        return String(number).padStart(2, '0');
    }

    function initSlider(root) {
        if (root.dataset.sliderReady === 'true') return;
        const track = root.querySelector('[data-slider-track]');
        const slides = track ? Array.from(track.querySelectorAll('[data-slider-slide]')) : [];
        if (!track || slides.length === 0) return;
        root.dataset.sliderReady = 'true';
        root.classList.add('is-enhanced');

        const controls = root.querySelector('[data-slider-controls]');
        const prevButton = root.querySelector('[data-slider-prev]');
        const nextButton = root.querySelector('[data-slider-next]');
        const current = root.querySelector('[data-slider-current]');
        // 한 장이면 넘길 것이 없다. 번호·버튼은 서버가 그리지 않거나 감춘 그대로 둔다.
        if (slides.length < 2 || !controls || !prevButton || !nextButton || !current) return;

        let index = 0;
        let frame = 0;

        function render(nextIndex) {
            index = nextIndex;
            current.textContent = pad(index + 1);
            prevButton.disabled = index === 0;
            nextButton.disabled = index === slides.length - 1;
            // 보이지 않는 사진은 화면 읽기 도구에서 숨긴다. (사진 안에 누를 수 있는 것은 없다)
            slides.forEach((slide, slideIndex) => {
                slide.setAttribute('aria-hidden', String(slideIndex !== index));
            });
        }

        function goTo(targetIndex) {
            const target = Math.max(0, Math.min(slides.length - 1, targetIndex));
            track.scrollTo({
                left: slides[target].offsetLeft,
                behavior: reducedMotion.matches ? 'auto' : 'smooth'
            });
            render(target);
        }

        // 손가락·트랙패드로 넘겨도 번호가 따라오게 스크롤 위치로 현재 사진을 정한다.
        // 넘어가는 도중이 아니라 한 장에 거의 멈췄을 때만 바꾼다. (버튼으로 넘기는 중에 번호가 되돌아가지 않게)
        track.addEventListener('scroll', () => {
            if (frame) return;
            frame = window.requestAnimationFrame(() => {
                frame = 0;
                const position = track.scrollLeft / (track.clientWidth || 1);
                const visible = Math.max(0, Math.min(slides.length - 1, Math.round(position)));
                if (visible !== index && Math.abs(position - visible) < 0.1) render(visible);
            });
        }, {passive: true});

        prevButton.addEventListener('click', () => goTo(index - 1));
        nextButton.addEventListener('click', () => goTo(index + 1));

        root.addEventListener('keydown', event => {
            if (event.altKey || event.ctrlKey || event.metaKey) return;
            if (event.key === 'ArrowLeft') {
                event.preventDefault();
                goTo(index - 1);
            } else if (event.key === 'ArrowRight') {
                event.preventDefault();
                goTo(index + 1);
            }
        });

        // 폭이 바뀌면(회전·창 크기) 보던 사진에 다시 맞춘다.
        if ('ResizeObserver' in window) {
            new ResizeObserver(() => {
                track.scrollTo({left: slides[index].offsetLeft, behavior: 'auto'});
            }).observe(track);
        }

        controls.hidden = false;
        render(0);
    }

    function initAll() {
        document.querySelectorAll('[data-structured-slider]').forEach(initSlider);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll);
    } else {
        initAll();
    }
})();
