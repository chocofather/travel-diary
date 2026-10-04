package com.tripbora.service.user;

import com.tripbora.model.SocialProvider;

public interface SocialProviderUnlinkClient {

    void unlink(SocialProvider provider, String accessToken, String providerUserId);
}
