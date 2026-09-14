/*
 * 내 여행일기 목록에 띄우는 "체험 여행일기가 남아 있어요" 안내.
 *
 * [나중에 하기] 를 고르면 체험 여행일기가 이 브라우저에 그대로 남는다. 그때 다시 들어올 길이
 * 필요해서 목록 위에 한 줄만 둔다. 서버는 남아 있는지 알 수 없으므로 여기에서 저장소를 보고 켠다.
 *
 * 읽기만 한다. 지우지도, 서버로 보내지도 않는다.
 */
document.addEventListener('DOMContentLoaded', () => {
    const notice = document.querySelector('[data-guest-import-notice]');
    const store = window.TravelDiaryGuestDraftStore;
    if (!notice || !store) return;

    // 남은 것이 없으면 아무것도 보여 주지 않는다.
    notice.hidden = !store.hasDraft();
});
