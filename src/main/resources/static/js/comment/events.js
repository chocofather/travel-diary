import {
    postComment,
    postReply,
    fetchThumbnails,
    toggleLikeApi,
    deleteCommentApi,
    updateCommentApi,
    fetchCommentsPage
} from './api.js';

import { appendComments, createCommentItem, renderComments } from './render.js';
import {
    getLoadedComments,
    setLoadedComments,
    addComment,
    concatComments
} from './commentsState.js';

let currentPage = 1;
let currentSort = 'oldest';
let isLoading = false;
let isLastPage = false;

// 비회원 클릭 시 로그인 페이지로 리다이렉트
export function initGuestRedirect() {
    const guestBox = document.getElementById('guest-comment-box');
    if (!guestBox) return;
    guestBox.addEventListener('click', () => {
        const redirect = encodeURIComponent(window.location.pathname);
        window.location.href = `/login?redirect=${redirect}`;
    });
}

// 정렬(dropdown)시 전체 초기화하고 새로 로딩
export function setupSortDropdownEvent(destinationId) {
    const btn = document.getElementById('sort-toggle-btn');
    const menu = document.getElementById('sort-options');
    btn?.addEventListener('click', () => menu.classList.toggle('hidden'));

    menu?.addEventListener('click', e => {
        if (e.target.tagName === 'LI') {
            const sort = e.target.dataset.value;
            currentSort = sort;
            currentPage = 0;
            isLastPage = false;
            setLoadedComments([]);
            btn.textContent = e.target.textContent + ' ▼';
            menu.classList.add('hidden');
            const container = document.getElementById('comment-list');
            container.innerHTML = '';

            fetchCommentsPage(destinationId, 0, 5, sort).then(data => {
                setLoadedComments(data.content.slice());
                renderComments(getLoadedComments(), container);
                currentPage = 1;
            });
        }
    });
}

// 댓글 등록 폼
export function initCommentForm(destinationId, onThumbnailsReload) {
    const form = document.getElementById('comment-form');
    if (!form) return;
    form.addEventListener('submit', e => {
        e.preventDefault();
        const data = new FormData(form);
        postComment(destinationId, data)
            .then(newComment => {
                form.reset();
                const countEl = document.getElementById('comment-count');
                if (countEl) countEl.textContent = parseInt(countEl.textContent || 0) + 1;
                addComment(newComment);
                appendComments([newComment], document.getElementById('comment-list'));
                onThumbnailsReload();
            })
            .catch(err => console.error('댓글 등록 실패:', err));
    });
}

// 답글(대댓글) 등록
export function bindReplySubmit(containerEl, destinationId) {
    if (!containerEl) return;
    containerEl.addEventListener('submit', e => {
        const form = e.target.closest('.nested-reply-form');
        if (!form) return;
        e.preventDefault();

        const commentDiv = form.closest('.comment-item');
        let parentId = commentDiv.dataset.id;
        let mention = '';
        if (commentDiv.dataset.parentId) {
            parentId = commentDiv.dataset.parentId;
            mention = commentDiv.dataset.writerNickname ? `@${commentDiv.dataset.writerNickname} ` : '';
        }
        if (commentDiv.dataset.parentId && form.closest('.reply-list').parentElement.dataset.parentId) {
            parentId = form.closest('.reply-list').parentElement.dataset.parentId;
            mention = form.closest('.reply-list').parentElement.dataset.writerNickname
                ? `@${form.closest('.reply-list').parentElement.dataset.writerNickname} `
                : '';
        }
        const textarea = form.querySelector('textarea[name="content"]');
        if (mention && !textarea.value.startsWith(mention)) {
            textarea.value = mention + textarea.value;
        }
        const data = new FormData(form);
        data.set('parentCommentId', parentId);

        postReply(destinationId, parentId, data)
            .then(newReply => {
                form.remove();
                addComment(newReply);
                // 부모 .reply-list에만 append
                const commentList = document.getElementById('comment-list');
                const parentLi = commentList.querySelector(`.comment-item[data-id="${parentId}"]`);
                if (parentLi) {
                    let replyUl = parentLi.querySelector('.reply-list');
                    if (!replyUl) {
                        replyUl = document.createElement('ul');
                        replyUl.className = 'reply-list';
                        parentLi.appendChild(replyUl);
                    }
                    replyUl.appendChild(createCommentItem(newReply, 1, newReply.writer?.nickname || ''));
                }
            })
            .catch(err => console.error('대댓글 등록 실패:', err));
    });
}

