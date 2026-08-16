import {
    postComment,
    postReply,
    fetchThumbnails,
    toggleLikeApi,
    deleteCommentApi,
    updateCommentApi
} from './api.js';

import { createCommentItem } from './render.js';
import { getLoadedComments } from './commentsState.js';

/** 댓글 하나에 첨부할 수 있는 사진 수. 서버 DestinationCommentService.MAX_COMMENT_IMAGES 와 같은 값. */
const MAX_COMMENT_IMAGES = 3;
const IMAGE_LIMIT_MESSAGE = `사진은 최대 ${MAX_COMMENT_IMAGES}장까지 첨부할 수 있습니다.`;

/**
 * 선택한 사진이 제한을 넘으면 안내하고 전송을 막는다.
 * @param {HTMLFormElement} form 댓글/답글 작성 폼
 * @returns {boolean} 제한을 넘어 전송하면 안 되는 경우 true
 */
function exceedsImageLimit(form) {
    const input = form.querySelector('input[type="file"][name="images"]');
    if (!input || input.files.length <= MAX_COMMENT_IMAGES) return false;
    alert(IMAGE_LIMIT_MESSAGE);
    return true;
}

// 비회원 클릭 시 로그인 페이지로 리다이렉트
export function initGuestRedirect() {
    const guestBox = document.getElementById('guest-comment-box');
    if (!guestBox) return;
    guestBox.addEventListener('click', () => {
        const redirect = encodeURIComponent(window.location.pathname);
        window.location.href = `/login?redirect=${redirect}`;
    });
}

// 정렬 상태와 조회는 init.js 한 곳에서 관리한다.
export function setupSortDropdownEvent(containerEl, onSortChange) {
    containerEl?.addEventListener('click', e => {
        const button = e.target.closest('[data-comment-sort]');
        if (!button || !containerEl.contains(button)) return;
        onSortChange(button.dataset.commentSort);
    });
}

/**
 * 댓글 작성 사진 선택/미리보기.
 * 파일 선택창을 다시 열면 브라우저가 input.files 를 통째로 교체하므로,
 * 선택 목록(selected)을 따로 들고 DataTransfer 로 input.files 와 동기화해
 * 여러 번 나눠 고른 사진이 누적되게 한다.
 * @param {HTMLFormElement} form 댓글 작성 폼
 */
function initCommentImagePreview(form) {
    const input = form.querySelector('#comment-image-input');
    const preview = form.querySelector('#comment-image-preview');
    if (!input || !preview) return;

    /** 전송 대상 사진 목록. input.files 는 항상 이 목록과 같게 맞춘다. */
    let selected = [];

    const fileKey = file => `${file.name}|${file.size}|${file.lastModified}`;

    /** selected 를 실제 input.files 에 반영한다. */
    function syncInput() {
        try {
            const transfer = new DataTransfer();
            selected.forEach(file => transfer.items.add(file));
            input.files = transfer.files;
        } catch (error) {
            // DataTransfer 미지원 환경에서는 마지막 선택만 유지한다.
            selected = Array.from(input.files || []).slice(0, MAX_COMMENT_IMAGES);
        }
        // value 가 비면 같은 파일을 다시 골라도 change 가 정상 발생한다.
        if (selected.length === 0) input.value = '';
    }

    /** 새로 고른 파일을 기존 선택에 누적한다. 중복은 제외하고 최대 3장까지만 남긴다. */
    function addFiles(files) {
        const keys = new Set(selected.map(fileKey));
        const added = Array.from(files || []).filter(file => {
            if (keys.has(fileKey(file))) return false;
            keys.add(fileKey(file));
            return true;
        });
        const merged = selected.concat(added);
        if (merged.length > MAX_COMMENT_IMAGES) {
            alert(IMAGE_LIMIT_MESSAGE);
        }
        selected = merged.slice(0, MAX_COMMENT_IMAGES);
        syncInput();
        render();
    }

    function removeFileAt(index) {
        selected.splice(index, 1);
        syncInput();
        render();
    }

    function clearFiles() {
        selected = [];
        syncInput();
        render();
    }

    function render() {
        preview.querySelectorAll('img[data-object-url]').forEach(img => {
            URL.revokeObjectURL(img.src);
        });
        preview.innerHTML = '';

        // 선택된 사진이 없으면 미리보기 영역 자체를 감춘다.
        preview.hidden = selected.length === 0;
        if (selected.length === 0) return;

        selected.forEach((file, index) => {
            const item = document.createElement('div');
            item.className = 'comment-image-preview-item';

            const image = document.createElement('img');
            image.src = URL.createObjectURL(file);
            image.dataset.objectUrl = 'true';
            image.alt = `${file.name} 미리보기`;

            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'comment-image-remove';
            remove.setAttribute('aria-label', `${file.name} 선택 취소`);
            remove.textContent = '×';
            remove.addEventListener('click', () => removeFileAt(index));

            item.append(image, remove);
            preview.append(item);
        });
    }

    input.addEventListener('change', () => addFiles(input.files));
    // 등록 성공 후 form.reset() 이 호출되면 선택/미리보기도 함께 비운다.
    form.addEventListener('reset', () => setTimeout(clearFiles, 0));
}

