package org.example.community.global.file.service;

import org.example.community.global.file.dto.FileStoreResult;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    FileStoreResult store(MultipartFile file, String directory);
}