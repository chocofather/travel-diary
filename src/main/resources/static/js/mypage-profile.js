document.addEventListener("DOMContentLoaded", () => {
    setUpFileName();

    const form = document.querySelector(".mypage-profile-form");
    const nicknameInput = document.querySelector("#nickname");
    const status = document.querySelector("#nickname-availability");
    const saveButton = document.querySelector("#profileSaveButton");
    if (!form || !nicknameInput || !status || !saveButton) {
        return;
    }

    const originalNickname = nicknameInput.dataset.currentNickname || "";
    // 안내 문구는 화면이 data-* 로 내려 준다. (언어별 문자열을 여기에 두지 않는다)
    const messages = nicknameInput.dataset;
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
            return messages.messageTooShort;
        }
        if (nickname.length > 12) {
            return messages.messageTooLong;
        }
        if (!nicknamePattern.test(nickname)) {
            return messages.messageInvalidChars;
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
                    setState("forbidden", result.message || messages.messageForbidden);
                    break;
                case "DUPLICATE":
                    setState("duplicate", result.message);
                    break;
                case "INVALID_FORMAT":
                default:
                    setState("invalid", result.message || messages.messageInvalid);
            }
        } catch (error) {
            if (error.name !== "AbortError" && sequence === requestSequence) {
                setState("error", messages.messageCheckFailed);
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
            setState("current", messages.messageCurrent);
            return;
        }

        setState("checking", messages.messageChecking);
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

/**
 * 선택창은 label 이 열고, 여기서는 고른 파일 이름만 보여 준다.
 * 안내 문구는 화면이 data-* 로 내려 준다. (언어별 문자열을 여기에 두지 않는다)
 */
function setUpFileName() {
    const fileInput = document.querySelector("#profileImageFile");
    const fileName = document.querySelector("#profileImageFileName");
    if (!fileInput || !fileName) {
        return;
    }

    const noneSelected = fileName.dataset.messageNoneSelected || "";

    function showSelectedFile() {
        // 선택창을 취소하면 브라우저가 이전 선택을 남겨 두므로 files 를 그대로 읽는다.
        const selected = fileInput.files && fileInput.files[0];
        fileName.textContent = selected ? selected.name : noneSelected;
        fileName.classList.toggle("is-selected", Boolean(selected));
    }

    fileInput.addEventListener("change", showSelectedFile);
    showSelectedFile();
}
