package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 회원탈퇴 신청 폼.
 * 계정 및 보안 진입 시 이미 재인증을 마쳤으므로 비밀번호는 다시 받지 않고,
 * 실수로 누르는 것을 막기 위한 확인 문구만 입력받는다.
 */
@Data
@NoArgsConstructor
public class AccountWithdrawalForm {
    private String confirmationPhrase;
}
