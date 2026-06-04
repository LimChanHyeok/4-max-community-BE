package org.example.community.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 인증이 필요한 요청을 Controller에 보내기 전에 먼저 검사하는 인터셉터
 *
 * 요청 헤더의 Authorization 값을 확인하고,
 * Bearer Access Token을 꺼낸 뒤 JWT 검증을 수행한다.
 *
 * 토큰이 유효하면 토큰에서 userId를 꺼내
 * request에 loginUserId라는 이름으로 저장한다.
 *
 * Controller에서는 loginUserId = 1L로 고정하지 않고,
 * request.getAttribute("loginUserId")로 실제 로그인 사용자 id를 꺼내 사용한다.
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtProvider jwtProvider;

    /**
     * Controller가 실행되기 전에 먼저 실행되는 메서드
     *
     * true를 반환하면 Controller로 요청이 계속 진행되고,
     * 예외가 발생하면 Controller로 가지 않고 에러 응답이 반환된다.
     */
    @Override
    public boolean preHandle(
            // 지금 들어온 HTTP 요청, 요청 객체의 모든 것을 다 볼 수 있음 url,method,header,parameter,cookie,body 정보 등등
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {

        // 프론트를 연동하다가 OPTIONS라는 Method로 OPTIONS /auth 이런식으로 보냄
        // 실제 요청이 아닌 브라우저가 이 요청을 보내도되는지 서버에게 미리 물어보는 요처인데
        // OPTIONS가 들어왔을 때 헤더가 없기 때문에 인터셉터에서 막혀버리면서 에러가 뜸
        if (request.getMethod().equals("OPTIONS")) {
            return true;
        }

        System.out.println("요청 URI = " + request.getRequestURI());
        System.out.println("요청 Method = " + request.getMethod());
        String authorizationHeader = request.getHeader("Authorization");

        /**
         * Authorization 헤더가 없거나 Bearer 형식이 아니면 인증을 실패했다고 판단
         * 정상 형식:
         * Authorization: Bearer access_token값
         */
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        /**
         * "Bearer " 부분을 제거하고 실제 Access Token 값만 추출
         */
        String accessToken = authorizationHeader.substring(7);

        /**
         * Access Token이 유효한지 검증
         *
         * 토큰이 위조되었거나, 만료되었거나, 형식이 잘못되었다면 인증 실패
         */
        if (!jwtProvider.validateToken(accessToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        /**
         * Access Token에서 userId 추출
         */
        Long loginUserId = jwtProvider.getUserId(accessToken);

        /**
         * 여기서 loginUserId라는 값을 임시로 저장하는것 이번 요청동안만 저장해서 controller에 보내기 위함
         */
        request.setAttribute("loginUserId", loginUserId);

        return true;
    }
}