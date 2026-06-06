package org.example.community.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
// JPA는 엔티티 객체를 만들 때 기본 생성자가 필요함, 하지만 외부에서 new User()을 막기 위해 protected 사용
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 63)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;


    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    // User를 만들 때 반드시 필요한 값만 빼서 생성자를 만듬
    // 생성자를 private로 만들면서 Service에서 사용을 못하게 막고
    // 외부에서는 create()만 이용하도록 함
    private User(String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    public static User create(String email, String password, String nickname) {
        return new User(email, password, nickname);
    }


    // jpa의 변경 감지를 이용한 updateProfile
    // 파일저장,중복검사, 예외 처리 같은 흐름은 Service에서 처리
    // 엔티티는 최종적으로 결정된 값으로 자신의 상태만 변경
    public void updateProfile(String nickname) {
        this.nickname = nickname;
    }

    // jpa의 변경 감지를 이용한 updatePassword
    // 변경된 password의 값은 JPA 변경 감지에 의해 자동으로 DB에 반영됨.
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}