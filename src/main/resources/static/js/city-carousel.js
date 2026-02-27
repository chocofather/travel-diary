/*

function getCurrentSort() {
    return document.querySelector('.sort-btn.active')?.dataset.sort || 'default';
}

function getCurrentType() {
    return document.body.dataset.type || 'domestic';
}

function getCurrentRegionId() {
    // subregion-btn이 우선, 없으면 상위 region-btn
    const subBtn = document.querySelector('.subregion-btn.selected');
    if (subBtn) return subBtn.dataset.cityId;
    const regionBtn = document.querySelector('.region-btn.selected');
    return regionBtn ? regionBtn.dataset.regionId : '';
}

// 정렬 버튼 바인딩
function bindSortButtons() {
    document.querySelectorAll('.sort-btn').forEach(btn => {
        btn.onclick = function (e) {
            e.preventDefault();
            document.querySelectorAll('.sort-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const regionId = getCurrentRegionId();
            const sort = btn.dataset.sort || 'default';
            const type = getCurrentType();
            let url = `/destinations/fragment?type=${type}&page=1&sort=${sort}`;
            if (regionId) url += `&region=${regionId}`;
            fetch(url, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
                .then(res => res.text())
                .then(html => {
                    document.getElementById('destination-list').innerHTML = html;
                    rebind();
                });
        };
    });
}

// 하위 서브지역 좌우 스크롤 화살표
function bindSubregionScrollArrows() {
    const scrollContainer = document.querySelector('.subregion-scroll-container');
    document.querySelectorAll('.subregion-arrow').forEach(btn => {
        if (!scrollContainer) return;
        btn.onclick = () => scrollContainer.scrollBy({
            left: btn.classList.contains('prev') ? -150 : 150,
            behavior: 'smooth'
        });
    });
}

// 페이징 바인딩
function bindFragmentPagination() {
    document.querySelectorAll('#destination-list .pagination a').forEach(link => {
        link.onclick = e => {
            e.preventDefault();
            const url = new URL(link.href, window.location.origin);
            const sort = getCurrentSort();
            url.searchParams.set('sort', sort);
            fetch(url.toString(), { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
                .then(res => res.text())
                .then(html => {
                    document.getElementById('destination-list').innerHTML = html;
                    rebind();
                });
        };
    });
}

// 공통 재바인딩(새 fragment마다 적용)
function rebind() {
    bindFragmentPagination();
    bindSubregionScrollArrows();
    bindSortButtons();
    if (window.initBookmarkButton) window.initBookmarkButton();
}

// 전체 이벤트 위임 (상위/하위)
document.addEventListener('DOMContentLoaded', () => {
    rebind();

    document.addEventListener('click', function (e) {
        // 1. 상위 region-btn 클릭: list.html 전체(region-fragment-container)를 교체
        const regionBtn = e.target.closest('.region-btn');
        if (regionBtn) {
            // region-bar-wrapper, subregion-selector, destination-list 전체 교체!
            const regionId = regionBtn.dataset.regionId;
            const type = getCurrentType();
            const sort = getCurrentSort();
            // 반드시 list.html 전체 반환(ajax 헤더 확인)
            fetch(`/destinations?type=${type}&region=${regionId}&sort=${sort}`, {
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            })
                .then(res => res.text())
                .then(html => {
                    document.getElementById('region-fragment-container').outerHTML =
                        html.match(/<div[^>]+id=["']region-fragment-container["'][^>]*>[\s\S]*?<\/div>/)?.[0] || html;
                    // 새로 그려진 region-fragment-container의 내부에 이벤트 재바인딩
                    rebind();
                });
            return;
        }

        // 2. 하위(subregion-btn) 클릭: 여행지 리스트만 교체
        const subBtn = e.target.closest('.subregion-btn');
        if (subBtn) {
            document.querySelectorAll('.subregion-btn.selected').forEach(btn => btn.classList.remove('selected'));
            subBtn.classList.add('selected');
            const regionId = subBtn.dataset.cityId;
            const type = getCurrentType();
            const sort = getCurrentSort();
            fetch(`/destinations/fragment?type=${type}&region=${regionId}&page=1&sort=${sort}`, {
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            })
                .then(res => res.text())
                .then(html => {
                    const container = document.getElementById('destination-list');
                    const title = document.getElementById('page-title');
                    if (container) container.innerHTML = html;
                    if (title) title.textContent = subBtn.textContent + ' 여행지';
                    rebind();
                });
            e.preventDefault();
            return;
        }
    });
});

*/
