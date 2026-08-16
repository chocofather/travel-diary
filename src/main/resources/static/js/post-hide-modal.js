/**
 * 게시글 상세의 관리자 숨김 모달.
 * 사유 입력창을 상시 노출하지 않고 버튼을 눌렀을 때만 받는다.
 * 전송은 기존 폼(액션/필드명/CSRF 토큰) 그대로이며 서버 로직은 건드리지 않는다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const modal = document.getElementById('post-hide-modal');
    if (!modal) return;

    const dialog = modal.querySelector('.post-hide-dialog');
    const form = modal.querySelector('.post-hide-form');
    const reason = modal.querySelector('#post-hide-reason');
    const openButton = document.querySelector('[data-post-hide-open]');
    if (!dialog || !form || !reason || !openButton) return;

    function open() {
        modal.hidden = false;
        reason.focus();
    }

    function close() {
        modal.hidden = true;
        // 다시 열었을 때 이전 입력이 남지 않게 한다.
        reason.value = '';
    }

    openButton.addEventListener('click', open);

    modal.addEventListener('click', event => {
        // 모달 내부 클릭은 backdrop 닫기로 이어지지 않는다.
        if (event.target === modal || event.target.closest('[data-post-hide-close]')) {
            close();
        }
    });

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !modal.hidden) close();
    });

    form.addEventListener('submit', event => {
        // 사유는 필수. 공백만 입력하면 전송하지 않는다.
        if (!reason.value.trim()) {
            event.preventDefault();
            window.alert('숨김 사유를 입력해 주세요.');
            reason.focus();
        }
    });
});
