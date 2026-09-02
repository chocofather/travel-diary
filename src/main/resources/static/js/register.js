$(function () {
    const form = $(".register-container form");
    if (!form.length) return;

    const availability = {username: false, email: false, nickname: false};
    const requestVersion = {username: 0, email: 0, nickname: 0};
    const usernamePattern = /^(?=.*[a-z])[a-z0-9_-]{3,16}$/;
    const fullNamePattern = /^[가-힣A-Za-z]+(?: +[가-힣A-Za-z]+)*$/;
    const passwordPattern = /^(?=.*[!@#$%^&*])[A-Za-z\d!@#$%^&*]{8,}$/;
    const emailPattern = /^[A-Z0-9._%+-]+@[A-Z0-9-]+(?:\.[A-Z0-9-]+)+$/i;
    const stepNames = ["", "약관 동의", "계정 정보", "기본 정보"];
    const serverErrorSelectors = {
        username: "#usernameServerError",
        userEmail: "#emailServerError",
        nickname: "#nicknameServerError"
    };
    const feedbackOwners = {
        "#usernameMessage": "#username",
        "#emailMessage": "#userEmail",
        "#passwordValidationMessage": "#userPassword",
        "#passwordMessage": "#passwordConfirm",
        "#nicknameMessage": "#nickname",
        "#fullNameMessage": "#fullName"
    };
    let currentStep = initialStep();
    let suggestedEmail = "";
    let emailDomainOptions = [];
    let activeEmailDomainIndex = -1;
    let isSubmitting = false;

    function debounce(callback, delay = 300) {
        let timer;
        return function (...args) {
            clearTimeout(timer);
            timer = setTimeout(() => callback.apply(this, args), delay);
        };
    }

    function initialStep() {
        const field = $("[data-field-error]").first().data("field-error");
        if (["nickname", "fullName", "userPhone", "userBirth"].includes(field)) return 3;
        if (field || $("[data-server-error]").length) return 2;
        return 1;
    }

    function showStep(step) {
        currentStep = step;
        $(".form-step").prop("hidden", true).removeClass("is-active");
        $("#step-" + step).prop("hidden", false).addClass("is-active");
        $("[data-step-indicator]").each(function () {
            const indicatorStep = Number($(this).data("step-indicator"));
            $(this).toggleClass("is-complete", indicatorStep < step)
                .toggleClass("is-current", indicatorStep === step)
                .attr("aria-current", indicatorStep === step ? "step" : null);
        });
        $("#registrationStepStatus").text("3단계 중 " + step + "단계 · " + stepNames[step]);
        updateButtons();
        window.scrollTo({top: 0, behavior: "smooth"});
    }

    function setMessage(selector, message, type = "") {
        $(selector).text(message).removeClass("error success").addClass(type);
        if (feedbackOwners[selector]) {
            $(feedbackOwners[selector]).attr("aria-invalid", String(type === "error"));
        }
    }

    function passwordIsValid() {
        return passwordPattern.test($("#userPassword").val());
    }

    function passwordsMatch() {
        const password = $("#userPassword").val();
        return password.length > 0 && password === $("#passwordConfirm").val();
    }

    function normalizedFullName() {
        return $("#fullName").val().trim().replace(/\s+/g, " ");
    }

    function fullNameIsValid() {
        const fullName = normalizedFullName();
        return fullName.length <= 50 && fullNamePattern.test(fullName);
    }

    function updateButtons() {
        const requiredTermsAccepted = $("#termsAgree1").is(":checked")
            && $("#termsAgree2").is(":checked");
        $("#step1-next").prop("disabled", !requiredTermsAccepted);

        const accountReady = availability.username
            && availability.email
            && passwordIsValid()
            && passwordsMatch();
        $("#step2-next").prop("disabled", !accountReady);

        const profileReady = availability.nickname
            && fullNameIsValid()
            && $("#userBirth").val().length > 0;
        $("#step3-submit").prop("disabled", isSubmitting || !profileReady);
    }

    function invalidate(field) {
        availability[field] = false;
        requestVersion[field] += 1;
        updateButtons();
    }

    function clearServerError(field) {
        $(serverErrorSelectors[field]).prop("hidden", true);
    }

    const checkUsernameAvailability = debounce(function () {
        const username = $("#username").val().trim();
        if (!usernamePattern.test(username)) return;
        const version = requestVersion.username;

        $.get("/api/users/check-username", {username})
            .done(function (response) {
                if (version !== requestVersion.username || username !== $("#username").val().trim()) return;
                availability.username = !response.exists;
                setMessage("#usernameMessage",
                    response.exists ? "이미 사용 중인 아이디입니다." : "사용 가능한 아이디입니다.",
                    response.exists ? "error" : "success");
                updateButtons();
            })
            .fail(function () {
                if (version !== requestVersion.username) return;
                setMessage("#usernameMessage", "아이디 중복 확인에 실패했습니다.", "error");
            });
    });

    const checkEmailAvailability = debounce(function () {
        const email = $("#userEmail").val().trim().toLowerCase();
        if (!emailPattern.test(email)) return;
        const version = requestVersion.email;

        $.get("/api/users/check-email", {email})
            .done(function (response) {
                if (version !== requestVersion.email
                    || email !== $("#userEmail").val().trim().toLowerCase()) return;
                availability.email = response.valid !== false && !response.exists;
                if (response.valid === false) {
                    setMessage("#emailMessage", "올바른 이메일 주소를 입력해주세요.", "error");
                } else {
                    setMessage("#emailMessage",
                        response.exists ? "이미 사용 중인 이메일입니다." : "사용 가능한 이메일입니다.",
                        response.exists ? "error" : "success");
                }
                updateButtons();
            })
            .fail(function () {
                if (version !== requestVersion.email) return;
                setMessage("#emailMessage", "이메일 중복 확인에 실패했습니다.", "error");
            });
    });

    function updateEmailTypoSuggestion(email) {
        suggestedEmail = window.TravelDiaryEmailDomain?.suggest(email) || "";
        $("#emailSuggestion").prop("hidden", !suggestedEmail);
        if (suggestedEmail) {
            $("#emailSuggestionText").text("혹시 " + suggestedEmail + "을 입력하려던 건가요?");
        }
    }

    function renderEmailDomainOptions(email) {
        emailDomainOptions = window.TravelDiaryEmailDomain?.autocomplete(email) || [];
        activeEmailDomainIndex = -1;
        const list = $("#emailDomainSuggestions").empty();
        emailDomainOptions.forEach(function (option, index) {
            $("<button>", {
                type: "button",
                id: "email-domain-option-" + index,
                role: "option",
                text: option,
                "aria-selected": "false"
            }).attr("data-value", option).appendTo(list);
        });
        const visible = emailDomainOptions.length > 0;
        list.prop("hidden", !visible);
        $("#userEmail").attr("aria-expanded", String(visible)).removeAttr("aria-activedescendant");
    }

    function closeEmailDomainOptions() {
        emailDomainOptions = [];
        activeEmailDomainIndex = -1;
        $("#emailDomainSuggestions").empty().prop("hidden", true);
        $("#userEmail").attr("aria-expanded", "false").removeAttr("aria-activedescendant");
    }

    function moveEmailDomainSelection(direction) {
        if (!emailDomainOptions.length) return;
        activeEmailDomainIndex = (activeEmailDomainIndex + direction + emailDomainOptions.length)
            % emailDomainOptions.length;
        $("#emailDomainSuggestions [role='option']").each(function (index) {
            $(this).attr("aria-selected", String(index === activeEmailDomainIndex));
        });
        const activeId = "email-domain-option-" + activeEmailDomainIndex;
        $("#userEmail").attr("aria-activedescendant", activeId);
        document.getElementById(activeId)?.scrollIntoView({block: "nearest"});
    }

    function chooseEmailDomain(value) {
        if (!value) return;
        closeEmailDomainOptions();
        $("#userEmail").val(value).trigger("input").focus();
    }

    $("#agreeAll").on("change", function () {
        $("#termsAgree1, #termsAgree2, #termsAgree3").prop("checked", this.checked);
        updateButtons();
    });

    $("#termsAgree1, #termsAgree2, #termsAgree3").on("change", function () {
        $("#agreeAll").prop("checked",
            $("#termsAgree1").is(":checked")
            && $("#termsAgree2").is(":checked")
            && $("#termsAgree3").is(":checked"));
        updateButtons();
    });

    $(".term-toggle").on("click", function () {
        const panel = $("#" + $(this).attr("aria-controls"));
        const expanded = $(this).attr("aria-expanded") === "true";
        $(this).attr("aria-expanded", String(!expanded))
            .text(expanded ? "내용 보기" : "내용 닫기");
        panel.prop("hidden", expanded);
    });

    $("#username").on("input", function () {
        clearServerError("username");
        invalidate("username");
        const username = this.value.trim();
        if (!usernamePattern.test(username)) {
            setMessage("#usernameMessage",
                "영문 소문자를 포함한 3~16자의 영문, 숫자, -, _만 사용할 수 있습니다.", "error");
            return;
        }
        setMessage("#usernameMessage", "사용 가능 여부를 확인하고 있습니다.");
        checkUsernameAvailability();
    });

    $("#userEmail").on("input", function () {
        clearServerError("userEmail");
        invalidate("email");
        const email = this.value.trim();
        updateEmailTypoSuggestion(email);
        renderEmailDomainOptions(email);
        if (!emailPattern.test(email)) {
            setMessage("#emailMessage", "올바른 이메일 주소를 입력해주세요.", "error");
            return;
        }
        setMessage("#emailMessage", "사용 가능 여부를 확인하고 있습니다.");
        checkEmailAvailability();
    }).on("keydown", function (event) {
        if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            if (!emailDomainOptions.length) return;
            event.preventDefault();
            moveEmailDomainSelection(event.key === "ArrowDown" ? 1 : -1);
        } else if (event.key === "Enter" && activeEmailDomainIndex >= 0) {
            event.preventDefault();
            chooseEmailDomain(emailDomainOptions[activeEmailDomainIndex]);
        } else if (event.key === "Escape") {
            closeEmailDomainOptions();
        }
    }).on("blur", function () {
        window.setTimeout(closeEmailDomainOptions, 120);
    });

    $("#emailDomainSuggestions")
        .on("pointerdown", "[role='option']", event => event.preventDefault())
        .on("click", "[role='option']", function () {
            chooseEmailDomain($(this).attr("data-value"));
        });

    $("#applyEmailSuggestion").on("click", function () {
        chooseEmailDomain(suggestedEmail);
    });

    window.TravelDiaryNicknameAvailability.initialize({
        onInput: () => clearServerError("nickname"),
        onAvailabilityChange: isAvailable => {
            availability.nickname = isAvailable;
            updateButtons();
        }
    });

    $("#userPassword, #passwordConfirm").on("input", function () {
        setMessage("#passwordValidationMessage",
            passwordIsValid() ? "사용 가능한 비밀번호입니다."
                : "8자 이상이며 영문, 숫자, !@#$%^&*만 사용할 수 있고 특수문자를 포함해야 합니다.",
            passwordIsValid() ? "success" : "error");
        if ($("#passwordConfirm").val().length > 0) {
            setMessage("#passwordMessage",
                passwordsMatch() ? "비밀번호가 일치합니다." : "비밀번호가 일치하지 않습니다.",
                passwordsMatch() ? "success" : "error");
        } else {
            setMessage("#passwordMessage", "");
        }
        updateButtons();
    });

    $("#fullName").on("input", function () {
        const valid = fullNameIsValid();
        setMessage("#fullNameMessage",
            valid ? "사용 가능한 이름입니다." : "한글, 영문과 이름 사이의 공백만 입력할 수 있습니다.",
            valid ? "success" : "error");
        updateButtons();
    });
    $("#userBirth").on("change", updateButtons);

    $("#userPhone").on("input", function () {
        const digits = this.value.replace(/[^0-9]/g, "").slice(0, 11);
        this.value = digits.length <= 3 ? digits
            : digits.length <= 7 ? digits.slice(0, 3) + "-" + digits.slice(3)
                : digits.slice(0, 3) + "-" + digits.slice(3, 7) + "-" + digits.slice(7);
    });

    $(".next-step").on("click", function () {
        if (!this.disabled) showStep(Math.min(3, currentStep + 1));
    });
    $(".prev-step").on("click", () => showStep(Math.max(1, currentStep - 1)));

    $(document).on("click", ".toggle-password", function () {
        const input = $($(this).data("toggle"));
        const reveal = input.attr("type") === "password";
        input.attr("type", reveal ? "text" : "password");
        $(this).toggleClass("show", reveal).toggleClass("hide", !reveal)
            .attr("aria-label", reveal ? "비밀번호 숨기기" : "비밀번호 표시");
    });

    form.on("submit", function (event) {
        if (isSubmitting) {
            event.preventDefault();
            return;
        }
        if (!availability.username || !availability.email || !availability.nickname
            || !passwordIsValid() || !passwordsMatch() || !fullNameIsValid()) {
            event.preventDefault();
            setMessage("#nicknameMessage", "중복 확인과 입력값 검증을 완료해주세요.", "error");
            showStep(availability.username && availability.email ? 3 : 2);
            return;
        }
        $("#username").val($("#username").val().trim());
        $("#userEmail").val($("#userEmail").val().trim().toLowerCase());
        $("#nickname").val($("#nickname").val().trim());
        $("#fullName").val(normalizedFullName());
        isSubmitting = true;
        $("#step3-submit").text("가입 처리 중...");
        updateButtons();
    });

    const today = new Date();
    $("#userBirth").attr("max", [today.getFullYear(),
        String(today.getMonth() + 1).padStart(2, "0"),
        String(today.getDate()).padStart(2, "0")].join("-"));

    showStep(currentStep);
    $("#username, #userEmail, #nickname").each(function () {
        const field = this.id;
        if (this.value.trim() && !$(serverErrorSelectors[field]).length) $(this).trigger("input");
    });
    if ($("#fullName").val().trim()) $("#fullName").trigger("input");

    window.addEventListener("pageshow", function () {
        isSubmitting = false;
        $("#step3-submit").text("회원가입");
        updateButtons();
    });
});
