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



    /**
     * @Valid 검증 실패
     *
     * 예:
     * - 이메일 형식 오류
     * - 비밀번호 정규식 불만족
     * - 닉네임 길이 초과
     * - 필수값 누락
     */
    INVALID_INPUT_VALUE(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "INVALID_INPUT_VALUE",
            "입력값 형식이 올바르지 않습니다."
    ),

    /**
     * 비밀번호와 비밀번호 확인 값이 일치하지 않는 경우
     */
    PASSWORD_MISMATCH(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PASSWORD_MISMATCH",
            "비밀번호와 일치하지 않습니다."
    ),
    /**
     * 로그인 실패할 때 이메일과 비밀번호를 둘 다 합쳐서 메세지 전달
     */
    LOGIN_FAILED(
            HttpStatus.UNAUTHORIZED,
            "LOGIN_FAILED",
            "이메일 또는 비밀번호가 일치하지 않습니다."
    ),

    /**
     * 이미 사용 중인 이메일 또는 닉네임으로 회원가입을 시도한 경우
     */
    DUPLICATE_USER(
            HttpStatus.CONFLICT,
            "DUPLICATE_USER",
            "이미 사용 중인 이메일 또는 닉네임입니다."
    ),
    /**
     * 존재하지 않는 회원을 조회한 경우
     */
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "USER_NOT_FOUND",
            "존재하지 않는 회원입니다."
    ),
    /**
     * 존재하지 않는 게시글을 조회한 경우
     */
    POST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "POST_NOT_FOUND",
            "존재하지 않는 게시글입니다."
    ),

    /**
     * 이미 좋아요를 누른 게시글에 다시 좋아요 등록을 요청한 경우
     */
    ALREADY_LIKED_POST(
            HttpStatus.CONFLICT,
            "ALREADY_LIKED_POST",
            "이미 좋아요를 누른 게시글입니다."
    ),

    /**
     * 좋아요를 누르지 않은 게시글에 좋아요 취소를 요청한 경우
     */
    POST_LIKE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "POST_LIKE_NOT_FOUND",
            "좋아요를 찾을 수 없습니다."
    ),

    /**
     * 조재하지 않는 댓글을 조회한 경우
     */
    COMMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COMMENT_NOT_FOUND",
            "존재하지 않는 댓글입니다."
    ),

    /**
     * 게시글 수정 권한이 없는 경우
     */
    POST_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "POST_FORBIDDEN",
            "게시글 수정 권한이 없습니다."
    ),
    /**
     * 게시글 삭제 권한이 없는 경우
     */
    POST_DELETE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "POST_DELETE_FORBIDDEN",
            "게시글 삭제 권한이 없습니다."
    ),

    /**
     * 댓글 수정 권한이 없는 경우
     */
    COMMENT_UPDATE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "COMMENT_UPDATE_FORBIDDEN",
            "댓글 수정 권한이 없습니다."
    ),
    /**
     * 댓글 삭제 권한이 없는 경우
     */
    COMMENT_DELETE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "COMMENT_DELETE_FORBIDDEN",
            "댓글 삭제 권한이 없습니다."
    ),
    /**
     * 유효하지 않은 토큰일 때
     */
    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "INVALID_TOKEN",
            "유효하지 않은 토큰입니다."
    ),




    /**
     * 요청 JSON 문법이 잘못되었거나 요청 Body를 읽을 수 없는 경우
     */
    BAD_REQUEST(
            HttpStatus.BAD_REQUEST,
            "BAD_REQUEST",
            "잘못된 요청입니다."
    ),



    /**
     * 지원하지 않는 Content-Type으로 요청한 경우
     *
     * 예:
     * 서버는 application/json을 기대하는데 text/plain으로 요청한 경우
     */
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_MEDIA_TYPE",
            "지원하지 않는 요청 형식입니다."
    ),

    /**
     * 이미지가 존재하지 않을 때
     */
    IMAGE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "IMAGE_NOT_FOUND",
            "이미지를 찾을 수 없습니다."
    ),

    /**
     * 예상하지 못한 서버 내부 오류
     */
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "서버 내부 오류가 발생했습니다."
    );



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