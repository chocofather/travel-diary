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
    const $existingAccount = $("#socialSignupExistingAccount");
    const $signupSubmit = $("#socialSignupSubmit");
    const $newFields = $("#socialSignupNewFields");
    const $newHeader = $("#socialSignupNewHeader");
    const $linkHeader = $("#socialSignupLinkHeader");
    let requestVersion = 0;

    function setMessage(text, type = "") {
        $message.text(text).removeClass("error success").addClass(type);
    }

    /*
     * 이미 가입된 이메일이면 신규가입 항목을 통째로 감추고 연결 경로만 남긴다.
     * 기존 계정 연결은 새 users 를 만들지 않으므로 닉네임도 약관도 받지 않는다.
     */
    function showExistingAccount(show) {
        $newHeader.prop("hidden", show);
        $linkHeader.prop("hidden", !show);
        $newFields.prop("hidden", show);
        // 감춘 필수 입력이 브라우저 검증을 막지 않도록 required 도 함께 내린다.
        $("#nickname").prop("required", !show);
        $signupSubmit.prop("disabled", show);
        $existingAccount.prop("hidden", !show);
        $("#linkExistingEmail").val(show ? $input.val().trim() : "");
    }

    $input.on("input", function () {
        requestVersion += 1;
        setMessage(helpText);
        $("#emailServerError").prop("hidden", true);
        // 이메일을 고치면 이전 확인 결과와 연결 버튼을 되돌리고 다시 확인하게 한다.
        showExistingAccount(false);
    });

    showExistingAccount(false);

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
                const status = response?.status;
                const result = messages[status];
                if (result) {
                    setMessage(result.text, result.type);
                } else {
                    setMessage(checkFailed, "error");
                }
                showExistingAccount(status === "EXISTING_ACTIVE");
            })
            .fail(function () {
                if (version !== requestVersion) return;
                setMessage(checkFailed, "error");
            });
    });
}
