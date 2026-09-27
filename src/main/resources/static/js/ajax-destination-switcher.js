let currentRegionBarDepth = null;

// region-bar(상단) 전용 rebind 및 depth 저장
function rebindRegionBar() {
    bindRegionScrollArrows();
    const firstBtn = document.querySelector('.region-btn');
    currentRegionBarDepth = firstBtn ? parseInt(firstBtn.dataset.depth, 10) : null;
}

// fragment(카드/서브) 전용 rebind
function rebindList() {
    bindFragmentPagination();
    bindSubregionScrollArrows();
    bindSortButtons();
    bindCategoryFilter();
    if (window.initBookmarkButton) window.initBookmarkButton();
}

// 항상 region-bar-wrapper에서 type 읽기
function getCurrentType() {
    const regionBar = document.querySelector('.region-bar-wrapper');
    return regionBar?.dataset.type || document.body.dataset.type || 'domestic';
}

// 현재 sort, region id 가져오기
function getCurrentSort() {
    return document.querySelector('.sort-btn.active')?.dataset.sort || 'default';
}
function getCurrentRegionId() {
    const sub = document.querySelector('.subregion-btn.selected');
    if (sub) return sub.dataset.cityId;
    const sel = document.querySelector('.region-btn.selected');
    return sel ? sel.dataset.regionId : '';
}

// 지금 목록의 고른 카테고리(하나, 없으면 '')와 쪽 크기. 서버가 #destination-list 에 적어 둔 값이다.
function getCurrentCategory() {
    const value = document.getElementById('destination-list')?.dataset.category || '';
    return /^\d+$/.test(value) ? value : '';
}
function getCurrentPageSize() {
    return document.getElementById('destination-list')?.dataset.pageSize || '12';
}

/*
  주소창을 지금 보이는 목록과 같게 맞춘다. 새로고침·공유·상세에서 뒤로 와도 같은 목록이 열린다.
  history: 'replace'(지역·정렬·쪽) | 'push'(카테고리 바꾸기 — 뒤로 가기로 이전 카테고리로 돌아간다) | 'none'(뒤로 가기 복원)
*/
function syncAddressBar(requestUrl, history = 'replace') {
    if (history === 'none') return;
    const source = new URL(requestUrl, window.location.origin).searchParams;
    const params = new URLSearchParams();
    const skipDefault = {sort: 'default', page: '1', size: '12'};
    ['type', 'region', 'sort', 'page', 'size', 'category'].forEach(name => {
        const value = source.get(name);
        if (value && value !== skipDefault[name]) params.set(name, value);
    });
    const query = params.toString();
    const url = '/destinations' + (query ? `?${query}` : '');
    if (history === 'push') {
        window.history.pushState({destinationList: true}, '', url);
    } else {
        window.history.replaceState(window.history.state, '', url);
    }
}

