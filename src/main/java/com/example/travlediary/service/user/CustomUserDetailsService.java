package com.example.travlediary.service.user;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    @Autowired
    public CustomUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
        }
        if (user.getUserPassword() == null) {
            throw new BadCredentialsException("비밀번호가 설정되지 않았습니다.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("이메일 인증이 완료되지 않았습니다.");
        }

        /* ★ 변경된 부분: id 포함 CustomUserDetails 반환 */
        return new com.example.travlediary.security.CustomUserDetails(user);
    }
}


