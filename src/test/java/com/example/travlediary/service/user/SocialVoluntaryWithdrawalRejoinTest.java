package com.example.travlediary.service.user;

import com.example.travlediary.dto.SocialSignupForm;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.SocialAccountMapper;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SocialVoluntaryWithdrawalRejoinTest {

    @ParameterizedTest
    @EnumSource(SocialProvider.class)
    void voluntaryWithdrawalReleasesIdentityForANewUserWithoutReactivatingOldUser(
            SocialProvider provider) {
        long oldUserId = 7L;
        long newUserId = 41L;
        String providerUserId = provider.name().toLowerCase() + "-same-identity";
        User oldUser = socialUser(oldUserId);
        AtomicReference<SocialAccount> storedAccount =
                new AtomicReference<>(socialAccount(oldUserId, provider, providerUserId));

        UserMapper userMapper = mock(UserMapper.class);
        SocialAccountMapper socialAccountMapper = mock(SocialAccountMapper.class);
        AccountAnonymizationService anonymizationService = mock(AccountAnonymizationService.class);
        when(userMapper.findActiveAccountSecurityByIdForUpdate(oldUserId)).thenReturn(oldUser);
        doAnswer(invocation -> {
            oldUser.setStatus(invocation.getArgument(1));
            return 1;
        }).when(userMapper).requestWithdrawal(
                org.mockito.ArgumentMatchers.eq(oldUserId),
                org.mockito.ArgumentMatchers.eq(UserStatus.WITHDRAWAL_PENDING),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        when(socialAccountMapper.findByProviderAndProviderUserId(provider, providerUserId))
                .thenAnswer(invocation -> storedAccount.get());

        MyPageAccountService withdrawalService = new MyPageAccountService(
                userMapper,
                anonymizationService,
                mock(PasswordEncoder.class),
                socialAccountMapper,
                mock(org.springframework.context.MessageSource.class));
        withdrawalService.withdrawAfterSocialReauthentication(oldUserId);

        // 30일 유예 동안에는 상태만 바뀐다.
        assertThat(oldUser.getStatus()).isEqualTo(UserStatus.WITHDRAWAL_PENDING);
        assertThat(oldUser.getId()).isEqualTo(oldUserId);

        // 소셜 연결과 개인정보는 그대로라 같은 provider 계정으로 곧바로 재가입할 수 없다.
        verify(socialAccountMapper, never()).deleteAllByUserId(oldUserId);
        verifyNoInteractions(anonymizationService);
        assertThat(socialAccountMapper.findByProviderAndProviderUserId(
                provider, providerUserId)).isNotNull();
        assertThat(storedAccount.get().getUserId()).isEqualTo(oldUserId);
        assertThat(newUserId).isNotEqualTo(oldUserId);
    }

    private User socialUser(long userId) {
        User user = new User();
        user.setId(userId);
        user.setUserPassword(null);
        user.setUserEmail(null);
        user.setNickname("기존여행자");
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private SocialAccount socialAccount(long userId, SocialProvider provider,
                                        String providerUserId) {
        SocialAccount account = new SocialAccount();
        account.setUserId(userId);
        account.setProvider(provider);
        account.setProviderUserId(providerUserId);
        return account;
    }

    private PendingSocialSignup pending(SocialProvider provider, String providerUserId) {
        Instant now = Instant.now();
        return new PendingSocialSignup(
                "new-flow", provider, providerUserId, null, null,
                now.minusSeconds(10), now.plusSeconds(590));
    }

    private SocialSignupForm acceptedForm(String nickname) {
        SocialSignupForm form = new SocialSignupForm();
        form.setNickname(nickname);
        form.setTermsAccepted(true);
        form.setPrivacyAccepted(true);
        return form;
    }
}
