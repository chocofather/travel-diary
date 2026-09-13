package com.example.travlediary.service.user;

import com.example.travlediary.model.PolicyConsentSource;
import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.policy.PolicyConsentDecision;
import com.example.travlediary.service.policy.PolicyConsentRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 일반 회원가입의 저장 경계. users INSERT 와 약관 동의 이력 INSERT 를 한 트랜잭션에서 끝낸다.
 *
 * <p>회원만 생기고 동의 이력이 없는 상태는 증빙이 불가능하므로, 동의 저장이 실패하면
 * 회원 생성도 함께 되돌린다.
 *
 * <p>{@link UserService} 가 아니라 별도 빈으로 둔 이유는 두 가지다. 하나는 Spring 프록시를
 * 확실히 통과시키기 위해서고, 다른 하나는 인증메일 발송이 트랜잭션 밖에 남아야 하기 때문이다.
 * 발송은 @Async 라 커밋 전에 부르면 아직 없는 회원에게 링크를 보낼 수 있다.
 * 소셜 신규가입은 {@code SocialSignupService.complete} 자체가 이 경계를 갖는다.
 */
@Service
@RequiredArgsConstructor
public class RegistrationTransactionService {

    private final UserMapper userMapper;
    private final PolicyConsentRecorder policyConsentRecorder;

    /**
     * @param user 저장할 회원. 성공하면 id 가 채워진다
     * @param decisions 서버가 현재 정책 세트로 판정한 동의/거절 결정
     * @param locale 가입 화면의 현재 locale
     */
    @Transactional
    public void insertUserWithConsents(User user,
                                       List<PolicyConsentDecision> decisions,
                                       String locale) {
        userMapper.insertUser(user);
        // 남길 결정이 있는데 id 가 없으면 recorder 가 거절한다. 정책 활성화 전에는 결정이 없어 그대로 지나간다.
        policyConsentRecorder.record(
                user.getId(), decisions, PolicyConsentSource.SIGNUP, locale);
    }
}
