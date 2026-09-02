const homeI18n = document.getElementById('home-i18n').dataset;

function localizedTags(labels, ids) {
    return labels.split('|').map((label, index) => ({label, id: ids[index]}));
}

// 1. 시즌별 메타데이터 (각 태그: label, id)
const seasonMeta = {
    SPRING: {
        badge: homeI18n.springBadge,
        title: homeI18n.springTitle,
        desc: homeI18n.springDescription,
        bgClass: 'spring',
        monthRange: [3,     4, 5],
        tags: localizedTags(homeI18n.springTags, [89, 91, 88, 90, 93])
    },
    SUMMER: {
        badge: homeI18n.summerBadge,
        title: homeI18n.summerTitle,
        desc: homeI18n.summerDescription,
        bgClass: 'summer',
        monthRange: [6, 7, 8],
        tags: localizedTags(homeI18n.summerTags, [6, 94, 96, 23, 97])
    },
    FALL: {
        badge: homeI18n.fallBadge,
        title: homeI18n.fallTitle,
        desc: homeI18n.fallDescription,
        bgClass: 'fall',
        monthRange: [9, 10, 11],
        tags: localizedTags(homeI18n.fallTags, [46, 98, 99])
    },
    WINTER: {
        badge: homeI18n.winterBadge,
        title: homeI18n.winterTitle,
        desc: homeI18n.winterDescription,
        bgClass: 'winter',
        monthRange: [12, 1, 2],
        tags: localizedTags(homeI18n.winterTags, [100, 30, 101, 102, 103])
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
const popularTags = localizedTags(homeI18n.popularTags, [
    "/api/popular-destinations/domestic",
    "/api/popular-destinations/overseas",
    "/api/popular-destinations/history",
    "/api/popular-destinations/photo",
    "/api/popular-destinations/artmuseum",
    "/api/popular-destinations/zoo"
]).map(tag => ({label: tag.label, api: tag.id}));

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
            const emptyState = document.createElement('div');
            emptyState.className = 'home-empty-state';
            emptyState.textContent = homeI18n.destinationEmpty;
            recommendCardList.appendChild(emptyState);
            return;
        }
        data.forEach(dest => {
            recommendCardList.appendChild(createDestinationCard(dest));
        });
    } catch (e) {
        const errorState = document.createElement('div');
        errorState.className = 'home-empty-state';
        errorState.textContent = homeI18n.destinationError;
        recommendCardList.appendChild(errorState);
    }
}

// =========== 8. 인기 태그/카드 최초 랜더링 ===========
renderPopularTags();
renderPopularRecommend(popularTags[0].api);
