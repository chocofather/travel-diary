/**
 * 축제·행사 폼의 한국어 원본 편집기.
 *
 * <p>등록 화면과 수정 화면은 폼 id 가 다르므로(festival-create-form / festival-edit-form)
 * 편집기가 들어 있는 폼에서 id 를 읽어 붙인다. TourAPI 자동입력은 등록 화면에서만 필요해
 * 따로 두었고, 편집기 초기화는 두 화면 모두에서 돈다.
 *
 * <p>언어별 번역 편집기는 /js/admin-translation-editors.js 가 맡는다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const editorElement = document.getElementById('festival-editor');
    const form = editorElement?.closest('form');
    if (!editorElement || !form?.id) return;

    window.initQuillEditor(
        editorElement,
        'festival-content',
        form.id,
        'festival-initial-content'
    );
});
