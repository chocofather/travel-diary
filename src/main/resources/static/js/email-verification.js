document.addEventListener("DOMContentLoaded", () => {
    /* 문구는 서버 messages 번들이 source of truth 다. data-* 로 현재 locale 값을 받는다. */
    const formatMessage = (template, ...values) => values.reduce(
        (text, value, index) => text.split("{" + index + "}").join(String(value)),
        template || "");

    const button = document.getElementById("resendVerificationButton");
    const message = document.getElementById("resendCooldown");
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
        }
    }

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
