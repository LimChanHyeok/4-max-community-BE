package org.example.community.image.service.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.community.global.file.service.FileStorageService;
import org.example.community.image.entity.Image;
import org.example.community.image.repository.ImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanImageCleanupService {

    private final ImageRepository imageRepository;
    private final FileStorageService fileStorageService;

    @Value("${app.image.orphan-retention-hours:24}")
    private long orphanRetentionHours;

    @Transactional
    public void deleteExpiredOrphanImages() {
        // 현재 시간 기준으로 보관 시간을 지난 이미지 기준 시각 계산
        // 24시간보다 오래된 고아 이미지 삭제
        LocalDateTime cutoff = LocalDateTime.now().minusHours(orphanRetentionHours);

        // referenceId가 null이고 createdAt이 cutoff보다 오래된 이미지 조회
        List<Image> orphanImages =
                imageRepository.findAllByReferenceIdIsNullAndUpdatedAtBefore(cutoff);

        if (orphanImages.isEmpty()) {
            return;
        }

        // 실제 업로드 폴더에 있는 파일 삭제능
        // 파일은 각각 하나씩 삭제해야함
        for (Image image : orphanImages) {
            fileStorageService.delete(image.getImageUrl());
        }

        // DB에서 삭제할 images.id 목록 추출
        List<Long> imageIds = orphanImages.stream()
                .map(Image::getId)
                .toList();

        // images 테이블 row를 한 번에 삭제
        int deletedCount = imageRepository.deleteAllByIdInBulk(imageIds);

        log.info("고아 이미지 정리 완료. fileCount={}, deletedRowCount={}",
                orphanImages.size(), deletedCount);
    }
}