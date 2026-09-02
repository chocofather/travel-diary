package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.repository.user.SocialAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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

    private SocialAccountService socialAccountService;

    @BeforeEach
    void setUp() {
        socialAccountService = new SocialAccountService(socialAccountMapper);
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
