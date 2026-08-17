/**
 * 새 여행일기 폼의 대표 이미지 선택 표시.
 * 파일은 폼 제출 때 함께 올라가고, 여기서는 선택한 파일명과 미리보기만 갱신한다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('diary-cover-image');
    const name = document.getElementById('diary-cover-name');
    const preview = document.getElementById('diary-cover-preview');
    const previewImage = document.getElementById('diary-cover-preview-image');
    if (!input || !name || !preview || !previewImage) return;

    const EMPTY_TEXT = '선택된 이미지 없음';
    let previewUrl = null;

    input.addEventListener('change', () => {
        if (previewUrl) {
            URL.revokeObjectURL(previewUrl);
            previewUrl = null;
        }

        const file = input.files && input.files[0];
        if (!file) {
            name.textContent = EMPTY_TEXT;
            previewImage.removeAttribute('src');
            preview.hidden = true;
            return;
        }

        name.textContent = file.name;
        previewUrl = URL.createObjectURL(file);
        previewImage.src = previewUrl;
        preview.hidden = false;
    });
});
