package org.example.community.global.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.community.global.exception.ErrorCode;
import org.example.community.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    // 필터에서 인증 실패 응답을 ApiResponse로 내려주기 때문에 자바 객체를 JSON문자열로 바꿔주는 클래스가 필요함
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 여기서 인증이 필요한지 안필요한지 확인
        if (isExcludedPath(request)) {
            filterChain.doFilter(request,response);
            return;
        }

        String accessToken = resolveToken(request);

        //acccessToken을 꺼내서 값이 없으면 401응담
        if (accessToken == null) {
            sendUnauthorizedResponse(response);
            return;
        }

        // 토큰이 존재해도 유효한지는 확인해야함 없으면 401 응답
        if (!jwtProvider.validateToken(accessToken)) {
            sendUnauthorizedResponse(response);
            return;
        }

        //토큰에서 userId 꺼내오기
        Long loginUserId = jwtProvider.getUserId(accessToken);
        request.setAttribute("loginUserId", loginUserId);

        // 다음 필터로 넘기거나 없으면 컨트롤러로 넘김
        filterChain.doFilter(request, response);
    }


    //Bearer뒤에 있는 토큰 값 꺼내는 메소드
    private String resolveToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        return null;
    }

    private boolean isExcludedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (method.equals("OPTIONS")) {
            return true;
        }

        return uri.equals("/auth")
                || uri.equals("/auth/reissue")
                || uri.equals("/auth/status")
                || uri.equals("/users")
                || uri.equals("/users/email/check")
                || uri.equals("/users/nickname/check")
                || uri.startsWith("/uploads/")
                || uri.equals("/images/profiles");
    }

    private void sendUnauthorizedResponse(HttpServletResponse response) throws IOException {
        //모두 인증실패 401을 반환
        ErrorCode errorCode = ErrorCode.INVALID_TOKEN;

        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.fail(
                errorCode.getCode(),
                errorCode.getMessage()
        );

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
