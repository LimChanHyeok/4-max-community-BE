package org.example.community.global.exception;

import org.example.community.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 프로젝트 전체에서 발생한 예외를 잡아서
 * API 명세에 맞는 HTTP 상태코드와 JSON 응답으로 변환하는 클래스
 * 자바 빈으로 등록됨
 * @RestControllerAdvice 이 어노테이션을 보고 여기가 전역 예외 처리 클래스구나 하고 일로 옴
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 우리가 직접 정의한 비즈니스 예외를 처리한다.
     * Service에서 CustomException을 던지면
     * 여기서 ErrorCode 안에 들어있는 status, code, message를 꺼내 응답을 생성하고 프론트에게 전달한다
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(
            CustomException e
    ) {
        e.printStackTrace();
        ErrorCode errorCode = e.getErrorCode();

        /**
         * 여기서 각각 get으로 Enum에서 정의한 status와 code와 message를 꺼낸다.
         */
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * @Valid 검증에 실패했을 때 발생하는 예외를 처리한다.
     * 이메일 형식 오류,비밀번호 정규식 불만족,닉네임 10글자 초과,닉네임 공백 포함,필수값 누락
     * 요청 JSON 형식은 맞지만 입력값 조건을 만족하지 못했으므로 422를 반환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e
    ) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(
                        ErrorCode.INVALID_INPUT_VALUE.getCode(),
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                ));
    }

    /**
     * 요청 Body를 읽을 수 없을 때 발생하는 예외를 처리한다.
     * JSON 문법 오류,요청 Body 구조가 깨진 경우
     * 요청 자체가 잘못되었으므로 400을 반환한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            HttpMessageNotReadableException e
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        ErrorCode.BAD_REQUEST.getCode(),
                        ErrorCode.BAD_REQUEST.getMessage()
                ));
    }

    /**
     * 지원하지 않는 Content-Type으로 요청이 들어왔을 때 발생하는 예외를 처리한다.
     * 회원가입 API는 application/json을 기대하는데
     * text/plain이나 multipart/form-data로 요청한 경우
     * 서버가 지원하지 않는 요청 형식이므로 415를 반환한다.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e
    ) {
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.fail(
                        ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode(),
                        ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage()
                ));
    }

    /**
     * 위에서 따로 처리하지 못한 모든 예외를 처리
     * - 예상하지 못한 서버 내부 오류
     * 500반환
     * 대신 예외가 잡아서 응답으로 바꿔버리면 처리된 예외가 되버리면서 콘솔에 에러가 안뜸
     * 그래서 e.printStackTreace()를 넣었는데 실무에서는 빼야되나..?
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        e.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }

}