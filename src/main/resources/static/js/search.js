// /js/search.js
document.addEventListener('DOMContentLoaded', () => {
    // 1. 쿼리스트링에서 검색어 추출
    const urlParams = new URLSearchParams(window.location.search);
    const keyword = urlParams.get('q')?.trim();

    // ★ 하이라이트 함수 추가
    function highlight(text, keyword) {
        if (!text || !keyword) return text || '';
        // XSS 방지
        const esc = s => s.replace(/[&<>"']/g, ch => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;"
        }[ch]));
        // 하이라이트: 대소문자 구분 없이
        return esc(text).replace(
            new RegExp(`(${esc(keyword)})`, "gi"),
            '<mark class="search-highlight">$1</mark>'
        );
    }

    // 검색어가 없으면 안내
    if (!keyword) {
        document.getElementById('search-keyword').textContent = '';
        document.getElementById('search-count').textContent = '0';
        document.getElementById('search-list').innerHTML = '<p class="no-result">검색어를 입력해주세요.</p>';
        return;
    }

    // 화면에 검색어 표시
    document.getElementById('search-keyword').textContent = keyword;

    // 2. 검색 API 호출
    fetch(`/api/search?q=${encodeURIComponent(keyword)}`)
        .then(res => res.json())
        .then(list => {
            document.getElementById('search-count').textContent = list.length;
            const listElem = document.getElementById('search-list');

            if (list.length === 0) {
                listElem.innerHTML = `<p class="no-result">"${keyword}"에 해당하는 결과가 없습니다.</p>`;
                return;
            }

            // ★ 한 줄 리스트 스타일로 렌더링 + 하이라이트
            listElem.innerHTML = list.map(item => `
                <div class="search-row-card" data-id="${item.id}">
                    <img src="${item.thumbnailUrl || '/images/no-image.svg'}" alt="">
                    <div class="search-row-info">
                        <div class="search-row-title">${highlight(item.name, keyword)}</div>
                        <div class="search-row-desc">${highlight(item.shortDescription || '', keyword)}</div>
                        <div class="search-row-region">
                          <span>${highlight((item.parentRegionName || '') + ' ' + (item.regionName || ''), keyword)}</span>
                        </div>
                    </div>
                </div>
            `).join('');

            // 카드 클릭 → 상세페이지 이동 (경로: /destinations/아이디)
            listElem.querySelectorAll('.search-row-card').forEach(card => {
                card.addEventListener('click', () => {
                    const id = card.getAttribute('data-id');
                    if (id) location.href = `/destinations/${id}`;
                });
            });
        })
        .catch(err => {
            document.getElementById('search-list').innerHTML = `<p class="no-result">검색 중 오류가 발생했습니다.</p>`;
            console.error(err);
        });
});
