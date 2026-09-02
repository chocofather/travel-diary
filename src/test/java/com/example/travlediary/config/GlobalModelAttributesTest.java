package com.example.travlediary.config;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalModelAttributesTest {

    @Mock
    private UserMapper userMapper;

    @Test
    void authenticatedMemberProfileUsesPrincipalId() {
        User authenticatedUser = user(7L, "member");
        User storedUser = user(7L, "member");
        storedUser.setProfileImage("/uploads/member.png");
        when(userMapper.findById(7L)).thenReturn(storedUser);
        ExtendedModelMap model = new ExtendedModelMap();

        new GlobalModelAttributes(userMapper).addCommonAttributes(
                model, authentication(authenticatedUser));

        assertThat(model.get("isLoggedIn")).isEqualTo(true);
        assertThat(model.get("currentUserProfileImage")).isEqualTo("/uploads/member.png");
        assertThat(model).doesNotContainKey("hasLocalPassword");
        verify(userMapper).findById(7L);
        verify(userMapper, never()).hasLocalPasswordById(7L);
    }

    @Test
    void socialOnlyMemberDoesNotExposeLocalPasswordCapabilityGlobally() {
        User authenticatedUser = user(77L, null);
        User storedUser = user(77L, null);
        storedUser.setUserPassword(null);
        when(userMapper.findById(77L)).thenReturn(storedUser);
        ExtendedModelMap model = new ExtendedModelMap();

        new GlobalModelAttributes(userMapper).addCommonAttributes(
                model, authentication(authenticatedUser));

        assertThat(model).doesNotContainKey("hasLocalPassword");
        assertThat(model.asMap()).doesNotContainValue("encoded-password");
        verify(userMapper, never()).hasLocalPasswordById(77L);
    }

    @Test
    void anonymousAuthenticationIsHandledAsLoggedOut() {
        var anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        ExtendedModelMap model = new ExtendedModelMap();

        new GlobalModelAttributes(userMapper).addCommonAttributes(model, anonymous);

        assertThat(model.get("isLoggedIn")).isEqualTo(false);
        assertThat(model).doesNotContainKey("currentUserProfileImage");
        verify(userMapper, never()).findById(7L);
    }

    @Test
    void unexpectedAuthenticatedPrincipalIsHandledAsLoggedOut() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "member", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        ExtendedModelMap model = new ExtendedModelMap();

        new GlobalModelAttributes(userMapper).addCommonAttributes(model, authentication);

        assertThat(model.get("isLoggedIn")).isEqualTo(false);
        assertThat(model).doesNotContainKey("currentUserProfileImage");
        verify(userMapper, never()).findById(7L);
    }

    private UsernamePasswordAuthenticationToken authentication(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(
                userDetails, userDetails.getPassword(), userDetails.getAuthorities());
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setUserPassword("encoded-password");
        user.setUserRole(UserRole.USER);
        return user;
    }
}
