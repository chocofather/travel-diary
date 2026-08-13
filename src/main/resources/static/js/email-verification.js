document.addEventListener("DOMContentLoaded", () => {
    const button = document.getElementById("resendVerificationButton");
    const message = document.getElementById("resendCooldown");
    if (button && message) {
        let remainingSeconds = Number.parseInt(button.dataset.cooldownSeconds || "0", 10);
        if (Number.isFinite(remainingSeconds) && remainingSeconds > 0) {
            const render = () => {
                button.disabled = remainingSeconds > 0;
                message.textContent = remainingSeconds > 0
                    ? `${remainingSeconds}초 후 다시 요청할 수 있습니다.`
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
            ? `혹시 ${suggestedEmail}을 입력하려던 건가요?`
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
