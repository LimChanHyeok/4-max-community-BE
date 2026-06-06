package org.example.community.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.community.global.response.ApiResponse;
import org.example.community.user.entity.User;
import org.example.community.user.dto.request.PasswordUpdateRequest;
import org.example.community.user.dto.request.SignupRequest;
import org.example.community.user.dto.request.UserUpdateRequest;
import org.example.community.user.dto.response.SignupResponse;
import org.example.community.user.dto.response.UserProfileResponse;
import org.example.community.user.dto.response.UserUpdateResponse;
import org.example.community.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    /**
     * 회원 관련 비즈니스 로직을 처리하는 Service
     */
    private final UserService userService;


    @PostMapping
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        User user = userService.signup(request);

        SignupResponse response = new SignupResponse(user.getId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입에 성공했습니다.", response));
    }


    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable Long userId
    ) {
        UserProfileResponse response = userService.getUserProfile(userId);

        return ResponseEntity.ok(
                ApiResponse.success("회원 정보 조회에 성공했습니다.", response)
        );
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserUpdateResponse>> updateUser(
            @Valid @RequestBody UserUpdateRequest request,
            HttpServletRequest httpServletRequest
    ) {
        Long loginUserId = (Long) httpServletRequest.getAttribute("loginUserId");

        UserUpdateResponse response = userService.updateUser(
                loginUserId,
                request
        );

        return ResponseEntity.ok(
                ApiResponse.success("회원정보 수정에 성공했습니다.", response)
        );
    }

    /**
     * URL에서 명시하였듯이 password로 했기 때문에 전체 수정을 의미하는 PUT 사용
     */
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @Valid @RequestBody PasswordUpdateRequest request,
            HttpServletRequest httpServletRequest
    ) {
        Long loginUserId = (Long) httpServletRequest.getAttribute("loginUserId");

        userService.updatePassword(
                loginUserId,
                request.getPassword(),
                request.getPasswordConfirm()
        );

        return ResponseEntity.ok(
                ApiResponse.success("비밀번호 수정에 성공했습니다.", null)
        );
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteUser( HttpServletRequest httpServletRequest) {

        Long loginUserId = (Long) httpServletRequest.getAttribute("loginUserId");

        userService.deleteUser(loginUserId);

        return ResponseEntity.ok(
                ApiResponse.success("회원정보 삭제에 성공했습니다.", null)
        );
    }
}