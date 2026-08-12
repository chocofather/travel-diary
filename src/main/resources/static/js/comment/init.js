import {
    getDestinationId,
    fetchCommentsPage,
    fetchCommentLocation,
    fetchThumbnails
} from './api.js';

import {
    setLoadedComments,
    getLoadedComments,
    concatComments
} from './commentsState.js';

import {
    setupCommentPagingEvents,
    initGuestRedirect,
    initCommentForm,
    bindCommentActions,
    bindReplySubmit,
    bindGalleryEvents,
    bindSingleImageModal,
    setupSortDropdownEvent
} from './events.js';

import { initPhotoModal } from './modal.js';
import {
    appendComments,
    renderComments,
    renderThumbnails
} from './render.js';

export function init() {
    const destinationId = getDestinationId();
    const commentSectionEl = document.querySelector('.comment-section.content-comments');
    const commentListEl = document.getElementById('comment-list');
    const commentFormEl = document.getElementById('comment-form');
    const countEl = document.getElementById('comment-count');
    const moreButton = document.getElementById('load-more-comments');
    const sortButtons = commentSectionEl?.querySelectorAll('[data-comment-sort]') || [];
    const deepLink = window.TravelDiaryCommentDeepLink;
    const targetCommentId = deepLink?.readTargetCommentId() ?? null;

    const pageSize = 5;
    let currentSort = 'latest';
    let nextPage = 0;
    let isLoading = false;
    let isLastPage = false;
    let requestGeneration = 0;

    initGuestRedirect();

    const openModal = initPhotoModal(
        '#photo-modal',
        '#sidebar-thumbnails',
        '#main-photo'
    );

    function reloadThumbnails() {
        fetchThumbnails(destinationId)
            .then(data => renderThumbnails(data, '.photo-review-list', openModal))
            .catch(err => console.error('썸네일 로드 실패:', err));
    }

    function updateSortUi() {
        sortButtons.forEach(button => {
            const active = button.dataset.commentSort === currentSort;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', String(active));
        });
    }

    function updateMoreButton() {
        if (!moreButton) return;
        moreButton.hidden = isLastPage || nextPage === 0;
        moreButton.disabled = isLoading;
        moreButton.textContent = isLoading ? '불러오는 중…' : '댓글 더보기';
    }

    async function loadCommentPage({reset = false, pageOverride = null} = {}) {
        if (isLoading && !reset) return;
        const generation = reset ? ++requestGeneration : requestGeneration;
        const page = Number.isInteger(pageOverride) && pageOverride >= 0
            ? pageOverride
            : (reset ? 0 : nextPage);

        if (reset) {
            nextPage = 0;
            isLastPage = false;
            setLoadedComments([]);
            renderComments([], commentListEl);
        }

        isLoading = true;
        updateMoreButton();

        try {
            const data = await fetchCommentsPage(destinationId, page, pageSize, currentSort);
            if (generation !== requestGeneration) return;
            if (!Array.isArray(data.content)) throw new Error('댓글 응답 형식이 올바르지 않습니다.');

            if (reset) {
                setLoadedComments(data.content.slice());
                renderComments(getLoadedComments(), commentListEl);
            } else {
                concatComments(data.content);
                appendComments(data.content, commentListEl);
            }

            if (countEl) countEl.textContent = String(data.totalCommentCount ?? 0);
            nextPage = page + 1;
            isLastPage = Boolean(data.last);
        } catch (error) {
            if (generation === requestGeneration) {
                console.error('댓글 목록 갱신 실패:', error);
            }
        } finally {
            if (generation !== requestGeneration) return;
            isLoading = false;
            updateMoreButton();
        }
    }

    function reloadComments() {
        return loadCommentPage({reset: true});
    }

    async function loadInitialComments() {
        if (targetCommentId == null || !deepLink) {
            await reloadComments();
            return;
        }

        const locationGeneration = ++requestGeneration;
        try {
            const location = await fetchCommentLocation(destinationId, targetCommentId);
            if (locationGeneration !== requestGeneration) return;
            const targetPage = Number(location.page) - 1;
            if (!Number.isInteger(targetPage) || targetPage < 0) {
                throw new Error('댓글 위치 응답이 올바르지 않습니다.');
            }

            await loadCommentPage({reset: true, pageOverride: targetPage});
            if (!deepLink.focusTarget(commentListEl, 'data-id', targetCommentId)) {
                deepLink.scrollToSection(commentSectionEl);
            }
        } catch (error) {
            if (locationGeneration !== requestGeneration) return;
            await reloadComments();
            deepLink.scrollToSection(commentSectionEl);
        }
    }

    setupSortDropdownEvent(commentSectionEl, sort => {
        if (!['latest', 'oldest', 'likes'].includes(sort) || sort === currentSort) return;
        currentSort = sort;
        updateSortUi();
        void reloadComments();
    });

    setupCommentPagingEvents(() => {
        if (!isLastPage) void loadCommentPage();
    });

    if (commentFormEl) {
        bindReplySubmit(commentListEl, destinationId, reloadComments);
        initCommentForm(destinationId, reloadComments, reloadThumbnails);
        bindCommentActions(commentListEl, reloadComments, reloadThumbnails);
    }

    bindGalleryEvents(destinationId, openModal);
    bindSingleImageModal();

    updateSortUi();
    void loadInitialComments();
    reloadThumbnails();
}

document.addEventListener('DOMContentLoaded', init);
