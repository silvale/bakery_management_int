package com.bakery.api.master.controller;

import com.bakery.api.master.dto.UploadProperties;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.repository.ItemRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ItemImageController {

    private final UploadProperties uploadProps;
    private final ItemRepository itemRepository;

    private Path uploadDir;

    @PostConstruct
    public void init() throws IOException {
        uploadDir = Paths.get(uploadProps.getDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/items/{id}/image")
    public ResponseEntity<?> uploadImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {

        // Validate size
        long maxBytes = (long) uploadProps.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File vượt quá giới hạn " + uploadProps.getMaxSizeMb() + "MB"));
        }

        // Validate extension
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!uploadProps.getAllowedTypes().contains(ext)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Định dạng không được phép. Chấp nhận: " + uploadProps.getAllowedTypes()));
        }

        // Tìm item
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item không tồn tại: " + id));

        // Xóa ảnh cũ nếu có
        deleteOldFile(item.getImageUrl());

        // Lưu file mới
        String fileName = UUID.randomUUID() + "." + ext;
        Path target = uploadDir.resolve(fileName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        // Cập nhật DB
        String imageUrl = "/api/v1/uploads/" + fileName;
        item.setImageUrl(imageUrl);
        itemRepository.save(item);

        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/api/v1/items/{id}/image")
    public ResponseEntity<?> deleteImage(@PathVariable UUID id) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item không tồn tại: " + id));

        deleteOldFile(item.getImageUrl());
        item.setImageUrl(null);
        itemRepository.save(item);

        return ResponseEntity.ok(Map.of("message", "Đã xóa ảnh"));
    }

    // ── Serve (public) ────────────────────────────────────────────────────────

    @GetMapping("/api/v1/uploads/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws MalformedURLException {
        Path filePath = uploadDir.resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = "application/octet-stream";
        try {
            contentType = Files.probeContentType(filePath);
            if (contentType == null) contentType = "application/octet-stream";
        } catch (IOException ignored) {}

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(resource);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private void deleteOldFile(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/api/v1/uploads/")) return;
        String oldFileName = imageUrl.substring("/api/v1/uploads/".length());
        try {
            Files.deleteIfExists(uploadDir.resolve(oldFileName).normalize());
        } catch (IOException ignored) {}
    }
}
