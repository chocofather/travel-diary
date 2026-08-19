/**
 * 상단 편집 툴바의 '사진' 액션.
 * 파일을 고르면 기존 PHOTO 생성 폼을 그대로 전송한다. (별도 업로드 API 없음)
 */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.diary-photo-add').forEach((form) => {
        const input = form.querySelector('.diary-photo-input');
        const button = form.querySelector('.diary-toolbar-button');
        const status = form.querySelector('.diary-photo-status');
        if (!input || !button) return;

        let uploading = false;

        // 올리는 중에는 이 페이지의 사진 버튼만 다시 열리지 않게 막는다.
        button.addEventListener('click', (event) => {
            if (uploading) event.preventDefault();
        });

        input.addEventListener('change', () => {
            const file = input.files && input.files[0];
            if (!file) return;

            if (!file.type.startsWith('image/')) {
                input.value = '';
                window.alert('사진 파일만 붙일 수 있습니다.');
                return;
            }

            uploading = true;
            button.classList.add('is-uploading');
            button.setAttribute('aria-disabled', 'true');
            if (status) status.hidden = false;

            // 전송 결과(성공/실패 안내, 현재 spread 유지)는 기존 서버 흐름을 그대로 따른다.
            if (typeof form.requestSubmit === 'function') {
                form.requestSubmit();
            } else {
                form.submit();
            }
        });
    });
});
