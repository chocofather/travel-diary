(function ($) {
    if (!$) return;

    // 서버 NicknamePolicy 와 같은 범위(한글/영문/숫자 + 일본어 가나 + CJK 한자, 2~16자)
    const nicknamePattern = /^[가-힣A-Za-z0-9\u3041-\u3096\u30A1-\u30FA\u30FC\u4E00-\u9FFF]{2,16}$/;

    /* 문구는 서버 messages 번들이 source of truth 다.
       일반 가입 / 소셜 가입 템플릿이 같은 fragment 로 현재 locale 값을 data-* 에 실어 준다. */
    const messages = document.getElementById("nickname-messages")?.dataset || {};

    function debounce(callback, delay = 300) {
        let timer;
        return function (...args) {
            clearTimeout(timer);
            timer = setTimeout(() => callback.apply(this, args), delay);
        };
    }

    function initialize(options = {}) {
        const inputSelector = options.inputSelector || "#nickname";
        const messageSelector = options.messageSelector || "#nicknameMessage";
        const recommendationSelector = options.recommendationSelector || "#generateNickname";
        const $input = $(inputSelector);
        const $message = $(messageSelector);
        const $recommendationButton = $(recommendationSelector);
        if (!$input.length || !$message.length || !$recommendationButton.length) return null;

        let requestVersion = 0;
        let available = false;

        function setMessage(message, type = "") {
            $message.text(message).removeClass("error success").addClass(type);
            $input.attr("aria-invalid", String(type === "error"));
        }

        function setAvailable(nextAvailable) {
            available = nextAvailable;
            options.onAvailabilityChange?.(available);
        }

        function invalidate() {
            requestVersion += 1;
            setAvailable(false);
        }

        const checkAvailability = debounce(function () {
            const nickname = $input.val().trim();
            if (!nicknamePattern.test(nickname)) return;
            const version = requestVersion;

            $.get("/api/users/check-nickname", {nickname})
                .done(function (response) {
                    if (version !== requestVersion || nickname !== $input.val().trim()) return;
                    const isAvailable = !response.exists && response.status === "AVAILABLE";
                    setAvailable(isAvailable);
                    const message = response.status === "FORBIDDEN"
                        ? messages.forbidden
                        : response.exists ? messages.taken : messages.available;
                    setMessage(message, isAvailable ? "success" : "error");
                })
                .fail(function () {
                    if (version !== requestVersion) return;
                    setMessage(messages.checkFailed, "error");
                });
        });

        $input.on("input.nicknameAvailability", function () {
            options.onInput?.();
            invalidate();
            const nickname = this.value.trim();
            if (!nicknamePattern.test(nickname)) {
                setMessage(messages.invalid, "error");
                return;
            }
            setMessage(messages.checking);
            checkAvailability();
        });

        $recommendationButton.on("click.nicknameAvailability", function () {
            invalidate();
            $.get("/api/users/generate-nickname")
                .done(nickname => $input.val(nickname).trigger("input").focus())
                .fail(() => setMessage(messages.generateFailed, "error"));
        });

        return {
            isAvailable: () => available
        };
    }

    window.TravelDiaryNicknameAvailability = Object.freeze({initialize});
})(window.jQuery);
