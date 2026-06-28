package org.example.community.global.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.global.file.dto.FileStoreResult;
import org.example.community.global.file.validator.ImageFileValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * prod 환경에서 S3에 이미지 파일을 저장하는 구현체
 *
 * local 환경에서는 LocalFileStorageService가 로컬 uploads 폴더에 저장
 * prod 환경에서는 이 클래스가 S3 Bucket에 저장
 * S3
 * - community-board-images-prod/profiles/uuid.png
 * - community-board-images-prod/posts/uuid.png
 * DB
 * - profiles/uuid.png
 * - posts/uuid.png
 *
 * DB에는 전체 URL을 저장하지 않고 S3 Object Key만 저장
 * CDN URL은 게시글/회원 응답 DTO를 만들 때 cdnBaseUrl을 붙여서 만든다
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Profile("prod")
public class S3FileStorageService implements FileStorageService {

    private final ImageFileValidator imageFileValidator;
    private final S3Client s3Client;

    private static final Set<String> ALLOWED_DIRECTORIES = Set.of("posts", "profiles");

    @Value("${app.aws.s3.bucket}")
    private String bucketName;

    @Override
    public FileStoreResult store(MultipartFile file, String directory) {
        imageFileValidator.validate(file);

        String uploadDirectory = validateDirectory(directory);

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + extension;

        /*
         * S3 Object Key 생성.
         *
         * ex)
         * directory = posts
         * storedFilename = abc.png
         * objectKey = posts/abc.png
         *
         * 이 objectKey가 S3 저장 경로이면서 DB images에 저장될 값
         * CDN 경로를 하드코딩으로 붙여서 DB에 저장하면 나중에 도메인이 바뀐다면 전체를 다 바꿔야하기 때문에 넣지 않고 응답에만 포함시킬 것이다.
         */
        String objectKey = uploadDirectory + "/" + storedFilename;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );

            /*
             * 여기서 imageUrl이라는 이제는 URL이 아닌 S3 Object Key다.
             *
             * posts/abc.png
             * profiles/abc.png
             *
             * 나중에 응답 DTO에서:
             * https://cdn.max-cm.cloud + "/" + posts/abc.png 로 바뀔 예정
             */
            return new FileStoreResult(
                    objectKey,
                    originalFilename,
                    storedFilename
            );

        } catch (IOException | S3Exception e) {
            log.error("S3 이미지 업로드에 실패했습니다. bucket={}, key={}", bucketName, objectKey, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean delete(String imageUrl) {
        /*
         * 현재 프로젝트에서는 imageUrl 파라미터에
         * 실제로는 DB에 저장된 S3 Object Key가 들어옴
         *
         * profiles/abc.png
         */
        if (imageUrl == null || imageUrl.isBlank()) {
            return true;
        }

        String objectKey = validateObjectKey(imageUrl);

        if(objectKey == null) {
            log.warn("삭제할 수 없는 S3 Object Key 형식입니다. imageUrl={}", imageUrl);
            return false;
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);

            return true;

        } catch (S3Exception e) {
            log.warn("S3 이미지 삭제에 실패했습니다. bucket={}, key={}", bucketName, objectKey, e);
            return false;
        }
    }

    /**
     * 업로드 가능한 디렉터리인지 검증
     */
    private String validateDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_DIRECTORY);
        }

        String uploadDirectory = directory.trim();

        if (!ALLOWED_DIRECTORIES.contains(uploadDirectory)) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_DIRECTORY);
        }

        return uploadDirectory;
    }

    /**
     * 원본 파일명에서 확장자만 추출
     */
    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }

        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }

    private String validateObjectKey(String imageUrl) {
        String objectKey = imageUrl.trim();

        if (objectKey.startsWith("posts/") || objectKey.startsWith("profiles/")) {
            return objectKey;
        }

        return null;

    }
}