// 댓글 액션 바인딩(수정, 삭제 등은 필요시 배열 업데이트 & 부분 렌더로)
export function bindCommentActions(containerEl, onThumbnailsReload, destinationId) {
    if (!containerEl) return;
    containerEl.addEventListener('click', e => {
        const commentDiv = e.target.closest('.comment-item');
        if (!commentDiv) return;
        const id = commentDiv.dataset.id;
        if (handleLike(e, id)) return;
        if (handleDelete(e, id, onThumbnailsReload, destinationId)) return;
        if (showEditForm(e, commentDiv)) return;
        if (handleSaveEdit(e, commentDiv, id)) return;
        if (handleCancelEdit(e, commentDiv, id)) return;
        if (toggleReplyForm(e, commentDiv)) return;
        if (cancelReply(e)) return;
    });
}

function handleLike(e, id) {
    const btn = e.target.closest('.like-btn');
    if (!btn) return false;
    if (!document.getElementById('comment-form')) {
        console.warn('로그인이 필요합니다.');
        return true;
    }
    toggleLikeApi(id)
        .then(status => {
            const img = btn.querySelector('img');
            img.src = status === 'liked'
                ? '/uploads/icons/like2.png'
                : '/uploads/icons/like.png';
            const span = btn.querySelector('span');
            span.innerText = status === 'liked'
                ? +span.innerText + 1
                : +span.innerText - 1;
        })
        .catch(err => console.error('좋아요 실패:', err));
    return true;
}

function removeCommentAndRepliesFromArray(comments, id) {
    const allIdsToRemove = new Set([Number(id)]);
    let added = true;
    while (added) {
        added = false;
        comments.forEach(c => {
            if (allIdsToRemove.has(c.parentCommentId)) {
                if (!allIdsToRemove.has(c.id)) {
                    allIdsToRemove.add(c.id);
                    added = true;
                }
            }
        });
    }
    return comments.filter(c => !allIdsToRemove.has(c.id));
}

function handleDelete(e, id, onThumbnailsReload, destinationId) {
    if (!e.target.matches('.delete-btn')) return false;
    if (!confirm('댓글을 삭제하시겠습니까?')) return true;
    deleteCommentApi(id)
        .then(res => {
            if (res.ok) {
                // 배열에서 삭제
                setLoadedComments(getLoadedComments().filter(c => c.id !== Number(id)));
                // 화면에서 LI 삭제
                const li = document.querySelector(`.comment-item[data-id="${id}"]`);
                if (li) li.remove();

                // 전체 카운트 갱신 (destinationId가 undefined 아니게!)
                if (destinationId) {
                    fetchCommentsPage(destinationId, 0, 1)
                        .then(data => {
                            const countEl = document.getElementById('comment-count');
                            if (countEl) countEl.textContent = data.totalElements;
                        });
                }

                // 루트댓글이면 전체 다시 렌더 (트리구조 무너지지 않게)
                const isRootComment = li && !li.classList.contains('reply');
                if (isRootComment) {
                    const commentList = document.getElementById('comment-list');
                    renderComments(getLoadedComments(), commentList);
                }
                onThumbnailsReload();
            } else {
                alert('삭제 권한 없음');
            }
        })
        .catch(err => console.error('삭제 실패:', err));
    return true;
}





function showEditForm(e, commentDiv) {
    if (!e.target.matches('.edit-btn')) return false;
    commentDiv.querySelector('.reply-form')?.remove();
    const contentEl = commentDiv.querySelector('.comment-content');
    const original = contentEl.innerText.replace('수정됨', '').trim();
    const form = document.createElement('form');
    form.className = 'edit-comment-form comment-write-form';
    const inputId = `edit-image-${commentDiv.dataset.id}`;
    form.innerHTML = `
    <div class="textarea-wrapper">
      <textarea name="content" class="edit-text" required>${original}</textarea>
    </div>
    <div class="comment-controls">
      <input type="file" id="${inputId}" name="image" accept="image/*" hidden>
      <label for="${inputId}" class="image-upload-label">
        <img src="/uploads/icons/camera.png" alt="사진"> 사진 변경
      </label>
      <button type="button" class="save-edit-btn submit-btn">저장</button>
      <button type="button" class="cancel-edit-btn">취소</button>
    </div>
  `;
    contentEl.replaceWith(form);
    commentDiv.classList.add('editing');
    return true;
}

