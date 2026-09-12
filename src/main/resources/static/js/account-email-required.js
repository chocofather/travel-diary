$(function () {
    initializeAccountEmailStatus();
});

/**
 * 예전 소셜 회원의 이메일 등록 화면에서 쓰는 상태 확인.
 * 서버가 최종 판정을 다시 하므로 여기 결과는 안내용일 뿐이다.
 */
function initializeAccountEmailStatus() {
    const $field = $("#accountEmailField");
    const $input = $("#userEmail");
    if ($field.length === 0 || $input.length === 0) return;

    const $message = $("#emailMessage");
    const helpText = $message.text();
    const messages = {
        AVAILABLE: {text: $field.data("msg-available"), type: "success"},
        UNAVAILABLE: {text: $field.data("msg-unavailable"), type: "error"},
        INVALID: {text: $field.data("msg-invalid"), type: "error"}
    };
    const checkFailed = $field.data("msg-check-failed");
    // 등록 화면과 변경 화면이 같은 스크립트를 쓰고 확인 경로만 다르다.
    const statusUrl = $field.data("status-url") || "/account/email-required/email-status";
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

        $.get(statusUrl, {email})
            .done(function (response) {
                if (version !== requestVersion) return;
                const result = messages[response?.status];
                setMessage(result ? result.text : checkFailed,
                    result ? result.type : "error");
            })
            .fail(function () {
                if (version !== requestVersion) return;
                setMessage(checkFailed, "error");
            });
    });
}
