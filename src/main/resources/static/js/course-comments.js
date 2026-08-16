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
    const deepLink = window.TravelDiaryCommentDeepLink;
    const targetCommentId = deepLink?.readTargetCommentId() ?? null;

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
    /** 댓글 하나에 첨부할 수 있는 사진 수. 서버 CourseCommentServiceImpl.MAX_COMMENT_IMAGES 와 같은 값. */
    const MAX_COMMENT_IMAGES = 3;
    const IMAGE_LIMIT_MESSAGE = `사진은 최대 ${MAX_COMMENT_IMAGES}장까지 첨부할 수 있습니다.`;
    /** 폼(댓글/답글)별 사진 선택 상태 */
    const imagePickers = new WeakMap();

    async function requestJson(url, options = {}) {
        // FormData 는 브라우저가 boundary 를 붙여야 하므로 Content-Type 을 직접 지정하지 않는다.
        const sendsFormData = options.body instanceof FormData;
        const baseHeaders = sendsFormData ? {'Accept': 'application/json'} : jsonHeaders;
        const response = await fetch(url, {
            credentials: 'same-origin',
            ...options,
            headers: {...baseHeaders, ...(options.headers || {})}
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

    function makeProfileLink(comment, child, className) {
        if (comment.writerUserId == null) return child;
        const link = document.createElement('a');
        link.className = className;
        link.href = `/users/${encodeURIComponent(String(comment.writerUserId))}`;
        link.append(child);
        return link;
    }

    /**
     * 댓글/답글 폼의 사진 선택·미리보기.
     * 파일 선택창을 다시 열면 브라우저가 input.files 를 통째로 교체하므로,
     * 선택 목록(selected)을 따로 들고 DataTransfer 로 input.files 와 동기화해
     * 여러 번 나눠 고른 사진이 누적되게 한다. (커뮤니티 게시글 댓글과 같은 방식)
     * @param {HTMLFormElement} form 댓글/답글 작성 폼
     */
    function createImagePicker(form) {
        const input = form.querySelector('input[type="file"][name="images"]');
        const preview = form.querySelector('.comment-image-preview');
        if (!input || !preview) return null;

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
                window.alert(IMAGE_LIMIT_MESSAGE);
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

        function render() {
            preview.querySelectorAll('img[data-object-url]').forEach(image => {
                URL.revokeObjectURL(image.src);
            });
            preview.replaceChildren();

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

        const picker = {
            files: () => selected.slice(),
            /** 제한을 넘어 전송하면 안 되는 경우 true */
            exceedsLimit() {
                if (selected.length <= MAX_COMMENT_IMAGES) return false;
                window.alert(IMAGE_LIMIT_MESSAGE);
                return true;
            },
            clear() {
                selected = [];
                syncInput();
                render();
            }
        };
        imagePickers.set(form, picker);
        return picker;
    }

    /** 현재 선택된 사진만 images 키로 담는다. */
    function appendSelectedImages(body, form) {
        imagePickers.get(form)?.files().forEach(file => body.append('images', file));
    }

    function removeReplyForm(replyForm) {
        if (!replyForm) return;
        // 미리보기 objectURL 과 선택 파일을 정리한 뒤 폼을 없앤다.
        imagePickers.get(replyForm)?.clear();
        replyForm.remove();
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

    /**
     * 댓글 첨부 사진(최대 3장). 사진이 없으면 영역 자체를 만들지 않는다.
     * 클릭 확대 모달은 .comment-images / .comment-image 를 기준으로 잡는다.
     */
    function makeCommentImages(comment) {
        const imageUrls = Array.isArray(comment.imageUrls) ? comment.imageUrls : [];
        if (imageUrls.length === 0) return null;

        const group = document.createElement('div');
        group.className = 'comment-images content-comment-images';
        imageUrls.forEach((url, index) => {
            const image = document.createElement('img');
            image.className = 'comment-image content-comment-image';
            image.src = url;
            image.alt = `댓글 이미지 ${index + 1}`;
            group.append(image);
        });
        return group;
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
            content.textContent = comment.moderated
                ? '관리자에 의해 조치된 댓글입니다.'
                : '삭제된 댓글입니다.';
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
        meta.append(makeProfileLink(comment, writer, 'content-comment-writer-link'), timeMeta);

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
        // 관리자 조치 버튼은 ADMIN 에게만 노출한다. 권한은 서버에서 다시 확인한다.
        if (window.adminModeration?.isAdminUser()) {
            actions.append(window.adminModeration.makeButton(
                'COURSE_COMMENT', comment.id, 'course-comment-moderate content-comment-action'));
        }

        const body = document.createElement('div');
        body.className = 'content-comment-body';
        // 삭제·관리자 조치 댓글은 위에서 플레이스홀더로 끝나므로 여기까지 오지 않는다.
        const images = makeCommentImages(comment);
        body.append(meta, content);
        if (images) body.append(images);
        body.append(actions);

        const card = document.createElement('div');
        card.className = 'content-comment-card';
        card.append(
            makeProfileLink(comment, makeProfileImage(comment), 'content-comment-profile-link'),
            body
        );
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
            // 관리자 조치된 대댓글은 트리에서 빼지 않고 placeholder 로 남긴다.
            // 사용자가 직접 삭제한 대댓글은 기존대로 제외한다.
            .filter(comment => comment.parentCommentId != null
                && (!comment.deleted || comment.moderated))
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

    async function loadCommentPage({reset = false, pageOverride = null} = {}) {
        if (isLoading && !reset) return;
        const generation = reset ? ++requestGeneration : requestGeneration;
        const page = Number.isInteger(pageOverride) && pageOverride >= 0
            ? pageOverride
            : (reset ? 0 : nextPage);

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

    async function loadInitialComments() {
        if (targetCommentId == null || !deepLink) {
            await resetComments();
            return;
        }

        const locationGeneration = ++requestGeneration;
        try {
            const location = await requestJson(
                `/course-comments/${encodeURIComponent(targetCommentId)}/location`
                + `?courseId=${encodeURIComponent(courseId)}`
            );
            if (locationGeneration !== requestGeneration) return;
            const targetPage = Number(location.page) - 1;
            if (!Number.isInteger(targetPage) || targetPage < 0) {
                throw new Error('댓글 위치 응답이 올바르지 않습니다.');
            }

            await loadCommentPage({reset: true, pageOverride: targetPage});
            if (!deepLink.focusTarget(list, 'data-comment-id', targetCommentId)) {
                deepLink.scrollToSection(section);
            }
        } catch (error) {
            if (locationGeneration !== requestGeneration) return;
            await resetComments();
            deepLink.scrollToSection(section);
        }
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
        const picker = imagePickers.get(form);
        if (picker?.exceedsLimit()) return;
        try {
            const body = new FormData();
            body.append('courseId', String(courseId));
            body.append('content', contentInput.value);
            appendSelectedImages(body, form);
            await requestJson('/course-comments', {method: 'POST', body});
            form.reset();
            picker?.clear();
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
            removeReplyForm(list.querySelector('.course-comment-reply-form'));

            const replyForm = document.createElement('form');
            replyForm.className = 'course-comment-reply-form';
            replyForm.enctype = 'multipart/form-data';
            replyForm.dataset.replyToCommentId = commentId;

            const textarea = document.createElement('textarea');
            textarea.name = 'content';
            textarea.maxLength = 2000;
            textarea.required = true;
            textarea.placeholder = '답글을 입력해 주세요.';

            // 선택한 사진 미리보기 (등록 전)
            const preview = document.createElement('div');
            preview.className = 'comment-image-preview';
            preview.hidden = true;

            const actions = document.createElement('div');
            actions.className = 'course-comment-reply-actions';

            // 답글도 댓글과 같은 사진 버튼/필드명을 쓴다 (최대 3장)
            const inputId = `course-comment-reply-image-${commentId}`;
            const imageInput = document.createElement('input');
            imageInput.type = 'file';
            imageInput.id = inputId;
            imageInput.name = 'images';
            imageInput.accept = 'image/*';
            imageInput.multiple = true;
            imageInput.hidden = true;

            const uploadLabel = document.createElement('label');
            uploadLabel.className = 'image-upload-label';
            uploadLabel.htmlFor = inputId;
            const cameraIcon = document.createElement('img');
            cameraIcon.src = '/uploads/icons/camera.png';
            cameraIcon.alt = '';
            cameraIcon.setAttribute('aria-hidden', 'true');
            uploadLabel.append(cameraIcon, document.createTextNode(' 사진'));

            const submit = document.createElement('button');
            submit.type = 'submit';
            submit.className = 'course-comment-reply-submit';
            submit.textContent = '등록';
            actions.append(imageInput, uploadLabel, submit,
                makeButton('취소', 'course-comment-reply-cancel'));
            replyForm.append(textarea, preview, actions);
            createImagePicker(replyForm);

            const replyList = item.querySelector(':scope > .course-comment-replies');
            if (replyList) item.insertBefore(replyForm, replyList);
            else item.append(replyForm);
            textarea.focus();
            return;
        }

        if (event.target.matches('.course-comment-reply-cancel')) {
            removeReplyForm(event.target.closest('.course-comment-reply-form'));
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

        const picker = imagePickers.get(replyForm);
        if (picker?.exceedsLimit()) return;

        const submit = replyForm.querySelector('.course-comment-reply-submit');
        if (submit.disabled) return;
        submit.disabled = true;

        try {
            const textarea = replyForm.querySelector('textarea[name="content"]');
            const body = new FormData();
            body.append('courseId', String(courseId));
            body.append('replyToCommentId', String(replyForm.dataset.replyToCommentId));
            body.append('content', textarea.value);
            appendSelectedImages(body, replyForm);
            await requestJson('/course-comments', {method: 'POST', body});
            picker?.clear();
            await resetComments();
        } catch (error) {
            showMessage(error.message);
        } finally {
            submit.disabled = false;
        }
    });

    /**
     * 댓글 사진 클릭 시 확대 모달.
     * 이동은 클릭한 댓글에 첨부된 사진 안에서만 순환한다. (커뮤니티 게시글 댓글과 같은 방식)
     */
    function initCommentImageModal() {
        const modal = document.getElementById('course-comment-image-modal');
        const modalImage = document.getElementById('course-comment-modal-img');
        if (!modal || !modalImage) return;

        /** 현재 보고 있는 댓글의 사진 목록과 위치 */
        let images = [];
        let index = 0;

        const isOpen = () => modal.style.display !== 'none';

        /** 목록 범위를 벗어나면 반대쪽 끝으로 순환한다. */
        function show(nextIndex) {
            if (images.length === 0) return;
            index = (nextIndex + images.length) % images.length;
            modalImage.src = images[index];
        }

        function open(target) {
            // 목록은 클릭한 댓글의 사진 그룹으로만 만든다. (다른 댓글 사진은 섞이지 않는다)
            const group = target.closest('.comment-images');
            const items = group
                ? Array.from(group.querySelectorAll('.comment-image'))
                : [target];
            images = items.map(image => image.src);
            // 사진이 한 장뿐이면 좌/우 버튼을 숨긴다.
            modal.classList.toggle('is-single', images.length <= 1);
            // 클릭한 사진부터 보여준다. (같은 주소가 있어도 위치로 찾는다)
            show(Math.max(items.indexOf(target), 0));
            modal.style.display = 'flex';
        }

        function close() {
            modal.style.display = 'none';
            modalImage.removeAttribute('src');
            images = [];
            index = 0;
        }

        list.addEventListener('click', event => {
            const image = event.target.closest('.comment-image');
            if (image && list.contains(image)) open(image);
        });

        modal.addEventListener('click', event => {
            const nav = event.target.closest('.comment-image-nav');
            if (nav) {
                // 확대 이미지/배경 클릭(닫기)으로 이어지지 않도록 여기서 끊는다.
                event.preventDefault();
                event.stopPropagation();
                show(index + (nav.classList.contains('prev') ? -1 : 1));
                return;
            }
            // 확대 이미지, 닫기 버튼, 배경 클릭이면 닫는다.
            if (event.target === modalImage
                || event.target === modal
                || event.target.closest('.close-btn')) {
                close();
            }
        });

        document.addEventListener('keydown', event => {
            // 모달이 닫혀 있으면 방향키/ESC 에 간섭하지 않는다.
            if (!isOpen()) return;

            if (event.key === 'Escape') {
                close();
                return;
            }
            if (event.key === 'ArrowLeft') {
                event.preventDefault();
                show(index - 1);
                return;
            }
            if (event.key === 'ArrowRight') {
                event.preventDefault();
                show(index + 1);
            }
        });
    }

    if (form) createImagePicker(form);
    initCommentImageModal();
    updateSortUi();
    void loadInitialComments();
});
