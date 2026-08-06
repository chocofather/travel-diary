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

    function renderComment(comment) {
        const item = document.createElement('li');
        item.className = 'course-comment-item';
        item.dataset.commentId = comment.id;

        const meta = document.createElement('div');
        meta.className = 'course-comment-meta';
        const writer = document.createElement('strong');
        writer.textContent = comment.writerNickname || '';
        const date = document.createElement('time');
        date.className = 'course-comment-date';
        date.dateTime = comment.updatedAt || comment.createdAt;
        date.textContent = formatDate(comment.updatedAt || comment.createdAt);
        meta.append(writer, date);

        const content = document.createElement('p');
        content.className = 'course-comment-content';
        content.textContent = comment.content || '';
        item.append(meta, content);

        if (comment.myComment) {
            const actions = document.createElement('div');
            actions.className = 'course-comment-actions';
            actions.append(
                makeButton('수정', 'course-comment-edit'),
                makeButton('삭제', 'course-comment-delete')
            );
            item.append(actions);
        }
        return item;
    }

    async function loadComments() {
        try {
            showMessage();
            const comments = await requestJson(`/course-comments?courseId=${encodeURIComponent(courseId)}`);
            list.replaceChildren();
            count.textContent = String(comments.length);
            if (comments.length === 0) {
                const empty = document.createElement('li');
                empty.className = 'course-comment-empty';
                empty.textContent = '첫 댓글을 작성해 보세요.';
                list.append(empty);
                return;
            }
            comments.forEach(comment => list.append(renderComment(comment)));
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

    loadComments();
});
