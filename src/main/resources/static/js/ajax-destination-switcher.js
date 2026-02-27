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

// region-bar + 리스트 전체 교체
function fetchRegionFragment(type, regionId, sort) {
    const url = `/destinations/fragment?type=${type}`
        + (regionId ? `&region=${regionId}` : '')
        + `&sort=${sort}`;
    fetch(url, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
        .then(r => r.text())
        .then(html => {
            const tmp = document.createElement('div');
            tmp.innerHTML = html;
            const nr = tmp.querySelector('#region-fragment-container');
            if (nr) {
                document.getElementById('region-fragment-container').replaceWith(nr);
                rebindRegionBar();
                rebindList();
            } else {
                console.error('region-fragment-container가 응답에 없음');
            }
        });
}

// 리스트만 교체 (type, region, sort, page, size 모두 URL에서 읽어옴)
function fetchListFragmentByUrl(url) {
    fetch(url, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
        .then(r => r.text())
        .then(html => {
            const tmp = document.createElement('div');
            tmp.innerHTML = html;
            const dl = tmp.querySelector('#destination-list');
            if (dl) {
                document.getElementById('destination-list').replaceWith(dl);
                rebindList();
            } else {
                console.error('#destination-list가 응답에 없음');
            }
        });
}

// 정렬 버튼 바인딩 (1페이지로 돌아감)
function bindSortButtons() {
    document.querySelectorAll('.sort-btn').forEach(btn => {
        btn.onclick = e => {
            e.preventDefault();
            document.querySelectorAll('.sort-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const type = getCurrentType();
            const region = getCurrentRegionId();
            const sort = btn.dataset.sort || 'default';
            // 항상 page=1, size 그대로
            const url = new URL('/destinations/list-fragment', window.location.origin);
            url.searchParams.set('type', type);
            if (region) url.searchParams.set('region', region);
            url.searchParams.set('sort', sort);
            url.searchParams.set('page', '1');
            // size는 기존 페이지에 있던 size 가져오기
            const currentSize = new URLSearchParams(window.location.search).get('size');
            url.searchParams.set('size', currentSize || '5');
            fetchListFragmentByUrl(url.toString());
        };
    });
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
                // 같은 depth 이동: 리스트만
                document.querySelectorAll('.region-btn.selected')
                    .forEach(b => b.classList.remove('selected'));
                rb.classList.add('selected');
                // page/size 유지
                const params = new URLSearchParams(window.location.search);
                params.set('type', type);
                params.set('region', regionId);
                params.set('sort', sort);
                const url = `/destinations/list-fragment?${params.toString()}`;
                fetchListFragmentByUrl(url);
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
            const regionId = sb.dataset.cityId;
            const type = getCurrentType();
            const sort = getCurrentSort();
            const params = new URLSearchParams(window.location.search);
            params.set('type', type);
            params.set('region', regionId);
            params.set('sort', sort);
            // 서브는 page=1로
            params.set('page', '1');
            const url = `/destinations/list-fragment?${params.toString()}`;
            fetchListFragmentByUrl(url);
        }
    });
});
