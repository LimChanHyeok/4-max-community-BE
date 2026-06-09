package org.example.community.post.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * response 응답에서 writer에 해당하는 부분
 * 이 객체를 PostSummaryResponse(게시글 목록 카드용 요약 정보)가 감싸서 응답
 * @AllArgsConstructor -> 클래스의 모든 필드를 받는 생성자를 자동으로 만들어줌
 * PostWriterResponse writer = new PostWriterResponse(
 *         rs.getLong("user_id"),
 *         rs.getString("nickname"),
 *         rs.getString("profile_image")
 * );
 * 이런식으로 값을 한번에 넣어 객체를 만들기 위해서 사용
 */
@Getter
@AllArgsConstructor
public class PostWriterResponse {

    @JsonProperty("user_id")
    private Long userId;

    private String nickname;

    @JsonProperty("profile_image_url")
    private String profileImageUrl;
}