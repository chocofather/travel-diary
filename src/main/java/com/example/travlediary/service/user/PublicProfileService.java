package com.example.travlediary.service.user;

import com.example.travlediary.dto.PublicUserProfileDto;
import com.example.travlediary.repository.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PublicProfileService {

    private static final String DEFAULT_PROFILE_IMAGE = "/images/default.png";
    private static final Set<String> LEGACY_DEFAULT_IMAGES = Set.of(
            "uploads/default.png",
            "/uploads/default.png",
            "images/default.png",
            "/images/default.png",
            "images/default-profile.png",
            "/images/default-profile.png"
    );

    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public PublicUserProfileDto getPublicProfile(Long userId) {
        PublicUserProfileDto profile = userId == null ? null : userMapper.findPublicProfileById(userId);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        profile.setProfileImage(normalizeProfileImage(profile.getProfileImage()));
        return profile;
    }

    String normalizeProfileImage(String profileImage) {
        if (profileImage == null) {
            return DEFAULT_PROFILE_IMAGE;
        }

        String normalized = profileImage.trim();
        if (normalized.isEmpty() || LEGACY_DEFAULT_IMAGES.contains(normalized)) {
            return DEFAULT_PROFILE_IMAGE;
        }
        if (normalized.startsWith("uploads/")) {
            normalized = "/" + normalized;
        }
        return normalized.startsWith("/uploads/") ? normalized : DEFAULT_PROFILE_IMAGE;
    }
}
