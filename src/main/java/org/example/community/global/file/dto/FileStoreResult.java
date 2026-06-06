package org.example.community.global.file.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
// 기존 이미지 응답과는 달리 orginalFilename과 storedFilename이 추가되어서 DTO를 생성
public class FileStoreResult {

    private String imageUrl;

    private String originalFilename;

    private String storedFilename;
}