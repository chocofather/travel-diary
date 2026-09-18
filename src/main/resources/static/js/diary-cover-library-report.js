/**
 * 공유 표지 신고 창.
 * 라이브러리 목록에 신고 창을 하나만 두고, 카드의 신고 아이콘을 누른 표지 기준으로
 * 보낼 주소와 공유 사진 대상만 바꿔 연다. (신고 서비스·검증은 서버가 그대로 한다)
 *
 * 목록 페이지를 직접 열었을 때와, 표지 디자인 패널이 목록을 끼워 넣었을 때 모두 쓰도록
 * init(root) 로 연결한다. 같은 창을 두 번 연결하지 않는다.
 */
(function (global) {
    'use strict';

    function init(root) {
        const modal = (root || document).querySelector('[data-cover-report-modal]');
        if (!modal || modal.diaryCoverReport) return;
        const scope = modal.closest('[data-cover-library-view]') || document;
        const form = modal.querySelector('[data-cover-report-form]');
        const targets = modal.querySelector('[data-cover-report-targets]');
        const subject = modal.querySelector('[data-cover-report-subject]');
        const reason = modal.querySelector('[data-cover-report-reason]');
        const description = modal.querySelector('[data-cover-report-description]');
        const descriptionHint = modal.querySelector('[data-cover-report-description-hint]');
        const submit = modal.querySelector('[data-cover-report-submit]');
        if (!form || !targets || !reason || !description || !submit) return;

        let opener = null;

        const updateDescriptionRequirement = () => {
            const required = reason.value === 'OTHER';
            description.required = required;
            if (descriptionHint) descriptionHint.textContent = required ? '(필수)' : '(선택)';
        };

        const close = () => {
            modal.hidden = true;
            submit.disabled = false;
            // 초점은 신고 창을 연 카드의 신고 아이콘으로 돌려준다.
            if (opener?.isConnected) opener.focus();
            opener = null;
        };

        const open = button => {
            opener = button;
            form.reset();
            form.action = button.dataset.reportAction;
            if (subject) subject.textContent = button.dataset.reportTitle || '';
            // 이전 표지의 사진 대상은 지우고, 이 표지의 공유 사진만 넣는다.
            targets.querySelectorAll('.diary-cover-library-report-target:not(.is-item)')
                .forEach(label => label.remove());
            const photos = button.closest('[data-library-item]')
                ?.querySelector('template[data-cover-report-photos]');
            if (photos) targets.append(photos.content.cloneNode(true));
            updateDescriptionRequirement();
            modal.hidden = false;
            reason.focus();
        };

        modal.diaryCoverReport = {close};

        scope.addEventListener('click', event => {
            const button = event.target.closest('[data-cover-report-open]');
            if (!button || !button.dataset.reportAction) return;
            event.preventDefault();
            open(button);
        });
        modal.querySelectorAll('[data-cover-report-close]').forEach(button => {
            button.addEventListener('click', close);
        });
        modal.addEventListener('click', event => {
            if (event.target === modal) close();
        });
        reason.addEventListener('change', updateDescriptionRequirement);
        form.addEventListener('submit', event => {
            updateDescriptionRequirement();
            if (!form.checkValidity()) {
                event.preventDefault();
                form.reportValidity();
                return;
            }
            submit.disabled = true;
        });
        updateDescriptionRequirement();
    }

    // Esc 는 열린 신고 창만 닫는다. 표지 디자인 패널이 같은 키로 닫히지 않도록 기본 동작을 막아 알린다.
    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        const open = document.querySelector('[data-cover-report-modal]:not([hidden])');
        if (!open || !open.diaryCoverReport) return;
        event.preventDefault();
        open.diaryCoverReport.close();
    });

    global.diaryCoverLibraryReport = {init};
    document.addEventListener('DOMContentLoaded', () => init(document));
})(window);
