package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 마이페이지 회원정보 수정 입력값.
 *
 * <p>생년월일은 이 화면에서 고칠 수 없으므로 여기에 두지 않는다. 요청에 실려 와도 바인딩되지 않는다.
 */
@Data
@NoArgsConstructor
public class AccountEditForm {
    private String fullName;
    private String userPhone;
}
