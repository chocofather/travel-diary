package com.example.travlediary.service.user;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserMapper userMapper;
    private final UserSanctionService userSanctionService;

    @Autowired
    public CustomUserDetailsService(UserMapper userMapper,
                                    UserSanctionService userSanctionService) {
        this.userMapper = userMapper;
        this.userSanctionService = userSanctionService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }
        if (user.getUserPassword() == null) {
            throw new BadCredentialsException("비밀번호가 설정되지 않았습니다.");
        }
        if (user.getStatus() == UserStatus.RESTRICTED) {
            // 이용제한 회원도 인증 자체는 허용한다. 접근 통제는 RestrictedAccountFilter 가 맡는다.
            // 배치가 아직 처리하지 못한 기간제한은 로그인 시점에 만료 처리한다.
            userSanctionService.releaseIfExpired(user.getId());
        } else if (user.getStatus() == UserStatus.WITHDRAWAL_PENDING) {
            // 탈퇴 유예 회원도 인증까지만 허용한다. 서비스 이용 권한이 돌아오는 것은 아니고,
            // 접근 통제와 안내 화면 이동은 WithdrawalPendingAccountFilter 가 맡는다.
            log.debug("Withdrawal pending account authenticated for the notice screen only");
        } else if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException(inactiveMessage(user.getStatus()));
        }

        /* ★ 변경된 부분: id 포함 CustomUserDetails 반환 */
        return new com.example.travlediary.security.CustomUserDetails(user);
    }

    /** WITHDRAWAL_PENDING 은 인증을 허용하므로 여기로 오지 않는다. 최종 탈퇴(DEACTIVATED)는 그대로 차단한다. */
    private String inactiveMessage(UserStatus status) {
        return switch (status) {
            case DEACTIVATED -> "탈퇴한 계정입니다.";
            case SUSPENDED -> "휴면 상태의 계정입니다. 고객센터로 문의해주세요.";
            default -> "이메일 인증이 완료되지 않았습니다.";
        };
    }
}