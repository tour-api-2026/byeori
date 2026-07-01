package com.byeori.domain.upload;

import com.byeori.global.exception.BadRequestException;
import com.byeori.global.response.ApiResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 업로드/서빙. 업로드본은 DB(BYTEA)에 저장하고,
 * 반환 URL(/api/v1/uploads/images/{id})로 다시 내려준다.
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadedImageRepository repository;

    public UploadController(UploadedImageRepository repository) {
        this.repository = repository;
    }

    /** 업로드 응답: 저장된 이미지를 가리키는 (호스트 제외) 경로. 프론트가 API origin을 붙여 절대 URL로 사용. */
    public record UploadResponse(String url) {}

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResponse> upload(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("EMPTY_FILE", "업로드할 파일이 없습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("INVALID_TYPE", "이미지 파일만 업로드할 수 있습니다.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BadRequestException("READ_FAILED", "파일을 읽지 못했습니다.");
        }
        String id = UUID.randomUUID().toString().replace("-", "");
        repository.save(UploadedImage.of(id, contentType, bytes, userId));
        return ApiResponse.ok(new UploadResponse("/api/v1/uploads/images/" + id));
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<byte[]> get(@PathVariable("id") String id) {
        return repository.findById(id)
                .map(img -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(img.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                        .body(img.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
