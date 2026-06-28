package org.example.community.post.service.viewcount;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class InMemoryViewCountBuffer implements ViewCountBuffer {

    // key: 게시글 ID, value: 조회수를 누적하는 LongAdder
    private final ConcurrentHashMap<Long, LongAdder> buffer = new ConcurrentHashMap<>();

    // 게시글이 조회될 때마다 조회수 증가분을 메모리 버퍼에 누적
    @Override
    public void increase(Long postId) {
        buffer.computeIfAbsent(postId, key -> new LongAdder())
                .increment();
    }

    // 스케줄러가 DB에 반영할 조회수 증가분을 꺼낼 때 사용
    // sumThenReset()으로 현재까지 쌓인 값을 꺼내고 해당 카운터를 0으로 초기화
    @Override
    public Map<Long, Long> drain() {
        Map<Long, Long> drained = new HashMap<>();

        buffer.forEach((postId, adder) -> {
            long count = adder.sumThenReset();

            if (count > 0) {
                drained.put(postId, count);
            }
        });

        return drained;
    }

    // DB 반영 실패 시 drain했던 조회수 증가분을 다시 버퍼에 복구 하기 위한 메소드
    // 다음 스케줄러 실행 때 다시 DB 반영을 재시도 하기 위해
    @Override
    public void restore(Map<Long, Long> viewCounts) {
        if (viewCounts == null || viewCounts.isEmpty()) {
            return;
        }

        viewCounts.forEach((postId, count) -> {
            if (postId == null || count == null || count <= 0) {
                return;
            }

            // DB 반영에 실패했을 때 그 count 값을 다시 버퍼에 넣는 것
            buffer.computeIfAbsent(postId, key -> new LongAdder())
                    .add(count);
        });
    }
}