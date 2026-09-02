package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.repository.user.SocialAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SocialAccountService {

    private final SocialAccountMapper socialAccountMapper;

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
}
