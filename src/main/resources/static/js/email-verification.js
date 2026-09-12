document.addEventListener("DOMContentLoaded", () => {
    /* 문구는 서버 messages 번들이 source of truth 다. data-* 로 현재 locale 값을 받는다. */
    const formatMessage = (template, ...values) => values.reduce(
        (text, value, index) => text.split("{" + index + "}").join(String(value)),
        template || "");

    const button = document.getElementById("resendVerificationButton");
    const message = document.getElementById("resendCooldown");
    /** 인증이 끝나면 카운트다운도 멈춰야 하므로 timer 를 밖에서 끌 수 있게 남긴다. */
    let stopResendCooldown = () => {
    };
    if (button && message) {
        let remainingSeconds = Number.parseInt(button.dataset.cooldownSeconds || "0", 10);
        if (Number.isFinite(remainingSeconds) && remainingSeconds > 0) {
            const render = () => {
                button.disabled = remainingSeconds > 0;
                message.textContent = remainingSeconds > 0
                    ? formatMessage(button.dataset.cooldownFormat, remainingSeconds)
                    : "";
            };

            render();
            const timer = window.setInterval(() => {
                remainingSeconds -= 1;
                render();
                if (remainingSeconds <= 0) window.clearInterval(timer);
            }, 1000);
            stopResendCooldown = () => window.clearInterval(timer);
        }
    }

    initializeVerificationPolling(stopResendCooldown);

    const emailInput = document.getElementById("resendEmail");
    const suggestion = document.getElementById("emailSuggestion");
    const suggestionText = document.getElementById("emailSuggestionText");
    const applySuggestion = document.getElementById("applyEmailSuggestion");
    if (!emailInput || !suggestion || !suggestionText || !applySuggestion) return;

    let suggestedEmail = "";
    const updateSuggestion = () => {
        suggestedEmail = window.TravelDiaryEmailDomain?.suggest(emailInput.value) || "";
        suggestion.hidden = !suggestedEmail;
        suggestionText.textContent = suggestedEmail
            ? formatMessage(suggestion.dataset.suggestionFormat, suggestedEmail)
            : "";
    };

    emailInput.addEventListener("input", updateSuggestion);
    applySuggestion.addEventListener("click", () => {
        if (!suggestedEmail) return;
        emailInput.value = suggestedEmail;
        updateSuggestion();
        emailInput.focus();
    });
    updateSuggestion();
});

/**
 * 다른 탭/창에서 인증 링크를 눌러 계정이 ACTIVE 가 되면 이 대기 화면도 스스로 알아챈다.
 * 확인 대상은 서버 세션이 기다리는 이메일뿐이라 요청에 아무 식별값도 싣지 않는다.
 */
function initializeVerificationPolling(stopResendCooldown) {
    const poller = document.getElementById("verificationStatusPoller");
    if (!poller || typeof window.fetch !== "function") return;

    const statusUrl = poller.dataset.statusUrl;
    const loginUrl = poller.dataset.loginUrl;
    if (!statusUrl || !loginUrl) return;

    const pollInterval = Number.parseInt(poller.dataset.pollInterval || "5000", 10);
    const redirectDelay = Number.parseInt(poller.dataset.redirectDelay || "1500", 10);
    const notice = document.getElementById("verificationCompleteNotice");
    const resendButton = document.getElementById("resendVerificationButton");
    const cooldown = document.getElementById("resendCooldown");

    let timer = null;
    let inFlight = false;
    let finished = false;

    const stop = () => {
        if (timer !== null) {
            window.clearInterval(timer);
            timer = null;
        }
    };

    /* 자동 로그인은 하지 않는다. 안내만 띄우고 기존 정책대로 로그인 화면으로 보낸다. */
    const complete = () => {
        finished = true;
        stop();
        stopResendCooldown();
        if (notice) {
            notice.textContent = poller.dataset.msgVerified || "";
            notice.hidden = false;
        }
        if (resendButton) resendButton.disabled = true;
        if (cooldown) cooldown.textContent = "";
        window.setTimeout(() => {
            window.location.href = loginUrl;
        }, redirectDelay);
    };

    /* 탭이 숨겨져 있거나 앞선 요청이 아직 안 끝났으면 새로 보내지 않는다. */
    const check = () => {
        if (finished || inFlight || document.hidden) return;
        inFlight = true;
        window.fetch(statusUrl, {
            headers: {"Accept": "application/json"},
            credentials: "same-origin"
        })
            .then((response) => (response.ok ? response.json() : null))
            .then((body) => {
                if (!finished && body && body.status === "VERIFIED") complete();
            })
            .catch(() => {
                /* 일시적인 실패는 다음 주기에 다시 확인한다. */
            })
            .finally(() => {
                inFlight = false;
            });
    };

    const start = () => {
        if (finished || timer !== null) return;
        timer = window.setInterval(check, pollInterval);
    };

    document.addEventListener("visibilitychange", () => {
        if (document.hidden) {
            stop();
            return;
        }
        check();
        start();
    });
    window.addEventListener("pagehide", stop);

    if (!document.hidden) start();
}
