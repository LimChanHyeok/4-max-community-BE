package org.example.community.post.service.viewcount;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushScheduler {

    private final ViewCountBuffer viewCountBuffer;
    private final PostViewCountService postViewCountService;


    @Scheduled(fixedDelayString = "${app.view-count.flush-delay-ms:5000}")
    public void flushViewCounts() {
        // DB에 반영할 조회수 증가분을 버퍼에서 꺼냄
        // drain()은 값을 꺼내는 동시에 버퍼를 비운다.
        Map<Long, Long> viewCounts = viewCountBuffer.drain();

        if (viewCounts.isEmpty()) {
            return;
        }

        try {
            // 꺼낸 조회수 증가분을 DB에 반영
            postViewCountService.flush(viewCounts);

        } catch (RuntimeException e) {
            /*
             * drain() 이후 flush에 실패하면 기존 버퍼에 있던 조회수가 다 사라진다.
             * 그래서 flush 실패 시 drain했던 값을 다시 버퍼에 복구
             */
            viewCountBuffer.restore(viewCounts);

            log.warn("조회수 버퍼 flush 실패로 증가분을 다시 버퍼에 복구했습니다. viewCounts={}",
                    viewCounts, e);
        }
    }
}