/* 관리자 지역 관리: 지역 등록 대화상자. 검증은 서버가 다시 한다. */
(function () {
    function init() {
        const dialog = document.getElementById('region-create-dialog');
        if (!dialog) return;
        const parent = dialog.querySelector('[data-region-parent]');
        const codeField = dialog.querySelector('[data-region-code-field]');
        const code = dialog.querySelector('[data-region-code]');
        const codeHint = dialog.querySelector('[data-region-code-hint]');

        // 최상위 바로 아래(국가·시/도)를 등록할 때만 코드를 받는다. 그 밖에는 보내지 않는다.
        function syncCodeField() {
            const option = parent.selectedOptions[0];
            const required = option?.dataset.codeRequired === 'true';
            codeField.hidden = !required;
            code.disabled = !required;
            code.required = required;
            const prefix = option?.dataset.codePrefix;
            codeHint.textContent = prefix
                ? `시/도 코드는 ${prefix}11 처럼 ${prefix} 뒤에 영문/숫자로 입력합니다.`
                : '국가는 JP 처럼 영문 대문자 2~3자로 입력합니다.';
            code.placeholder = prefix ? `예: ${prefix}11` : '예: JP';
        }

        function open() {
            syncCodeField();
            if (typeof dialog.showModal === 'function') dialog.showModal();
            else dialog.setAttribute('open', '');
            parent.focus();
        }

        function close() {
            if (typeof dialog.close === 'function') dialog.close();
            else dialog.removeAttribute('open');
        }

        parent.addEventListener('change', syncCodeField);
        document.querySelectorAll('[data-region-dialog-open]').forEach(button => button.addEventListener('click', open));
        dialog.querySelectorAll('[data-region-dialog-close]').forEach(button => button.addEventListener('click', close));
        // 바깥(배경)을 누르면 닫는다.
        dialog.addEventListener('click', event => {
            if (event.target === dialog) close();
        });

        syncCodeField();
        if (dialog.dataset.openOnLoad === 'true') open();
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
}());
