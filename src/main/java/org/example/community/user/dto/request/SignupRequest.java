package org.example.community.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원가입 요청 데이터를 받은 DTP
 * @Valid를 사용하여 입력값 검증도 수행함
 */
@Getter
@Setter
public class SignupRequest {

    @NotBlank
    @Email
    private String email;

    /**
     * 필수 입력값이며 정규식 조건 만족해야됨
     * 대문자,소문자,숫자,특수문자 최소1개씩 포함, 전체길이 8자 이상 20자 이하
     */
    @NotBlank
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,20}$"
    )
    private String password;

    /**
     * 비밀번호 확인값
     * JSON에서의 snake_case와 Java에서의 camelCase를 서로 맞춰주기 위해 JSONProperty 사용
     */
    @NotBlank
    @JsonProperty("password_confirm")
    private String passwordConfirm;

    /**
     * 최대10글자, 공백 안됨
     */
    @NotBlank
    @Size(max = 10)
    @Pattern(regexp = "^\\S+$")
    private String nickname;

    @JsonProperty("image_id")
    private Long imageId;

}