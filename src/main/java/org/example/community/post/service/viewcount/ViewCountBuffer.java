package org.example.community.post.service.viewcount;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ViewCountBuffer {

    // key : 게시글 ID , value : 조회수를 누적하는 LongAdder
    private final ConcurrentHashMap<Long, LongAdder> buffer = new ConcurrentHashMap<>();

    // 게시글이 조회될 때마다 호출
    public void increase(Long postId) {
        buffer.computeIfAbsent(postId, key -> new LongAdder())
                .increment();
    }

    // 스케줄러가 DB에 반영할 조회수를 꺼낼 때 사용
    public Map<Long, Long> drain() {
        Map<Long, Long> drained = new HashMap<>();

        // 버퍼에서 조회수를 꺼내고 sumThenReset()으로 0으로 초기화
        buffer.forEach((postId, adder) -> {
            long count = adder.sumThenReset();

            // 반영할 애들만 뽑아서 return
            if (count > 0) {
                drained.put(postId, count);
            }
        });

        return drained;
    }
}
