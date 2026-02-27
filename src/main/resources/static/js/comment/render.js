/**
 * @닉네임 하이라이트(파란색, 클릭) 변환 함수
 */
function highlightMentions(content) {
    if (!content) return '';
    return content.replace(/@([^\s@]+)/g, (match, nickname) =>
        `<a href="/profile/${encodeURIComponent(nickname)}" class="mention" onclick="event.stopPropagation()">@${nickname}</a>`
    );
}

/**
 * 부모 ID별로 댓글을 그룹화
 */
export function groupByParent(comments) {
    return comments.reduce((acc, c) => {
        const key = c.parentCommentId ?? null;
        acc[key] = acc[key] || [];
        acc[key].push(c);
        return acc;
    }, {});
}

/**
 * 단일 댓글 LI 생성
 */
export function createCommentItem(comment, depth = 0, parentNickname = '') {
    const li = document.createElement('li');
    li.className = 'comment-item' + (depth > 0 ? ' reply' : '');
    li.dataset.id = comment.id;
    if (depth === 1) {
        li.dataset.parentId = comment.parentCommentId;
        li.dataset.writerNickname = parentNickname;
    }
    const profileUrl = comment.writer?.profileImage || '/images/default.png';
    const nickname   = comment.writer?.nickname     || '알 수 없음';
    const isWriter   = comment.writer?.isWriter === true;
    const isReply    = depth > 0;
    const isLoggedIn = comment.isLoggedIn === true;

    li.innerHTML = `
        <div class="comment-header">
            <img src="${profileUrl}" class="comment-profile" alt="프로필 이미지">
            <span class="comment-nickname">${nickname}</span>
            ${isReply && isWriter ? `<span class="comment-author-tag">작성자</span>` : ''}
            ${comment.updatedAt !== comment.createdAt
        ? '<span class="edited-tag"><img src="/uploads/icons/note.png" alt="수정됨 아이콘"> 수정됨</span>'
        : ''}
        </div>
        <p class="comment-content">${highlightMentions(comment.content)}</p>
        ${comment.imageUrl ? `<img src="${comment.imageUrl}" class="comment-image" alt="댓글 이미지">` : ''}
        <div class="comment-actions">
            <button class="like-btn" data-id="${comment.id}">
                <img src="${comment.likedByMe ? '/uploads/icons/like2.png' : '/uploads/icons/like.png'}" alt="좋아요" class="likeicon">
                <span>${comment.likes}</span>
            </button>
            ${comment.myComment || comment.admin ? `
                <button class="edit-btn">수정</button>
                <button class="delete-btn">삭제</button>
            ` : ''}
            ${isLoggedIn ? `<button class="reply-btn">답글달기</button>` : ''}
        </div>
    `;
    return li;
}

/**
 * 댓글/답글 append (초기화X)
 * comments: 추가로 붙일 것만 (보통 새댓글/새대댓글 한 개 or 더보기 불러온 배열)
 */
export function appendComments(comments, container) {
    if (!Array.isArray(comments)) return;

    // 각 댓글/대댓글 별로 올바른 위치에 append
    comments.forEach(comment => {
        // 부모 댓글이 있으면, 부모의 .reply-list에 append
        if (comment.parentCommentId) {
            const parentLi = container.querySelector(`.comment-item[data-id="${comment.parentCommentId}"]`);
            if (!parentLi) return;
            let replyUl = parentLi.querySelector('.reply-list');
            if (!replyUl) {
                replyUl = document.createElement('ul');
                replyUl.className = 'reply-list';
                parentLi.appendChild(replyUl);
            }
            replyUl.appendChild(createCommentItem(comment, 1, comment.writer?.nickname || ''));
        } else {
            // 원댓글(부모 없음)은 컨테이너에 append
            container.appendChild(createCommentItem(comment, 0, ''));
        }
    });
}

/**
 * 전체 초기화 후 모든 댓글/답글 트리 렌더
 */
export function renderComments(comments, container) {
    // (1) container가 반드시 <ul>인지 체크
    if (!(container instanceof HTMLUListElement)) {
        console.error('container must be a <ul>!');
        // 여기서 <ul>이 아니라면 아래처럼 새로 만들어서 교체해줘도 됨
        const newUl = document.createElement('ul');
        newUl.id = container.id;
        container.replaceWith(newUl);
        container = newUl;
    }
    container.innerHTML = '';
    if (!Array.isArray(comments)) return;
    const ids = new Set(comments.map(c => c.id));
    const filtered = comments.filter(c =>
        c.parentCommentId == null || ids.has(c.parentCommentId)
    );
    const grouped = groupByParent(filtered);
    (grouped.null || []).forEach(root => renderFlatTree(grouped, root, 0, container));
}

function renderFlatTree(grouped, comment, depth, parentUl, parentNickname = '') {
    // (2) LI 생성/append만 사용 (이미 잘 되어있음)
    const li = createCommentItem(comment, depth > 1 ? 1 : depth, parentNickname);
    parentUl.appendChild(li);
    const children = grouped[comment.id] || [];
    if (children.length) {
        const childUl = document.createElement('ul');
        childUl.className = 'reply-list';
        li.appendChild(childUl);
        children.forEach(child =>
            renderFlatTree(grouped, child, (depth > 0 ? 1 : depth + 1), childUl, comment.writer?.nickname || '')
        );
    }
}


/**
 * 기타(이미지, 단순리스트 등은 동일)
 */

export function renderThumbnails(thumbnails, containerSelector, openModalFn) {
    if (!Array.isArray(thumbnails)) {
        console.error('renderThumbnails: thumbnails is not an array', thumbnails);
        return;
    }
    const container = document.querySelector(containerSelector);
    if (!container) {
        console.error('renderThumbnails: container not found', containerSelector);
        return;
    }
    container.innerHTML = '';
    thumbnails.forEach((t, idx) => {
        const img = document.createElement('img');
        img.src = t.imageUrl;
        img.className = 'photo-thumbnail';
        img.alt = '사진 후기';
        img.addEventListener('click', () => openModalFn(idx, thumbnails));
        container.appendChild(img);
    });
}

export function renderCommentList(comments) {
    const container = document.getElementById('comment-list');
    container.innerHTML = comments.map(comment => `
        <div class="comment-item">${highlightMentions(comment.content)}</div>
    `).join('');
}

