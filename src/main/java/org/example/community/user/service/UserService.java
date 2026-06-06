package org.example.community.user.service;

import lombok.RequiredArgsConstructor;
import org.example.community.auth.repository.RefreshTokenRepository;
import org.example.community.global.auth.JwtProvider;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.global.file.dto.FileStoreResult;
import org.example.community.global.file.service.FileStorageService;
import org.example.community.image.entity.Image;
import org.example.community.image.entity.ImageType;
import org.example.community.image.repository.ImageRepository;
import org.example.community.user.dto.request.SignupRequest;
import org.example.community.user.dto.request.UserUpdateRequest;
import org.example.community.user.dto.response.UserProfileResponse;
import org.example.community.user.dto.response.UserUpdateResponse;
import org.example.community.user.entity.User;
import org.example.community.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    private final ImageRepository imageRepository;

    /**
     * signup이라는 하나의 흐름 속에서 중간에 문제가 생긴다면 저장이 되면 안되고
     * 모든 과정이 정상적으로 끝나야 커밋을 완료할 수 있도록
     * Transactional을 추가하였음
     */
    @Transactional
    // 매개변수 request로 축소시킴
    public User signup(SignupRequest request) {

        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        if (userRepository.existsByEmail(request.getEmail()) ||
                userRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.DUPLICATE_USER);
        }

        Image profileImage = null;

        if (request.getImageId() != null) {
            profileImage = imageRepository.findById(request.getImageId())
                    .orElseThrow(() -> new CustomException(ErrorCode.IMAGE_NOT_FOUND));

            if (profileImage.getImageType() != ImageType.USER) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            // 본인것이 아닌 image_id를 참조하면 다른 사용자의 이미지를 뺏어버리는 걸 테스트하다 확인
            // 회원가입시에는 id가 없기 때문에 referenceId가 있다면 무조건 차단
            if (profileImage.getReferenceId() != null) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.create(
                request.getEmail(),
                encodedPassword,
                request.getNickname()
        );

        User savedUser = userRepository.save(user);

        // 어기서 래퍼런스아이디와 userId를 연결해줌
        if (profileImage != null) {
            profileImage.connectReference(savedUser.getId());
        }

        return savedUser;
    }


    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Image profileImage = imageRepository
                .findByImageTypeAndReferenceId(ImageType.USER, user.getId())
                .orElse(null);

        String profileImageUrl = profileImage != null ? profileImage.getImageUrl() : null;

        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                profileImageUrl,
                user.getCreatedAt()
        );
    }

    @Transactional
    public UserUpdateResponse updateUser(Long userId, UserUpdateRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 현재 유저에게 연결된 기존 프로필 이미지 조회
        Image responseImage = imageRepository
                .findByImageTypeAndReferenceId(ImageType.USER, user.getId())
                .orElse(null);

        // 수정할 이미지가 왔다면 그 이미지의 id를 가지고 옴
        if (request.getImageId() != null) {
            // 만약 이미지가 없다면 예외 처리
            Image newProfileImage = imageRepository.findById(request.getImageId())
                    .orElseThrow(() -> new CustomException(ErrorCode.IMAGE_NOT_FOUND));
            // 이미지타입이 유저라 아니라면 예외처리
            if (newProfileImage.getImageType() != ImageType.USER) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            // 테스트를 하던 중 다른 image_id를 넣게 되면 다른 사람의 이미지를 뺏어버리는 걸 확인
            // 따라서 referenceId가 있고, 그 아이디가 로그인한 사용자와 다르다면 예외처리하는 걸 추가함
            if (newProfileImage.getReferenceId() != null
                    && !newProfileImage.getReferenceId().equals(user.getId())) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
            //현재 유저에게 연결되어있던 기존 프로필을 찾은다음 연결을 끊어냄 referenceId를 null로 만듬
            imageRepository.findByImageTypeAndReferenceId(ImageType.USER, user.getId())
                    .ifPresent(Image::disconnectReference);

            // 새 이미지에 현재 유저 id연결
            newProfileImage.connectReference(user.getId());

            // 응답에는 새 이미지 URL이 나가야 하므로 responseImage 교체
            responseImage = newProfileImage;
        }

        // user엔티티에는 이제 profileImage가 없기 때문에 닉네임만 변경
        user.updateProfile(request.getNickname());

        // reponseImage에 있을 기존 사진이나, 새 사진, 또는 null값을 담는다
        String profileImageUrl = responseImage != null ? responseImage.getImageUrl() : null;

        return new UserUpdateResponse(
                user.getId(),
                user.getNickname(),
                profileImageUrl
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

        user.updatePassword(encodedPassword);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // userId기반으로 찾았으므로 user객체 그대로 삭제
        userRepository.delete(user);
    }


}