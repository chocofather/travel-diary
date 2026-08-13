(function () {
    const commonDomains = Object.freeze([
        "gmail.com",
        "naver.com",
        "daum.net",
        "hanmail.net",
        "kakao.com"
    ]);
    const domainCorrections = new Map([
        ["gamil.com", "gmail.com"],
        ["gmial.com", "gmail.com"],
        ["gmai.com", "gmail.com"]
    ]);

    function splitEmail(email) {
        const value = String(email || "").trim();
        const separator = value.indexOf("@");
        if (separator <= 0 || value.indexOf("@", separator + 1) !== -1) return null;
        return {
            localPart: value.slice(0, separator),
            domainPart: value.slice(separator + 1).toLowerCase()
        };
    }

    function suggest(email) {
        const parts = splitEmail(email);
        if (!parts) return "";
        const correctedDomain = domainCorrections.get(parts.domainPart);
        return correctedDomain ? parts.localPart + "@" + correctedDomain : "";
    }

    function autocomplete(email) {
        const parts = splitEmail(email);
        if (!parts || domainCorrections.has(parts.domainPart)) return [];
        return commonDomains
            .filter(domain => domain.startsWith(parts.domainPart) && domain !== parts.domainPart)
            .map(domain => parts.localPart + "@" + domain);
    }

    window.TravelDiaryEmailDomain = Object.freeze({suggest, autocomplete});
})();
