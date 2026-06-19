package org.example.community.image.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.community.global.file.dto.FileStoreResult;
import org.example.community.global.file.service.FileStorageService;
import org.example.community.image.dto.response.ImageUploadResponse;
import org.example.community.image.entity.Image;
import org.example.community.image.entity.ImageType;
import org.example.community.image.repository.ImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
// 이미지 url을 만들어서 직접 DB에 저장하는 역할 이때는 reference_id에 null값이 저장되고
// 나중에 포스트나 회원가입이 되었을 때 그때 reference_id가 할당됨
public class ImageService {

    private final FileStorageService fileStorageService;
    private final ImageRepository imageRepository;

    @Transactional
    public ImageUploadResponse uploadImage(
            MultipartFile file,
            ImageType imageType,
            String directory
    ) {
        // 파일 검증은 store내부에서 진행
        FileStoreResult fileStoreResult = fileStorageService.store(file, directory);

        registerRollbackFileDelete(fileStoreResult);

        // 여기선 referenceId를 null로 저장
        Image image = Image.create(
                fileStoreResult.getImageUrl(),
                fileStoreResult.getOriginalFilename(),
                fileStoreResult.getStoredFilename(),
                imageType,
                null
        );

        Image savedImage = imageRepository.save(image);

        return new ImageUploadResponse(
                savedImage.getId(),
                savedImage.getImageUrl()
        );
    }


    /**
     * 현재 @Transactional 작업이 rollback 되었을 때 실행할 삭제 작업 등록
     * 이 함수가 호출되는 순간에 파일을 삭제하는 것이 아닌 트랜잭션이 나중에 rollback되면 이 파일을 삭제하라는 예약 함수
     */
    private void registerRollbackFileDelete(FileStoreResult fileStoreResult) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            /*
             * afterCompletion은 트랜잭션이 완전히 끝난 뒤 호출된다.
             *
             * status 값으로 트랜잭션이 commit되었는지 rollback되었는지 알 수 있다.
             */
            @Override
            public void afterCompletion(int status) {

                /**
                 * DB 트랜잭션이 rollback된 경우에만 실제 파일을 삭제
                 */
                if (status == STATUS_ROLLED_BACK) {
                    boolean deleted = fileStorageService.delete(fileStoreResult.getImageUrl());

                    /**
                     * 파일 삭제에 실패해도 여기서 예외를 다시 던지지않음
                     * 이미 트랜잭션이 끝났기 때문에 로그를 남겨주는 것
                     */
                    if (!deleted) {
                        log.warn("트랜잭션 롤백 후 이미지 파일 보상 삭제에 실패했습니다. imageUrl={}",
                                fileStoreResult.getImageUrl());
                    }
                }
            }
        });
    }
}