package com.example.travlediary.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SocialProviderTest {

    @Test
    void mapsOnlyImplementedSocialRegistrationIds() {
        assertThat(SocialProvider.fromRegistrationId("google"))
                .contains(SocialProvider.GOOGLE);
        assertThat(SocialProvider.fromRegistrationId("kakao"))
                .contains(SocialProvider.KAKAO);
        assertThat(SocialProvider.fromRegistrationId("naver"))
                .contains(SocialProvider.NAVER);
        assertThat(SocialProvider.fromRegistrationId("unsupported")).isEmpty();
        assertThat(SocialProvider.fromRegistrationId(null)).isEmpty();
    }
}
