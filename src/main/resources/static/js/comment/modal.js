// comment/modal.js

/**
 * 사진 모달 초기화.
 * @param {string} modalSel   모달 컨테이너 셀렉터
 * @param {string} sidebarSel 사이드바 썸네일 컨테이너 셀렉터
 * @param {string} mainSel    메인 이미지 셀렉터
 * @returns {(startIndex: number, items: Array<{imageUrl: string}>) => void} openModal 함수
 */
export function initPhotoModal(modalSel, sidebarSel, mainSel) {
    const modal   = document.querySelector(modalSel);
    const sidebar = document.querySelector(sidebarSel);
    const mainImg = document.querySelector(mainSel);
    let list = [];
    let idx = 0;

    /**
     * 사이드바 썸네일 렌더링
     */
    function renderSidebar() {
        if (!sidebar) return;
        sidebar.innerHTML = '';
        list.forEach((item, i) => {
            const thumb = document.createElement('img');
            thumb.src = item.imageUrl;
            thumb.className = 'thumbnail-item';
            if (i === idx) thumb.classList.add('active');
            thumb.addEventListener('click', () => show(i));
            sidebar.appendChild(thumb);
        });
    }

    /**
     * 메인 이미지 표시 및 사이드바 업데이트
     * @param {number} i 현재 인덱스
     */
    function show(i) {
        idx = i;
        if (mainImg) mainImg.src = list[idx].imageUrl;
        renderSidebar();
        if (modal) modal.style.display = 'flex';
    }

    function hide() {
        if (modal) modal.style.display = 'none';
    }

    function prev() {
        show((idx - 1 + list.length) % list.length);
    }

    function next() {
        show((idx + 1) % list.length);
    }

    // 모달 내 버튼 바인딩
    if (modal) {
        const closeBtn = modal.querySelector('.photo-close');
        const prevBtn  = modal.querySelector('.photo-prev');
        const nextBtn  = modal.querySelector('.photo-next');
        const mainImg  = modal.querySelector('#main-photo'); // 메인 이미지

        if (closeBtn) closeBtn.onclick = hide;
        if (prevBtn)  prevBtn.onclick  = prev;
        if (nextBtn)  nextBtn.onclick  = next;

        // 메인 이미지 클릭 시만 닫기
        if (mainImg) {
            mainImg.addEventListener('click', hide);
        }

        /*// 모달 배경 클릭 시 닫기
        modal.addEventListener('click', e => {
            if (e.target === modal) {
                hide();
            }
        });*/
    }

    // ESC 키로 닫기
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape') hide();
    });

    /**
     * openModal 호출 시 내부 리스트와 인덱스 설정 후 모달 표시
     * @param {number} startIndex
     * @param {Array<{imageUrl: string}>} items
     */
    return function openModal(startIndex, items) {
        list = items;
        show(startIndex);
    };
}