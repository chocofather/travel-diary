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
    const res = await fetch('/api/events/slide');
    const list = await res.json();

    const pastelColors = [
        '#f8e8ee', // 연분홍
        '#e6f7ff', // 연하늘
        '#e0ffe0', // 연초록
        '#fff5cc', // 연노랑
        '#f3e5f5', // 연보라
        '#ffe0f0', // 복숭아색
        '#e0f7fa'  // 민트
    ];

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
    list.forEach((ev, index) => {
        const div = document.createElement('div');
        ev.bgcolor = pastelColors[index % pastelColors.length];
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
          <span class="badge">${escapeHtml(homeI18n.eventBadge)}</span>
          <h2 class="title">${title}</h2>
          ${description}
          <a href="/events/${encodeURIComponent(ev.id)}" class="more">${escapeHtml(homeI18n.eventDetails)}</a>
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

    totalSpan.textContent = String(list.length).padStart(2, '0');

    // ✅ 슬라이드에 맞춰 배경색 변경
    function updateBackgroundColor(swiperInstance) {
        const currentData = list[swiperInstance.realIndex];
        const bgColor = currentData?.bgcolor || '#ffffff';

        const eventSlider = document.getElementById('event-slider');
        const navBar = document.querySelector('.main-nav');

        eventSlider.style.backgroundColor = bgColor;
        navBar.style.backgroundColor = bgColor;
    }


    // 3. Swiper 초기화
    function initializeSwiper() {
        swiper = new Swiper('.swiper', {
            slidesPerView: 1,
            centeredSlides: false,
            loop: true,
            speed: 600,
            autoplay: { delay: 10000, disableOnInteraction: false },
            on: {
                slideChangeTransitionStart() {
                    resetProgress();
                    startProgress();
                    updateCounter();
                    updateBackgroundColor(this); // ✅ 슬라이드 전환 시 배경 변경
                },
                init() {
                    resetProgress();
                    startProgress();
                    updateCounter();
                    updateBackgroundColor(this); // ✅ 초기 진입 시 배경 설정
                }
            }
        });
    }

    // 4. 진행률 바 시작
    function startProgress() {
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
        indexSpan.textContent = String(swiper.realIndex + 1).padStart(2, '0');
    }

    // 7. Swiper 시작
    initializeSwiper();
    updateCounter();
    startProgress();

    // 8. 일시정지/재생 버튼
    const pauseBtn = document.querySelector('.pause');
    pauseBtn.onclick = () => {
        isPaused = !isPaused;
        if (isPaused) {
            swiper.autoplay.stop();
            cancelAnimationFrame(animationFrameId);
            if (startTime !== null) {
                elapsedTime += performance.now() - startTime;
                startTime = null;
            }
            pauseBtn.textContent = '▶';
            pauseBtn.setAttribute('aria-label', homeI18n.eventPlay);
        } else {
            swiper.autoplay.start();
            startTime = performance.now() - elapsedTime;
            startProgress();
            pauseBtn.textContent = '❚❚';
            pauseBtn.setAttribute('aria-label', homeI18n.eventPause);
        }
    };

    // 9. 이전/다음 버튼
    document.querySelector('.prev').onclick = () => {
        resetProgress();
        swiper.slidePrev();
    };
    document.querySelector('.next').onclick = () => {
        resetProgress();
        swiper.slideNext();
    };
});
