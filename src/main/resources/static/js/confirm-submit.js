/**
 * data-confirm 이 붙은 폼은 보내기 전에 그 문구로 한 번 더 묻는다.
 *
 * <p>문구는 화면(템플릿)이 현재 언어로 채워 넣는다. 여기서는 언어를 알지 않는다.
 */
document.addEventListener('submit', event => {
    const form = event.target.closest('form[data-confirm]');
    if (!form) return;
    if (!window.confirm(form.dataset.confirm)) {
        event.preventDefault();
    }
});
