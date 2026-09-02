package com.example.travlediary.security;     // ← 편한 패키지로 변경 가능

import com.example.travlediary.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** username/password/roles 외에 id 를 담은 UserDetails */
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final List<GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id        = user.getId();
        this.username  = user.getUsername() == null
                ? "user:" + user.getId()
                : user.getUsername();
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
    @Override public String getUsername()   { return username; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
