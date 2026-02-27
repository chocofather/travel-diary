function loadBoardList(page, sort) {
    // 필요한 추가 파라미터(boardType, postType 등)도 동적으로 추가 가능
    fetch(`/board/fragment?page=${page}&sort=${sort}`)
        .then(res => res.text())
        .then(html => {
            document.getElementById('board-fragment-container').innerHTML = html;
        });
}
