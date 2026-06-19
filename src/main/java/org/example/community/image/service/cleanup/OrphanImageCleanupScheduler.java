package org.example.community.image.service.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanImageCleanupScheduler {

    private final OrphanImageCleanupService orphanImageCleanupService;

    // 매일 새벽 3시에 스케줄러 실행
    @Scheduled(
            cron = "${app.image.cleanup.cron:0 0 3 * * *}",
            zone = "Asia/Seoul"
    )
    public void deleteExpiredOrphanImages() {
        try {
            orphanImageCleanupService.deleteExpiredOrphanImages();
        } catch (Exception e) {
            log.error("고아 이미지 정리 스케줄러 실행 중 오류가 발생했습니다.", e);
        }
    }
}