document.addEventListener('DOMContentLoaded', () => {
    const section = document.getElementById('course-comments');
    if (!section) return;

    const courseId = Number(section.dataset.courseId);
    const list = document.getElementById('course-comment-list');
    const count = document.getElementById('course-comment-count');
    const message = document.getElementById('course-comment-message');
    const form = document.getElementById('course-comment-form');
    const contentInput = document.getElementById('course-comment-content');
    const lengthOutput = document.getElementById('course-comment-length');
    const sortButtons = section.querySelectorAll('[data-comment-sort]');
    const moreButton = document.getElementById('course-comment-more');

    const pageSize = 5;
    let currentSort = 'latest';
    let nextPage = 0;
    let isLoading = false;
    let isLastPage = false;
    let requestGeneration = 0;

    const jsonHeaders = {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    };
    const defaultProfileImage = '/images/default.png';
    const unlikedIcon = '/uploads/icons/like.png';
    const likedIcon = '/uploads/icons/like2.png';

    async function requestJson(url, options = {}) {
        const response = await fetch(url, {
            credentials: 'same-origin',
            ...options,
            headers: {...jsonHeaders, ...(options.headers || {})}
        });

        if (response.status === 401) {
            window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`;
            throw new Error('로그인이 필요합니다.');
        }

        if (!response.ok) {
            let errorMessage = `요청에 실패했습니다. (HTTP ${response.status})`;
            const contentType = response.headers.get('Content-Type') || '';
            if (contentType.includes('application/json')) {
                const body = await response.json();
                errorMessage = body.detail || body.message || body.error || errorMessage;
            }
            throw new Error(errorMessage);
        }

        if (response.status === 204) return null;
        return response.json();
    }

    function showMessage(text = '') {
        message.textContent = text;
        message.hidden = !text;
    }

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

    function makeProfileImage(comment) {
        const nickname = comment.writerNickname || '알 수 없는 사용자';
        const image = document.createElement('img');
        image.className = 'content-comment-avatar';
        image.src = profileImageUrl(comment.writerProfileImage);
        image.alt = `${nickname} 프로필 이미지`;
        image.addEventListener('error', () => {
            if (image.dataset.fallbackApplied === 'true') return;
            image.dataset.fallbackApplied = 'true';
            image.src = defaultProfileImage;
        });
        return image;
    }

    function makeButton(label, className) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = className;
        button.textContent = label;
        return button;
    }

    function makeLikeControl(comment, loggedIn) {
        const likeCount = document.createElement('span');
        likeCount.className = 'course-comment-like-count content-comment-like-count';
        likeCount.textContent = String(comment.likeCount ?? 0);

        const icon = document.createElement('img');
        icon.className = 'content-comment-like-icon';
        icon.src = comment.likedByMe ? likedIcon : unlikedIcon;
        icon.alt = '';
        icon.setAttribute('aria-hidden', 'true');

        const label = document.createElement('span');
        label.className = 'content-comment-sr-only';
        label.textContent = '좋아요';

        if (!loggedIn) {
            const readonly = document.createElement('span');
            readonly.className = 'course-comment-like-readonly content-comment-like-readonly content-comment-like';
            readonly.append(icon, label, likeCount);
            return readonly;
        }

        const button = makeButton('', 'course-comment-like-button content-comment-action content-comment-like');
        button.append(icon, label, likeCount);
        button.classList.toggle('is-liked', Boolean(comment.likedByMe));
        button.setAttribute('aria-pressed', String(Boolean(comment.likedByMe)));
        button.dataset.likedByMe = String(Boolean(comment.likedByMe));
        return button;
    }

    function renderComment(comment, isReply = false) {
        const item = document.createElement('li');
        item.className = isReply
            ? 'course-comment-item course-comment-reply content-comment-item'
            : 'course-comment-item course-comment-root content-comment-item';
        item.dataset.commentId = comment.id;
        if (comment.writerUserId != null) item.dataset.userId = String(comment.writerUserId);

        const content = document.createElement('p');
        content.className = 'course-comment-content content-comment-text';
        if (comment.deleted) {
            item.classList.add('course-comment-deleted', 'content-comment-deleted');
            content.classList.add('content-comment-deleted-text');
            content.textContent = '삭제된 댓글입니다.';
            item.append(content);
            return item;
        }

        const meta = document.createElement('div');
        meta.className = 'course-comment-meta content-comment-header';
        const writer = document.createElement('strong');
        writer.className = 'content-comment-nickname';
        writer.textContent = comment.writerNickname || '알 수 없는 사용자';
        const timeMeta = document.createElement('span');
        timeMeta.className = 'content-comment-meta';
        const date = document.createElement('time');
        date.className = 'course-comment-date';
        date.dateTime = comment.createdAt || '';
        date.textContent = formatDate(comment.createdAt);
        timeMeta.append(date);
        if (isEdited(comment)) {
            const edited = document.createElement('span');
            edited.className = 'content-comment-edited';
            edited.textContent = '· 수정됨';
            timeMeta.append(edited);
        }
        meta.append(writer, timeMeta);

        if (comment.replyToCommentId != null) {
            const replyTarget = document.createElement('span');
            replyTarget.className = 'course-comment-reply-target content-comment-mention';
            replyTarget.textContent = comment.replyToDeleted
                ? '삭제된 댓글에 대한 답글'
                : `@${comment.replyToNickname || '알 수 없는 사용자'}`;
            content.append(replyTarget);
        }
        content.append(document.createTextNode(comment.content || ''));

        const loggedIn = typeof isLoggedIn !== 'undefined' && isLoggedIn;
        const actions = document.createElement('div');
        actions.className = 'course-comment-actions content-comment-actions';
        actions.append(makeLikeControl(comment, loggedIn));
        if (loggedIn) {
            actions.append(makeButton('답글', 'course-comment-reply-button content-comment-action'));
        }
        if (comment.myComment) {
            actions.append(
                makeButton('수정', 'course-comment-edit content-comment-action'),
                makeButton('삭제', 'course-comment-delete content-comment-action')
            );
        }

        const body = document.createElement('div');
        body.className = 'content-comment-body';
        body.append(meta, content, actions);

        const card = document.createElement('div');
        card.className = 'content-comment-card';
        card.append(makeProfileImage(comment), body);
        item.append(card);
        return item;
    }

    function compareByCreatedAtAndId(left, right) {
        const timeDifference = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime();
        return timeDifference || Number(left.id) - Number(right.id);
    }

    function renderCommentTree(comments) {
        const roots = comments
            .filter(comment => comment.parentCommentId == null);
        const repliesByParent = new Map();

        comments
            .filter(comment => comment.parentCommentId != null && !comment.deleted)
            .forEach(reply => {
                const parentId = Number(reply.parentCommentId);
                if (!repliesByParent.has(parentId)) repliesByParent.set(parentId, []);
                repliesByParent.get(parentId).push(reply);
            });

        roots.forEach(root => {
            const rootItem = renderComment(root);
            const replies = repliesByParent.get(Number(root.id)) || [];
            if (replies.length > 0) {
                const replyList = document.createElement('ul');
                replyList.className = 'course-comment-replies content-comment-replies';
                replies.sort(compareByCreatedAtAndId)
                    .forEach(reply => replyList.append(renderComment(reply, true)));
                rootItem.append(replyList);
            }
            list.append(rootItem);
        });
    }

    function updateSortUi() {
        sortButtons.forEach(button => {
            const active = button.dataset.commentSort === currentSort;
            button.classList.toggle('is-active', active);
            button.setAttribute('aria-pressed', String(active));
        });
    }

    function updateMoreButton() {
        moreButton.hidden = isLastPage || nextPage === 0;
        moreButton.disabled = isLoading;
        moreButton.textContent = isLoading ? '불러오는 중…' : '댓글 더보기';
    }

    function emptyCommentItem() {
        const empty = document.createElement('li');
        empty.className = 'course-comment-empty';
        empty.textContent = '첫 댓글을 작성해 보세요.';
        return empty;
    }

    async function loadCommentPage({reset = false} = {}) {
        if (isLoading && !reset) return;
        const generation = reset ? ++requestGeneration : requestGeneration;
        const page = reset ? 0 : nextPage;

        if (reset) {
            nextPage = 0;
            isLastPage = false;
            list.replaceChildren();
        }

        isLoading = true;
        updateMoreButton();
        try {
            showMessage();
            const data = await requestJson(
                `/course-comments/page?courseId=${encodeURIComponent(courseId)}`
                + `&page=${page}&size=${pageSize}&sort=${encodeURIComponent(currentSort)}`
            );
            if (generation !== requestGeneration) return;
            if (!Array.isArray(data.content)) throw new Error('댓글 응답 형식이 올바르지 않습니다.');

            count.textContent = String(data.totalCommentCount ?? 0);
            if (reset && data.totalElements === 0) {
                list.append(emptyCommentItem());
            } else {
                renderCommentTree(data.content);
            }
            nextPage = page + 1;
            isLastPage = Boolean(data.last);
        } catch (error) {
            if (generation === requestGeneration) {
                showMessage(error.message);
                if (reset && !list.children.length) list.append(emptyCommentItem());
            }
        } finally {
            if (generation !== requestGeneration) return;
            isLoading = false;
            updateMoreButton();
        }
    }

    async function resetComments() {
        await loadCommentPage({reset: true});
    }

    sortButtons.forEach(button => {
        button.addEventListener('click', () => {
            const nextSort = button.dataset.commentSort;
            if (!['latest', 'oldest', 'likes'].includes(nextSort) || nextSort === currentSort) return;
            currentSort = nextSort;
            updateSortUi();
            void resetComments();
        });
    });

    moreButton?.addEventListener('click', () => {
        if (!isLastPage) void loadCommentPage();
    });

    form?.addEventListener('submit', async event => {
        event.preventDefault();
        try {
            await requestJson('/course-comments', {
                method: 'POST',
                body: JSON.stringify({courseId, content: contentInput.value})
            });
            form.reset();
            lengthOutput.textContent = '0';
            await resetComments();
        } catch (error) {
            showMessage(error.message);
        }
    });

    contentInput?.addEventListener('input', () => {
        lengthOutput.textContent = String(contentInput.value.length);
    });

    list.addEventListener('click', async event => {
        const item = event.target.closest('.course-comment-item');
        if (!item) return;
        const commentId = item.dataset.commentId;

        if (event.target.closest('.course-comment-like-button')) {
            const likeButton = event.target.closest('.course-comment-like-button');
            if (likeButton.disabled) return;
            likeButton.disabled = true;
            try {
                const method = likeButton.dataset.likedByMe === 'true' ? 'DELETE' : 'POST';
                await requestJson(`/course-comments/${commentId}/likes`, {method});
                await resetComments();
            } catch (error) {
                showMessage(error.message);
            } finally {
                if (likeButton.isConnected) likeButton.disabled = false;
            }
            return;
        }

        if (event.target.matches('.course-comment-reply-button')) {
            list.querySelector('.course-comment-reply-form')?.remove();

            const replyForm = document.createElement('form');
            replyForm.className = 'course-comment-reply-form';
            replyForm.dataset.replyToCommentId = commentId;

            const textarea = document.createElement('textarea');
            textarea.name = 'content';
            textarea.maxLength = 2000;
            textarea.required = true;
            textarea.placeholder = '답글을 입력해 주세요.';

            const actions = document.createElement('div');
            actions.className = 'course-comment-reply-actions';
            const submit = document.createElement('button');
            submit.type = 'submit';
            submit.className = 'course-comment-reply-submit';
            submit.textContent = '등록';
            actions.append(submit, makeButton('취소', 'course-comment-reply-cancel'));
            replyForm.append(textarea, actions);

            const replyList = item.querySelector(':scope > .course-comment-replies');
            if (replyList) item.insertBefore(replyForm, replyList);
            else item.append(replyForm);
            textarea.focus();
            return;
        }

        if (event.target.matches('.course-comment-reply-cancel')) {
            event.target.closest('.course-comment-reply-form')?.remove();
            return;
        }

        if (event.target.matches('.course-comment-delete')) {
            if (!window.confirm('댓글을 삭제하시겠습니까?')) return;
            try {
                await requestJson(`/course-comments/${commentId}`, {method: 'DELETE'});
                await resetComments();
            } catch (error) {
                showMessage(error.message);
            }
            return;
        }

        if (event.target.matches('.course-comment-edit')) {
            const content = item.querySelector('.course-comment-content');
            const actions = item.querySelector('.course-comment-actions');
            const textarea = document.createElement('textarea');
            textarea.className = 'course-comment-edit-textarea';
            textarea.maxLength = 2000;
            textarea.value = content.textContent;

            const editActions = document.createElement('div');
            editActions.className = 'course-comment-edit-actions course-comment-actions';
            editActions.append(
                makeButton('저장', 'course-comment-save'),
                makeButton('취소', 'course-comment-cancel')
            );
            content.replaceWith(textarea);
            actions.replaceWith(editActions);
            textarea.focus();
            return;
        }

        if (event.target.matches('.course-comment-cancel')) {
            await resetComments();
            return;
        }

        if (event.target.matches('.course-comment-save')) {
            const textarea = item.querySelector('.course-comment-edit-textarea');
            try {
                await requestJson(`/course-comments/${commentId}`, {
                    method: 'PUT',
                    body: JSON.stringify({content: textarea.value})
                });
                await resetComments();
            } catch (error) {
                showMessage(error.message);
            }
        }
    });

    list.addEventListener('submit', async event => {
        const replyForm = event.target.closest('.course-comment-reply-form');
        if (!replyForm) return;
        event.preventDefault();

        const submit = replyForm.querySelector('.course-comment-reply-submit');
        if (submit.disabled) return;
        submit.disabled = true;

        try {
            const textarea = replyForm.querySelector('textarea[name="content"]');
            await requestJson('/course-comments', {
                method: 'POST',
                body: JSON.stringify({
                    courseId,
                    replyToCommentId: Number(replyForm.dataset.replyToCommentId),
                    content: textarea.value
                })
            });
            await resetComments();
        } catch (error) {
            showMessage(error.message);
        } finally {
            submit.disabled = false;
        }
    });

    updateSortUi();
    void resetComments();
});
