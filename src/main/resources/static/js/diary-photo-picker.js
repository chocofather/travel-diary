/**
 * 상단 편집 툴바의 '사진' 액션.
 * 파일을 고르면 기존 PHOTO 생성 폼을 그대로 전송한다. (별도 업로드 API 없음)
 *
 * 사진의 모습(일반 / 폴라로이드)은 어느 고르개로 열었는지가 정한다.
 * 고른 자리의 값을 폼의 photoStyle 에 담아 함께 보낸다 — 붙인 뒤에 모습을 바꾸지 않는다.
 * (표지 디자인 편집의 사진 등록과 같은 방식이다)
 */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.diary-photo-add').forEach((form) => {
        const inputs = form.querySelectorAll('.diary-photo-input');
        const buttons = form.querySelectorAll('.diary-toolbar-button');
        const style = form.querySelector('[data-photo-style-value]');
        const status = form.querySelector('.diary-photo-status');
        if (!inputs.length) return;

        let uploading = false;

        // 올리는 중에는 이 페이지의 사진 버튼만 다시 열리지 않게 막는다.
        buttons.forEach((button) => {
            button.addEventListener('click', (event) => {
                if (uploading) event.preventDefault();
            });
        });

        inputs.forEach((input) => {
            input.addEventListener('change', () => {
                const files = Array.from(input.files || []);
                if (!files.length) return;

                if (files.some((file) => !file.type.startsWith('image/'))) {
                    input.value = '';
                    window.alert('사진 파일만 붙일 수 있습니다.');
                    return;
                }

                // 이번에 고른 자리의 모습으로 붙인다.
                if (style) style.value = input.dataset.photoStyle || '';

                uploading = true;
                buttons.forEach((button) => {
                    button.classList.add('is-uploading');
                    button.setAttribute('aria-disabled', 'true');
                });
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
});
