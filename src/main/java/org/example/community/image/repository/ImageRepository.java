package org.example.community.image.repository;

import org.example.community.image.entity.Image;
import org.example.community.image.entity.ImageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ImageRepository extends JpaRepository<Image, Long> {

    // 기존 연결 이미지 조회용
    Optional<Image> findByImageTypeAndReferenceId(ImageType imageType, Long referenceId);

    // 연관관계는 이용하지 않기 때문에 JPA 메소드 활용 가능
    List<Image> findAllByReferenceIdIsNullAndUpdatedAtBefore(LocalDateTime cutoff);

    // DB row를 한번에 지우는 메소드
    // 조회가 아닌 삭제쿼리 이기 때문에 @Modifying 추가
    // DB 쿼리를 한번만 날리기 위해
    @Modifying
    @Query("delete from Image i where i.id in :ids")
    int deleteAllByIdInBulk(List<Long> ids);
}