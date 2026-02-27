$(document).ready(function () {
    let currentStep = 1;

    // 전체 동의 → 하위 약관 모두 체크/해제
    $("#agreeAll").on("change", function () {
        const checked = $(this).is(":checked");
        $("#termsAgree1, #termsAgree2, #termsAgree3").prop("checked", checked);
        toggleNextButton();
    });

    // 하위 약관 체크 시 전체 동의 여부 업데이트
    $("#termsAgree1, #termsAgree2, #termsAgree3").on("change", function () {
        const allChecked = $("#termsAgree1").is(":checked") &&
            $("#termsAgree2").is(":checked") &&
            $("#termsAgree3").is(":checked");
        $("#agreeAll").prop("checked", allChecked);
    });

    function toggleNextButton() {
        const isAgree1 = $('#termsAgree1').is(':checked');
        const isAgree2 = $('#termsAgree2').is(':checked');

        if (isAgree1 && isAgree2) {
            $('#step1-next').prop('disabled', false);
        } else {
            $('#step1-next').prop('disabled', true);
        }
    }

    $('#termsAgree1, #termsAgree2').on('change', toggleNextButton);


    // Step 2 버튼 상태 업데이트
    function updateStep2Button() {
        const username = $("#username").val()?.trim() || "";
        const emailInput = $("input[name='userEmail']");
        const email = emailInput.length ? emailInput.val().trim() : "";
        const pw = $("#userPassword").val();
        const pwConfirm = $("#passwordConfirm").val();

        const validPw = /^(?=.*[!@#$%^&*])[A-Za-z\d!@#$%^&*]{8,}$/.test(pw);
        const matchPw = pw === pwConfirm;

        const isValid = username && email && validPw && matchPw;
        $("#step2-next").prop("disabled", !isValid);
    }

    function updateStep3Button() {
        const nicknameInput = $("#nickname");
        const fullNameInput = $("#fullName");
        const birthInput = $("input[name='userBirth']");

        const nickname = nicknameInput.length ? nicknameInput.val().trim() : "";
        const fullName = fullNameInput.length ? fullNameInput.val().trim() : "";
        const birth = birthInput.length ? birthInput.val().trim() : "";

        const isValid = nickname.length >= 2 && fullName && birth;
        $("#step3-submit").prop("disabled", !isValid);
    }



// Step 2 필드 변경 감지
    $("#username, input[name='userEmail'], #userPassword, #passwordConfirm").on("input", updateStep2Button);

// Step 3 필드 변경 감지
    $("#nickname, #fullName, input[name='userBirth']").on("input", updateStep3Button);



    function showStep(step) {
        $(".form-step").hide();
        $("#step-" + step).show();
    }

    function validateStep(step) {
        const agree1 = $("#termsAgree1").is(":checked");
        const agree2 = $("#termsAgree2").is(":checked");
        // termsAgree3는 선택 항목이므로 제외

        if (!agree1 || !agree2) {
            alert("필수 약관에 모두 동의해주세요.");
            return false;
        }


        if (step === 2) {
            const username = $("#username").val().trim();
            const email = $("input[name='userEmail']").val().trim();
            const pw = $("#userPassword").val();
            const pwConfirm = $("#passwordConfirm").val();

            if (!username || !email || !pw || !pwConfirm) {
                alert("모든 항목을 입력해주세요.");
                return false;
            }

            if (!/^(?=.*[!@#$%^&*])[A-Za-z\d!@#$%^&*]{8,}$/.test(pw)) {
                alert("비밀번호는 8자 이상, 특수문자 1개 이상 포함해야 합니다.");
                return false;
            }

            if (pw !== pwConfirm) {
                alert("비밀번호가 일치하지 않습니다.");
                return false;
            }
        }

        if (step === 3) {
            const nickname = $("#nickname").val().trim();
            const fullName = $("#fullName").val().trim();
            const birth = $("input[name='userBirth']").val().trim();

            if (!nickname || nickname.length < 2) {
                alert("닉네임은 최소 2자 이상 입력해주세요.");
                return false;
            }

            if (!fullName) {
                alert("이름을 입력해주세요.");
                return false;
            }

            if (!birth) {
                alert("생년월일을 선택해주세요.");
                return false;
            }
        }
        return true;
    }

    // 초기 화면
    showStep(currentStep);

    // 다음 단계
    $(".next-step").on("click", function (e) {
        e.preventDefault();
        if (!validateStep(currentStep)) return;
        if (currentStep < 3) {
            currentStep++;
            showStep(currentStep);
        }
    });

    // 이전 단계
    $(".prev-step").on("click", function (e) {
        e.preventDefault();
        if (currentStep > 1) {
            currentStep--;
            showStep(currentStep);
        }
    });

    // 비밀번호 실시간 검사
    $("#userPassword").on("input", function () {
        const password = $(this).val();
        const message = $("#passwordValidationMessage");
        const regex = /^(?=.*[!@#$%^&*])[A-Za-z\d!@#$%^&*]{8,}$/;

        if (regex.test(password)) {
            message.text("✔ 사용 가능한 비밀번호입니다.").removeClass("error").addClass("success");
        } else {
            message.text("❌ 비밀번호는 8자 이상, 특수문자 1개 이상 포함해야 합니다.").removeClass("success").addClass("error");
        }
    });

    // 비밀번호 확인 검사
    $("#userPassword, #passwordConfirm").on("input", function () {
        const password = $("#userPassword").val();
        const confirm = $("#passwordConfirm").val();
        const msg = $("#passwordMessage");

        if (password && confirm && password === confirm) {
            msg.text("✔ 비밀번호가 일치합니다.").removeClass("error").addClass("success");
        } else {
            msg.text("❌ 비밀번호가 일치하지 않습니다.").removeClass("success").addClass("error");
        }
    });

    // 전화번호 자동 포맷
    $("#userPhone").on("input", function () {
        let number = $(this).val().replace(/[^0-9]/g, "");
        let formatted = "";

        if (number.length <= 3) {
            formatted = number;
        } else if (number.length <= 7) {
            formatted = number.substring(0, 3) + "-" + number.substring(3);
        } else {
            formatted = number.substring(0, 3) + "-" + number.substring(3, 7) + "-" + number.substring(7, 11);
        }

        $(this).val(formatted);
    });

    // 아이디 중복 확인
    $("#username").on("keyup", function () {
        const username = $(this).val();
        const msg = $("#usernameMessage");

        const usernameRegex = /^(?=.*[a-z])[a-z0-9_-]{3,16}$/;

        if (!usernameRegex.test(username)) {
            msg.text("아이디는 영문 소문자를 포함해야 하며, 3~16자의 영문/숫자/-,_만 사용할 수 있습니다.")
                .removeClass("success").addClass("error");
            return;
        }

        $.ajax({
            type: "GET",
            url: "/api/users/check-username",
            data: { username },
            success: function (res) {
                if (res.exists) {
                    msg.text("중복된 아이디입니다.").removeClass("success").addClass("error");
                } else {
                    msg.text("사용 가능한 아이디입니다.").removeClass("error").addClass("success");
                }
            }
        });
    });


    // 닉네임 중복 확인
    $("#nickname").on("keyup", function () {
        const nickname = $(this).val();
        const msg = $("#nicknameMessage");

        if (nickname.length < 2) {
            msg.text("닉네임은 최소 2자 이상 입력하세요.").removeClass("success").addClass("error");
            return;
        }

        $.ajax({
            type: "GET",
            url: "/api/users/check-nickname",
            data: { nickname },
            success: function (res) {
                if (res.exists) {
                    msg.text("중복된 닉네임입니다.").removeClass("success").addClass("error");
                } else {
                    msg.text("사용 가능한 닉네임입니다.").removeClass("error").addClass("success");
                }
            }
        });
    });

    // 닉네임 자동 추천
    $("#generateNickname").on("click", function () {
        $.ajax({
            type: "GET",
            url: "/api/users/generate-nickname",
            success: function (res) {
                $("#nickname").val(res);
                $("#nicknameMessage").text("사용 가능한 닉네임입니다.").removeClass("error").addClass("success");
            },
            error: function () {
                $("#nicknameMessage").text("닉네임 생성 실패").removeClass("success").addClass("error");
            }
        });
    });


    // 이메일 유효성 검사 + 중복 확인
    function isValidEmail(email) {
        const emailRegex = /^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$/;
        return emailRegex.test(email);
    }

    $("input[name='userEmail']").on("input", function () {
        const email = $(this).val().trim();
        const msg = $("#emailMessage");

        if (!isValidEmail(email)) {
            msg.text("유효하지 않은 이메일 형식입니다.").removeClass("success").addClass("error");
            return;
        }

        // 이메일 중복 검사 AJAX
        $.ajax({
            type: "GET",
            url: "/api/users/check-email",
            data: { email },
            success: function (res) {
                if (res.exists) {
                    msg.text("중복된 이메일입니다.").removeClass("success").addClass("error");
                } else {
                    msg.text("사용 가능한 이메일입니다.").removeClass("error").addClass("success");
                }
            },
            error: function () {
                msg.text("이메일 확인 중 오류 발생").removeClass("success").addClass("error");
            }
        });
    });

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

    // 생년월일 계산
    $(document).ready(function () {
        const today = new Date();
        const yyyy = today.getFullYear();
        const mm = String(today.getMonth() + 1).padStart(2, '0'); // 01~12
        const dd = String(today.getDate()).padStart(2, '0');      // 01~31
        const formattedToday = `${yyyy}-${mm}-${dd}`;

        // 모든 date input에 적용
        $("input[type='date']").attr("max", formattedToday);
    });


    // ✅ 초기 버튼 상태 반영
    toggleNextButton();
    updateStep2Button();
    updateStep3Button();

});


