package org.example.community.global.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.global.file.dto.FileStoreResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.example.community.global.file.validator.ImageFileValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 나중에 배포환경에서를 위해 인터페이스를 만들고 local이라고 명시하였음
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Profile({"local","test"})
public class LocalFileStorageService implements FileStorageService {

    private final ImageFileValidator imageFileValidator;


    @Value("${app.upload.base-dir}")
    private String baseDir;

    @Value("${app.upload.base-url}")
    private String baseUrl;

    // 현재는 직접 값을 넣지만 directory가 많아진다면 설정값으로 분류
    private static final Set<String> ALLOWED_DIRECTORIES = Set.of("posts", "profiles");

    @Override
    public FileStoreResult store(MultipartFile file, String directory) {

        // 파일 자체 검증
        imageFileValidator.validate(file);


        try {

            /**
             * 여기서 업로드 경로 생성
             * 기존에는 검증 없이 만들었지만 resloveUploadPath를 만들어 검증 후 생성
             */
            Path uploadPath = resolveUploadPath(directory);


            /**
             * 폴더가 있는지 확인, 없으면 폴더 만듬
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
            String imageUrl = baseUrl + "/" + directory + "/" + storedFilename;

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

    /*
     * 업로드할 directory를 검증하고 실제 업로드 경로를 만든다
     * 지금은
     */
    private Path resolveUploadPath(String directory) {
        if (directory == null || directory.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_DIRECTORY);
        }

        // 공백 제거
        String normalizedDirectory = directory.trim();

        /*
         * 현재 프로젝트에서 허용하는 이미지 저장 폴더만 허용한다.
         * 나중에 banners, thumbnails 등이 생기면 ALLOWED_DIRECTORIES에 추가 (나중에 폴더가 많아지면 설정값에 추가)
         */
        if (!ALLOWED_DIRECTORIES.contains(normalizedDirectory)) {
            throw new CustomException(ErrorCode.INVALID_IMAGE_DIRECTORY);
        }

        // 업로드 루트 경로
        // local 예: uploads
        Path basePath = Paths.get(baseDir)
                .toAbsolutePath()
                .normalize();

        // 실제 업로드 폴더 경로
        // 예: uploads/posts, uploads/profiles
        Path uploadPath = basePath.resolve(normalizedDirectory)
                .normalize();

        /*
         * directory에 ../ 같은 값이 들어와서
         * baseDir 밖으로 나가는 것을 방지한다.
         */
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
        // . 부터 끝까지 잘라냄(lastIndexOF 이기 때문에 마지막 점을 기준으로)
        return originalFilename.substring(originalFilename.lastIndexOf("."));
    }

    @Override
    public boolean delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return true;
        }

        if (!imageUrl.startsWith(baseUrl + "/")) {
            log.warn("삭제할 수 없는 이미지 URL 형식입니다. imageUrl={}", imageUrl);
            return false;
        }

        try {
            // uploads폴더까지의 경로를 절대 경로로 만드는 것
            // /Users/max/Desktop/kakao-personal_project/uploads 이런식
            Path basePath = Paths.get(baseDir)
                    .toAbsolutePath()
                    .normalize();

            // 여기서 imgaeUrl에서 /uploads부분 제거
            // 만약 /uploads/posts/test.png면 -> /posts/test.png로 결과가 나옴
            String relativePath = imageUrl.substring(baseUrl.length());

            // 여기서 앞에 /를 제거하는 이유는 /가 있으면 루트 경로처럼 해석될 수 있기 때문
            // 따라서 resolve를 해서 basePath에 붙이면 최종적으로 삭제할 실제 파일 경로가 완성됨
            Path filePath = basePath.resolve(relativePath.substring(1))
                    .normalize();

            // upload/../이런식의 값이 들어가있으면 baseDir밖으로 나가면서 삭제 실패
            if (!filePath.startsWith(basePath)) {
                log.warn("업로드 경로 밖의 파일 삭제 시도가 감지되었습니다. imageUrl={}", imageUrl);
                return false;
            }

            Files.deleteIfExists(filePath);

            return true;

        } catch (IOException e) {
            log.warn("이미지 파일 삭제에 실패했습니다. imageUrl={}", imageUrl, e);
            return false;
        }
    }
}