(function () {
    const MINIMUM_AGE = 14;
    const EARLIEST_YEAR = 1900;
    const PATTERN = /^\d{4}-\d{2}-\d{2}$/;

    /**
     * 생년월일에 연 단위를 더한다. 서버 LocalDate.plusYears 와 같은 규칙으로,
     * 더한 해에 같은 날짜가 없으면 그 달의 마지막 날로 당긴다.
     * 2012-02-29 + 14년은 2026-03-01 이 아니라 2026-02-28 이다.
     */
    function plusYears(year, month, day, years) {
        const targetYear = year + years;
        const shifted = new Date(targetYear, month - 1, day);
        // 없는 날짜면 Date 가 다음 달로 넘겨 버린다. 넘어갔으면 그 달 말일로 되돌린다.
        if (shifted.getMonth() !== month - 1) {
            return new Date(targetYear, month, 0);
        }
        return shifted;
    }

    /** yyyy-MM-dd 를 자정 기준 Date 로. 형식이 맞지 않거나 없는 날짜면 null. */
    function parseDate(value) {
        const raw = String(value == null ? "" : value).trim();
        if (!PATTERN.test(raw)) return null;

        const [year, month, day] = raw.split("-").map(Number);
        const parsed = new Date(year, month - 1, day);
        // 존재하지 않는 날짜(2025-02-30 등)는 Date 가 다른 날로 넘겨 버리므로 되돌려 확인한다.
        if (parsed.getFullYear() !== year
            || parsed.getMonth() !== month - 1
            || parsed.getDate() !== day) return null;
        return parsed;
    }

    /**
     * 기준일은 서버가 렌더링에 실어 보낸 날짜다. 브라우저 timezone 이 서버보다 앞서거나
     * 뒤처져도 14번째 생일 경계가 갈리지 않는다.
     *
     * <p>값을 못 받았을 때만 브라우저 날짜로 물러난다. 안내가 통째로 멎는 것보다 낫고,
     * 어차피 최종 판정은 서버가 다시 한다.
     */
    function resolveToday(serverToday) {
        const parsed = parseDate(serverToday);
        if (parsed !== null) return parsed;

        const now = new Date();
        return new Date(now.getFullYear(), now.getMonth(), now.getDate());
    }

    /**
     * 만 14세 판정. 서버 AgeVerificationPolicy 와 같은 경계를 쓴다.
     * 14번째 생일 당일은 통과하고 그 전날까지는 통과하지 못한다.
     *
     * <p>여기 결과는 화면 안내용일 뿐이고 최종 판정은 언제나 서버가 다시 한다.
     * 화면을 자정 너머까지 열어 둬 기준일이 지나가도 POST 시점의 서버 판정이 우선한다.
     *
     * @param rawBirthDate 사용자가 입력한 yyyy-MM-dd
     * @param serverToday 서버가 내려준 오늘(yyyy-MM-dd)
     * @returns {"empty"|"invalid"|"underage"|"ok"}
     */
    function status(rawBirthDate, serverToday) {
        const raw = String(rawBirthDate == null ? "" : rawBirthDate).trim();
        if (raw === "") return "empty";

        const birthDate = parseDate(raw);
        if (birthDate === null) return "invalid";

        const [year, month, day] = raw.split("-").map(Number);
        const today = resolveToday(serverToday);
        if (birthDate > today || year < EARLIEST_YEAR) return "invalid";

        return plusYears(year, month, day, MINIMUM_AGE) > today ? "underage" : "ok";
    }

    window.TravelDiaryAgeEligibility = Object.freeze({status});
})();
