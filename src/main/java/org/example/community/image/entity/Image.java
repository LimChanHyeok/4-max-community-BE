package org.example.community.image.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    //@Enumerated -> enum을 DB에 저장할 때 문자열로 저장하라는 어노테이션
    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false, length = 50)
    private ImageType imageType;

    @Column(name = "reference_id")
    private Long referenceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // create를 하기 위한 생성자 private로 감싸줘서 create에서만 생성할 수 있게 하였다.
    private Image(
            String imageUrl,
            String originalFilename,
            String storedFilename,
            ImageType imageType,
            Long referenceId
    ) {
        this.imageUrl = imageUrl;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.imageType = imageType;
        this.referenceId = referenceId;
    }

    public static Image create(
            String imageUrl,
            String originalFilename,
            String storedFilename,
            ImageType imageType,
            Long referenceId
    ) {
        return new Image(imageUrl, originalFilename, storedFilename, imageType, referenceId);
    }

    // 이미지 업로드 후 나중에 생성된 userId또는 postIdfmf referenceId에 넣기 위해 사용함
    public void connectReference(Long referenceId) {
        this.referenceId = referenceId;
    }

    public void disconnectReference() {
        this.referenceId = null;
    }
}