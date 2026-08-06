document.addEventListener('DOMContentLoaded', () => {
    const section = document.getElementById('post-comments');
    if (!section) return;

    const postId = Number(section.dataset.postId);
    const list = document.getElementById('post-comment-list');
    const count = document.getElementById('post-comment-count');
    const message = document.getElementById('post-comment-message');
    const form = document.getElementById('post-comment-form');
    const contentInput = document.getElementById('post-comment-content');
    const lengthOutput = document.getElementById('post-comment-length');

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
        item.className = 'post-comment-item';
        item.dataset.commentId = comment.id;

        const meta = document.createElement('div');
        meta.className = 'post-comment-meta';

        const writer = document.createElement('strong');
        writer.textContent = comment.writerNickname;
        const date = document.createElement('time');
        date.className = 'post-comment-date';
        date.dateTime = comment.updatedAt || comment.createdAt;
        date.textContent = formatDate(comment.updatedAt || comment.createdAt);
        meta.append(writer, date);

        const content = document.createElement('p');
        content.className = 'post-comment-content';
        content.textContent = comment.content;
        item.append(meta, content);

        if (comment.myComment) {
            const actions = document.createElement('div');
            actions.className = 'post-comment-actions';
            actions.append(
                makeButton('수정', 'post-comment-edit'),
                makeButton('삭제', 'post-comment-delete')
            );
            item.append(actions);
        }
        return item;
    }

    async function loadComments() {
        try {
            showMessage();
            const comments = await requestJson(`/post-comments?postId=${encodeURIComponent(postId)}`);
            list.replaceChildren();
            count.textContent = comments.length;
            if (comments.length === 0) {
                const empty = document.createElement('li');
                empty.className = 'post-comment-empty';
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
            await requestJson('/post-comments', {
                method: 'POST',
                body: JSON.stringify({postId, content: contentInput.value})
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
        const item = event.target.closest('.post-comment-item');
        if (!item) return;
        const commentId = item.dataset.commentId;

        if (event.target.matches('.post-comment-delete')) {
            if (!window.confirm('댓글을 삭제하시겠습니까?')) return;
            try {
                await requestJson(`/post-comments/${commentId}`, {method: 'DELETE'});
                await loadComments();
            } catch (error) {
                showMessage(error.message);
            }
            return;
        }

        if (event.target.matches('.post-comment-edit')) {
            const content = item.querySelector('.post-comment-content');
            const actions = item.querySelector('.post-comment-actions');
            const textarea = document.createElement('textarea');
            textarea.className = 'post-comment-edit-textarea';
            textarea.maxLength = 2000;
            textarea.value = content.textContent;

            const editActions = document.createElement('div');
            editActions.className = 'post-comment-edit-actions post-comment-actions';
            editActions.append(
                makeButton('저장', 'post-comment-save'),
                makeButton('취소', 'post-comment-cancel')
            );
            content.replaceWith(textarea);
            actions.replaceWith(editActions);
            textarea.focus();
            return;
        }

        if (event.target.matches('.post-comment-cancel')) {
            await loadComments();
            return;
        }

        if (event.target.matches('.post-comment-save')) {
            const textarea = item.querySelector('.post-comment-edit-textarea');
            try {
                await requestJson(`/post-comments/${commentId}`, {
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
