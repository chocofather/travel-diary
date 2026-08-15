/**
 * 관리자 콘텐츠 조치 공통 헬퍼.
 * 사유(필수)와 내부 메모(선택)를 받아 기존 관리자 조치 엔드포인트로 폼 전송한다.
 */
(function () {
    const isAdminUser = () => typeof isAdmin !== 'undefined' && isAdmin === true;

    function hide(targetType, targetId) {
        if (!isAdminUser() || !targetId) return;

        const reason = window.prompt('관리자 조치 사유를 입력하세요. (필수)');
        if (reason === null) return;
        if (!reason.trim()) {
            window.alert('조치 사유는 필수입니다.');
            return;
        }
        const adminNote = window.prompt('관리자 내부 메모를 입력하세요. (선택)') || '';

        const form = document.createElement('form');
        form.method = 'post';
        form.action = `/admin/contents/${targetType}/${targetId}/hide`;
        form.hidden = true;

        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const fields = {
            reason: reason.trim(),
            adminNote: adminNote.trim(),
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
