package org.example.community.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 프로젝트에서 사용하는 에러 상태코드와 메시지를 관리하는 enum이다.
 *
 * enum은 정해진 값들의 목록을 하나의 타입으로 관리하는 문법이다.
 * 에러 상태코드, 프론트에서 구분할 에러 코드, 메시지를 한 곳에서 관리한다.
 */
@Getter
public enum ErrorCode {


    // Common
    // @Valid 검증 실패
    INVALID_INPUT_VALUE(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_INPUT_VALUE", "입력값 형식이 올바르지 않습니다."),

    // 잘못된 요청
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "잘못된 요청입니다."),

    // 지원하지 않는 요청 Content-Type
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다."),


    // Auth
    // 로그인 실패
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "이메일 또는 비밀번호가 일치하지 않습니다."),

    // 유효하지 않은 토큰
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 토큰입니다."),


    // User
    // 비밀번호 확인 불일치
    PASSWORD_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_MISMATCH", "비밀번호와 일치하지 않습니다."),

    // 이미 사용 중인 이메일 또는 닉네임
    DUPLICATE_USER(HttpStatus.CONFLICT, "DUPLICATE_USER", "이미 사용 중인 이메일 또는 닉네임입니다."),

    // 이미 사용 중인 이메일
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),

    // 이미 사용 중인 닉네임
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다."),

    // 존재하지 않는 회원
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 회원입니다."),


    // Post
    // 존재하지 않는 게시글
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "존재하지 않는 게시글입니다."),

    // 게시글 수정 권한 없음
    POST_FORBIDDEN(HttpStatus.FORBIDDEN, "POST_FORBIDDEN", "게시글 수정 권한이 없습니다."),

    // 게시글 삭제 권한 없음
    POST_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "POST_DELETE_FORBIDDEN", "게시글 삭제 권한이 없습니다."),


    // Comment
    // 존재하지 않는 댓글
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "존재하지 않는 댓글입니다."),

    // 댓글 수정 권한 없음
    COMMENT_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "COMMENT_UPDATE_FORBIDDEN", "댓글 수정 권한이 없습니다."),

    // 댓글 삭제 권한 없음
    COMMENT_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "COMMENT_DELETE_FORBIDDEN", "댓글 삭제 권한이 없습니다."),


    // PostLike
    // 이미 좋아요를 누른 게시글
    ALREADY_LIKED_POST(HttpStatus.CONFLICT, "ALREADY_LIKED_POST", "이미 좋아요를 누른 게시글입니다."),

    // 좋아요를 찾을 수 없음
    POST_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_LIKE_NOT_FOUND", "좋아요를 찾을 수 없습니다."),


    // Image / File
    // 이미지 파일이 비어 있음
    EMPTY_IMAGE_FILE(HttpStatus.BAD_REQUEST, "EMPTY_IMAGE_FILE", "이미지 파일이 비어 있습니다."),

    // 이미지 파일명이 올바르지 않음
    INVALID_IMAGE_FILENAME(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_FILENAME", "이미지 파일명이 올바르지 않습니다."),

    // 지원하지 않는 이미지 확장자
    UNSUPPORTED_IMAGE_EXTENSION(HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE_EXTENSION", "지원하지 않는 이미지 확장자입니다."),

    // 지원하지 않는 이미지 MIME 타입
    UNSUPPORTED_IMAGE_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE_CONTENT_TYPE", "지원하지 않는 이미지 형식입니다."),

    // 이미지 파일 크기 초과
    IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "IMAGE_SIZE_EXCEEDED", "이미지 파일 크기가 제한을 초과했습니다."),

    // 존재하지 않는 이미지
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "이미지를 찾을 수 없습니다."),

    INVALID_IMAGE_DIRECTORY(HttpStatus.BAD_REQUEST, "INVALID_IMAGE_DIRECTORY", "이미지 저장 경로가 올바르지 않습니다."),


    // Server
    // 서버 내부 오류
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");



    /**
     * HTTP 상태코드
     *
     * 예:
     * 400 Bad Request
     * 409 Conflict
     * 422 Unprocessable Entity
     * 500 Internal Server Error
     */
    private final HttpStatus status;

    /**
     * 프론트엔드가 에러를 구분하기 위한 코드
     *
     * 예:
     * DUPLICATE_USER
     * PASSWORD_MISMATCH
     * INVALID_INPUT_VALUE
     */
    private final String code;

    /**
     * 클라이언트에게 내려줄 에러 메시지이다.
     */
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}