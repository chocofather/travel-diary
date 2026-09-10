$(function () {
    const form = $(".register-container form");
    if (!form.length) return;

    const availability = {username: false, email: false, nickname: false};
    const requestVersion = {username: 0, email: 0, nickname: 0};
    const usernamePattern = /^(?=.*[a-z])[a-z0-9_-]{3,16}$/;
    const passwordPattern = /^(?=.*[!@#$%^&*])[A-Za-z\d!@#$%^&*]{8,}$/;
    const emailPattern = /^[A-Z0-9._%+-]+@[A-Z0-9-]+(?:\.[A-Z0-9-]+)+$/i;

    /* 화면 문구는 서버 messages 번들이 source of truth 다. data-* 로 현재 locale 값을 받는다. */
    const messages = form.get(0).dataset;
    const progress = $(".registration-progress").get(0)?.dataset || {};
    const stepNames = ["", progress.stepName1 || "", progress.stepName2 || ""];
    const formatMessage = (template, ...values) => values.reduce(
        (text, value, index) => text.split("{" + index + "}").join(String(value)),
        template || "");
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
        "#nicknameMessage": "#nickname"
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
        $("#registrationStepStatus").text(
            formatMessage(progress.stepStatusFormat, 2, step, stepNames[step]));
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

    function updateButtons() {
        const requiredTermsAccepted = $("#termsAgree1").is(":checked")
            && $("#termsAgree2").is(":checked");
        $("#step1-next").prop("disabled", !requiredTermsAccepted);

        const accountReady = availability.username
            && availability.email
            && passwordIsValid()
            && passwordsMatch()
            && availability.nickname;
        $("#step2-submit").prop("disabled", isSubmitting || !accountReady);
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
                    response.exists ? messages.msgUsernameTaken : messages.msgUsernameAvailable,
                    response.exists ? "error" : "success");
                updateButtons();
            })
            .fail(function () {
                if (version !== requestVersion.username) return;
                setMessage("#usernameMessage", messages.msgUsernameCheckFailed, "error");
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
                    setMessage("#emailMessage", messages.msgEmailInvalid, "error");
                } else {
                    setMessage("#emailMessage",
                        response.exists ? messages.msgEmailTaken : messages.msgEmailAvailable,
                        response.exists ? "error" : "success");
                }
                updateButtons();
            })
            .fail(function () {
                if (version !== requestVersion.email) return;
                setMessage("#emailMessage", messages.msgEmailCheckFailed, "error");
            });
    });

    function updateEmailTypoSuggestion(email) {
        suggestedEmail = window.TravelDiaryEmailDomain?.suggest(email) || "";
        $("#emailSuggestion").prop("hidden", !suggestedEmail);
        if (suggestedEmail) {
            $("#emailSuggestionText").text(
                formatMessage(messages.msgEmailSuggestion, suggestedEmail));
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
            .text(expanded ? messages.msgTermsView : messages.msgTermsHide);
        panel.prop("hidden", expanded);
    });

    $("#username").on("input", function () {
        clearServerError("username");
        invalidate("username");
        const username = this.value.trim();
        if (!usernamePattern.test(username)) {
            setMessage("#usernameMessage", messages.msgUsernameInvalid, "error");
            return;
        }
        setMessage("#usernameMessage", messages.msgChecking);
        checkUsernameAvailability();
    });

    $("#userEmail").on("input", function () {
        clearServerError("userEmail");
        invalidate("email");
        const email = this.value.trim();
        updateEmailTypoSuggestion(email);
        renderEmailDomainOptions(email);
        if (!emailPattern.test(email)) {
            setMessage("#emailMessage", messages.msgEmailInvalid, "error");
            return;
        }
        setMessage("#emailMessage", messages.msgChecking);
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
            passwordIsValid() ? messages.msgPasswordValid : messages.msgPasswordInvalid,
            passwordIsValid() ? "success" : "error");
        if ($("#passwordConfirm").val().length > 0) {
            setMessage("#passwordMessage",
                passwordsMatch() ? messages.msgPasswordMatch : messages.msgPasswordMismatch,
                passwordsMatch() ? "success" : "error");
        } else {
            setMessage("#passwordMessage", "");
        }
        updateButtons();
    });

    $(".next-step").on("click", function () {
        if (!this.disabled) showStep(Math.min(2, currentStep + 1));
    });
    $(".prev-step").on("click", () => showStep(Math.max(1, currentStep - 1)));

    $(document).on("click", ".toggle-password", function () {
        const input = $($(this).data("toggle"));
        const reveal = input.attr("type") === "password";
        input.attr("type", reveal ? "text" : "password");
        // 표시/숨김 라벨은 버튼이 data-* 로 현재 locale 문구를 들고 있다.
        $(this).toggleClass("show", reveal).toggleClass("hide", !reveal)
            .attr("aria-label", reveal ? $(this).data("hide-label") : $(this).data("show-label"));
    });

    form.on("submit", function (event) {
        if (isSubmitting) {
            event.preventDefault();
            return;
        }
        if (!availability.username || !availability.email || !availability.nickname
            || !passwordIsValid() || !passwordsMatch()) {
            event.preventDefault();
            setMessage("#nicknameMessage", messages.msgIncomplete, "error");
            showStep(2);
            return;
        }
        $("#username").val($("#username").val().trim());
        $("#userEmail").val($("#userEmail").val().trim().toLowerCase());
        $("#nickname").val($("#nickname").val().trim());
        isSubmitting = true;
        $("#step2-submit").text($("#step2-submit").data("submitting-label"));
        updateButtons();
    });

    showStep(currentStep);
    $("#username, #userEmail, #nickname").each(function () {
        const field = this.id;
        if (this.value.trim() && !$(serverErrorSelectors[field]).length) $(this).trigger("input");
    });
    window.addEventListener("pageshow", function () {
        isSubmitting = false;
        $("#step2-submit").text($("#step2-submit").data("submit-label"));
        updateButtons();
    });
});
