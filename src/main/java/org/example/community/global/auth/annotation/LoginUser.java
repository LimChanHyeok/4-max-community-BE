package org.example.community.global.auth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 컨트롤러 메소드의 파라미터에 붙이는 어노테이션이라는 뜻
@Target(ElementType.PARAMETER)
// @LoginUser가 붙어있는지 확인할 수 있게 하는 설정
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}