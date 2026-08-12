document.addEventListener("DOMContentLoaded", () => {
    const form = document.querySelector(".mypage-profile-form");
    const nicknameInput = document.querySelector("#nickname");
    const status = document.querySelector("#nickname-availability");
    const saveButton = document.querySelector("#profileSaveButton");
    if (!form || !nicknameInput || !status || !saveButton) {
        return;
    }

    const originalNickname = nicknameInput.dataset.currentNickname || "";
    const nicknamePattern = /^[가-힣A-Za-z0-9]{2,12}$/;
    let debounceTimer = null;
    let requestController = null;
    let requestSequence = 0;
    let nicknameState = "current";

    function setState(nextState, message) {
        nicknameState = nextState;
        status.textContent = message;
        status.classList.remove("is-success", "is-error", "is-pending");
        if (nextState === "current" || nextState === "available") {
            status.classList.add("is-success");
        } else if (nextState === "checking") {
            status.classList.add("is-pending");
        } else {
            status.classList.add("is-error");
        }
        saveButton.disabled = nextState !== "current" && nextState !== "available";
    }

    function cancelPendingCheck() {
        if (debounceTimer !== null) {
            window.clearTimeout(debounceTimer);
            debounceTimer = null;
        }
        if (requestController !== null) {
            requestController.abort();
            requestController = null;
        }
        requestSequence += 1;
    }

    function validateNickname(nickname) {
        if (nickname.length < 2) {
            return "닉네임은 2자 이상 입력해주세요.";
        }
        if (nickname.length > 12) {
            return "닉네임은 12자 이하로 입력해주세요.";
        }
        if (!nicknamePattern.test(nickname)) {
            return "공백과 특수문자는 사용할 수 없습니다.";
        }
        return null;
    }

    async function checkAvailability(nickname, sequence) {
        requestController = new AbortController();
        try {
            const response = await fetch(
                `/mypage/profile/check-nickname?nickname=${encodeURIComponent(nickname)}`,
                {
                    method: "GET",
                    headers: {"Accept": "application/json"},
                    signal: requestController.signal
                }
            );
            const result = await response.json();
            if (sequence !== requestSequence || nicknameInput.value !== nickname) {
                return;
            }
            switch (result.status) {
                case "AVAILABLE":
                    setState("available", result.message);
                    break;
                case "CURRENT":
                    setState("current", result.message);
                    break;
                case "FORBIDDEN":
                    setState("forbidden", result.message || "사용할 수 없는 닉네임입니다.");
                    break;
                case "DUPLICATE":
                    setState("duplicate", result.message);
                    break;
                case "INVALID_FORMAT":
                default:
                    setState("invalid", result.message || "닉네임을 확인해주세요.");
            }
        } catch (error) {
            if (error.name !== "AbortError" && sequence === requestSequence) {
                setState("error", "중복 확인에 실패했습니다. 다시 입력해주세요.");
            }
        } finally {
            if (sequence === requestSequence) {
                requestController = null;
            }
        }
    }

    function handleNicknameInput() {
        cancelPendingCheck();
        const nickname = nicknameInput.value;
        const validationMessage = validateNickname(nickname);
        if (validationMessage !== null) {
            setState("invalid", validationMessage);
            return;
        }
        if (nickname === originalNickname) {
            setState("current", "현재 사용 중인 닉네임입니다.");
            return;
        }

        setState("checking", "닉네임 중복을 확인하고 있습니다.");
        const sequence = requestSequence;
        debounceTimer = window.setTimeout(() => {
            debounceTimer = null;
            checkAvailability(nickname, sequence);
        }, 250);
    }

    nicknameInput.addEventListener("input", handleNicknameInput);
    form.addEventListener("submit", event => {
        if (nicknameState !== "current" && nicknameState !== "available") {
            event.preventDefault();
        }
    });

    handleNicknameInput();
});
