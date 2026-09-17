document.addEventListener('DOMContentLoaded', () => {
    const form = document.querySelector('[data-cover-report-process-form]');
    const decision = form?.querySelector('[data-cover-report-decision]');
    const reason = form?.querySelector('[data-cover-report-reason]');
    const submit = form?.querySelector('[data-cover-report-process-submit]');
    if (form && decision && reason && submit) {
        const updateReasonRequirement = () => {
            reason.required = decision.value !== '' && decision.value !== 'REJECT';
        };

        decision.addEventListener('change', updateReasonRequirement);
        form.addEventListener('submit', event => {
            updateReasonRequirement();
            if (!form.checkValidity()) {
                event.preventDefault();
                form.reportValidity();
                return;
            }
            if (!window.confirm('선택한 내용으로 신고를 처리하시겠습니까?')) {
                event.preventDefault();
                return;
            }
            submit.disabled = true;
        });
        updateReasonRequirement();
    }

    document.querySelectorAll('[data-cover-restore-form]').forEach(restoreForm => {
        const submitButton = restoreForm.querySelector('[data-cover-restore-submit]');
        if (!submitButton) return;

        restoreForm.addEventListener('submit', event => {
            if (!restoreForm.checkValidity()) {
                event.preventDefault();
                restoreForm.reportValidity();
                return;
            }
            const kind = restoreForm.dataset.coverRestoreKind || '대상';
            if (!window.confirm(`${kind} 차단을 해제하시겠습니까?`)) {
                event.preventDefault();
                return;
            }
            submitButton.disabled = true;
        });
    });
});
