// comment/api.js

/**
 * 공통 응답 처리 헬퍼
 * @param {Response} res - Fetch API 응답 객체
 * @param {string} label - 작업 라벨 (에러 메시지에 사용)
 * @returns {Promise<any>} JSON 파싱 결과
 */
function handleJson(res, label) {
    if (!res.ok) throw new Error(`${label} 실패: HTTP ${res.status}`);
    const ct = res.headers.get('Content-Type') || '';
    if (!ct.includes('application/json')) {
        throw new Error(`${label} 응답이 JSON이 아닙니다: ${ct}`);
    }
    return res.json();
}

/**
 * URL에서 destinationId 추출
 * @returns {string}
 */
export function getDestinationId() {
    return window.location.pathname.split('/').pop();
}

/**
 * 댓글 등록
 * @param {string|number} destinationId
 * @param {FormData} formData
 * @returns {Promise<Object>} 생성된 댓글 데이터
 */
export function postComment(destinationId, formData) {
    return fetch(`/comments?destinationId=${destinationId}`, {
        method: 'POST',
        body: formData
    }).then(res => handleJson(res, '댓글 등록'));
}

/**
 * 댓글 목록 조회 (페이지 기반 + 정렬 포함)
 * @param {string|number} destinationId
 * @param {number} page - 0부터 시작하는 페이지 번호
 * @param {number} size - 한 페이지에 불러올 댓글 수
 * @param {string} [sort='latest'] - 정렬 방식 ('latest', 'oldest', 'likes')
 * @returns {Promise<Object>} Page<CommentDto> 형태 응답
 */
export function fetchCommentsPage(destinationId, page = 0, size = 5, sort = 'latest') {
    const url = `/comments/list/page?destinationId=${destinationId}&page=${page}&size=${size}&sort=${sort}&_=${Date.now()}`;
    return fetch(url, {
        headers: { 'Accept': 'application/json' },
        cache: 'no-store'
    }).then(res => handleJson(res, '댓글 페이지 로드'));
}

/**
 * 대댓글(답글) 등록
 * @param {string|number} destinationId
 * @param parentCommentId
 * @param {FormData} formData
 * @returns {Promise<Object>} 생성된 대댓글 데이터
 */
export function postReply(destinationId, parentCommentId, formData) {
    formData.append('parentCommentId', parentCommentId);
    return fetch(
        `/comments?destinationId=${destinationId}`,
        { method: 'POST', body: formData }
    )
    .then(res => handleJson(res, '대댓글 등록'));
}

/**
 * 좋아요 토글
 * @param {string|number} commentId
 * @returns {Promise<string>} 'liked' 또는 'unliked'
 */
export function toggleLikeApi(commentId) {
    return fetch(`/comments/${commentId}/like-toggle`, { method: 'POST' })
        .then(res => {
            if (!res.ok) throw new Error(`좋아요 토글 실패: HTTP ${res.status}`);
            return res.text();
        });
}

/**
 * 댓글 삭제
 * @param {string|number} commentId
 * @returns {Promise<Response>}
 */
export function deleteCommentApi(commentId) {
    return fetch(`/comments/${commentId}`, {
        method: 'DELETE',
        credentials: 'include', // 반드시 추가!
        headers: {
            'Content-Type': 'application/json' // 있으면 좋음 (필수는 아님)
        }
    });
}

/**
 * 댓글 수정
 * @param {string|number} commentId
 * @param {Object} body - 수정 데이터 (예: { content: '...' })
 * @returns {Promise<Response>}
 */
export function updateCommentApi(commentId, body) {
    return fetch(`/comments/${commentId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
}

/**
 * 댓글 이미지(썸네일) 목록 조회
 * @param {string|number} destinationId
 * @returns {Promise<Array<{imageUrl: string}>>}
 */
export function fetchThumbnails(destinationId) {
    return fetch(`/comments/images?destinationId=${destinationId}`)
        .then(res => handleJson(res, '썸네일 로드'));
}


