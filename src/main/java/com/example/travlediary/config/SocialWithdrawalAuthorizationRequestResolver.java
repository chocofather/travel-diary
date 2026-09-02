package com.example.travlediary.config;

import com.example.travlediary.model.PendingSocialWithdrawal;
import com.example.travlediary.model.SocialProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class SocialWithdrawalAuthorizationRequestResolver
        implements OAuth2AuthorizationRequestResolver {

    private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";

    private final OAuth2AuthorizationRequestResolver delegate;

    public SocialWithdrawalAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        this(new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, AUTHORIZATION_BASE_URI));
    }

    SocialWithdrawalAuthorizationRequestResolver(
            OAuth2AuthorizationRequestResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest original = delegate.resolve(request);
        return customize(request, original, registrationId(original));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request,
                                              String clientRegistrationId) {
        OAuth2AuthorizationRequest original = delegate.resolve(request, clientRegistrationId);
        return customize(request, original, clientRegistrationId);
    }

    private OAuth2AuthorizationRequest customize(HttpServletRequest request,
                                                 OAuth2AuthorizationRequest original,
                                                 String registrationId) {
        if (original == null) {
            return null;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return original;
        }
        Object value = session.getAttribute(PendingSocialWithdrawal.SESSION_ATTRIBUTE);
        if (!(value instanceof PendingSocialWithdrawal pending)) {
            return original;
        }
        Object userId = session.getAttribute("userId");
        SocialProvider requestedProvider = SocialProvider.fromRegistrationId(registrationId)
                .orElse(null);
        if (!pending.isValidAt(Instant.now())
                || !(userId instanceof Long currentUserId)
                || !currentUserId.equals(pending.userId())
                || requestedProvider != pending.provider()) {
            // 성공 핸들러가 이 intent를 일회성으로 소비하고 안전하게 실패 처리한다.
            // 여기서 제거하면 잘못된 provider 요청이 일반 로그인으로 바뀔 수 있다.
            return original;
        }

        Map<String, Object> parameters = new LinkedHashMap<>(
                original.getAdditionalParameters());
        switch (pending.provider()) {
            case GOOGLE -> parameters.put("prompt", "select_account");
            case KAKAO -> parameters.put("prompt", "login");
            case NAVER -> parameters.put("auth_type", "reauthenticate");
        }
        return OAuth2AuthorizationRequest.from(original)
                .additionalParameters(parameters)
                .build();
    }

    private String registrationId(OAuth2AuthorizationRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttributes().get("registration_id");
        return value instanceof String registrationId ? registrationId : null;
    }
}