// region-bar + 리스트 전체를 주어진 주소의 조각으로 바꾼다.
function replaceRegionFragment(url, history = 'replace') {
    return fetch(url, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
        .then(r => r.text())
        .then(html => {
            const tmp = document.createElement('div');
            tmp.innerHTML = html;
            const nr = tmp.querySelector('#region-fragment-container');
            if (nr) {
                document.getElementById('region-fragment-container').replaceWith(nr);
                rebindRegionBar();
                rebindList();
                syncAddressBar(url, history);
            } else {
                console.error('region-fragment-container가 응답에 없음');
            }
        });
}

// region-bar + 리스트 전체 교체 (고른 카테고리는 유지)
function fetchRegionFragment(type, regionId, sort) {
    const params = new URLSearchParams({type});
    if (regionId) params.set('region', regionId);
    params.set('sort', sort);
    params.set('size', getCurrentPageSize());
    const category = getCurrentCategory();
    if (category) params.set('category', category);
    return replaceRegionFragment(`/destinations/fragment?${params}`);
}

// 리스트만 교체 (type, region, sort, page, size, category 모두 URL에서 읽어옴)
function fetchListFragmentByUrl(url, history = 'replace') {
    return fetch(url, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
        .then(r => r.text())
        .then(html => {
            const tmp = document.createElement('div');
            tmp.innerHTML = html;
            const dl = tmp.querySelector('#destination-list');
            if (dl) {
                document.getElementById('destination-list').replaceWith(dl);
                rebindList();
                syncAddressBar(url, history);
            } else {
                console.error('#destination-list가 응답에 없음');
            }
        });
}

// 지금 지역·정렬·쪽 크기·카테고리에 바꿀 값만 덮은 목록 주소 (page 는 1 로)
function listFragmentUrl(overrides) {
    const params = new URLSearchParams();
    params.set('type', getCurrentType());
    const region = overrides.region ?? getCurrentRegionId();
    if (region) params.set('region', region);
    params.set('sort', overrides.sort ?? getCurrentSort());
    params.set('page', '1');
    params.set('size', getCurrentPageSize());
    const category = 'category' in overrides ? overrides.category : getCurrentCategory();
    if (category) params.set('category', category);
    return `/destinations/list-fragment?${params}`;
}

// 정렬 버튼 바인딩 (1페이지로 돌아감, 쪽 크기·카테고리는 그대로)
function bindSortButtons() {
    document.querySelectorAll('.sort-btn').forEach(btn => {
        btn.onclick = e => {
            e.preventDefault();
            document.querySelectorAll('.sort-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            fetchListFragmentByUrl(listFragmentUrl({sort: btn.dataset.sort || 'default'}));
        };
    });
}

// ─── 카테고리 필터(하나만 고른다) ───
// 메뉴에서 카테고리를 누르면 바로 그 카테고리 목록(1쪽)으로 바뀌고 메뉴가 닫힌다. '전체'는 필터를 푼다.
// 카테고리를 바꿀 때마다 방문 기록을 남겨 뒤로 가기로 이전 카테고리로 돌아간다.
function categoryFilterParts() {
    const filter = document.querySelector('[data-category-filter]');
    if (!filter) return null;
    return {
        toggle: filter.querySelector('[data-category-filter-toggle]'),
        panel: filter.querySelector('[data-category-filter-panel]'),
        options: [...filter.querySelectorAll('[data-category-filter-option]')]
    };
}

function openCategoryPanel() {
    const parts = categoryFilterParts();
    if (!parts) return;
    parts.panel.hidden = false;
    parts.toggle.setAttribute('aria-expanded', 'true');
    (parts.options.find(option => option.getAttribute('aria-pressed') === 'true') || parts.options[0])?.focus();
}

function closeCategoryPanel(focusToggle) {
    const parts = categoryFilterParts();
    if (!parts || parts.panel.hidden) return;
    parts.panel.hidden = true;
    parts.toggle.setAttribute('aria-expanded', 'false');
    if (focusToggle) parts.toggle.focus();
}

function selectCategory(category) {
    closeCategoryPanel(true);
    if (category === getCurrentCategory()) return;
    fetchListFragmentByUrl(listFragmentUrl({category}), 'push')
        .then(() => document.querySelector('[data-category-filter-toggle]')?.focus());
}

function bindCategoryFilter() {
    const parts = categoryFilterParts();
    if (!parts) return;
    parts.toggle.onclick = () => (parts.panel.hidden ? openCategoryPanel() : closeCategoryPanel(false));
    parts.options.forEach(option => {
        option.onclick = () => selectCategory(option.value);
    });
}

// 뒤로·앞으로 가기: 그 주소의 지역·정렬·카테고리 목록을 다시 그린다(주소는 이미 맞으므로 기록은 건드리지 않는다).
function restoreListFromAddress() {
    replaceRegionFragment(`/destinations/fragment${window.location.search}`, 'none');
}

// 페이징 바인딩 (href 그대로 사용, sort/type만 덮어쓰기)
function bindFragmentPagination() {
    document.querySelectorAll('#destination-list .pagination a').forEach(link => {
        link.onclick = e => {
            e.preventDefault();
            const url = new URL(link.href, window.location.origin);
            url.searchParams.set('sort', getCurrentSort());
            url.searchParams.set('type', getCurrentType());
            fetchListFragmentByUrl(url.toString());
        };
    });
}

// 상단 캐러셀 좌우
function bindRegionScrollArrows() {
    const cont = document.querySelector('.region-buttons.scrollable');
    document.querySelectorAll('.region-selector .arrow').forEach(btn => {
        if (!cont) return;
        btn.onclick = () => cont.scrollBy({
            left: btn.classList.contains('prev') ? -200 : 200,
            behavior: 'smooth'
        });
    });
}

// 하위 서브지역 좌우
function bindSubregionScrollArrows() {
    const cont = document.querySelector('.subregion-scroll-container');
    document.querySelectorAll('.subregion-arrow').forEach(btn => {
        if (!cont) return;
        btn.onclick = () => cont.scrollBy({
            left: btn.classList.contains('prev') ? -150 : 150,
            behavior: 'smooth'
        });
    });
}

// 최초 바인딩 & 클릭 위임
document.addEventListener('DOMContentLoaded', () => {
    rebindRegionBar();
    rebindList();

    document.addEventListener('click', e => {
        // 1) 상단 아이콘(region-btn) 클릭
        const rb = e.target.closest('.region-btn');
        if (rb) {
            e.preventDefault();
            const regionId = rb.dataset.regionId;
            const btnDepth = parseInt(rb.dataset.depth, 10);
            const type = getCurrentType();
            const sort = getCurrentSort();

            // depth 변경이면 전체 교체
            if (!document.querySelector('.region-btn.selected') || btnDepth !== currentRegionBarDepth) {
                fetchRegionFragment(type, regionId, sort);
            } else {
                // 같은 depth 이동: 리스트만 (정렬·쪽 크기·카테고리 유지, 다른 지역이므로 1쪽부터)
                document.querySelectorAll('.region-btn.selected')
                    .forEach(b => b.classList.remove('selected'));
                rb.classList.add('selected');
                fetchListFragmentByUrl(listFragmentUrl({region: regionId, sort}));
            }
            return;
        }

        // 2) 서브지역(subregion-btn) 클릭
        const sb = e.target.closest('.subregion-btn');
        if (sb) {
            e.preventDefault();
            document.querySelectorAll('.subregion-btn.selected')
                .forEach(b => b.classList.remove('selected'));
            sb.classList.add('selected');
            // 서브는 page=1로, 카테고리는 유지
            fetchListFragmentByUrl(listFragmentUrl({region: sb.dataset.cityId}));
            return;
        }

        // 3) 카테고리 메뉴 바깥을 누르면 닫는다.
        if (!e.target.closest('[data-category-filter]')) closeCategoryPanel(false);
    });

    document.addEventListener('keydown', e => {
        if (e.key === 'Escape') closeCategoryPanel(true);
    });

    window.addEventListener('popstate', restoreListFromAddress);
});
