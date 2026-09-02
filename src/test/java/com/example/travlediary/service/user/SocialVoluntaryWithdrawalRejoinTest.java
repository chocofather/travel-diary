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
        when(anonymizationService.anonymizedEmail(oldUserId))
                .thenReturn("withdrawn-7@example.invalid");
        when(anonymizationService.anonymizedNickname()).thenReturn("탈퇴회원1234567");
        doAnswer(invocation -> {
            oldUser.setStatus(invocation.getArgument(3));
            oldUser.setUserEmail(invocation.getArgument(1));
            oldUser.setNickname(invocation.getArgument(2));
            return 1;
        }).when(userMapper).deactivateAccount(
                org.mockito.ArgumentMatchers.eq(oldUserId), anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(UserStatus.DEACTIVATED));
        doAnswer(invocation -> {
            storedAccount.set(null);
            return 1;
        }).when(socialAccountMapper).deleteAllByUserId(oldUserId);
        when(socialAccountMapper.findByProviderAndProviderUserId(provider, providerUserId))
                .thenAnswer(invocation -> storedAccount.get());
        doAnswer(invocation -> {
            invocation.<User>getArgument(0).setId(newUserId);
            return null;
        }).when(userMapper).insertUser(any(User.class));
        doAnswer(invocation -> {
            storedAccount.set(invocation.getArgument(0));
            return 1;
        }).when(socialAccountMapper).insert(any(SocialAccount.class));

        MyPageAccountService withdrawalService = new MyPageAccountService(
                userMapper,
                anonymizationService,
                mock(PasswordEncoder.class),
                socialAccountMapper);
        withdrawalService.withdrawAfterSocialReauthentication(oldUserId);

        assertThat(oldUser.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        assertThat(socialAccountMapper.findByProviderAndProviderUserId(
                provider, providerUserId)).isNull();

        SocialSignupService signupService = new SocialSignupService(
                userMapper, socialAccountMapper);
        long signedUpUserId = signupService.complete(
                pending(provider, providerUserId), acceptedForm("새로운여행자"));

        assertThat(signedUpUserId).isEqualTo(newUserId);
        assertThat(storedAccount.get().getUserId()).isEqualTo(newUserId);
        assertThat(storedAccount.get().getProvider()).isEqualTo(provider);
        assertThat(storedAccount.get().getProviderUserId()).isEqualTo(providerUserId);
        assertThat(oldUser.getId()).isEqualTo(oldUserId);
        assertThat(oldUser.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
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
