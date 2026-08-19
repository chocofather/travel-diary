/**
 * 새 여행일기 / 수정 폼의 대표 이미지 picker.
 * 파일은 폼 제출 때 함께 올라가고, 여기서는 picker 안의 썸네일과 파일명만 갱신한다.
 * (별도 미리보기 상자를 만들지 않고 고르기 전/후가 같은 자리에서 바뀐다)
 */
document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('diary-cover-image');
    const picker = document.getElementById('diary-cover-picker');
    const name = document.getElementById('diary-cover-name');
    const previewImage = document.getElementById('diary-cover-preview-image');
    const action = document.getElementById('diary-cover-action');
    if (!input || !picker || !name || !previewImage) return;

    const EMPTY_TEXT = '대표 이미지 선택';
    let previewUrl = null;

    input.addEventListener('change', () => {
        if (previewUrl) {
            URL.revokeObjectURL(previewUrl);
            previewUrl = null;
        }

        const file = input.files && input.files[0];
        if (!file) {
            // 고르기를 취소하면 처음 상태로 되돌린다. (수정 화면의 기존 표지도 이때 비워진다)
            previewImage.removeAttribute('src');
            previewImage.hidden = true;
            picker.classList.remove('has-image');
            name.textContent = EMPTY_TEXT;
            if (action) action.textContent = '선택';
            return;
        }

        previewUrl = URL.createObjectURL(file);
        previewImage.src = previewUrl;
        previewImage.hidden = false;
        picker.classList.add('has-image');
        name.textContent = file.name;
        if (action) action.textContent = '변경';
    });
});
