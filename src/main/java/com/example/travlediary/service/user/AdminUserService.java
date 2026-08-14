package com.example.travlediary.service.user;

import com.example.travlediary.dto.AdminUserDetailDto;
import com.example.travlediary.dto.AdminUserListItemDto;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 관리자 회원 조회 전용 서비스. 이 단계에서는 읽기만 제공한다. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public long countUsers(String keyword, UserStatus status) {
        return userMapper.countAdminUsers(keyword, status);
    }

    @Transactional(readOnly = true)
    public List<AdminUserListItemDto> getUsers(String keyword,
                                               UserStatus status,
                                               long offset,
                                               int limit) {
        return userMapper.findAdminUsers(keyword, status, offset, limit);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailDto getUser(Long id) {
        AdminUserDetailDto user = userMapper.findAdminUserById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        return user;
    }
}