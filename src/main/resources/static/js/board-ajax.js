function loadBoardList(page, sort) {
    const params = new URLSearchParams(window.location.search);
    params.set('page', Math.max(Number(page) || 1, 1).toString());
    params.set('sort', sort || 'latest');

    if (!params.has('size')) {
        params.set('size', '10');
    }

    fetch(`/board/fragment?${params.toString()}`)
        .then(res => {
            if (!res.ok) {
                throw new Error(`게시판 목록 요청 실패: ${res.status}`);
            }
            return res.text();
        })
        .then(html => {
            document.getElementById('board-fragment-container').innerHTML = html;
        })
        .catch(error => {
            console.error(error);
            alert('게시판 목록을 불러오지 못했습니다.');
        });
}
