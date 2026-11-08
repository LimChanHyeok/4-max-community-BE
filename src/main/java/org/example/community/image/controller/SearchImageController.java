package org.example.community.image.controller;

import lombok.RequiredArgsConstructor;
import org.example.community.image.repository.ImageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

@Profile("search")
@RestController
@RequiredArgsConstructor
public class SearchImageController {

    private final ImageRepository imageRepository;
    private final S3Presigner s3Presigner;

    @Value("${app.aws.s3.bucket}")
    private String bucketName;

    @GetMapping("/images/public/{directory}/{filename}")
    public ResponseEntity<Void> getImage(@PathVariable String directory, @PathVariable String filename) {
        if (!"posts".equals(directory) && !"profiles".equals(directory)) {
            return ResponseEntity.notFound().build();
        }

        String key = directory + "/" + filename;
        // 검색 DB에 등록된 객체만 제공해 공유 버킷의 다른 파일을 보호한다.
        if (!imageRepository.existsByImageUrl(key)) {
            return ResponseEntity.notFound().build();
        }

        URI signedUrl = URI.create(s3Presigner.presignGetObject(request -> request
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(object -> object.bucket(bucketName).key(key)))
                .url().toExternalForm());

        return ResponseEntity.status(HttpStatus.FOUND)
                .cacheControl(CacheControl.noStore())
                .location(signedUrl)
                .build();
    }
}
