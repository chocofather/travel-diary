package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.repository.user.SocialAccountMapper;
import com.example.travlediary.repository.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SocialAccountService {

    private final SocialAccountMapper socialAccountMapper;
    private final UserMapper userMapper;

    public SocialAccount findByProviderAndProviderUserId(SocialProvider provider,
                                                          String providerUserId) {
        return socialAccountMapper.findByProviderAndProviderUserId(provider, providerUserId);
    }

    public SocialAccount findByUserIdAndProvider(Long userId, SocialProvider provider) {
        return socialAccountMapper.findByUserIdAndProvider(userId, provider);
    }

    public List<SocialAccount> findAllByUserId(Long userId) {
        return socialAccountMapper.findAllByUserId(userId);
    }

    public int connect(SocialAccount socialAccount) {
        return socialAccountMapper.insert(socialAccount);
    }

    public SocialConnectionResult connectToUser(Long userId,
                                                SocialProvider provider,
                                                String providerUserId,
                                                String providerEmail,
                                                Boolean providerEmailVerified) {
        SocialAccount identityOwner = findByProviderAndProviderUserId(
                provider, providerUserId);
        if (identityOwner != null) {
            return userId.equals(identityOwner.getUserId())
                    ? SocialConnectionResult.ALREADY_CONNECTED
                    : SocialConnectionResult.OWNED_BY_ANOTHER_USER;
        }

        SocialAccount providerConnection = findByUserIdAndProvider(userId, provider);
        if (providerConnection != null) {
            return SocialConnectionResult.ALREADY_CONNECTED;
        }

        SocialAccount socialAccount = new SocialAccount();
        socialAccount.setUserId(userId);
        socialAccount.setProvider(provider);
        socialAccount.setProviderUserId(providerUserId);
        socialAccount.setProviderEmail(providerEmail);
        socialAccount.setProviderEmailVerified(providerEmailVerified);
        try {
            if (socialAccountMapper.insert(socialAccount) != 1) {
                throw new IllegalStateException(
                        "Social account connection was not persisted");
            }
        } catch (DuplicateKeyException exception) {
            SocialAccount concurrentOwner = findByProviderAndProviderUserId(
                    provider, providerUserId);
            if (concurrentOwner != null) {
                return userId.equals(concurrentOwner.getUserId())
                        ? SocialConnectionResult.ALREADY_CONNECTED
                        : SocialConnectionResult.OWNED_BY_ANOTHER_USER;
            }
            if (findByUserIdAndProvider(userId, provider) != null) {
                return SocialConnectionResult.ALREADY_CONNECTED;
            }
            throw exception;
        }
        return SocialConnectionResult.CONNECTED;
    }

    @Transactional
    public SocialDisconnectionResult disconnectFromUser(Long userId,
                                                        SocialProvider provider) {
        List<SocialAccount> currentConnections =
                socialAccountMapper.findAllByUserIdForUpdate(userId);
        boolean connected = currentConnections.stream()
                .anyMatch(account -> provider == account.getProvider());
        if (!connected) {
            return SocialDisconnectionResult.ALREADY_DISCONNECTED;
        }

        boolean hasLocalPassword = userMapper.hasLocalPasswordById(userId);
        if (!hasLocalPassword && currentConnections.size() == 1) {
            return SocialDisconnectionResult.LAST_LOGIN_METHOD;
        }

        return socialAccountMapper.deleteByUserIdAndProvider(userId, provider) == 1
                ? SocialDisconnectionResult.DISCONNECTED
                : SocialDisconnectionResult.ALREADY_DISCONNECTED;
    }
}
