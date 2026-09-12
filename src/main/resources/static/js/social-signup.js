$(function () {
    window.TravelDiaryNicknameAvailability?.initialize();
    initializeEmailStatus();
});

/**
 * Kakao/Naver 소셜 가입의 이메일 상태 확인.
 * 서버가 최종 판정을 다시 하므로 여기 결과는 안내용일 뿐이다.
 */
function initializeEmailStatus() {
    const $field = $("#socialSignupEmailField");
    const $input = $("#userEmail");
    if ($field.length === 0 || $input.length === 0) return;

    const $message = $("#emailMessage");
    const helpText = $message.text();
    const messages = {
        AVAILABLE: {text: $field.data("msg-available"), type: "success"},
        EXISTING_ACTIVE: {text: $field.data("msg-existing"), type: "error"},
        UNAVAILABLE: {text: $field.data("msg-unavailable"), type: "error"},
        INVALID: {text: $field.data("msg-invalid"), type: "error"}
    };
    const checkFailed = $field.data("msg-check-failed");
    let requestVersion = 0;

    function setMessage(text, type = "") {
        $message.text(text).removeClass("error success").addClass(type);
    }

    $input.on("input", function () {
        requestVersion += 1;
        setMessage(helpText);
        $("#emailServerError").prop("hidden", true);
    });

    $("#checkEmailStatus").on("click", function () {
        const email = $input.val().trim();
        if (email === "") {
            setMessage(messages.INVALID.text, "error");
            return;
        }
        const version = ++requestVersion;

        $.get("/social-signup/email-status", {email})
            .done(function (response) {
                if (version !== requestVersion) return;
                const result = messages[response?.status];
                if (result) {
                    setMessage(result.text, result.type);
                } else {
                    setMessage(checkFailed, "error");
                }
            })
            .fail(function () {
                if (version !== requestVersion) return;
                setMessage(checkFailed, "error");
            });
    });
}
