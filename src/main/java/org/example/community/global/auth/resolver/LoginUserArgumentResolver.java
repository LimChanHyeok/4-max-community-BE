package org.example.community.global.auth.resolver;

import jakarta.servlet.http.HttpServletRequest;
import org.example.community.global.auth.annotation.LoginUser;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

// HttpServletRequest의 loginUserId 속성 조회
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    // 필터와 resolver에서 loginUserId 이 문자열이 같아야함
    private static final String LOGIN_USER_ID_ATTRIBUTE = "loginUserId";

    /**
     * 현재 컨트롤러의 파라미터를 이 resolver가 처리할 것인지 판단하는 메소드
     * @LoginUser 어노테이션이 붙어있고 타입이 Long인 경우에만 해당 파라미터를 실행
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean hasLoginUserAnnotation =
                parameter.hasParameterAnnotation(LoginUser.class);

        boolean isLongType =
                Long.class.equals(parameter.getParameterType());

        return hasLoginUserAnnotation && isLongType;
    }

    /**
     *컨트롤러 파라미터에 실제로 넣을 값을 반환
     * 필터가 request에 저장해준 loginUserId를 반환하는 역할을 함
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        // NativeWebRequest에서 실제 HttpServletRequest 객체를 꺼냄
        HttpServletRequest request =
                webRequest.getNativeRequest(HttpServletRequest.class);

        //없으면 예회처리
        if (request == null) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        // 필터에서 저장해 둔 LoginUserId 꺼냄
        Object loginUserId =
                request.getAttribute(LOGIN_USER_ID_ATTRIBUTE);

        // LoginUserId가 없거나 Long 타입이 아니면 예외 처ㅣㄹ
        if (!(loginUserId instanceof Long)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        // 반환한 값을 컨트롤러의 @LoginUser Long LoginUserId 파라미터에 주입함
        return loginUserId;
    }
}