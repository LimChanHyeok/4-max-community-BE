package org.example.community.postlike.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostLikeResponse {

    @JsonProperty("post_id")
    private Long postId;

    @JsonProperty("is_liked")
    private boolean liked;

    /**
     * 서버가 DB에서 조회한 다음 프론트에게 알려주기 위해 넣음
     */
    @JsonProperty("like_count")
    private Long likeCount;
}
