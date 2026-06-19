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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * prod 환경에서 파일 시스템에 파일을 저장하는 구현체
 *
 * 현재는 EC2 내부 디렉토리에 파일을 저장
 * 나중에 S3를 사용하게 되면 이 클래스 내부 구현만 S3 업로드 방식으로 변경하면 된다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Profile("prod")
public class ProdFileStorageService implements FileStorageService {

    private final ImageFileValidator imageFileValidator;

    private static final Set<String> ALLOWED_DIRECTORIES = Set.of("posts", "profiles");


    @Value("${app.upload.base-dir}")
    private String baseDir;

    @Value("${app.upload.base-url}")
    private String baseUrl;

    @Override
    public FileStoreResult store(MultipartFile file, String directory) {

        imageFileValidator.validate(file);

        try {
            /**
             * baseDir과 directory를 합쳐 실제 저장 폴더 경로를 만든다.
             * baseDir = /home/ubuntu/community/uploads
             * directory = posts
             * 결과 = /home/ubuntu/community/uploads/posts
             * resolveUplodaPath에서 검증 후 uploadPath 생성
             */
            Path uploadPath = resolveUploadPath(directory);

            /**
             * 폴더가 없으면 생성한다.
             */
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 원본 파일명 가져오기
            String originalFilename = file.getOriginalFilename();

            // 원본 파일명에서 확장자만 추출
            String extension = extractExtension(originalFilename);

            // 서버에 저장할 파일명을 UUID로 생성
            String storedFilename = UUID.randomUUID() + extension;

            // 저장 폴더와 파일명을 합쳐 최종 저장 경로 생성
            Path filePath = uploadPath.resolve(storedFilename)
                    .normalize();

            // 실제 파일 저장
            file.transferTo(filePath);

            /**
             * 클라이언트가 접근할 이미지 URL 생성
             *
             * 예)
             * baseUrl = /uploads
             * directory = posts
             * storedFilename = abc.jpg
             * 결과 = /uploads/posts/abc.jpg
             */
            String imageUrl = baseUrl + "/" + directory + "/" + storedFilename;

            return new FileStoreResult(
                    imageUrl,
                    originalFilename,
                    storedFilename
            );

        } catch (IOException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private Path resolveUploadPath(String directory) {
        if (directory == null || directory.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_DIRECTORY);
        }

        String uploadDirectory = directory.trim();

        if (!ALLOWED_DIRECTORIES.contains(uploadDirectory)) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_DIRECTORY);
        }

        Path basePath = Paths.get(baseDir)
                .toAbsolutePath()
                .normalize();

        Path uploadPath = basePath.resolve(uploadDirectory)
                .normalize();

        if (!uploadPath.startsWith(basePath)) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_DIRECTORY);
        }

        return uploadPath;
    }

    // 확장자를 잘라내는 함수
    private String extractExtension(String originalFilename) {
        // 파일명이 없거나, .이 없으면 확장자를 찾을 수 없음
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }

        // 마지막 .부터 끝까지 잘라냄
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }
    @Override
    public boolean delete(String imageUrl) {
        // 삭제할 이미지 URL이 없으면 삭제할 대상이 없으므로 바로 종료
        if (imageUrl == null || imageUrl.isBlank()) {
            return true;
        }

        // 운영 DB에 저장된 imageUrl은 /uploads/posts/파일명 형태여야함
        if (!imageUrl.startsWith(baseUrl + "/")) {
            log.warn("삭제할 수 없는 이미지 URL 형식입니다. imageUrl={}", imageUrl);
            return false;
        }

        try {
            // 실제 업로드 파일이 저장되는 기본 폴더 경로
            // /home/ubuntu/community/uploads
            Path basePath = Paths.get(baseDir)
                    .toAbsolutePath()
                    .normalize();

            // imageUrl에서 URL prefix인 /uploads 부분 제거
            // 예: /uploads/posts/test.png -> /posts/test.png
            String relativePath = imageUrl.substring(baseUrl.length());

            // basePath와 상대 경로를 합쳐 실제 삭제할 파일 경로 생성
            // /home/ubuntu/community/uploads/posts/test.png
            Path filePath = basePath.resolve(relativePath.substring(1))
                    .normalize();

            // ../ 같은 경로 조작으로 uploads 폴더 밖의 파일 삭제를 막는다
            if (!filePath.startsWith(basePath)) {
                log.warn("업로드 경로 밖의 파일 삭제 시도가 감지되었습니다. imageUrl={}", imageUrl);
                return false;
            }

            // 실제 파일 하나만 삭제한다
            Files.deleteIfExists(filePath);

            return true;

        } catch (IOException e) {
            // 파일 삭제 실패 때문에 전체 스케줄러를 터뜨리지 않도록 로그만 남긴다
            log.warn("이미지 파일 삭제에 실패했습니다. imageUrl={}", imageUrl, e);
            return false;
        }
    }


}