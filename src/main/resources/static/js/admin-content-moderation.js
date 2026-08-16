/**
 * 관리자 콘텐츠 조치 공통 헬퍼.
 * 숨김 사유(필수)를 커스텀 모달로 받아 기존 관리자 조치 엔드포인트로 폼 전송한다.
 * (엔드포인트/필드명/CSRF 전달 방식은 그대로다)
 */
(function () {
    const isAdminUser = () => typeof isAdmin !== 'undefined' && isAdmin === true;

    /** 모달은 처음 열 때 한 번만 만든다. */
    let ui = null;
    /** 현재 모달이 대상으로 삼은 댓글 (닫을 때 반드시 비운다) */
    let target = null;

    function ensureModal() {
        if (ui) return ui;

        const overlay = document.createElement('div');
        overlay.className = 'content-moderation-modal';
        overlay.hidden = true;

        const dialog = document.createElement('div');
        dialog.className = 'content-moderation-dialog';
        dialog.setAttribute('role', 'dialog');
        dialog.setAttribute('aria-modal', 'true');
        dialog.setAttribute('aria-label', '댓글 숨김');

        const closeButton = document.createElement('button');
        closeButton.type = 'button';
        closeButton.className = 'content-moderation-modal-close';
        closeButton.setAttribute('aria-label', '닫기');
        closeButton.textContent = '×';

        const title = document.createElement('h2');
        title.className = 'content-moderation-modal-title';
        title.textContent = '댓글 숨김';

        const text = document.createElement('p');
        text.className = 'content-moderation-modal-text';
        text.textContent = '이 댓글을 숨김 처리하시겠습니까?';

        const label = document.createElement('label');
        label.className = 'content-moderation-modal-label';
        label.htmlFor = 'content-moderation-reason-input';
        label.textContent = '숨김 사유';

        const reason = document.createElement('textarea');
        reason.id = 'content-moderation-reason-input';
        reason.className = 'content-moderation-modal-reason';
        reason.rows = 3;
        reason.maxLength = 500;
        reason.required = true;
        reason.placeholder = '사유를 입력해 주세요.';

        const actions = document.createElement('div');
        actions.className = 'content-moderation-modal-actions';

        const cancel = document.createElement('button');
        cancel.type = 'button';
        cancel.className = 'content-moderation-modal-cancel';
        cancel.textContent = '취소';

        const submit = document.createElement('button');
        submit.type = 'button';
        submit.className = 'content-moderation-modal-submit';
        submit.textContent = '숨김 처리';

        actions.append(cancel, submit);
        dialog.append(closeButton, title, text, label, reason, actions);
        overlay.append(dialog);
        document.body.append(overlay);

        overlay.addEventListener('click', event => {
            // 모달 내부 클릭은 backdrop 닫기로 이어지지 않는다.
            if (event.target === overlay) close();
        });
        closeButton.addEventListener('click', close);
        cancel.addEventListener('click', close);
        submit.addEventListener('click', confirmHide);
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape' && !overlay.hidden) close();
        });

        ui = {overlay, reason, submit};
        return ui;
    }

    function close() {
        if (!ui) return;
        ui.overlay.hidden = true;
        // 사유와 대상 댓글 상태를 함께 비운다.
        ui.reason.value = '';
        ui.submit.disabled = false;
        target = null;
    }

    /** 숨김 버튼 → 해당 댓글을 대상으로 모달을 연다. */
    function hide(targetType, targetId) {
        if (!isAdminUser() || !targetId) return;

        const modal = ensureModal();
        target = {targetType, targetId};
        modal.reason.value = '';
        modal.submit.disabled = false;
        modal.overlay.hidden = false;
        modal.reason.focus();
    }

    function confirmHide() {
        if (!ui || !target) return;

        const reason = ui.reason.value;
        // 사유는 필수. 공백만 입력하면 요청하지 않는다.
        if (!reason.trim()) {
            window.alert('조치 사유는 필수입니다.');
            ui.reason.focus();
            return;
        }

        ui.submit.disabled = true; // 중복 제출 방지
        submitHideForm(target.targetType, target.targetId, reason.trim());
    }

    /** 기존 요청 형식 그대로 폼을 만들어 전송한다. */
    function submitHideForm(targetType, targetId, reason) {
        const form = document.createElement('form');
        form.method = 'post';
        form.action = `/admin/contents/${targetType}/${targetId}/hide`;
        form.hidden = true;

        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const fields = {
            reason: reason,
            adminNote: '',
            redirect: window.location.pathname + window.location.search
        };
        if (csrfToken) {
            fields._csrf = csrfToken;
        }
        Object.entries(fields).forEach(([name, value]) => {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
            input.value = value;
            form.append(input);
        });

        document.body.append(form);
        form.submit();
    }

    function makeButton(targetType, targetId, className) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = className;
        button.textContent = '관리자 조치';
        button.dataset.moderationType = targetType;
        button.dataset.moderationId = String(targetId);
        button.addEventListener('click', () => hide(targetType, targetId));
        return button;
    }

    window.adminModeration = {isAdminUser, hide, makeButton};
})();
