package com.example.travlediary.security;     // ← 편한 패키지로 변경 가능

import com.example.travlediary.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Spring Security 인증 정보에 회원 id 와 비개인 식별자를 담는 UserDetails. */
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String principalName;
    private final String password;
    private final List<GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id        = user.getId();
        // Spring Security principal 이름과 STOMP 사용자 목적지에 이메일/공개 닉네임을 노출하지 않는다.
        this.principalName = "user:" + user.getId();
        this.password  = user.getUserPassword();
        this.authorities = user.getUserRole().name().equals("ADMIN")
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /* ---- 추가 getter ---- */
    public Long getId() { return id; }

    /* ---- UserDetails 구현 ---- */
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()   { return password; }
    @Override public String getUsername()   { return principalName; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
