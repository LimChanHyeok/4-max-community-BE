package org.example.community.post.service.viewcount;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ViewCountFlushScheduler {

    private final ViewCountBuffer viewCountBuffer;
    private final PostViewCountService postViewCountService;


    // 스케쥴러로 5초마다 실행한다는 의미
    @Scheduled(fixedDelay = 5000)
    public void flushViewCounts() {
        // DB에 반영할 버퍼에 있는 조회수를 꺼냄
        Map<Long, Long> viewCounts = viewCountBuffer.drain();

        if (viewCounts.isEmpty()) {
            return;
        }
        // 꺼낸 걸 flush
        postViewCountService.flush(viewCounts);
    }
}