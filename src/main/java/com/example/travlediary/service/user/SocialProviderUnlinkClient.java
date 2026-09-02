package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialProvider;

public interface SocialProviderUnlinkClient {

    void unlink(SocialProvider provider, String accessToken, String providerUserId);
}
