/* 관리자 지역 관리: 등록·수정·일괄 등록 대화상자. 검증은 서버가 다시 한다. */
(function () {
    function openDialog(dialog, focusTarget) {
        if (typeof dialog.showModal === 'function') dialog.showModal();
        else dialog.setAttribute('open', '');
        focusTarget?.focus();
    }

    function closeDialog(dialog) {
        if (typeof dialog.close === 'function') dialog.close();
        else dialog.removeAttribute('open');
    }

    // 닫기 버튼과 바깥(배경) 클릭으로 닫는다.
    function bindClose(dialog) {
        dialog.querySelectorAll('[data-region-dialog-close]').forEach(button =>
            button.addEventListener('click', () => closeDialog(dialog)));
        dialog.addEventListener('click', event => {
            if (event.target === dialog) closeDialog(dialog);
        });
    }

    function initCreate() {
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
            openDialog(dialog, parent);
        }

        parent.addEventListener('change', syncCodeField);
        document.querySelectorAll('[data-region-dialog-open]').forEach(button => button.addEventListener('click', open));
        bindClose(dialog);

        syncCodeField();
        if (dialog.dataset.openOnLoad === 'true') open();
    }

    // 카드의 [수정]이 들고 있는 현재 값으로 채운다. 저장 실패로 돌아왔으면 서버가 채운 입력값을 그대로 둔다.
    function initEdit() {
        const dialog = document.getElementById('region-edit-dialog');
        if (!dialog) return;
        const form = dialog.querySelector('[data-region-edit-form]');
        const alert = dialog.querySelector('.admin-region-dialog-alert');
        const codeRow = dialog.querySelector('[data-edit-code-row]');
        const codeValue = dialog.querySelector('[data-edit-code]');
        const fields = {
            ko: 'nameKo', en: 'nameEn', ja: 'nameJa', 'zh-cn': 'nameZhCn', 'zh-tw': 'nameZhTw'
        };

        function open(button) {
            const data = button.dataset;
            form.action = dialog.dataset.actionBase + encodeURIComponent(data.id) + '/edit';
            Object.entries(fields).forEach(([language, key]) => {
                const input = dialog.querySelector(`[data-edit-name="${language}"]`);
                if (input) input.value = data[key] || '';
            });
            codeValue.textContent = data.code || '';
            codeRow.hidden = !data.code;
            if (alert) alert.hidden = true;
            openDialog(dialog, dialog.querySelector('[data-edit-name="ko"]'));
        }

        document.querySelectorAll('[data-region-edit-open]').forEach(button =>
            button.addEventListener('click', () => open(button)));
        bindClose(dialog);

        if (dialog.dataset.openOnLoad === 'true') {
            openDialog(dialog, dialog.querySelector('[data-edit-name="ko"]'));
        }
    }

    function initBulk() {
        const dialog = document.getElementById('region-bulk-dialog');
        if (!dialog) return;
        const input = dialog.querySelector('textarea');
        document.querySelectorAll('[data-region-bulk-open]').forEach(button =>
            button.addEventListener('click', () => openDialog(dialog, input)));
        bindClose(dialog);
        if (dialog.dataset.openOnLoad === 'true') openDialog(dialog, input);
    }

    function init() {
        initCreate();
        initEdit();
        initBulk();
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
}());
