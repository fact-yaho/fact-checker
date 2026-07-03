package com.yaho.factchecker.global.util.config; // 프로젝트 패키지 구조에 맞게 수정

import com.yaho.factchecker.domain.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

// UserDetails와 OAuth2User를 동시에 구현하여 어떤 로그인 방식이든 이 한 장으로 통일합니다.
public class PrincipalDetails implements UserDetails, OAuth2User {

    private final User user; // 우리 서비스의 실제 DB User 엔티티
    private Map<String, Object> attributes; // 소셜 로그인 시 구글 등에서 받는 속성값

    // 일반 로그인용 생성자
    public PrincipalDetails(User user) {
        this.user = user;
    }

    // 소셜 로그인용 생성자
    public PrincipalDetails(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    /**
     * User 엔티티를 외부(컨트롤러 등)에서 꺼내 쓸 수 있도록 Getter 제공
     */
    public User getUser() {
        return user;
    }

    // ==========================================
    // OAuth2User 인터페이스 구현부
    // ==========================================
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return user.getEmail(); // 또는 user.getId().toString() 등 유니크한 값 리턴
    }

    // ==========================================
    // UserDetails 인터페이스 구현부
    // ==========================================
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 엔티티의 Role(Enum)을 시큐리티가 인식할 수 있는 권한 형태로 변환 (예: "ROLE_USER")
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}