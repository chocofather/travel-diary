// editor-init.js
window.initToastEditor = function(editorSelector, contentInputId, formId) {
    const editorElement = document.querySelector(editorSelector);
    const contentInput = document.getElementById(contentInputId);
    const form = document.getElementById(formId);

    if (!editorElement) {
        console.error(`[initToastEditor] editor ${editorSelector}을(를) 찾을 수 없습니다.`);
        return null;
    }
    if (!contentInput) {
        console.error(`[initToastEditor] content input#${contentInputId}을(를) 찾을 수 없습니다.`);
        return null;
    }
    if (!form) {
        console.error(`[initToastEditor] form#${formId}을(를) 찾을 수 없습니다.`);
        return null;
    }
    if (editorElement.toastEditorInstance) {
        return editorElement.toastEditorInstance;
    }

    // 에디터 생성
    const editor = new toastui.Editor({
        el: editorElement,
        height: '400px',
        initialEditType: 'wysiwyg',
        previewStyle: 'vertical',
        hooks: {
            async addImageBlobHook(blob, callback) {
                const fd = new FormData();
                fd.append('image', blob);
                try {
                    const resp = await fetch('/api/upload/editor-image', {
                        method: 'POST',
                        body: fd,
                    });
                    const data = await resp.json();
                    callback(data.url, blob.name);
                } catch {
                    alert('이미지 업로드 실패');
                }
            }
        }
    });
    editorElement.toastEditorInstance = editor;

    if (form.dataset.toastEditorSubmitBound !== 'true') {
        form.addEventListener('submit', event => {
            const html = editor.getHTML();
            const markdown = editor.getMarkdown().trim();

            if (!markdown) {
                event.preventDefault();
                contentInput.value = '';
                alert('본문을 입력해 주세요.');
                editor.focus();
                return;
            }

            contentInput.value = html;
        });
        form.dataset.toastEditorSubmitBound = 'true';
    }

    return editor;
};
