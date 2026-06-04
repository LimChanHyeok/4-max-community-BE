package org.example.community.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 이 클래스는 내부용 DTO로서 authService는 accesstoken과 refreshtoken을 만들어서 컨트롤러에게 전달해야함
 * 따라서 loginresponse는 프론트에게 응답하기 위한 DTO이고
 * AuthTokenResult는 내부에서 서비스가 컨트롤러에게 전달하기 위한 DTO이다.
 * 내부용과 외부용을 따로분리!!! refreshToken도 결국 컨트롤러가 헤더로 넘겨야하니까
 */
@Getter
@AllArgsConstructor
public class AuthTokenResult {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
}