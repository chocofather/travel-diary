/*
  메인 슬라이더 공용 동작: 최상단 Hero(#home-hero)와 이벤트 프로모션 배너(#home-promotion).
  슬라이드와 컨트롤은 서버가 그려 둔다(0장이면 영역 자체가 없고, 한 장이면 컨트롤이 없다).
  여기서는 Swiper 초기화와 카운터·진행선·이전/다음·일시정지만 맡는다.

  영역마다 상태를 따로 가진다. 한 영역의 컨트롤이 다른 영역의 Swiper 를 움직이지 않도록
  요소는 언제나 자기 영역(root) 안에서만 찾고, 문서 전체 id 에 기대지 않는다.
*/
document.addEventListener('DOMContentLoaded', () => {
    const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
    document.querySelectorAll('#home-hero, #home-promotion')
        .forEach(root => initHomeSlider(root, reducedMotion));
});

function initHomeSlider(root, reducedMotion) {
    const AUTOPLAY_DELAY = 10000;
    const swiperElement = root.querySelector('.swiper');
    const slideCount = swiperElement ? swiperElement.querySelectorAll('.swiper-slide').length : 0;
    if (slideCount === 0 || typeof Swiper === 'undefined') return;

    const hasMultipleSlides = slideCount > 1;
    // 자동재생은 슬라이드가 두 장 이상이고 움직임 줄이기를 쓰지 않을 때만 켠다.
    const autoplayEnabled = hasMultipleSlides && !reducedMotion;
    const indexSpan = root.querySelector('.slider-index');
    const progressElement = root.querySelector('.progress');
    const progressBar = root.querySelector('.progress-bar');
    const pauseBtn = root.querySelector('.nav.pause');
    const prevBtn = root.querySelector('.nav.prev');
    const nextBtn = root.querySelector('.nav.next');
    const slideWrapper = swiperElement.querySelector('.swiper-wrapper');

    if (pauseBtn) pauseBtn.hidden = reducedMotion;
    if (progressElement) progressElement.hidden = reducedMotion;

    let swiper = null;
    let progress = 0;
    let startTime = null;
    let animationFrameId = null;
    let elapsedTime = 0;
    let isPaused = false;      // 사용자가 일시정지 버튼으로 멈춤
    let heldByFocus = false;   // 키보드 초점이 슬라이드 안에 있는 동안 잠시 멈춤

    swiper = new Swiper(swiperElement, {
        slidesPerView: 1,
        centeredSlides: false,
        // 한 장이면 반복·자동재생·끌어 넘기기를 끈다.
        loop: hasMultipleSlides,
        allowTouchMove: hasMultipleSlides,
        effect: 'fade',
        fadeEffect: { crossFade: true },
        speed: reducedMotion ? 0 : 600,
        autoplay: autoplayEnabled ? { delay: AUTOPLAY_DELAY, disableOnInteraction: false } : false,
        on: {
            init() {
                resetProgress();
                startProgress();
                syncSlides(this);
            },
            slideChangeTransitionStart() {
                resetProgress();
                startProgress();
                syncSlides(this);
            }
        }
    });

    /*
      번호는 언제나 1 ~ 슬라이드 수 안에 둔다.
      페이드라 지나간 슬라이드도 같은 자리에 겹쳐 있으므로, 보이지 않는 슬라이드의 링크로
      초점이 가지 않도록 지금 슬라이드 말고는 inert 로 막는다.
    */
    function syncSlides(instance) {
        const index = ((Number(instance.realIndex) || 0) % slideCount + slideCount) % slideCount;
        if (indexSpan) indexSpan.textContent = String(index + 1).padStart(2, '0');
        Array.from(instance.slides).forEach((slide, slideIndex) => {
            slide.inert = slideIndex !== instance.activeIndex;
        });
    }

    // 진행선. 다 차면 다음 슬라이드로 넘긴다. 진행 루프는 하나만 돈다.
    function startProgress() {
        if (!autoplayEnabled || isPaused || heldByFocus) return;
        cancelAnimationFrame(animationFrameId);
        function updateProgress(timestamp) {
            if (isPaused || heldByFocus) return;

            if (!startTime) startTime = timestamp - elapsedTime;
            const elapsed = timestamp - startTime;
            progress = Math.min((elapsed / AUTOPLAY_DELAY) * 100, 100);
            if (progressBar) progressBar.style.width = progress + '%';

            if (progress < 100) {
                animationFrameId = requestAnimationFrame(updateProgress);
            } else {
                resetProgress();
                swiper.slideNext();
                startProgress();
            }
        }
        animationFrameId = requestAnimationFrame(updateProgress);
    }

    function resetProgress() {
        if (progressBar) progressBar.style.width = '0%';
        progress = 0;
        elapsedTime = 0;
        startTime = null;
    }

    function stopTimer() {
        if (autoplayEnabled) swiper.autoplay.stop();
        cancelAnimationFrame(animationFrameId);
        if (startTime !== null) {
            // startTime 은 이미 앞서 흐른 시간을 빼 둔 값이라, 지금까지의 경과는 이 차이 그대로다.
            elapsedTime = performance.now() - startTime;
            startTime = null;
        }
    }

    function resumeTimer() {
        if (!autoplayEnabled || isPaused || heldByFocus) return;
        swiper.autoplay.start();
        startTime = performance.now() - elapsedTime;
        startProgress();
    }

    // 일시정지/재생. 자동재생이 있는 여러 장일 때만 버튼이 있다.
    pauseBtn?.addEventListener('click', () => {
        isPaused = !isPaused;
        pauseBtn.setAttribute('aria-pressed', String(isPaused));
        if (isPaused) {
            stopTimer();
            pauseBtn.textContent = '▶';
            pauseBtn.setAttribute('aria-label', root.dataset.labelPlay);
        } else {
            resumeTimer();
            pauseBtn.textContent = '❚❚';
            pauseBtn.setAttribute('aria-label', root.dataset.labelPause);
        }
    });

    prevBtn?.addEventListener('click', () => {
        resetProgress();
        swiper.slidePrev();
    });
    nextBtn?.addEventListener('click', () => {
        resetProgress();
        swiper.slideNext();
    });

    // 키보드로 슬라이드 안의 링크에 머무는 동안에는 넘어가지 않는다. 초점이 나가면 이어서 돈다.
    slideWrapper.addEventListener('focusin', () => {
        if (!autoplayEnabled || heldByFocus) return;
        heldByFocus = true;
        stopTimer();
    });
    slideWrapper.addEventListener('focusout', event => {
        if (!heldByFocus || slideWrapper.contains(event.relatedTarget)) return;
        heldByFocus = false;
        resumeTimer();
    });
}
