package org.example.community.image.controller;

import lombok.RequiredArgsConstructor;
import org.example.community.global.response.ApiResponse;
import org.example.community.image.dto.response.ImageUploadResponse;
import org.example.community.image.entity.ImageType;
import org.example.community.image.service.ImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/images")
public class ImageController {

    private final ImageService imageService;

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadPostImage(
            @RequestPart("image") MultipartFile image
    ) {
        ImageUploadResponse response = imageService.uploadImage(
                image,
                ImageType.POST,
                "posts"
        );

        return ResponseEntity.ok(
                ApiResponse.success("게시글 이미지 업로드에 성공했습니다.", response)
        );
    }

    @PostMapping("/profiles")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadProfileImage(
            @RequestPart("image") MultipartFile image
    ) {
        ImageUploadResponse response = imageService.uploadImage(
                image,
                ImageType.USER,
                "profiles"
        );

        return ResponseEntity.ok(
                ApiResponse.success("프로필 이미지 업로드에 성공했습니다.", response)
        );
    }
}