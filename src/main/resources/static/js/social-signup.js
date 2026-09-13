/* 신규 소셜가입은 일반 회원가입과 같은 2단계다. 현재 단계는 화면 안에서만 바뀌고 서버는 모른다. */
let socialSignupStep = 1;

$(function () {
    window.TravelDiaryNicknameAvailability?.initialize();
    initializeAgeCheck();
    initializePolicyConsents();
    initializeEmailStatus();
    initializeSteps();
});

/**
 * 1 약관 동의 → 2 계정 정보. 일반 회원가입과 같은 markup·같은 클래스를 그대로 쓴다.
 * 단계 상태를 서버 세션에 두지 않고 하나의 form 안에서 화면만 전환한다.
 */
function initializeSteps() {
    const $progress = $("#socialSignupProgress");
    if ($progress.length === 0) return;

    $(".next-step").on("click", function () {
        if (this.disabled) return;
        // 버튼은 이미 비활성이지만, 값이 바뀐 직후를 대비해 넘어가기 직전에 한 번 더 본다.
        const status = socialSignupBirthDateStatus();
        if (status !== "ok") {
            renderSocialSignupBirthDateStatus(status);
            updateSocialSignupStepGate();
            $("#birthDate").trigger("focus");
            return;
        }
        showSocialSignupStep(2);
    });
    $(".prev-step").on("click", () => showSocialSignupStep(1));

    showSocialSignupStep(initialSocialSignupStep());
}

/**
 * 서버가 되돌려준 화면은 어느 단계에서 고쳐야 하는지 오류 필드가 알려준다.
 * 연령·약관은 1단계, 나머지(닉네임/이메일)는 2단계에서 고친다.
 */
function initialSocialSignupStep() {
    const field = $("[data-field-error]").first().data("field-error");
    if (field === "birthDate" || field === "agreedPolicyVersionIds") return 1;
    if (field || $("[data-server-error]").length) return 2;
    return 1;
}

function showSocialSignupStep(step) {
    socialSignupStep = step;
    $("#socialSignupForm .form-step").prop("hidden", true).removeClass("is-active");
    $("#step-" + step).prop("hidden", false).addClass("is-active");

    $("[data-step-indicator]").each(function () {
        const indicatorStep = Number($(this).data("step-indicator"));
        $(this).toggleClass("is-complete", indicatorStep < step)
            .toggleClass("is-current", indicatorStep === step)
            .attr("aria-current", indicatorStep === step ? "step" : null);
    });

    // 단계 안내 문구는 서버가 현재 locale 템플릿을 data-* 로 내려준다.
    const progress = $("#socialSignupProgress").get(0)?.dataset || {};
    $("#registrationStepStatus").text(
        formatSocialSignupMessage(progress.stepStatusFormat,
            2, step, progress["stepName" + step] || ""));

    updateSocialSignupStepGate();
    updateSocialSignupSubmitState();
    window.scrollTo({top: 0, behavior: "smooth"});
}

function formatSocialSignupMessage(template, ...values) {
    return values.reduce(
        (text, value, index) => text.split("{" + index + "}").join(String(value)),
        template || "");
}

/* 만 14세 판정은 일반 회원가입과 같은 age-eligibility.js 를 쓴다. 기준일은 서버가 내려준 값이다. */
function socialSignupBirthDateStatus() {
    const $input = $("#birthDate");
    if ($input.length === 0) return "ok";
    return window.TravelDiaryAgeEligibility.status($input.val(), $input.data("today"));
}

/** 1단계 통과 조건은 만 14세 이상 + 필수 정책 동의 두 가지뿐이다. 선택 항목은 조건이 아니다. */
function updateSocialSignupStepGate() {
    $("#step1-next").prop("disabled",
        socialSignupBirthDateStatus() !== "ok" || !socialSignupRequiredPoliciesAccepted());
}

function renderSocialSignupBirthDateStatus(status) {
    const $field = $("#socialSignupBirthDateField");
    if ($field.length === 0) return;
    if (status === "underage") {
        setSocialSignupBirthDateMessage($field.data("msg-underage"), "error");
    } else if (status === "invalid") {
        setSocialSignupBirthDateMessage($field.data("msg-invalid"), "error");
    } else {
        // 비어 있는 동안에는 오류가 아니라 원래 안내로 되돌린다.
        setSocialSignupBirthDateMessage($field.data("help-text"));
    }
}

function setSocialSignupBirthDateMessage(text, type = "") {
    $("#birthDateMessage").text(text).removeClass("error success").addClass(type);
    $("#birthDate").attr("aria-invalid", String(type === "error"));
}

