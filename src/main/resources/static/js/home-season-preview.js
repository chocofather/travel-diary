(function (root) {
    /*
      메인 계절 hero 미리보기 (개발환경 전용).
      localhost 에서만 ?season=spring|summer|autumn|winter 로 계절을 강제한다.
      운영 도메인이거나 값이 없거나 틀리면 null 을 돌려주고, home.js 가 월별 판별을 그대로 쓴다.
     */

    // 쿼리 값 → home.js seasonMeta 키
    const PREVIEW_SEASONS = {
        spring: 'SPRING',
        summer: 'SUMMER',
        autumn: 'FALL',
        winter: 'WINTER'
    };

    // location.hostname 은 IPv6 주소를 대괄호째 돌려준다.
    const LOCAL_HOSTNAMES = ['localhost', '127.0.0.1', '[::1]'];

    function previewSeasonKey(hostname, search) {
        if (!LOCAL_HOSTNAMES.includes(hostname)) return null;
        const value = new URLSearchParams(search || '').get('season');
        return Object.prototype.hasOwnProperty.call(PREVIEW_SEASONS, value)
            ? PREVIEW_SEASONS[value]
            : null;
    }

    const api = { previewSeasonKey };
    root.TravelDiarySeasonPreview = api;
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? window : globalThis);
