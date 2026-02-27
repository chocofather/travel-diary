// 페이지 로드 시 쿠키에서 아이디 불러오기
$(document).ready(function () {
    const savedUsername = getCookie("savedUsername");
    if (savedUsername) {
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

// 비밀번호 숨기기
$(document).on("click", ".toggle-password", function () {
    const targetSelector = $(this).data("toggle");
    const input = $(targetSelector);
    const isPassword = input.attr("type") === "password"; // ✅ 수정됨

    input.attr("type", isPassword ? "text" : "password");

    // 아이콘 상태 토글
    $(this).toggleClass("show", isPassword);
    $(this).toggleClass("hide", !isPassword);
});