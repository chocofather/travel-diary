package com.example.travlediary.service.user;

import com.example.travlediary.dto.PublicUserProfileDto;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProfileServiceTest {

    @Mock
    private UserMapper userMapper;

    private PublicProfileService service;

    @BeforeEach
    void setUp() {
        service = new PublicProfileService(userMapper);
    }

    @Test
    void publicDtoContainsOnlyPublicFields() {
        assertThat(Arrays.stream(PublicUserProfileDto.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .containsExactlyInAnyOrder("id", "nickname", "profileImage");
    }

    @Test
    void missingOrUnavailableMemberUsesTheSameNotFoundResponse() {
        when(userMapper.findPublicProfileById(11L)).thenReturn(null);
        when(userMapper.findPublicProfileById(12L)).thenReturn(null);
        when(userMapper.findPublicProfileById(13L)).thenReturn(null);

        for (long userId : new long[]{11L, 12L, 13L}) {
            assertThatThrownBy(() -> service.getPublicProfile(userId))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(exception.getReason()).isEqualTo("회원을 찾을 수 없습니다.");
                    });
        }
    }

    @Test
    void normalizesUploadPathAndAllKnownFallbackValues() {
        assertThat(service.normalizeProfileImage("uploads/member.png")).isEqualTo("/uploads/member.png");
        assertThat(service.normalizeProfileImage(" /uploads/member.png ")).isEqualTo("/uploads/member.png");

        for (String value : new String[]{"", " ", "uploads/default.png", "/uploads/default.png",
                "/images/default-profile.png", "https://example.com/member.png"}) {
            assertThat(service.normalizeProfileImage(value)).isEqualTo("/images/default.png");
        }
        assertThat(service.normalizeProfileImage(null)).isEqualTo("/images/default.png");
    }

    @Test
    void returnsOnlyMappedProfileWithNormalizedImage() {
        PublicUserProfileDto profile = new PublicUserProfileDto();
        profile.setId(7L);
        profile.setNickname("여행자");
        profile.setProfileImage("uploads/avatar.png");
        when(userMapper.findPublicProfileById(7L)).thenReturn(profile);

        PublicUserProfileDto result = service.getPublicProfile(7L);

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getNickname()).isEqualTo("여행자");
        assertThat(result.getProfileImage()).isEqualTo("/uploads/avatar.png");
    }
}
