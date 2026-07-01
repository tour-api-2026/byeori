package com.byeori.domain.upload;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedImageRepository extends JpaRepository<UploadedImage, String> {
}
