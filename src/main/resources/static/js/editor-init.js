// editor-init.js
window.initToastEditor = function(editorSelector, contentInputId, formId) {
    // 에디터 생성
    const editor = new toastui.Editor({
        el: document.querySelector(editorSelector),
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

    // 명시된 폼에만 submit 리스너 추가
    const form = document.getElementById(formId);
    if (!form) {
        console.error(`[initToastEditor] form#${formId} 을(를) 찾을 수 없습니다.`);
        return editor;
    }

    form.addEventListener('submit', () => {
        const html = editor.getHTML();
        document.getElementById(contentInputId).value = html;
        console.log('[DEBUG] 서버로 전송될 content:', html);
    });

    return editor;
};
