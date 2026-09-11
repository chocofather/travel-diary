// 회원탈퇴 확인 문구 입력 보조.
// 버튼 비활성화는 실수 방지용 편의일 뿐이고, 실제 검증은 서버가 다시 한다.
document.addEventListener("DOMContentLoaded", function () {
    const form = document.getElementById("withdrawalForm");
    const input = document.getElementById("withdrawalConfirmPhrase");
    const submit = document.getElementById("withdrawalSubmit");
    if (!form || !input || !submit) {
        return;
    }

    // 문구는 서버 messages 를 source of truth 로 두고 data-* 로 받는다.
    const expected = normalize(form.dataset.confirmPhrase);
    if (!expected) {
        return;
    }

    function normalize(value) {
        return (value || "").trim().replace(/\s+/g, " ").toLowerCase();
    }

    function sync() {
        submit.disabled = normalize(input.value) !== expected;
    }

    input.addEventListener("input", sync);
    form.addEventListener("submit", function (event) {
        if (normalize(input.value) !== expected) {
            event.preventDefault();
        }
    });
    sync();
});
