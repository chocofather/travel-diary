import {
    getDestinationId,
    fetchCommentsPage,
    fetchThumbnails
} from './api.js';

import { setLoadedComments, getLoadedComments } from './commentsState.js';

import {
    setupCommentPagingEvents,
    initGuestRedirect,
    initCommentForm,
    bindCommentActions,
    bindReplySubmit,
    bindGalleryEvents,      // ✅ 사진 전체보기(모달) 기능 있으면 이 줄 포함
    bindSingleImageModal,
    setupSortDropdownEvent
} from './events.js';

import { initPhotoModal } from './modal.js';
import {
    renderComments,
    renderThumbnails
} from './render.js';

export function init() {
    const destinationId = getDestinationId();
    const commentListEl = document.getElementById('comment-list');
    const commentFormEl = document.getElementById('comment-form');
    let currentSort = 'oldest';

    // 1) 비회원 클릭 시 로그인 리다이렉트
    initGuestRedirect();

    // 2) 사진 모달 초기화 (openModal 함수 반환)
    const openModal = initPhotoModal(
        '#photo-modal',
        '#sidebar-thumbnails',
        '#main-photo'
    );

    // 3) 썸네일 초기 로딩 함수
    function reloadThumbnails() {
        fetchThumbnails(destinationId)
            .then(data => renderThumbnails(data, '.photo-review-list', openModal))
            .catch(err => console.error('썸네일 로드 실패:', err));
    }

    // 4) 댓글 첫 페이지 새로고침 함수
    function reloadComments(sort = currentSort) {
        fetchCommentsPage(destinationId, 0, 5, sort)
            .then(data => {
                if (!Array.isArray(data.content)) {
                    console.error('❌ 댓글 응답 형식 오류: content가 배열 아님', data);
                    return;
                }
                setLoadedComments(data.content.slice());
                renderComments(getLoadedComments(), commentListEl);

                const countEl = document.getElementById('comment-count');
                if (countEl) countEl.textContent = data.totalElements;
            })
            .catch(err => console.error('댓글 목록 갱신 실패:', err));
    }

    // 5) 첫 페이지 댓글만 로드 (초기 1회)
    reloadComments();

    // 6) 썸네일 로딩
    reloadThumbnails();

    // 7) 댓글/대댓글/정렬/좋아요/삭제/수정 이벤트 (로그인 시만)
    if (commentFormEl) {
        bindReplySubmit(commentListEl, destinationId, reloadComments);
        initCommentForm(destinationId, reloadComments, reloadThumbnails);
        bindCommentActions(commentListEl, reloadThumbnails, destinationId);
        setupSortDropdownEvent(destinationId, (sort) => {
            currentSort = sort;
            reloadComments(sort);
        });
    }

    // 8) 더보기 등 페이지 추가 로딩 이벤트 (항상)
    setupCommentPagingEvents(destinationId);

    // 9) 사진 전체보기 모달, 단일 이미지 모달
    bindGalleryEvents(destinationId, openModal);
    bindSingleImageModal();
}

document.addEventListener('DOMContentLoaded', init);