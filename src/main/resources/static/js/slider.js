document.addEventListener('DOMContentLoaded', async () => {
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

    // 2. DOM에 슬라이드 추가
    list.forEach((ev, index) => {
        const div = document.createElement('div');
        ev.bgcolor = pastelColors[index % pastelColors.length];
        div.className = 'swiper-slide';
        div.innerHTML = `
      <div class="slide-inner">
        <div class="slide-text">
          <span class="badge">${ev.title}</span>
          <h2 class="title">${ev.title}</h2>
          <p class="description">${ev.description}</p>
          <a href="/events/${ev.id}" class="more">자세히 보기</a>
        </div>
        <div class="slide-img">
          <img src="${ev.eventImg}" alt="${ev.title}" data-id="${ev.id}" style="cursor:pointer;">
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
        } else {
            swiper.autoplay.start();
            startTime = performance.now() - elapsedTime;
            startProgress();
            pauseBtn.textContent = '❚❚';
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
