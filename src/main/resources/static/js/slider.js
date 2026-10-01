document.addEventListener('DOMContentLoaded', async () => {
    const homeI18n = document.getElementById('home-i18n').dataset;
    // 0. 변수 최상단에 선언
    let progress = 0;
    let startTime = null;
    let animationFrameId = null;
    let elapsedTime = 0;
    let swiper;
    let isPaused = false;

    // 1. Fetch Slide Data from API
    // 서버는 노출 중(is_slide, 기간 안)인 이벤트만 준다. 응답이 비었거나 읽지 못하면 슬라이드 0개로 본다.
    let list = [];
    try {
        const res = await fetch('/api/events/slide');
        const data = res.ok ? await res.json() : [];
        if (Array.isArray(data)) list = data;
    } catch (error) {
        list = [];
    }
    // 상세로 이동할 번호가 있는 이벤트만 슬라이드로 그린다. 슬라이드 수는 이 목록 기준이다.
    list = list.filter(ev => ev && ev.id != null && ev.id !== '');
    const slideCount = list.length;
    const hasMultipleSlides = slideCount > 1;
    const eventSlider = document.getElementById('event-slider');
    const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
    const sliderUi = eventSlider.querySelector('.slider-ui');
    sliderUi.hidden = !hasMultipleSlides;
    eventSlider.querySelector('.pause').hidden = reducedMotion;
    eventSlider.querySelector('.progress').hidden = reducedMotion;

    // 표시할 슬라이드가 없으면 슬라이더 내용·컨트롤을 숨기고 타이머도 시작하지 않는다.
    if (slideCount === 0) {
        eventSlider.classList.add('is-empty');
        return;
    }

    const slideArea = document.getElementById('slide-area');
    const totalSpan = document.getElementById('slide-total');
    const indexSpan = document.getElementById('slide-index');
    const progressBar = document.getElementById('progress-bar');

    /* 관리자가 쓴 값이 그대로 마크업이 되지 않게 감싼다 */
    const escapeHtml = (value) => String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');

    /* 값이 없거나 공백뿐이거나 문자열 'null' 이면 없는 값으로 본다 */
    const hasText = (value) => {
        const text = (value ?? '').toString().trim();
        return text !== '' && text !== 'null';
    };

    // 2. DOM에 슬라이드 추가
    list.forEach(ev => {
        const div = document.createElement('div');
        div.className = 'swiper-slide';
        // 슬라이더가 받는 이미지는 유형과 무관하게 언제나 대표 이미지(event_img)다.
        // 대표 이미지는 가로형으로 등록하는 값이라 유형별로 담는 방식을 나누지 않는다.
        // 상세용 세로 인포그래픽(poster_img)은 여기로 오지 않는다.
        const title = escapeHtml(ev.title);
        // 설명이 없는 이벤트는 설명 영역 자체를 만들지 않는다.
        const description = hasText(ev.description)
            ? `<p class="description">${escapeHtml(ev.description)}</p>`
            : '';
        div.innerHTML = `
      <div class="slide-inner">
        <div class="slide-text">
          <span class="badge">EVENT</span>
          <h2 class="title">${title}</h2>
          ${description}
          <a href="/events/${encodeURIComponent(ev.id)}" class="more">${escapeHtml(homeI18n.eventDetails)} <span aria-hidden="true">→</span></a>
        </div>
        <div class="slide-img">
          <img src="${escapeHtml(ev.eventImg)}" alt="${title}" data-id="${escapeHtml(ev.id)}">
        </div>
      </div>
    `;
        slideArea.appendChild(div);
    });

    // 이미지 클릭 시 상세 이동 코드 추가!
    slideArea.querySelectorAll('.slide-img img').forEach(img => {
        img.addEventListener('click', function() {
            const id = this.getAttribute('data-id');
            if (id) {
                window.location.href = `/events/${id}`;
            }
        });
    });

    totalSpan.textContent = String(slideCount).padStart(2, '0');

    // 3. Swiper 초기화
    function initializeSwiper() {
        swiper = new Swiper('#event-slider .swiper', {
            slidesPerView: 1,
            centeredSlides: false,
            // 한 장이면 반복·자동재생·끌어 넘기기를 끈다.
            loop: hasMultipleSlides,
            allowTouchMove: hasMultipleSlides,
            effect: 'fade',
            fadeEffect: { crossFade: true },
            speed: reducedMotion ? 0 : 600,
            autoplay: hasMultipleSlides && !reducedMotion ? { delay: 10000, disableOnInteraction: false } : false,
            on: {
                slideChangeTransitionStart() {
                    resetProgress();
                    startProgress();
                    updateCounter();
                },
                init() {
                    resetProgress();
                    startProgress();
                    updateCounter();
                }
            }
        });
    }

    // 4. 진행률 바 시작
    function startProgress() {
        if (!hasMultipleSlides || reducedMotion) return;
        // 진행 루프는 하나만 돈다. 전환 이벤트와 직접 호출이 겹쳐도 이전 루프를 이어서 쌓지 않는다.
        cancelAnimationFrame(animationFrameId);
        function updateProgress(timestamp) {
            if (isPaused) return;

            if (!startTime) startTime = timestamp - elapsedTime;
            const elapsed = timestamp - startTime;
            progress = Math.min((elapsed / 10000) * 100, 100);

            progressBar.style.width = progress + '%';

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

    // 5. 진행률 바 리셋
    function resetProgress() {
        progressBar.style.width = '0%';
        progress = 0;
        elapsedTime = 0;
        startTime = null;
    }

    // 6. 현재 슬라이드 인덱스 업데이트
    function updateCounter() {
        if (!swiper) return;
        // 번호는 언제나 1 ~ 슬라이드 수 안에 둔다.
        const index = ((Number(swiper.realIndex) || 0) % slideCount + slideCount) % slideCount;
        indexSpan.textContent = String(index + 1).padStart(2, '0');
    }

    // 7. Swiper 시작
    initializeSwiper();
    updateCounter();
    startProgress();

    // 8. 일시정지/재생 버튼
    // 자동재생이 있는 여러 장일 때만 컨트롤이 표시된다.
    const pauseBtn = eventSlider.querySelector('.pause');
    pauseBtn.onclick = () => {
        isPaused = !isPaused;
        pauseBtn.setAttribute('aria-pressed', String(isPaused));
        if (isPaused) {
            if (hasMultipleSlides) swiper.autoplay.stop();
            cancelAnimationFrame(animationFrameId);
            if (startTime !== null) {
                // startTime 은 이미 앞서 흐른 시간을 빼 둔 값이라, 지금까지의 경과는 이 차이 그대로다.
                elapsedTime = performance.now() - startTime;
                startTime = null;
            }
            pauseBtn.textContent = '▶';
            pauseBtn.setAttribute('aria-label', homeI18n.eventPlay);
        } else {
            if (hasMultipleSlides) swiper.autoplay.start();
            startTime = performance.now() - elapsedTime;
            startProgress();
            pauseBtn.textContent = '❚❚';
            pauseBtn.setAttribute('aria-label', homeI18n.eventPause);
        }
    };

    // 9. 이전/다음 버튼
    // 한 장의 컨트롤은 숨기지만 호출되더라도 같은 슬라이드를 유지한다.
    const restartSingleSlide = () => {
        resetProgress();
        startProgress();
    };
    eventSlider.querySelector('.prev').onclick = () => {
        if (!hasMultipleSlides) return restartSingleSlide();
        resetProgress();
        swiper.slidePrev();
    };
    eventSlider.querySelector('.next').onclick = () => {
        if (!hasMultipleSlides) return restartSingleSlide();
        resetProgress();
        swiper.slideNext();
    };
});
