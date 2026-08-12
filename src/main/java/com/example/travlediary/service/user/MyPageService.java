package com.example.travlediary.service.user;

import com.example.travlediary.dto.MyPageProfileDto;
import com.example.travlediary.dto.ProfileUpdateForm;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.file.ProfileImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyPageService {

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
    private final ProfileImageStorageService profileImageStorageService;

    @Transactional(readOnly = true)
    public MyPageProfileDto getProfile(Long userId) {
        MyPageProfileDto profile = requireProfile(userMapper.findMyPageProfileById(userId));
        profile.setProfileImage(normalizeProfileImage(profile.getProfileImage()));
        return profile;
    }

    @Transactional
    public void updateProfile(Long userId, ProfileUpdateForm form) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 정보를 확인할 수 없습니다.");
        }
        if (form == null) {
            throw new ProfileValidationException(null, "프로필 정보를 입력해 주세요.");
        }

        MyPageProfileDto current = requireProfile(userMapper.findMyPageProfileByIdForUpdate(userId));
        String nickname = validateNicknameFormat(form.getNickname());
        if (!nickname.equals(current.getNickname())) {
            validateForbiddenNickname(nickname);
        }
        form.setNickname(nickname);
        if (userMapper.countByNicknameExcludingUserId(nickname, userId) > 0) {
            throw duplicateNickname();
        }

        MultipartFile imageFile = form.getProfileImageFile();
        boolean replaceImage = imageFile != null && !imageFile.isEmpty();
        String newProfileImage = null;
        boolean lifecycleRegistered = false;

        try {
            if (replaceImage) {
                try {
                    newProfileImage = profileImageStorageService.saveProfileImage(imageFile);
                } catch (RuntimeException exception) {
                    throw new ProfileValidationException("profileImageFile", exception.getMessage());
                }
                lifecycleRegistered = registerFileLifecycle(
                        newProfileImage, current.getProfileImage());
            }

            try {
                userMapper.updateMyPageProfile(userId, nickname, newProfileImage);
            } catch (DuplicateKeyException exception) {
                throw duplicateNickname();
            }

            if (replaceImage && !lifecycleRegistered) {
                deleteSafely(current.getProfileImage());
            }
        } catch (RuntimeException exception) {
            if (newProfileImage != null && !lifecycleRegistered) {
                deleteSafely(newProfileImage);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public NicknameCheckStatus checkNickname(Long userId, String nickname) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 정보를 확인할 수 없습니다.");
        }
        String normalized = NicknamePolicy.normalizeAndValidateFormat(nickname);
        MyPageProfileDto current = requireProfile(userMapper.findMyPageProfileById(userId));
        if (normalized.equals(current.getNickname())) {
            return NicknameCheckStatus.CURRENT;
        }
        NicknamePolicy.validateForbiddenExpression(normalized);
        return userMapper.countByNicknameExcludingUserId(normalized, userId) == 0
                ? NicknameCheckStatus.AVAILABLE
                : NicknameCheckStatus.DUPLICATE;
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

    private String validateNicknameFormat(String nickname) {
        try {
            return NicknamePolicy.normalizeAndValidateFormat(nickname);
        } catch (NicknamePolicy.ViolationException exception) {
            throw new ProfileValidationException("nickname", exception.getMessage());
        }
    }

    private void validateForbiddenNickname(String nickname) {
        try {
            NicknamePolicy.validateForbiddenExpression(nickname);
        } catch (NicknamePolicy.ViolationException exception) {
            throw new ProfileValidationException("nickname", exception.getMessage());
        }
    }

    private boolean registerFileLifecycle(String newProfileImage, String previousProfileImage) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteSafely(previousProfileImage);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteSafely(newProfileImage);
                }
            }
        });
        return true;
    }

    private void deleteSafely(String imageUrl) {
        if (imageUrl == null) {
            return;
        }
        try {
            profileImageStorageService.deleteManagedProfileImage(imageUrl);
        } catch (RuntimeException exception) {
            log.warn("프로필 이미지 파일을 정리하지 못했습니다: {}", imageUrl, exception);
        }
    }

    private MyPageProfileDto requireProfile(MyPageProfileDto profile) {
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        return profile;
    }

    private ProfileValidationException duplicateNickname() {
        return new ProfileValidationException("nickname", "이미 사용 중인 닉네임입니다.");
    }
}