/**
 * 0/2000 글자수 표시. (커뮤니티 게시글 댓글 작성폼과 같은 방식)
 * @param {HTMLFormElement} form 댓글 작성 폼
 */
function initCommentLengthCounter(form) {
    const textarea = form.querySelector('textarea[name="content"]');
    const output = form.querySelector('#comment-length');
    if (!textarea || !output) return;

    const update = () => {
        output.textContent = String(textarea.value.length);
    };
    textarea.addEventListener('input', update);
    // 등록 성공 후 form.reset() 이 호출되면 글자수도 함께 되돌린다.
    form.addEventListener('reset', () => setTimeout(update, 0));
    update();
}

// 댓글 등록 폼
export function initCommentForm(destinationId, onCommentsReload, onThumbnailsReload) {
    const form = document.getElementById('comment-form');
    if (!form) return;
    initCommentImagePreview(form);
    initCommentLengthCounter(form);
    form.addEventListener('submit', e => {
        e.preventDefault();
        if (exceedsImageLimit(form)) return;
        // input[name="images"] 이므로 선택한 파일이 모두 images 키로 담긴다.
        const data = new FormData(form);
        postComment(destinationId, data)
            .then(() => {
                form.reset();
                onCommentsReload();
                onThumbnailsReload();
            })
            .catch(err => console.error('댓글 등록 실패:', err));
    });
}

// 답글(대댓글) 등록
export function bindReplySubmit(containerEl, destinationId, onCommentsReload) {
    if (!containerEl) return;
    containerEl.addEventListener('submit', e => {
        const form = e.target.closest('.nested-reply-form');
        if (!form) return;
        e.preventDefault();
        if (exceedsImageLimit(form)) return;

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
            .then(() => {
                form.remove();
                onCommentsReload();
            })
            .catch(err => console.error('대댓글 등록 실패:', err));
    });
}

// 댓글 액션 바인딩(수정, 삭제 등은 필요시 배열 업데이트 & 부분 렌더로)
export function bindCommentActions(containerEl, onCommentsReload, onThumbnailsReload) {
    if (!containerEl) return;
    containerEl.addEventListener('click', e => {
        const commentDiv = e.target.closest('.comment-item');
        if (!commentDiv) return;
        const id = commentDiv.dataset.id;
        if (handleLike(e, id, onCommentsReload)) return;
        if (handleDelete(e, id, onCommentsReload, onThumbnailsReload)) return;
        if (showEditForm(e, commentDiv)) return;
        if (handleSaveEdit(e, commentDiv, id, onCommentsReload)) return;
        if (handleCancelEdit(e, commentDiv, id)) return;
        if (toggleReplyForm(e, commentDiv)) return;
        if (cancelReply(e)) return;
    });
}

