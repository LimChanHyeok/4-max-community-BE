package org.example.community.user.repository;

import org.example.community.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // User save(User user); JpaRepository 기본 제공

    //Optional<User> findById(Long id); JpaRepository 기본 제공,findByXX 메소드는 기본으로 Optional을 반환

    //void updateProfile(Long userId, String nickname, String profileImage); Repository에서 제거하고 Entity 변경 메서드 + 변경 감지로 처리

    //void updatePassword(Long userId, String encodedPassword); Repository에서 제거하고 Entity 변경 메서드 + 변경 감지로 처리

    //void deleteById(Long userId); JpaRepository 기본 제공

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}