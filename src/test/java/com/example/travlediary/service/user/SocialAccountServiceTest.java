package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.repository.user.SocialAccountMapper;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAccountServiceTest {

    @Mock
    private SocialAccountMapper socialAccountMapper;
    @Mock
    private UserMapper userMapper;

    private SocialAccountService socialAccountService;

    @BeforeEach
    void setUp() {
        socialAccountService = new SocialAccountService(socialAccountMapper, userMapper);
    }

    @Test
    void googleLookupUsesGoogleAndItsProviderUserId() {
        SocialAccount google = account(1L, 10L, SocialProvider.GOOGLE, "google-123");
        when(socialAccountMapper.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "google-123")).thenReturn(google);

        SocialAccount found = socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "google-123");

        assertThat(found).isSameAs(google);
        verify(socialAccountMapper).findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "google-123");
    }

    @Test
    void kakaoLookupUsesKakaoAndItsProviderUserId() {
        SocialAccount kakao = account(2L, 20L, SocialProvider.KAKAO, "kakao-123");
        when(socialAccountMapper.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "kakao-123")).thenReturn(kakao);

        SocialAccount found = socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "kakao-123");

        assertThat(found).isSameAs(kakao);
        verify(socialAccountMapper).findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "kakao-123");
    }

    @Test
    void naverLookupUsesNaverAndItsProviderUserId() {
        SocialAccount naver = account(3L, 30L, SocialProvider.NAVER, "naver-123");
        when(socialAccountMapper.findByProviderAndProviderUserId(
                SocialProvider.NAVER, "naver-123")).thenReturn(naver);

        SocialAccount found = socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.NAVER, "naver-123");

        assertThat(found).isSameAs(naver);
        verify(socialAccountMapper).findByProviderAndProviderUserId(
                SocialProvider.NAVER, "naver-123");
    }

    @Test
    void sameProviderUserIdIsKeptSeparateByProvider() {
        SocialAccount google = account(1L, 10L, SocialProvider.GOOGLE, "shared-id");
        SocialAccount kakao = account(2L, 20L, SocialProvider.KAKAO, "shared-id");
        when(socialAccountMapper.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "shared-id")).thenReturn(google);
        when(socialAccountMapper.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "shared-id")).thenReturn(kakao);

        assertThat(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "shared-id")).isSameAs(google);
        assertThat(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "shared-id")).isSameAs(kakao);
    }

    @Test
    void userAndProviderLookupUsesBothValues() {
        SocialAccount account = account(1L, 10L, SocialProvider.GOOGLE, "google-123");
        when(socialAccountMapper.findByUserIdAndProvider(10L, SocialProvider.GOOGLE))
                .thenReturn(account);

        SocialAccount found = socialAccountService.findByUserIdAndProvider(
                10L, SocialProvider.GOOGLE);

        assertThat(found).isSameAs(account);
        verify(socialAccountMapper).findByUserIdAndProvider(10L, SocialProvider.GOOGLE);
    }

    @Test
    void providerListReturnsAllAccountsConnectedToOneUser() {
        List<SocialAccount> accounts = List.of(
                account(1L, 10L, SocialProvider.GOOGLE, "google-123"),
                account(2L, 10L, SocialProvider.KAKAO, "kakao-123"));
        when(socialAccountMapper.findAllByUserId(10L)).thenReturn(accounts);

        assertThat(socialAccountService.findAllByUserId(10L)).containsExactlyElementsOf(accounts);
        verify(socialAccountMapper).findAllByUserId(10L);
    }

    @Test
    void newConnectionReliesOnMapperInsertWithoutPreQuery() {
        SocialAccount account = account(1L, 10L, SocialProvider.NAVER, "naver-123");
        when(socialAccountMapper.insert(account)).thenReturn(1);

        assertThat(socialAccountService.connect(account)).isEqualTo(1);

        verify(socialAccountMapper).insert(account);
        verify(socialAccountMapper, never()).findByProviderAndProviderUserId(
                SocialProvider.NAVER, "naver-123");
    }

    @Test
    void connectsAProviderIdentityOnlyWhenBothUniqueSlotsAreAvailable() {
        when(socialAccountMapper.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "google-new")).thenReturn(null);
        when(socialAccountMapper.findByUserIdAndProvider(
                7L, SocialProvider.GOOGLE)).thenReturn(null);
        when(socialAccountMapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);

        SocialConnectionResult result = socialAccountService.connectToUser(
                7L, SocialProvider.GOOGLE, "google-new", "member@example.com", true);

        assertThat(result).isEqualTo(SocialConnectionResult.CONNECTED);
        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountMapper).insert(captor.capture());
        assertThat(captor.getValue()).satisfies(account -> {
            assertThat(account.getUserId()).isEqualTo(7L);
            assertThat(account.getProvider()).isEqualTo(SocialProvider.GOOGLE);
            assertThat(account.getProviderUserId()).isEqualTo("google-new");
            assertThat(account.getProviderEmail()).isEqualTo("member@example.com");
            assertThat(account.getProviderEmailVerified()).isTrue();
        });
    }

    @Test
    void refusesAProviderIdentityAlreadyOwnedByAnotherUserWithoutMovingIt() {
        when(socialAccountMapper.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "shared-kakao"))
                .thenReturn(account(3L, 99L, SocialProvider.KAKAO, "shared-kakao"));

        SocialConnectionResult result = socialAccountService.connectToUser(
                7L, SocialProvider.KAKAO, "shared-kakao", "same@example.com", true);

        assertThat(result).isEqualTo(SocialConnectionResult.OWNED_BY_ANOTHER_USER);
        verify(socialAccountMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void treatsAnExistingProviderForTheCurrentUserAsAlreadyConnected() {
        when(socialAccountMapper.findByProviderAndProviderUserId(
                SocialProvider.NAVER, "naver-new")).thenReturn(null);
        when(socialAccountMapper.findByUserIdAndProvider(7L, SocialProvider.NAVER))
                .thenReturn(account(4L, 7L, SocialProvider.NAVER, "naver-existing"));

        SocialConnectionResult result = socialAccountService.connectToUser(
                7L, SocialProvider.NAVER, "naver-new", "naver@example.com", null);

        assertThat(result).isEqualTo(SocialConnectionResult.ALREADY_CONNECTED);
        verify(socialAccountMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void concurrentUniqueConflictIsRecheckedWithoutMovingAnotherUsersIdentity() {
        when(socialAccountMapper.findByProviderAndProviderUserId(
                SocialProvider.GOOGLE, "racing-id"))
                .thenReturn(null)
                .thenReturn(account(8L, 99L, SocialProvider.GOOGLE, "racing-id"));
        when(socialAccountMapper.findByUserIdAndProvider(7L, SocialProvider.GOOGLE))
                .thenReturn(null);
        when(socialAccountMapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DuplicateKeyException("unique conflict"));

        SocialConnectionResult result = socialAccountService.connectToUser(
                7L, SocialProvider.GOOGLE, "racing-id", null, null);

        assertThat(result).isEqualTo(SocialConnectionResult.OWNED_BY_ANOTHER_USER);
        verify(socialAccountMapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void optionalProviderEmailAndVerificationPreserveThreeStates() {
        SocialAccount unknown = account(1L, 10L, SocialProvider.GOOGLE, "google-unknown");
        unknown.setProviderEmail(null);
        unknown.setProviderEmailVerified(null);

        SocialAccount verified = account(2L, 10L, SocialProvider.KAKAO, "kakao-verified");
        verified.setProviderEmail("verified@example.com");
        verified.setProviderEmailVerified(true);

        SocialAccount unverified = account(3L, 10L, SocialProvider.NAVER, "naver-unverified");
        unverified.setProviderEmail("unverified@example.com");
        unverified.setProviderEmailVerified(false);

        assertThat(unknown.getProviderEmail()).isNull();
        assertThat(unknown.getProviderEmailVerified()).isNull();
        assertThat(verified.getProviderEmailVerified()).isTrue();
        assertThat(unverified.getProviderEmailVerified()).isFalse();
    }

    @Test
    void disconnectsSocialAccountWhenLocalPasswordRemainsAvailable() {
        when(socialAccountMapper.findAllByUserIdForUpdate(7L)).thenReturn(List.of(
                account(1L, 7L, SocialProvider.GOOGLE, "google-123")));
        when(userMapper.hasLocalPasswordById(7L)).thenReturn(true);
        when(socialAccountMapper.deleteByUserIdAndProvider(7L, SocialProvider.GOOGLE))
                .thenReturn(1);

        SocialDisconnectionResult result = socialAccountService.disconnectFromUser(
                7L, SocialProvider.GOOGLE);

        assertThat(result).isEqualTo(SocialDisconnectionResult.DISCONNECTED);
        verify(socialAccountMapper).deleteByUserIdAndProvider(7L, SocialProvider.GOOGLE);
    }

    @Test
    void disconnectsOneOfTwoSocialLoginMethodsWithoutLocalPassword() {
        when(socialAccountMapper.findAllByUserIdForUpdate(7L)).thenReturn(List.of(
                account(1L, 7L, SocialProvider.GOOGLE, "google-123"),
                account(2L, 7L, SocialProvider.KAKAO, "kakao-123")));
        when(userMapper.hasLocalPasswordById(7L)).thenReturn(false);
        when(socialAccountMapper.deleteByUserIdAndProvider(7L, SocialProvider.KAKAO))
                .thenReturn(1);

        SocialDisconnectionResult result = socialAccountService.disconnectFromUser(
                7L, SocialProvider.KAKAO);

        assertThat(result).isEqualTo(SocialDisconnectionResult.DISCONNECTED);
        verify(socialAccountMapper).deleteByUserIdAndProvider(7L, SocialProvider.KAKAO);
    }

    @Test
    void refusesToDisconnectTheOnlyLoginMethodOfSocialOnlyMember() {
        when(socialAccountMapper.findAllByUserIdForUpdate(7L)).thenReturn(List.of(
                account(1L, 7L, SocialProvider.NAVER, "naver-123")));
        when(userMapper.hasLocalPasswordById(7L)).thenReturn(false);

        SocialDisconnectionResult result = socialAccountService.disconnectFromUser(
                7L, SocialProvider.NAVER);

        assertThat(result).isEqualTo(SocialDisconnectionResult.LAST_LOGIN_METHOD);
        verify(socialAccountMapper, never()).deleteByUserIdAndProvider(
                7L, SocialProvider.NAVER);
    }

    @Test
    void cannotDeleteAProviderConnectionOwnedByAnotherUser() {
        when(socialAccountMapper.findAllByUserIdForUpdate(7L)).thenReturn(List.of());

        SocialDisconnectionResult result = socialAccountService.disconnectFromUser(
                7L, SocialProvider.GOOGLE);

        assertThat(result).isEqualTo(SocialDisconnectionResult.ALREADY_DISCONNECTED);
        verify(socialAccountMapper, never()).deleteByUserIdAndProvider(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(SocialProvider.class));
    }

    @Test
    void concurrentOrRepeatedDisconnectIsHandledAsAlreadyDisconnected() {
        when(socialAccountMapper.findAllByUserIdForUpdate(7L)).thenReturn(List.of(
                account(1L, 7L, SocialProvider.GOOGLE, "google-123")));
        when(userMapper.hasLocalPasswordById(7L)).thenReturn(true);
        when(socialAccountMapper.deleteByUserIdAndProvider(7L, SocialProvider.GOOGLE))
                .thenReturn(0);

        SocialDisconnectionResult result = socialAccountService.disconnectFromUser(
                7L, SocialProvider.GOOGLE);

        assertThat(result).isEqualTo(SocialDisconnectionResult.ALREADY_DISCONNECTED);
    }

    @Test
    void persistenceApiDoesNotExposeEmailBasedAccountLookup() {
        assertThat(SocialAccountMapper.class.getDeclaredMethods())
                .extracting(method -> method.getName().toLowerCase())
                .noneMatch(name -> name.contains("email"));
        assertThat(SocialAccountService.class.getDeclaredMethods())
                .extracting(method -> method.getName().toLowerCase())
                .noneMatch(name -> name.contains("email"));
    }

    private SocialAccount account(Long id, Long userId, SocialProvider provider,
                                  String providerUserId) {
        SocialAccount account = new SocialAccount();
        account.setId(id);
        account.setUserId(userId);
        account.setProvider(provider);
        account.setProviderUserId(providerUserId);
        return account;
    }
}
