/* /js/main.js */
document.addEventListener('DOMContentLoaded', () => {
    /* 검색창 토글 */
    const toggleBtn = document.getElementById('search-toggle');
    const form = document.getElementById('search-form');

    if (toggleBtn && form) {
        toggleBtn.addEventListener('click', e => {
            e.preventDefault();
            form.classList.toggle('open');
            if (form.classList.contains('open')) {
                form.querySelector('input')?.focus();
            }
        });
    }

    /* 검색창 submit → 검색결과 페이지로 이동 */
    if (form) {
        form.addEventListener('submit', function(e) {
            e.preventDefault(); // 새로고침 막기
            const input = this.querySelector('input');
            const keyword = input.value.trim();
            if (keyword) {
                window.location.href = `/search?q=${encodeURIComponent(keyword)}`;
            }
        });
    }

    /* 프로필 클릭 → 마이페이지 이동 */
    const profile = document.getElementById('profile-img');
    if (profile) {
        profile.addEventListener('click', () => {
            location.href = '/mypage';
        });
    }

    /* 이미지 우클릭 방지 */
    document.addEventListener('contextmenu', e => {
        if (e.target.tagName === 'IMG') {
            e.preventDefault();
        }
    });
});
