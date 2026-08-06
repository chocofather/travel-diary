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

    const jsonHeaders = {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    };

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

    function formatDate(value) {
        if (!value) return '';
        return new Intl.DateTimeFormat('ko-KR', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit'
        }).format(new Date(value));
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
        likeCount.className = 'course-comment-like-count';
        likeCount.textContent = String(comment.likeCount ?? 0);

        if (!loggedIn) {
            const readonly = document.createElement('span');
            readonly.className = 'course-comment-like-readonly';
            readonly.textContent = '좋아요 ';
            readonly.append(likeCount);
            return readonly;
        }

        const button = makeButton('좋아요 ', 'course-comment-like-button');
        button.append(likeCount);
        button.classList.toggle('is-liked', Boolean(comment.likedByMe));
        button.setAttribute('aria-pressed', String(Boolean(comment.likedByMe)));
        button.dataset.likedByMe = String(Boolean(comment.likedByMe));
        return button;
    }

    function renderComment(comment, isReply = false) {
        const item = document.createElement('li');
        item.className = isReply
            ? 'course-comment-item course-comment-reply'
            : 'course-comment-item course-comment-root';
        item.dataset.commentId = comment.id;

        const content = document.createElement('p');
        content.className = 'course-comment-content';
        if (comment.deleted) {
            item.classList.add('course-comment-deleted');
            content.textContent = '삭제된 댓글입니다.';
            item.append(content);
            return item;
        }

        const meta = document.createElement('div');
        meta.className = 'course-comment-meta';
        const writer = document.createElement('strong');
        writer.textContent = comment.writerNickname || '';
        const date = document.createElement('time');
        date.className = 'course-comment-date';
        date.dateTime = comment.updatedAt || comment.createdAt;
        date.textContent = formatDate(comment.updatedAt || comment.createdAt);
        meta.append(writer, date);

        content.textContent = comment.content || '';
        item.append(meta);
        if (comment.replyToCommentId != null) {
            const replyTarget = document.createElement('p');
            replyTarget.className = 'course-comment-reply-target';
            replyTarget.textContent = comment.replyToDeleted
                ? '삭제된 댓글에 대한 답글'
                : `@${comment.replyToNickname || '알 수 없는 사용자'}에게 답글`;
            item.append(replyTarget);
        }
        item.append(content);

        const loggedIn = typeof isLoggedIn !== 'undefined' && isLoggedIn;
        const actions = document.createElement('div');
        actions.className = 'course-comment-actions';
        actions.append(makeLikeControl(comment, loggedIn));
        if (comment.myComment) {
            actions.append(
                makeButton('수정', 'course-comment-edit'),
                makeButton('삭제', 'course-comment-delete')
            );
        }
        if (loggedIn) {
            actions.append(makeButton('답글', 'course-comment-reply-button'));
        }
        item.append(actions);
        return item;
    }

    function compareByCreatedAtAndId(left, right) {
        const timeDifference = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime();
        return timeDifference || Number(left.id) - Number(right.id);
    }

    function renderCommentTree(comments) {
        const roots = comments
            .filter(comment => comment.parentCommentId == null)
            .sort(compareByCreatedAtAndId);
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
                replyList.className = 'course-comment-replies';
                replies.sort(compareByCreatedAtAndId)
                    .forEach(reply => replyList.append(renderComment(reply, true)));
                rootItem.append(replyList);
            }
            list.append(rootItem);
        });
    }

    async function loadComments() {
        try {
            showMessage();
            const comments = await requestJson(`/course-comments?courseId=${encodeURIComponent(courseId)}`);
            list.replaceChildren();
            count.textContent = String(comments.filter(comment => !comment.deleted).length);
            if (comments.length === 0) {
                const empty = document.createElement('li');
                empty.className = 'course-comment-empty';
                empty.textContent = '첫 댓글을 작성해 보세요.';
                list.append(empty);
                return;
            }
            renderCommentTree(comments);
        } catch (error) {
            showMessage(error.message);
        }
    }

    form?.addEventListener('submit', async event => {
        event.preventDefault();
        try {
            await requestJson('/course-comments', {
                method: 'POST',
                body: JSON.stringify({courseId, content: contentInput.value})
            });
            form.reset();
            lengthOutput.textContent = '0';
            await loadComments();
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
                await loadComments();
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
                await loadComments();
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
            await loadComments();
            return;
        }

        if (event.target.matches('.course-comment-save')) {
            const textarea = item.querySelector('.course-comment-edit-textarea');
            try {
                await requestJson(`/course-comments/${commentId}`, {
                    method: 'PUT',
                    body: JSON.stringify({content: textarea.value})
                });
                await loadComments();
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
            await loadComments();
        } catch (error) {
            showMessage(error.message);
        } finally {
            submit.disabled = false;
        }
    });

    loadComments();
});
