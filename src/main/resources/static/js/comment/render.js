const defaultProfileImage = '/images/default.png';
const unlikedIcon = '/uploads/icons/like.png';
const likedIcon = '/uploads/icons/like2.png';

function parseDate(value) {
    if (!value) return null;
    const normalized = typeof value === 'string'
        ? value.trim().replace(/^(\d{4}-\d{2}-\d{2})\s/, '$1T')
        : value;
    const date = new Date(normalized);
    return Number.isFinite(date.getTime()) ? date : null;
}

function formatDate(value) {
    const date = parseDate(value);
    if (!date) return '';
    const twoDigits = number => String(number).padStart(2, '0');
    return `${twoDigits(date.getFullYear() % 100)}.${twoDigits(date.getMonth() + 1)}.${twoDigits(date.getDate())} `
        + `${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}`;
}

function isEdited(comment) {
    const createdAt = parseDate(comment.createdAt)?.getTime();
    const updatedAt = parseDate(comment.updatedAt)?.getTime();
    return Number.isFinite(createdAt) && Number.isFinite(updatedAt) && updatedAt > createdAt;
}

function profileImageUrl(value) {
    if (typeof value !== 'string' || !value.trim()) return defaultProfileImage;
    const trimmed = value.trim();
    return /^(?:https?:|data:|blob:|\/)/i.test(trimmed) ? trimmed : `/${trimmed}`;
}

function bindProfileFallback(image) {
    image?.addEventListener('error', () => {
        if (image.dataset.fallbackApplied === 'true') return;
        image.dataset.fallbackApplied = 'true';
        image.src = defaultProfileImage;
    });
}

function makePublicProfileLink(userId, child, className) {
    if (userId == null) return child;
    const link = document.createElement('a');
    link.className = className;
    link.href = `/users/${encodeURIComponent(String(userId))}`;
    link.append(child);
    return link;
}

/**
 * @닉네임 하이라이트(파란색, 클릭) 변환 함수
 */
function highlightMentions(content) {
    if (!content) return '';
    return content.replace(/@([^\s@]+)/g, (match, nickname) =>
        `<span class="mention content-comment-mention">@${nickname}</span>`
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
    li.className = 'comment-item content-comment-item' + (depth > 0 ? ' reply' : '');
    li.dataset.id = comment.id;
    if (comment.writer?.id != null) li.dataset.userId = String(comment.writer.id);
    if (depth === 1) {
        li.dataset.parentId = comment.parentCommentId;
        li.dataset.writerNickname = parentNickname;
    }
    // 관리자 조치된 댓글: 트리(대댓글)는 유지하고 본문만 안내 문구로 대체한다.
    if (comment.moderated === true || comment.deleted === true) {
        li.classList.add('content-comment-deleted');
        const moderatedBody = document.createElement('div');
        moderatedBody.className = 'content-comment-body';
        const moderatedText = document.createElement('p');
        moderatedText.className = 'comment-content content-comment-text content-comment-deleted-text';
        moderatedText.textContent = '관리자에 의해 조치된 댓글입니다.';
        moderatedBody.append(moderatedText);
        const moderatedCard = document.createElement('div');
        moderatedCard.className = 'content-comment-card';
        moderatedCard.append(moderatedBody);
        li.append(moderatedCard);
        return li;
    }

    const profileUrl = profileImageUrl(comment.writer?.profileImage);
    const nickname   = comment.writer?.nickname     || '알 수 없음';
    const isWriter   = comment.writer?.isWriter === true;
    const isLoggedIn = comment.isLoggedIn === true;
    const edited = isEdited(comment);
    const likeControl = isLoggedIn
        ? `<button type="button" class="like-btn content-comment-action content-comment-like${comment.likedByMe ? ' is-liked' : ''}"
                   data-id="${comment.id}" aria-pressed="${Boolean(comment.likedByMe)}">
                <img src="${comment.likedByMe ? likedIcon : unlikedIcon}" alt="" aria-hidden="true"
                     class="likeicon content-comment-like-icon">
                <span class="content-comment-sr-only">좋아요</span>
                <span class="content-comment-like-count">${comment.likes ?? 0}</span>
            </button>`
        : `<span class="content-comment-like-readonly content-comment-like">
                <img src="${comment.likedByMe ? likedIcon : unlikedIcon}" alt="" aria-hidden="true"
                     class="likeicon content-comment-like-icon">
                <span class="content-comment-sr-only">좋아요</span>
                <span class="content-comment-like-count">${comment.likes ?? 0}</span>
            </span>`;

    const profileImage = document.createElement('img');
    profileImage.src = profileUrl;
    profileImage.className = 'comment-profile content-comment-avatar';
    profileImage.alt = `${nickname} 프로필 이미지`;
    bindProfileFallback(profileImage);

    const nicknameElement = document.createElement('span');
    nicknameElement.className = 'comment-nickname content-comment-nickname';
    nicknameElement.textContent = nickname;

    const body = document.createElement('div');
    body.className = 'content-comment-body';
    body.innerHTML = `
        <div class="comment-header content-comment-header">
            ${isWriter ? `<span class="comment-author-tag content-comment-author-tag">작성자</span>` : ''}
            <span class="content-comment-meta">
                <time datetime="${comment.createdAt || ''}">${formatDate(comment.createdAt)}</time>
                ${edited ? '<span class="edited-tag content-comment-edited">· 수정됨</span>' : ''}
            </span>
        </div>
        <p class="comment-content content-comment-text">${highlightMentions(comment.content)}</p>
        ${comment.imageUrl ? `<img src="${comment.imageUrl}" class="comment-image content-comment-image" alt="댓글 이미지">` : ''}
        <div class="comment-actions content-comment-actions">
            ${likeControl}
            ${isLoggedIn ? '<button type="button" class="reply-btn content-comment-action">답글</button>' : ''}
            ${comment.myComment || comment.admin ? `
                <button type="button" class="edit-btn content-comment-action">수정</button>
                <button type="button" class="delete-btn content-comment-action">삭제</button>
            ` : ''}
        </div>
    `;
    if (window.adminModeration?.isAdminUser()) {
        body.querySelector('.content-comment-actions').append(
            window.adminModeration.makeButton(
                'DESTINATION_COMMENT', comment.id, 'moderate-btn content-comment-action'));
    }
    body.querySelector('.content-comment-header').prepend(
        makePublicProfileLink(comment.writer?.id, nicknameElement, 'content-comment-writer-link')
    );

    const card = document.createElement('div');
    card.className = 'content-comment-card';
    card.append(
        makePublicProfileLink(comment.writer?.id, profileImage, 'content-comment-profile-link'),
        body
    );
    li.append(card);
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
                replyUl.className = 'reply-list content-comment-replies';
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
        childUl.className = 'reply-list content-comment-replies';
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
