(function ($) {
    if (!$) return;

    const nicknamePattern = /^[가-힣A-Za-z0-9]{2,12}$/;

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
                        ? "사용할 수 없는 닉네임입니다."
                        : response.exists ? "이미 사용 중인 닉네임입니다."
                            : "사용 가능한 닉네임입니다.";
                    setMessage(message, isAvailable ? "success" : "error");
                })
                .fail(function () {
                    if (version !== requestVersion) return;
                    setMessage("닉네임 중복 확인에 실패했습니다.", "error");
                });
        });

        $input.on("input.nicknameAvailability", function () {
            options.onInput?.();
            invalidate();
            const nickname = this.value.trim();
            if (!nicknamePattern.test(nickname)) {
                setMessage(
                    "2~12자의 한글, 영문, 숫자만 사용할 수 있습니다. 공백·특수문자 및 부적절한 표현은 사용할 수 없습니다.",
                    "error");
                return;
            }
            setMessage("사용 가능 여부를 확인하고 있습니다.");
            checkAvailability();
        });

        $recommendationButton.on("click.nicknameAvailability", function () {
            invalidate();
            $.get("/api/users/generate-nickname")
                .done(nickname => $input.val(nickname).trigger("input").focus())
                .fail(() => setMessage("닉네임 생성에 실패했습니다.", "error"));
        });

        return {
            isAvailable: () => available
        };
    }

    window.TravelDiaryNicknameAvailability = Object.freeze({initialize});
})(window.jQuery);
