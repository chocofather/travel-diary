document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('admin-user-sanction-form');
    if (!form) return;

    const typeInputs = Array.from(form.querySelectorAll('[data-sanction-type]'));
    const expiresField = document.getElementById('sanction-expires-field');
    const expiresInput = document.getElementById('sanction-expires-at');
    if (!expiresField || !expiresInput) return;

    // 영구제한을 고르면 종료일시 입력을 감춘다. 서버가 최종 검증한다.
    const syncExpiresField = () => {
        const selected = typeInputs.find((input) => input.checked)?.value;
        const temporary = selected !== 'PERMANENT';
        expiresField.hidden = !temporary;
        expiresInput.required = temporary;
    };

    typeInputs.forEach((input) => input.addEventListener('change', syncExpiresField));
    syncExpiresField();
});
