// -------------------- 로고 이미지 준비 --------------------
const logoImg = new window.Image();
logoImg.src = "/images/logo7.png";

// -------------------- 상태 --------------------
let filteredRegions = [];
let currentType = "all";   // all, domestic, asia, europe 등 버튼별 데이터타입
let isSpinning = false;

// -------------------- 카테고리 parentId 매핑 --------------------
// 실제 서버 DB id에 맞게 수정!
const regionParentIds = {
    all: null,
    domestic: 7,
    asia: 1,
    europe: 2,
    'north-america': 3,
    'south-america': 4,
    oceania: 5,
    africa: 6,
    'random-overseas': -99
};


// -------------------- 버튼 클릭 이벤트 --------------------
document.querySelectorAll('.roulette-type-btn').forEach(btn => {
    btn.addEventListener('click', function () {
        if (isSpinning) return;
        document.querySelectorAll('.roulette-type-btn').forEach(b => b.classList.remove('active'));
        this.classList.add('active');
        const type = this.getAttribute('data-type');
        currentType = type;

        if (type === 'random-overseas') {
            fetchRandomOverseasRegions();
        } else {
            const parentId = regionParentIds[type];
            if (parentId) {
                fetchRegionsByParentId(parentId);
            } else {
                filteredRegions = [];
                drawRoulette();
                resetRoulette();
            }
        }
    });
});

// -------------------- 지역 목록 AJAX (parentId별) --------------------
function fetchRegionsByParentId(parentId) {
    fetch(`/api/roulette-region/children/${parentId}`)
        .then(res => res.json())
        .then(regionList => {
            filteredRegions = regionList.map(r => ({
                id: r.id,
                name: r.regionName || r.name // 컬럼명 맞게!
            }));
            drawRoulette();
            resetRoulette();
        });
}

// -------------------- 랜덤 해외 20개 (백엔드에서 뽑아서 내려주면 베스트) --------------------
function fetchRandomOverseasRegions() {
    fetch(`/api/roulette-region/random-overseas?size=15`)
        .then(res => res.json())
        .then(regionList => {
            filteredRegions = regionList.map(r => ({
                id: r.id,
                name: r.regionName || r.name
            }));
            drawRoulette();
            resetRoulette();
        });
}

// -------------------- 캔버스(고해상도, 520x520) --------------------
const canvas = document.getElementById('roulette-canvas');
const ctx = canvas.getContext('2d');
const dpr = window.devicePixelRatio || 1;
canvas.width = 520 * dpr;
canvas.height = 520 * dpr;
canvas.style.width = "520px";
canvas.style.height = "520px";
ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

