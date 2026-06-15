package org.example.community.global.file.service;

import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.global.file.dto.FileStoreResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * prod 환경에서 파일 시스템에 파일을 저장하는 구현체
 *
 * 현재는 EC2 내부 디렉토리에 파일을 저장
 * 나중에 S3를 사용하게 되면 이 클래스 내부 구현만 S3 업로드 방식으로 변경하면 된다.
 */
@Service
@Profile("prod")
public class ProdFileStorageService implements FileStorageService {

    @Value("${app.upload.base-dir}")
    private String baseDir;

    @Value("${app.upload.base-url}")
    private String baseUrl;

    @Override
    public FileStoreResult store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            /**
             * baseDir과 directory를 합쳐 실제 저장 폴더 경로를 만든다.
             * baseDir = /home/ubuntu/community/uploads
             * directory = posts
             * 결과 = /home/ubuntu/community/uploads/posts
             */
            Path uploadPath = Paths.get(baseDir, directory)
                    .toAbsolutePath()
                    .normalize();

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

    // 확장자를 잘라내는 함수
    private String extractExtension(String originalFilename) {
        // 파일명이 없거나, .이 없으면 확장자를 찾을 수 없음
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }

        // 마지막 .부터 끝까지 잘라냄
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }
}