// 페이지 로드 시 쿠키에서 아이디 불러오기
$(document).ready(function () {
    const savedUsername = getCookie("savedUsername");
    if (savedUsername && !$("#username").val()) {
        $("#username").val(savedUsername);
        $("#rememberId").prop("checked", true);
    }

    //  redirect 유지 처리 (로그인 실패 후에도 그대로 남도록)
    $('input[name="redirect"]').val(new URLSearchParams(location.search).get('redirect') || '/');


    // 로그인 버튼 클릭 시 아이디 저장 처리
    $("#loginForm").on("submit", function () {
        if ($("#rememberId").is(":checked")) {
            setCookie("savedUsername", $("#username").val(), 7); // 7일 저장
        } else {
            deleteCookie("savedUsername");
        }
    });

    const failureFeedback = document.getElementById("loginFailureFeedback");
    const countdown = document.getElementById("loginThrottleCountdown");
    const loginButton = document.querySelector(".login-submit");
    const initialRemainingSeconds = failureFeedback
        ? Number(failureFeedback.dataset.loginRemainingSeconds)
        : 0;
    if (failureFeedback && countdown && loginButton && initialRemainingSeconds > 0) {
        // 문구는 서버 messages 를 source of truth 로 두고 data-* 로 받는다.
        const messages = failureFeedback.dataset;
        const formatMessage = function (template, ...values) {
            return values.reduce(
                (text, value, index) => text.split("{" + index + "}").join(String(value)),
                template);
        };
        const formatRemaining = function (seconds) {
            if (seconds < 60) {
                return formatMessage(messages.remainingSecondsFormat, seconds);
            }
            const minutes = Math.floor(seconds / 60);
            const remainingSeconds = seconds % 60;
            return remainingSeconds === 0
                ? formatMessage(messages.remainingMinutesFormat, minutes)
                : formatMessage(messages.remainingMinutesSecondsFormat, minutes, remainingSeconds);
        };

        const countdownEndsAt = performance.now() + initialRemainingSeconds * 1000;
        let timerId;
        const updateCountdown = function () {
            const remainingSeconds = Math.max(
                0, Math.ceil((countdownEndsAt - performance.now()) / 1000));
            if (remainingSeconds === 0) {
                loginButton.disabled = false;
                failureFeedback.classList.remove("login-feedback--locked");
                failureFeedback.classList.add("login-feedback--released");
                failureFeedback.setAttribute("role", "status");
                failureFeedback.setAttribute("aria-live", "polite");
                const releasedMessage = document.createElement("p");
                releasedMessage.className = "login-feedback__title";
                releasedMessage.textContent = messages.releasedMessage;
                failureFeedback.replaceChildren(releasedMessage);
                if (timerId) {
                    window.clearInterval(timerId);
                }
                return;
            }
            loginButton.disabled = true;
            countdown.textContent = formatRemaining(remainingSeconds);
        };

        updateCountdown();
        timerId = window.setInterval(updateCountdown, 250);
    }
});

// 쿠키 저장 함수
function setCookie(name, value, days) {
    const date = new Date();
    date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
    const expires = "expires=" + date.toUTCString();
    document.cookie = name + "=" + value + ";" + expires + ";path=/";
}

// 쿠키 가져오기 함수
function getCookie(name) {
    const cname = name + "=";
    const decodedCookie = decodeURIComponent(document.cookie);
    const ca = decodedCookie.split(';');
    for (let i = 0; i < ca.length; i++) {
        let c = ca[i].trim();
        if (c.indexOf(cname) === 0) {
            return c.substring(cname.length, c.length);
        }
    }
    return "";
}

// 쿠키 삭제 함수
function deleteCookie(name) {
    document.cookie = name + "=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
}

// 비밀번호 표시/숨김
$(document).on("click", ".toggle-password", function () {
    const targetSelector = $(this).data("toggle");
    const input = $(targetSelector);
    const isPassword = input.attr("type") === "password"; // ✅ 수정됨

    input.attr("type", isPassword ? "text" : "password");

    // 아이콘 상태 토글
    $(this).toggleClass("show", isPassword);
    $(this).toggleClass("hide", !isPassword);
    $(this).attr("aria-pressed", isPassword);
    // 로그인 화면은 현재 locale 문구를 data-* 로 넘긴다.
    // 아직 다국어 처리하지 않은 화면은 기존 한국어 라벨을 그대로 쓴다.
    const showLabel = $(this).data("show-label") || "비밀번호 표시";
    const hideLabel = $(this).data("hide-label") || "비밀번호 숨기기";
    $(this).attr("aria-label", isPassword ? hideLabel : showLabel);
});

// 새 비밀번호와 확인 값 일치 여부를 브라우저에서도 안내
$(document).ready(function () {
    const password = document.getElementById("newPassword");
    const passwordConfirmation = document.getElementById("newPasswordConfirm");
    if (!password || !passwordConfirmation) {
        return;
    }

    const validatePasswordConfirmation = function () {
        const mismatched = passwordConfirmation.value.length > 0
            && password.value !== passwordConfirmation.value;
        passwordConfirmation.setCustomValidity(
            mismatched ? "새 비밀번호가 일치하지 않습니다." : "");
        passwordConfirmation.setAttribute("aria-invalid", String(mismatched));
    };

    password.addEventListener("input", validatePasswordConfirmation);
    passwordConfirmation.addEventListener("input", validatePasswordConfirmation);
});