function handleLike(e, id, onCommentsReload) {
    const btn = e.target.closest('.like-btn');
    if (!btn) return false;
    if (!document.getElementById('comment-form')) {
        console.warn('로그인이 필요합니다.');
        return true;
    }
    toggleLikeApi(id)
        .then(() => onCommentsReload())
        .catch(err => console.error('좋아요 실패:', err));
    return true;
}

function handleDelete(e, id, onCommentsReload, onThumbnailsReload) {
    if (!e.target.matches('.delete-btn')) return false;
    if (!confirm('댓글을 삭제하시겠습니까?')) return true;
    deleteCommentApi(id)
        .then(res => {
            if (res.ok) {
                onCommentsReload();
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
    // 수정은 본문만 저장한다. (사진 변경 경로는 사용되지 않아 제거됨)
    form.innerHTML = `
    <div class="textarea-wrapper">
      <textarea name="content" class="edit-text" required>${original}</textarea>
    </div>
    <div class="comment-controls">
      <button type="button" class="save-edit-btn submit-btn">저장</button>
      <button type="button" class="cancel-edit-btn">취소</button>
    </div>
  `;
    contentEl.replaceWith(form);
    commentDiv.classList.add('editing');
    return true;
}

function handleSaveEdit(e, commentDiv, id, onCommentsReload) {
    if (!e.target.matches('.save-edit-btn')) return false;
    const newContent = commentDiv.querySelector('.edit-text').value;
    updateCommentApi(id, { content: newContent })
        .then(res => {
            if (res.ok) {
                onCommentsReload();
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
        <input type="file" id="${uniqueId}" name="images" accept="image/*" multiple hidden>
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

// 더보기 상태와 조회는 init.js 한 곳에서 관리한다.
export function setupCommentPagingEvents(onLoadMore) {
    const moreBtn = document.getElementById('load-more-comments');
    if (!moreBtn) return;
    moreBtn.addEventListener('click', () => {
        onLoadMore();
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

// 댓글 이미지 클릭 시 확대 모달 오픈. 이동은 그 댓글에 첨부된 사진 안에서만 순환한다.
export function bindSingleImageModal() {
    const modal = document.getElementById('image-modal');
    const modalImg = document.getElementById('modal-img');
    if (!modal || !modalImg) return;

    /** 현재 보고 있는 댓글의 사진 목록과 위치 */
    let images = [];
    let index = 0;

    const isOpen = () => modal.style.display !== 'none';

    /** 목록 범위를 벗어나면 반대쪽 끝으로 순환한다. */
    function show(nextIndex) {
        if (images.length === 0) return;
        index = (nextIndex + images.length) % images.length;
        modalImg.src = images[index];
    }

    function open(target) {
        const group = target.closest('.comment-images');
        images = group
            ? Array.from(group.querySelectorAll('.comment-image')).map(image => image.src)
            : [target.src];
        modal.classList.toggle('is-single', images.length <= 1);
        show(Math.max(images.indexOf(target.src), 0));
        modal.style.display = 'flex';
    }

    function close() {
        modal.style.display = 'none';
        images = [];
        index = 0;
    }

    document.addEventListener('click', e => {
        const nav = e.target.closest?.('.comment-image-nav');
        if (nav && modal.contains(nav)) {
            // 확대 이미지 클릭(닫기)으로 이어지지 않도록 여기서 끊는다.
            e.preventDefault();
            e.stopPropagation();
            show(index + (nav.classList.contains('prev') ? -1 : 1));
            return;
        }

        if (e.target.matches('.comment-image')) {
            open(e.target);
            return;
        }

        if (e.target === modalImg) {
            close();
            return;
        }

        if (e.target.matches('.close-btn')) {
            close();
        }
    });

    document.addEventListener('keydown', e => {
        // 모달이 닫혀 있으면 방향키/ESC 에 간섭하지 않는다.
        if (!isOpen()) return;

        if (e.key === 'Escape') {
            close();
            return;
        }
        if (e.key === 'ArrowLeft') {
            e.preventDefault();
            show(index - 1);
            return;
        }
        if (e.key === 'ArrowRight') {
            e.preventDefault();
            show(index + 1);
        }
    });
}
