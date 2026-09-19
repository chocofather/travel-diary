package com.example.travlediary.security;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.user.UserSanctionService;
import com.example.travlediary.service.user.WithdrawalGraceService;
import com.example.travlediary.service.user.WithdrawalGraceService.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 두 격리 필터는 같은 users.status 를 본다. 한 요청 안에서 그 값을 두 번 읽지 않는지,
 * 그러면서도 제한/탈퇴유예 판정 결과는 그대로인지 고정한다.
 *
 * <p>상태는 요청 하나 안에서만 쓰고 버린다. 요청이 바뀌면 반드시 다시 읽어야 한다 —
 * 로그인 중에 제재되거나 탈퇴가 접수된 회원이 다음 요청부터 바로 걸리는 계약이기 때문이다.
 */
@ExtendWith(MockitoExtension.class)
class AccountStatusSingleReadTest {

    private static final long USER_ID = 5L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserSanctionService userSanctionService;
    @Mock
    private WithdrawalGraceService withdrawalGraceService;

    private RestrictedAccountFilter restrictedFilter;
    private WithdrawalPendingAccountFilter withdrawalFilter;

    @BeforeEach
    void setUp() {
        restrictedFilter = new RestrictedAccountFilter(userMapper, userSanctionService);
        withdrawalFilter = new WithdrawalPendingAccountFilter(withdrawalGraceService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeMemberReadsTheAccountStatusOnlyOncePerRequest() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(USER_ID)).thenReturn(UserStatus.ACTIVE);

        MockHttpServletRequest request = get("/destinations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        restrictedFilter.doFilter(request, response, new MockFilterChain());
        withdrawalFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getRedirectedUrl()).isNull();
        // 앞선 필터가 읽어 둔 값을 그대로 쓰므로 탈퇴 유예 판정이 users 를 다시 읽지 않는다.
        verify(userMapper, times(1)).findStatusById(USER_ID);
        verify(withdrawalGraceService, never()).resolveAccess(anyLong(), any());
    }

    @Test
    void eachRequestReadsTheAccountStatusAgain() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(USER_ID)).thenReturn(UserStatus.ACTIVE);

        restrictedFilter.doFilter(get("/destinations"),
                new MockHttpServletResponse(), new MockFilterChain());
        restrictedFilter.doFilter(get("/travel-info"),
                new MockHttpServletResponse(), new MockFilterChain());

        // 요청 사이에는 절대 남기지 않는다.
        verify(userMapper, times(2)).findStatusById(USER_ID);
    }

    @Test
    void restrictedMemberIsStillSentToTheRestrictedNotice() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(USER_ID)).thenReturn(UserStatus.RESTRICTED);
        when(userSanctionService.releaseIfExpired(USER_ID)).thenReturn(false);

        MockHttpServletRequest request = get("/destinations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        restrictedFilter.doFilter(request, response, chain);

        assertThat(response.getRedirectedUrl())
                .isEqualTo(RestrictedAccountFilter.RESTRICTED_PATH);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void withdrawalPendingMemberIsStillSentToTheWithdrawalNotice() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(USER_ID)).thenReturn(UserStatus.WITHDRAWAL_PENDING);
        when(withdrawalGraceService.resolveAccess(USER_ID, null)).thenReturn(Outcome.IN_GRACE);

        MockHttpServletRequest request = get("/destinations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 제한 회원이 아니므로 첫 필터는 그냥 통과시킨다.
        restrictedFilter.doFilter(request, response, new MockFilterChain());
        MockFilterChain blocked = new MockFilterChain();
        withdrawalFilter.doFilter(request, response, blocked);

        assertThat(response.getRedirectedUrl())
                .isEqualTo(WithdrawalPendingAccountFilter.WITHDRAWAL_PENDING_PATH);
        assertThat(blocked.getRequest()).isNull();
    }

    @Test
    void releasedSanctionIsNotJudgedFromTheStaleStatusOfTheSameRequest() throws Exception {
        authenticate(UserRole.USER);
        when(userMapper.findStatusById(USER_ID)).thenReturn(UserStatus.RESTRICTED);
        // 기간이 끝난 제재가 이 요청에서 해제되면 status 가 바뀐다.
        when(userSanctionService.releaseIfExpired(USER_ID)).thenReturn(true);
        when(withdrawalGraceService.resolveAccess(USER_ID, null)).thenReturn(Outcome.NOT_PENDING);

        MockHttpServletRequest request = get("/destinations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        restrictedFilter.doFilter(request, response, new MockFilterChain());
        withdrawalFilter.doFilter(request, response, new MockFilterChain());

        // 바뀐 상태를 들고 있을 수 없으므로 뒤쪽 판정은 원래대로 다시 확인한다.
        verify(withdrawalGraceService).resolveAccess(USER_ID, null);
    }

    @Test
    void adminSkipsTheRestrictedCheckSoTheWithdrawalJudgementStillRuns() throws Exception {
        authenticate(UserRole.ADMIN);
        when(withdrawalGraceService.resolveAccess(USER_ID, null)).thenReturn(Outcome.NOT_PENDING);

        MockHttpServletRequest request = get("/destinations");
        MockHttpServletResponse response = new MockHttpServletResponse();

        restrictedFilter.doFilter(request, response, new MockFilterChain());
        withdrawalFilter.doFilter(request, response, new MockFilterChain());

        // 관리자는 제한 검사 대상이 아니라 읽어 둔 상태가 없다. 탈퇴 유예 판정은 그대로 수행된다.
        verify(userMapper, never()).findStatusById(anyLong());
        verify(withdrawalGraceService).resolveAccess(USER_ID, null);
    }

    private MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }

    private void authenticate(UserRole role) {
        User user = new User();
        user.setId(USER_ID);
        user.setUserRole(role);
        CustomUserDetails details = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
