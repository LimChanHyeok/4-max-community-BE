package org.example.community.global.file.service;

import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.global.file.dto.FileStoreResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 나중에 배포환경에서를 위해 인터페이스를 만들고 local이라고 명시하였음
 */
@Service
@Profile("local")
public class LocalFileStorageService implements FileStorageService {


    @Override
    public FileStoreResult store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            return null;
        }


        try {
            String uploadDir = "uploads/" + directory;
            /**
             * Path로 바꾸는 코드
             */
            Path uploadPath = Paths.get(uploadDir)
                    .toAbsolutePath()
                    .normalize();


            /**
             * 폴더가 있는지 확인
             */
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            // 원본 파일명 가져옴
            String originalFilename = file.getOriginalFilename();
            // 원본 파일명에서 확장자만 꺼냄
            String extension = extractExtension(originalFilename);
            // 서버에 저장할 파일명을 새로 만듬 UUID 이용
            String storedFilename = UUID.randomUUID() + extension;
            // 저장 폴더와 파일명을 합쳐서 최종 저장 경로 만들기
            Path filePath = uploadPath.resolve(storedFilename).normalize();
            //실제 파일 저장
            file.transferTo(filePath);

            // 클라이언트가 접근할 이미지 URL 생성
            String imageUrl = "/" + uploadDir + "/" + storedFilename;

            // 원래는 url만 반환하였다면 이제는 originalfilename과 storefilename 둘 다 반환
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
        // . 부터 끝까지 잘라냄(lastIndexOF 이기 때문에 마지막 점을 기준으로)
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }
}