// -------------------- 룰렛 그리기 (대형, 선명, 세련, 중앙로고) --------------------
function drawRoulette(rot = 0) {
    const len = filteredRegions.length;
    if (!len) {
        ctx.clearRect(0, 0, 520, 520);
        return;
    }
    const anglePer = (2 * Math.PI) / len;
    ctx.clearRect(0, 0, 520, 520);

    // --- 1. 메탈 프레임 ---
    let grad = ctx.createLinearGradient(0, 0, 520, 520);
    grad.addColorStop(0, "#e2e8f0");
    grad.addColorStop(0.4, "#8a99ac");
    grad.addColorStop(0.7, "#cdd6e1");
    grad.addColorStop(1, "#d5dae3");
    ctx.save();
    ctx.beginPath();
    ctx.arc(260, 260, 258, 0, 2 * Math.PI);
    ctx.lineWidth = 18;
    ctx.strokeStyle = grad;
    ctx.shadowBlur = 19;
    ctx.shadowColor = "#b6c0cc";
    ctx.stroke();
    ctx.restore();

    // --- 2. 얇은 흰색 링 ---
    ctx.save();
    ctx.beginPath();
    ctx.arc(260, 260, 243, 0, 2 * Math.PI);
    ctx.lineWidth = 6.5;
    ctx.strokeStyle = "#fff";
    ctx.shadowBlur = 0;
    ctx.stroke();
    ctx.restore();

    // --- 3. 섹터 ---
    const colors = [
        "#2476FF", // 선명한 파랑
        "#FE4A49", // 쨍한 레드
        "#F9CB40", // 진한 노랑
        "#53D769", // 선명한 그린
        "#A259F7", // 비비드 퍼플
        "#23B5D3", // 밝고 쨍한 청록
        "#FF9800", // 비비드 오렌지
        "#FF5ACD", // 핫핑크
        "#15B0F8", // 스카이블루
        "#52E2C3", // 밝은 민트
        "#F23460", // 강렬한 핑크레드
        "#FD892D", // 선명한 주황
        "#0AAB8E", // 청록
        "#EFC31A", // 쨍한 금색
        "#1B1C38"  // 딥네이비 (포인트)
    ];
    for (let i = 0; i < len; i++) {
        ctx.beginPath();
        ctx.moveTo(260, 260);
        ctx.arc(
            260, 260, 232,
            anglePer * i - Math.PI / 2 + rot,
            anglePer * (i + 1) - Math.PI / 2 + rot
        );
        ctx.closePath();
        ctx.fillStyle = colors[i % colors.length];
        ctx.globalAlpha = 0.92;
        ctx.fill();
        ctx.globalAlpha = 1;

        // 섹터 경계선
        ctx.save();
        ctx.lineWidth = 1.7;
        ctx.strokeStyle = "#fff";
        ctx.shadowBlur = 2;
        ctx.shadowColor = "#eee";
        ctx.stroke();
        ctx.restore();

        // --- 텍스트 ---
        ctx.save();
        ctx.translate(260, 260);
        ctx.rotate(anglePer * (i + 0.5) - Math.PI / 2 + rot);
        ctx.textAlign = "right";
        ctx.font = "bold 29px 'Pretendard', 'SUIT', 'Spoqa Han Sans', sans-serif";
        ctx.fillStyle = "#fff";
        ctx.shadowBlur = 7;
        ctx.shadowColor = "#3e4e6a44";
        ctx.lineWidth = 1.2;
        ctx.strokeStyle = "#232b39a0";
        ctx.strokeText(filteredRegions[i].name, 200, 15);
        ctx.fillText(filteredRegions[i].name, 200, 15);
        ctx.restore();
    }

    // --- 4. 중앙 흰 원 포인트 ---
    ctx.save();
    ctx.beginPath();
    ctx.arc(260, 260, 65, 0, 2 * Math.PI);
    ctx.fillStyle = "#fff";
    ctx.shadowBlur = 7;
    ctx.shadowColor = "#d2d2df88";
    ctx.fill();
    ctx.restore();

    // --- ★ 중앙에 로고 이미지 추가 ---
    if (logoImg.complete && logoImg.naturalWidth > 0) {
        ctx.save();
        ctx.globalAlpha = 1.0;
        ctx.drawImage(logoImg, 200, 200, 120, 120); // 크기 조절
        ctx.restore();
    }

    // --- 5. 중앙 투명 테두리 ---
    ctx.save();
    ctx.beginPath();
    ctx.arc(260, 260, 74, 0, 2 * Math.PI);
    ctx.lineWidth = 8;
    ctx.strokeStyle = "#eaeaf6bb";
    ctx.shadowBlur = 0;
    ctx.stroke();
    ctx.restore();
}

// (로고 로딩 완료 후 redraw 자동 호출)
logoImg.onload = () => { drawRoulette(); };

// 최초 진입시 국내(default) 뿌리기
fetchRegionsByParentId(regionParentIds.domestic);

// -------------------- 룰렛 초기화 --------------------
function resetRoulette() {
    document.getElementById('roulette-result').textContent = "";
    document.getElementById('roulette-trip-list').innerHTML = "";
}
resetRoulette();

// -------------------- 룰렛 돌리기 (섹터 중앙에 멈춤!) --------------------
document.getElementById('spin-btn').addEventListener('click', function () {
    if (isSpinning) return;
    if (!filteredRegions.length) return;
    isSpinning = true;
    resetRoulette();

    const len = filteredRegions.length;
    const anglePer = 360 / len;
    const idx = Math.floor(Math.random() * len);
    const stopDeg = 360 * 10 + anglePer * idx + anglePer / 2;
    let nowDeg = 0, speed = 29;

    function spin() {
        nowDeg += speed;
        if (speed > 1) speed *= 0.992;
        if (nowDeg >= stopDeg) {
            nowDeg = stopDeg;
            drawRoulette(-nowDeg * Math.PI / 180);
            showRouletteResult(idx);
            isSpinning = false;
            return;
        }
        drawRoulette(-nowDeg * Math.PI / 180);
        requestAnimationFrame(spin);
    }
    spin();
});

// -------------------- 결과/카드 보여주기 (API 연동) --------------------
function showRouletteResult(idx) {
    const regionId = filteredRegions[idx].id;
    const regionName = filteredRegions[idx].name;
    document.getElementById('roulette-result').textContent = `추천 지역: [${regionName}]`;

    // 여행지 5개 추천 API 호출 (ex: /api/random-recommend/{regionId}?size=5)
    fetch(`/api/random-recommend/${regionId}?size=5`)
        .then(res => res.json())
        .then(list => {
            document.getElementById('roulette-trip-list').innerHTML =
                list.map(p => `
                  <a href="/destinations/${p.destinationId}" class="recommend-card" style="text-decoration:none;color:inherit;">
                    <img src="${p.imageUrl}" alt="">
                    <div class="recommend-card-title">${p.destinationName}</div>
                    <div class="recommend-card-desc">${p.shortDescription}</div>
                  </a>
                `).join('');
        });
}
