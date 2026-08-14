package com.example.travlediary.service.user;

import com.example.travlediary.dto.AdminUserDetailDto;
import com.example.travlediary.dto.AdminUserListItemDto;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserMapper userMapper;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userMapper);
    }

    @Test
    void listPassesKeywordStatusAndPagingToMapper() {
        AdminUserListItemDto item = new AdminUserListItemDto();
        item.setId(3L);
        when(userMapper.findAdminUsers("여행", UserStatus.ACTIVE, 20L, 20))
                .thenReturn(List.of(item));

        assertThat(adminUserService.getUsers("여행", UserStatus.ACTIVE, 20L, 20))
                .containsExactly(item);
        verify(userMapper).findAdminUsers("여행", UserStatus.ACTIVE, 20L, 20);
    }

    @Test
    void countUsesTheSameFilterAsTheList() {
        when(userMapper.countAdminUsers(null, UserStatus.RESTRICTED)).thenReturn(4L);

        assertThat(adminUserService.countUsers(null, UserStatus.RESTRICTED)).isEqualTo(4L);
    }

    @Test
    void detailReturnsStoredUser() {
        AdminUserDetailDto detail = new AdminUserDetailDto();
        detail.setId(7L);
        detail.setUsername("travler");
        when(userMapper.findAdminUserById(7L)).thenReturn(detail);

        assertThat(adminUserService.getUser(7L)).isSameAs(detail);
    }

    @Test
    void missingUserReturnsNotFound() {
        when(userMapper.findAdminUserById(99L)).thenReturn(null);

        assertThatThrownBy(() -> adminUserService.getUser(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
    }
}