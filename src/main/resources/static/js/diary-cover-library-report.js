document.addEventListener('DOMContentLoaded', () => {
    const modal = document.querySelector('[data-cover-report-modal]');
    const openButton = document.querySelector('[data-cover-report-open]');
    const form = modal?.querySelector('[data-cover-report-form]');
    const reason = modal?.querySelector('[data-cover-report-reason]');
    const description = modal?.querySelector('[data-cover-report-description]');
    const descriptionHint = modal?.querySelector('[data-cover-report-description-hint]');
    const submit = modal?.querySelector('[data-cover-report-submit]');
    if (!modal || !openButton || !form || !reason || !description || !submit) return;

    const updateDescriptionRequirement = () => {
        const required = reason.value === 'OTHER';
        description.required = required;
        if (descriptionHint) descriptionHint.textContent = required ? '(필수)' : '(선택)';
    };

    const close = () => {
        modal.hidden = true;
        submit.disabled = false;
        openButton.focus();
    };

    openButton.addEventListener('click', () => {
        modal.hidden = false;
        reason.focus();
    });
    modal.querySelectorAll('[data-cover-report-close]').forEach(button => {
        button.addEventListener('click', close);
    });
    modal.addEventListener('click', event => {
        if (event.target === modal) close();
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !modal.hidden) close();
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
});
