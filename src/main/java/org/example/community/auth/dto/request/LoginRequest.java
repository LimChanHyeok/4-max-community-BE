package org.example.community.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

/**
 * 로그인 요청 데이터를 받는 DTO
 */
@Getter
public class LoginRequest {

    /**
     * 필수값이고 이메일 형식이어야함
     */
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    private String email;

    /**
     * 필수값이며 비밀번호 조건에 만족해야함
     * messagesms fieldError.getDefaultMessage()로 꺼내 쓸 수있다
     * 지금 당장은 안쓰지만 나중에 필요할 때 확장 할 수 있음
     */
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,20}$",
            message = "비밀번호는 8자 이상 20자 이하이며, 대문자, 소문자, 숫자, 특수문자를 각각 최소 1개 포함해야 합니다."
    )
    private String password;
}