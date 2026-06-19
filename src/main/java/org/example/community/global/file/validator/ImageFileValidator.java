package org.example.community.global.file.validator;

import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Component
public class ImageFileValidator {

    // 허용할 확장자 목록
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg",
            "jpeg",
            "png",
            "webp"
    );


    // 허용할 MIME 타입 목록
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", // -> jpg,jpeg는 같은 타입을 가진다
            "image/png",
            "image/webp"
    );

    /**
     * 이미지 파일 검증
     *
     * getOriginalFilename()과 getContentType()은 클라이언트가 보내는 값이므로
     * 그대로 신뢰하지 않고 서버에서 허용한 확장자와 MIME 타입인지 검증한다.
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_IMAGE_FILE);
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FILENAME);
        }

        // 원본 파일명에서 확장자 추출
        String extension = extractExtension(originalFilename);

        // 확장자 허용 검사
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_EXTENSION);
        }

        // MIME 타입 허용 여부 검사
        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_CONTENT_TYPE);
        }
    }

    /**
     * 원본 파일명에서 확장자 추출
     * image.png -> png
     */
    private String extractExtension(String originalFilename) {
        int lastDotIndex = originalFilename.lastIndexOf(".");

        if (lastDotIndex == -1 || lastDotIndex == originalFilename.length() - 1) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_FILENAME);
        }

        return originalFilename.substring(lastDotIndex + 1).toLowerCase();
    }
}