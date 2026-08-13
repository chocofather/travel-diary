// 1. 시즌별 메타데이터 (각 태그: label, id)
const seasonMeta = {
    SPRING: {
        badge: '따듯한 봄 여행지 여기 어떠세요? 🌸',
        title: '일기장 속에 저장하고 싶은 봄 여행지',
        desc: '향긋한 꽃내음과 따스한 햇살이 가득한 곳, 설렘 가득한 봄 여행지를 추천합니다.',
        bgClass: 'spring',
        monthRange: [3,     4, 5],
        tags: [
            { label: "국내 봄 여행지", id: 89 },
            { label: "벚꽃 여행", id: 91 },
            { label: "피크닉 명소", id: 88 },
            { label: "해외 봄 여행지", id: 90 },
            { label: "일본 벚꽃 명소", id: 93 }
        ]
    },
    SUMMER: {
        badge: '무더운 여름 지금 휴가 떠나볼까요? ☀️',
        title: '일기장 속에 저장하고 싶은 여름 여행지',
        desc: '여름방학·휴가철, 시원한 바다와 푸른 자연이 기다리는 인기 여행지입니다.',
        bgClass: 'summer',
        monthRange: [6, 7, 8],
        tags: [
            { label: "바다 여행", id: 6 },
            { label: "계곡 여행", id: 94 },
            { label: "해외 휴양지", id: 96 },
            { label: "섬 여행", id: 23 },
            { label: "여름 축제", id: 97 }
        ]
    },
    FALL: {
        badge: '선선한 바람, 가을 감성 여행 🍂',
        title: '일기장 속에 저장하고 싶은 가을 여행지',
        desc: '형형색색 물든 단풍길과 가을만의 감성이 가득한 여행지로 떠나보세요.',
        bgClass: 'fall',
        monthRange: [9, 10, 11],
        tags: [
            { label: "단풍 여행", id: 46 },
            { label: "가을 풍경 여행", id: 98 },
            { label: "가을 축제", id: 99 }
        ]
    },
    WINTER: {
        badge: '눈꽃처럼 반짝이는 겨울 여행지 ❄️',
        title: '일기장 속에 저장하고 싶은 겨울 여행지',
        desc: '따뜻한 온천, 눈 내리는 설경 속에서 즐기는 특별한 겨울 여행을 소개합니다.',
        bgClass: 'winter',
        monthRange: [12, 1, 2],
        tags: [
            { label: "눈꽃 여행", id: 100 },
            { label: "온천 여행", id: 30 },
            { label: "겨울 스포츠", id: 101 },
            { label: "따뜻한 여행지", id: 102 },
            { label: "겨울 축제", id: 103 }
        ]
    }
};

// 2. 현재 시즌 계산
const month = new Date().getMonth() + 1;
let currentSeason = 'SPRING';
for (const [key, value] of Object.entries(seasonMeta)) {
    if (value.monthRange.includes(month)) {
        currentSeason = key;
        break;
    }
}
const meta = seasonMeta[currentSeason];

// 3. DOM 메타데이터 적용
const wrapper = document.querySelector('.seasonal-bg-wrapper');
if (wrapper) wrapper.className = `seasonal-bg-wrapper ${meta.bgClass}`;
const badgeElem = document.querySelector('.season-badge');
if (badgeElem) badgeElem.textContent = meta.badge;
const titleElem = document.querySelector('.season-title');
if (titleElem) titleElem.textContent = meta.title;
const descElem = document.querySelector('.season-sub');
if (descElem) descElem.textContent = meta.desc;

// 4. 태그 버튼 생성 및 이벤트 바인딩
const tagListDiv = document.querySelector('.season-tag-list');
function renderTags() {
    if (tagListDiv && meta.tags) {
        tagListDiv.innerHTML = "";
        meta.tags.forEach((tag, idx) => {
            const btn = document.createElement('button');
            btn.className = 'tag';
            btn.textContent = tag.label;
            btn.onclick = () => {
                tagListDiv.querySelectorAll('.tag').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                // 태그 클릭 시 추천 여행지 뿌리기 (id 기반)
                renderSeasonDestinations(currentSeason, tag.id);
            };
            if (idx === 0) btn.classList.add('active');
            tagListDiv.appendChild(btn);
        });
    }
}
renderTags();

function createDestinationCard(dest) {
    const card = document.createElement('div');
    card.className = 'trip-card';
    card.innerHTML = `
        <img src="${dest.imageUrl}" alt="${dest.name}" />
        <div class="trip-info">
          <h4>${dest.name}</h4>
          <p>${dest.regionName}</p>
        </div>
    `;
    card.onclick = () => location.href = `/destinations/${dest.id}`;
    return card;
}

// 5. 추천 여행지 뿌리기 (API 연동)
async function renderSeasonDestinations(season, categoryId = null) {
    let url = `/api/season-destinations?season=${season}`;
    if (categoryId) url += `&categoryId=${categoryId}`;
    // API에서 [{id, name, imageUrl, regionName, season, ...}] 형태 반환
    const res = await fetch(url);
    const data = await res.json();
    const listDiv = document.querySelector('.trip-card-list');
    if (!listDiv) return;
    listDiv.innerHTML = '';
    data.forEach(dest => {
        listDiv.appendChild(createDestinationCard(dest));
    });
}

// 6. 페이지 진입 시 기본 여행지(시즌+첫번째 태그)
if (meta.tags.length > 0) {
    renderSeasonDestinations(currentSeason, meta.tags[0].id);
}

// =========== 7. 인기 많은 여행지 추천 (하단 인기 태그/카드) ===========
const popularTags = [
    { label: "국내 인기", api: "/api/popular-destinations/domestic" },
    { label: "해외 인기", api: "/api/popular-destinations/overseas" },
    { label: "역사 여행", api: "/api/popular-destinations/history" },
    { label: "인생샷 여행", api: "/api/popular-destinations/photo" },
    { label: "박물관·미술관", api: "/api/popular-destinations/artmuseum" },
    { label: "수족관·동물원", api: "/api/popular-destinations/zoo" }
];

const popularTagList = document.getElementById('popular-tag-list');
const recommendCardList = document.getElementById('recommend-card-list');

function renderPopularTags() {
    if (!popularTagList) return;
    popularTagList.innerHTML = '';
    popularTags.forEach((tag, idx) => {
        const btn = document.createElement('button');
        btn.className = 'tag';
        btn.textContent = tag.label;
        btn.onclick = () => {
            popularTagList.querySelectorAll('.tag').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            renderPopularRecommend(tag.api);
        };
        if (idx === 0) btn.classList.add('active');
        popularTagList.appendChild(btn);
    });
}

async function renderPopularRecommend(api, limit = 5) {
    if (!recommendCardList) return;
    recommendCardList.innerHTML = '';
    try {
        const res = await fetch(`${api}?limit=${limit}`);
        const data = await res.json();
        if (!Array.isArray(data) || data.length === 0) {
            recommendCardList.innerHTML = '<div class="home-empty-state">데이터가 없습니다.</div>';
            return;
        }
        data.forEach(dest => {
            recommendCardList.appendChild(createDestinationCard(dest));
        });
    } catch (e) {
        recommendCardList.innerHTML = '<div class="home-empty-state">불러오기에 실패했습니다.</div>';
    }
}

// =========== 8. 인기 태그/카드 최초 랜더링 ===========
renderPopularTags();
renderPopularRecommend(popularTags[0].api);
