package org.example.community.image.repository;

import org.example.community.image.entity.Image;
import org.example.community.image.entity.ImageType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ImageRepository extends JpaRepository<Image, Long> {

    // 기존 연결 이미지 조회용
    Optional<Image> findByImageTypeAndReferenceId(ImageType imageType, Long referenceId);
}