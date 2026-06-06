package org.example.community.image.service;

import lombok.RequiredArgsConstructor;
import org.example.community.global.exception.CustomException;
import org.example.community.global.exception.ErrorCode;
import org.example.community.global.file.dto.FileStoreResult;
import org.example.community.global.file.service.FileStorageService;
import org.example.community.image.dto.response.ImageUploadResponse;
import org.example.community.image.entity.Image;
import org.example.community.image.entity.ImageType;
import org.example.community.image.repository.ImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
// 이미지 url을 만들어서 직접 DB에 저장하는 역할 이때는 reference_id에 null값이 저장되고
// 나중에 포스트나 회원가입이 되었을 때 그때 reference_id가 할당됨
public class ImageService {

    private final FileStorageService fileStorageService;
    private final ImageRepository imageRepository;

    @Transactional
    public ImageUploadResponse uploadImage(
            MultipartFile file,
            ImageType imageType,
            String directory
    ) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        FileStoreResult fileStoreResult = fileStorageService.store(file, directory);

        // 여기선 referenceId를 null로 저장
        Image image = Image.create(
                fileStoreResult.getImageUrl(),
                fileStoreResult.getOriginalFilename(),
                fileStoreResult.getStoredFilename(),
                imageType,
                null
        );

        Image savedImage = imageRepository.save(image);

        return new ImageUploadResponse(
                savedImage.getId(),
                savedImage.getImageUrl()
        );
    }
}