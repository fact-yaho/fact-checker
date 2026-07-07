package com.yaho.factchecker.domain.user.repository;

import com.yaho.factchecker.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {

    // 회원가입할 때 이미 존재하는 이메일인지 확인할 때 사용합니다.
    Optional<User> findByEmail(String email);

    // 이메일 존재 여부만 빠르게
    boolean existsByEmail(String email);

    // 닉네임 존재 여부 확인
    boolean existsByNickname(String nickname);

}