function handleSaveEdit(e, commentDiv, id) {
    if (!e.target.matches('.save-edit-btn')) return false;
    const newContent = commentDiv.querySelector('.edit-text').value;
    updateCommentApi(id, { content: newContent })
        .then(res => {
            if (res.ok) {
                setLoadedComments(
                    getLoadedComments().map(c =>
                        c.id === Number(id)
                            ? { ...c, content: newContent, updatedAt: new Date().toISOString() }
                            : c
                    )
                );
                const oldLi = document.querySelector(`.comment-item[data-id="${id}"]`);
                if (oldLi) {
                    const depth = oldLi.classList.contains('reply') ? 1 : 0;
                    const target = getLoadedComments().find(c => c.id === Number(id));
                    const newLi = createCommentItem(target, depth);
                    oldLi.replaceWith(newLi);
                }
            } else {
                alert('수정 실패');
            }
        })
        .catch(err => console.error('수정 실패:', err));
    return true;
}

function handleCancelEdit(e, commentDiv, id) {
    if (!e.target.matches('.cancel-edit-btn')) return false;
    commentDiv.classList.remove('editing');
    const original = getLoadedComments().find(c => c.id === Number(id));
    if (original) {
        const depth = commentDiv.classList.contains('reply') ? 1 : 0;
        const newLi = createCommentItem(original, depth);
        commentDiv.replaceWith(newLi);
    } else {
        console.warn('원본을 찾지 못함!', id, getLoadedComments());
    }
    return true;
}

function toggleReplyForm(e, commentDiv) {
    if (!e.target.matches('.reply-btn')) return false;
    commentDiv.querySelector('.edit-comment-form')?.remove();
    commentDiv.classList.remove('editing');

    const existing = commentDiv.querySelector('.reply-form');
    if (existing) {
        existing.remove();
        return true;
    }

    const uniqueId = `nested-image-${commentDiv.dataset.id}`;
    const container = document.createElement('div');
    container.className = 'reply-form';
    container.innerHTML = `
    <form class="nested-reply-form comment-write-form" enctype="multipart/form-data">
      <div class="textarea-wrapper">
        <textarea name="content" placeholder="답글을 입력하세요" required></textarea>
      </div>
      <div class="comment-controls">
        <input type="file" id="${uniqueId}" name="image" accept="image/*" hidden>
        <label for="${uniqueId}" class="image-upload-label">
          <img src="/uploads/icons/camera.png" alt="사진"> 사진
        </label>
        <button type="submit" class="submit-btn">등록</button>
        <button type="button" class="cancel-edit-btn">취소</button>
      </div>
    </form>
  `;
    commentDiv.appendChild(container);
    return true;
}

function cancelReply(e) {
    if (!e.target.matches('.cancel-reply')) return false;
    e.target.closest('.reply-form')?.remove();
    return true;
}

// 더보기 버튼 - 누적
export function setupCommentPagingEvents(destinationId) {
    const moreBtn = document.getElementById('load-more-comments');
    if (!moreBtn) return;
    moreBtn.addEventListener('click', () => {
        loadNextComments(destinationId);
    });
}

function loadNextComments(destinationId) {
    if (isLoading) return;
    isLoading = true;
    fetchCommentsPage(destinationId, currentPage, 5, currentSort)
        .then(data => {
            const comments = data.content;
            const container = document.getElementById('comment-list');
            if (!Array.isArray(comments)) {
                isLoading = false;
                return;
            }
            concatComments(comments);
            appendComments(comments, container);
            currentPage += 1;
            if (comments.length < 5) {
                isLastPage = true;
                const moreBtn = document.getElementById('load-more-comments');
                if (moreBtn) moreBtn.style.display = 'none';
            }
        })
        .finally(() => {
            isLoading = false;
        });
}

// 썸네일 전체보기(모달) 바인딩 함수
export function bindGalleryEvents(destinationId, openModalFn) {
    document.addEventListener('click', e => {
        if (e.target.matches('.view-all')) {
            e.preventDefault();
            fetchThumbnails(destinationId)
                .then(data => {
                    if (!data.length) return;
                    openModalFn(0, data);
                })
                .catch(err => console.error('썸네일 로드 실패:', err));
            return;
        }
        if (e.target.matches('.photo-close')) {
            const modalEl = document.getElementById('photo-modal');
            if (modalEl) modalEl.style.display = 'none';
        }
    });
}

// 댓글 이미지 클릭 시 단일 이미지 모달 오픈
export function bindSingleImageModal() {
    const modal = document.getElementById('image-modal');
    const modalImg = document.getElementById('modal-img');

    document.addEventListener('click', e => {
        if (e.target.matches('.comment-image')) {
            modalImg.src = e.target.src;
            modal.style.display = 'flex';
            return;
        }

        if (e.target === modalImg) {
            modal.style.display = 'none';
            return;
        }

        if (e.target.matches('.close-btn')) {
            modal.style.display = 'none';
            return;
        }
    });

    document.addEventListener('keydown', e => {
        if (e.key === 'Escape') {
            modal.style.display = 'none';
        }
    });
}
