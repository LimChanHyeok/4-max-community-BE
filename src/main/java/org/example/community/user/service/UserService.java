package org.example.community.user.service;

import lombok.RequiredArgsConstructor;
import org.example.community.auth.domain.RefreshToken;
import org.example.community.auth.repository.RefreshTokenRepository;
import org.example.community.global.auth.JwtProvider;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.global.file.FileStorageService;
import org.example.community.user.domain.User;
import org.example.community.user.dto.response.UserProfileResponse;
import org.example.community.user.dto.response.UserUpdateResponse;
import org.example.community.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.community.auth.dto.response.LoginResponse;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    //JWT관련 클래스 추가
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * signup이라는 하나의 흐름 속에서 중간에 문제가 생긴다면 저장이 되면 안되고
     * 모든 과정이 정상적으로 끝나야 커밋을 완료할 수 있도록
     * Transactional을 추가하였음
     */
    @Transactional
    public User signup(String email, String password, String passwordConfirm,
                       String nickname, MultipartFile profileImage) {

        if (!password.equals(passwordConfirm)) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        if (userRepository.existsByEmail(email) || userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_USER);
        }

        String encodedPassword = passwordEncoder.encode(password);

        String profileImageUrl = fileStorageService.store(profileImage,"profiles");

        User user = new User(
                null,
                email,
                encodedPassword,
                nickname,
                profileImageUrl,
                null,
                null
        );

        return userRepository.save(user);
    }


    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImage(),
                user.getCreatedAt()
        );
    }

    @Transactional
    public UserUpdateResponse updateUser(
            Long userId,
            String nickname,
            MultipartFile profileImage
    ) {
        /**
         * userId가 없다면 USER_NOT_FOUND 에러 던짐
         */
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));


        String profileImageUrl = null;

        /**
         * profileImage != null -> 프로필 이미지가 실제로 들어왔는지 확인하는 부분
         * !profileImage.isEmpty() -> 파일이 비어있지 않은지 확인하는 부분
         * 밑에 조건식을 만족한다는 건 프로필 이미지 파일이 들어왔다는 것
         * 그렇다면 파일을 uploads/profiles폴더에 저장하고 저장된 이미지 경로를 ProfileImageUrl에 넣는다.
         */
        if (profileImage != null && !profileImage.isEmpty()) {
            profileImageUrl = fileStorageService.store(profileImage, "profiles");
        }

        userRepository.updateProfile(
                user.getId(),
                nickname,
                profileImageUrl
        );

        /**
         * DB 수정이 끝나고 다시 조회하는 코드
         * 수정된 최신값을 응답으로 내려주기 위해서
         */
        User updatedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return new UserUpdateResponse(
                updatedUser.getId(),
                updatedUser.getNickname(),
                updatedUser.getProfileImage()
        );
    }

    @Transactional
    public void updatePassword(
            Long userId,
            String password,
            String passwordConfirm
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!password.equals(passwordConfirm)) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        /**
         * 비밀번호 인코딩 후 updatePassword로 전달
         */
        String encodedPassword = passwordEncoder.encode(password);

        userRepository.updatePassword(user.getId(), encodedPassword);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        userRepository.deleteById(user.getId());
    }


}