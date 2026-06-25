package org.example.community.post.service.viewcount;

import java.util.Map;

public interface ViewCountBuffer {

    // 게시글 조회 시 조회수 증가분을 버퍼에 누적
    void increase(Long postId);

    // DB에 반영할 조회수 증가분을 꺼내고 버퍼를 비움
    Map<Long, Long> drain();

    // DB 반영 실패 시 drain했던 조회수 증가분을 다시 버퍼에 복구
    void restore(Map<Long, Long> viewCounts);
}