/**
 * 만 14세 미만 가입을 단계 이동과 제출 양쪽에서 막는다.
 * 판정 자체는 일반 회원가입 화면과 같은 age-eligibility.js 를 쓴다.
 */
function initializeAgeCheck() {
    const $field = $("#socialSignupBirthDateField");
    const $input = $("#birthDate");
    if ($field.length === 0 || $input.length === 0) return;

    // 통과했을 때 되돌릴 기본 안내. 서버가 내려준 현재 locale 문구다.
    $field.data("help-text", $("#birthDateMessage").text());

    $input.on("input change", function () {
        $("#birthDateServerError").prop("hidden", true);
        // 고쳐서 만 14세 이상이 되면 오류가 그 자리에서 풀린다.
        renderSocialSignupBirthDateStatus(socialSignupBirthDateStatus());
        updateSocialSignupStepGate();
    });

    // 가입 대상이 아니면 제출 자체를 막는다. 서버 검증은 그대로 남아 있다.
    $input.closest("form").on("submit", function (event) {
        const current = socialSignupBirthDateStatus();
        if (current === "ok") return;
        event.preventDefault();
        showSocialSignupStep(1);
        renderSocialSignupBirthDateStatus(current);
        $input.trigger("focus");
    });
}

/**
 * 약관 항목은 서버가 현재 정책 세트로 내려준다.
 * 어떤 항목이 필수인지도 data-policy-required 로 함께 오므로 화면 문자열을 보고 판단하지 않는다.
 * 활성화한 정책이 없으면 대상 체크박스가 없어 단계 이동을 막지 않는다.
 */
function initializePolicyConsents() {
    // 본문은 처음부터 화면에 있고 열고 닫기만 한다. 끝까지 읽어야 체크되는 구조가 아니다.
    const labels = $("#socialSignupForm").get(0)?.dataset || {};
    $(".term-toggle").on("click", function () {
        const $panel = $("#" + $(this).attr("aria-controls"));
        const expanded = $(this).attr("aria-expanded") === "true";
        $(this).attr("aria-expanded", String(!expanded))
            .text(expanded ? labels.msgTermsView : labels.msgTermsHide);
        $panel.prop("hidden", expanded);
    });

    $("#agreeAll").on("change", function () {
        $("[data-policy-consent]").prop("checked", this.checked);
        updateSocialSignupStepGate();
        updateSocialSignupSubmitState();
    });

    $("[data-policy-consent]").on("change", function () {
        const consents = $("[data-policy-consent]");
        $("#agreeAll").prop("checked",
            consents.length > 0 && consents.filter(":not(:checked)").length === 0);
        updateSocialSignupStepGate();
        updateSocialSignupSubmitState();
    });

    updateSocialSignupStepGate();
    updateSocialSignupSubmitState();
}

function socialSignupRequiredPoliciesAccepted() {
    return $("[data-policy-consent][data-policy-required='true']")
        .filter(":not(:checked)").length === 0;
}

/** 필수 동의와 기존 계정 연결 상태 두 가지가 모두 만족해야 제출 버튼이 열린다. */
function updateSocialSignupSubmitState() {
    const $existingAccount = $("#socialSignupExistingAccount");
    const linkingExistingAccount =
        $existingAccount.length > 0 && $existingAccount.prop("hidden") === false;
    $("#socialSignupSubmit").prop("disabled",
        linkingExistingAccount || !socialSignupRequiredPoliciesAccepted());
}

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

    /*
     * 이미 가입된 이메일이면 신규가입 항목을 통째로 감추고 연결 경로만 남긴다.
     * 기존 계정 연결은 새 users 를 만들지 않으므로 단계 UI 도, 연령도, 약관 동의도 받지 않는다.
     * 이메일 칸만 그대로 남아 주소를 고치면 2단계 신규가입 흐름으로 복귀한다.
     */
    function showExistingAccount(show) {
        $("#socialSignupNewHeader").prop("hidden", show);
        $("#socialSignupLinkHeader").prop("hidden", !show);
        $("[data-signup-only]").prop("hidden", show);
        // 감춘 필수 입력이 브라우저 검증을 막지 않도록 required 도 함께 내린다.
        $("#nickname").prop("required", !show);
        $("#birthDate").prop("required", !show);
        if (show) {
            $("#step-1").prop("hidden", true).removeClass("is-active");
            $("#step-2").prop("hidden", false).addClass("is-active");
        } else {
            // 신규가입으로 돌아오면 보고 있던 단계를 그대로 복원한다.
            showSocialSignupStep(socialSignupStep);
        }
        $("#socialSignupExistingAccount").prop("hidden", !show);
        $("#linkExistingEmail").val(show ? $input.val().trim() : "");
        updateSocialSignupSubmitState();
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
