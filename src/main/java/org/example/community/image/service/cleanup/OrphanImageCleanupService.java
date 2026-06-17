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
import java.util.ArrayList;
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

        // 실제 파일 삭제에 성공한 이미지 id만 모은다
        // 파일 삭제에 실패한 이미지는 DB row를 남겨서 다음 스케줄러에서 재시도할 수 있게 한다
        List<Long> deletedFileImageIds = new ArrayList<>();

        for (Image image : orphanImages) {
            boolean deleted = fileStorageService.delete(image.getImageUrl());

            if (deleted) {
                deletedFileImageIds.add(image.getId());
            }
        }

        // 실제 파일 삭제에 성공한 이미지가 없으면 DB row도 삭제하지 않는다
        if (deletedFileImageIds.isEmpty()) {
            log.warn("고아 이미지 파일 삭제 성공 건이 없어 DB row 삭제를 건너뜁니다. targetCount={}",
                    orphanImages.size());
            return;
        }

        // 파일 삭제에 성공한 이미지의 DB row만 한 번에 삭제
        int deletedCount = imageRepository.deleteAllByIdInBulk(deletedFileImageIds);

        log.info("고아 이미지 정리 완료. targetCount={}, deletedFileCount={}, deletedRowCount={}",
                orphanImages.size(), deletedFileImageIds.size(), deletedCount);
    }
}