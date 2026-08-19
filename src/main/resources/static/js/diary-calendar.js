/**
 * 달력 상단의 연/월 고르기.
 * 고른 값으로 기존과 같은 ?month=YYYY-MM 주소를 만들어 그대로 이동한다.
 * (이전/다음/오늘은 링크라 이 스크립트가 없어도 동작한다)
 */
document.addEventListener('DOMContentLoaded', () => {
    const head = document.getElementById('diary-calendar-head');
    const yearSelect = document.getElementById('diary-calendar-year');
    const monthSelect = document.getElementById('diary-calendar-month');
    if (!head || !yearSelect || !monthSelect) return;

    const calendarUrl = head.dataset.calendarUrl;
    if (!calendarUrl) return;

    // 연도를 바꾸면 월은 그대로, 월을 바꾸면 연도는 그대로 둔다.
    yearSelect.addEventListener('change', move);
    monthSelect.addEventListener('change', move);

    function move() {
        const year = yearSelect.value;
        const month = String(monthSelect.value).padStart(2, '0');
        window.location.href = `${calendarUrl}?month=${year}-${month}`;
    }
});
