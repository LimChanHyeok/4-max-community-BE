package org.example.community.post.service.viewcount;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.community.post.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// 사용자 요청처럼 에러 응답을 줄 대상이 없기 때문에 로그에 남기기 위한 어노테이션
@Slf4j
@Service
@RequiredArgsConstructor
public class PostViewCountService {

    private final PostRepository postRepository;

    // 버퍼에 있는걸 flush해서 DB에 반영하는 메소드
    @Transactional
    public void flush(Map<Long, Long> viewCounts) {
        viewCounts.forEach((postId, count) -> {
            int updatedCount = postRepository.increaseViewCountBy(postId, count);

            if (updatedCount == 0) {
                log.warn("조회수 반영 실패 - 존재하지 않는 게시글입니다. postId={}, count={}", postId, count);
            }
        });
    }
}