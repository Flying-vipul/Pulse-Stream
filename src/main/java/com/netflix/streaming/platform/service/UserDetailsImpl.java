package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.*;

@NoArgsConstructor
@Data
public class UserDetailsImpl implements UserDetails {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String email;

    @JsonIgnore
    private String password;

    private Collection<? extends GrantedAuthority> authorities;

    // Advanced Security Flags pulled from your Database
    private boolean isVerified;
    private LocalDateTime accountLockedUntil;

    public UserDetailsImpl(Long id, String email, String password,
                           Collection<? extends GrantedAuthority> authorities,
                           boolean isVerified, LocalDateTime accountLockedUntil) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
        this.isVerified = isVerified;
        this.accountLockedUntil = accountLockedUntil;
    }

    public static UserDetailsImpl build(User user) {
        // FIXED: Used .name() to extract the text from the Enum safely!
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getPlanTier().name());

        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                Collections.singletonList(authority), // Singleton list because one user has one plan
                user.isVerified(),
                user.getAccountLockedUntil()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        // CRITICAL: Spring Security expects a username, so we feed it the email!
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // This physically blocks login if the 15-minute OTP penalty is active!
        return accountLockedUntil == null || LocalDateTime.now().isAfter(accountLockedUntil);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // This blocks login if they haven't verified their email yet!
        return isVerified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserDetailsImpl user = (UserDetailsImpl) o;
        return Objects.equals(id, user.id);
    }
}