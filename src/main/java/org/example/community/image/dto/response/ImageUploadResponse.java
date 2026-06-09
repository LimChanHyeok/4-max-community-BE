package org.example.community.image.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImageUploadResponse {

    // 나중에 게시글 등록할 때 image_id를 주어야 나중에 게시글이나 프로필 등록할 때 이 이미지가 어떤 행인지 알수있음
    @JsonProperty("image_id")
    private Long imageId;

    @JsonProperty("image_url")
    private String imageUrl;
}