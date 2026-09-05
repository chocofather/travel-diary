/**
 * 관리자 폼의 언어별 본문 편집기.
 *
 * <p>DOM 순서가 아니라 languageCode 로 편집기 · hidden input · 초기값을 1:1 로 묶는다.
 * 탭을 열지 않은 언어도 편집기를 미리 만들어 두므로 기존 값이 그대로 다시 실린다.
 * 번역 본문은 선택 입력이라 비어 있어도 저장을 막지 않는다. (required: false)
 *
 * <p>탭 전환은 /js/admin-translation-tabs.js 가, 한국어 원본 편집기는 화면별 스크립트가 맡는다.
 */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-translation-editor]').forEach(editorElement => {
        const languageCode = editorElement.dataset.translationEditor;
        const form = editorElement.closest('form');
        if (!languageCode || !form?.id) return;

        const contentInput = form.querySelector(
            `[data-translation-content="${languageCode}"]`);
        const initialContent = form.querySelector(
            `[data-translation-initial-content="${languageCode}"]`);
        if (!contentInput) return;

        window.initQuillEditor(
            editorElement,
            contentInput,
            form.id,
            initialContent,
            {required: false}
        );
    });
});
