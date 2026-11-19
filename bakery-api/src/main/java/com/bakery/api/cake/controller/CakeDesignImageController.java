package com.bakery.api.cake.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.cake.entity.CakeDesign;
import com.bakery.api.cake.repository.CakeDesignRepository;
import com.bakery.api.master.dto.UploadProperties;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.security.RequirePermission;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Ảnh mẫu bánh — cùng thư mục upload và cùng URL {@code /api/v1/uploads/...} với ảnh item
 * ({@code ItemImageController} phục vụ file).
 */
@RestController
@RequiredArgsConstructor
public class CakeDesignImageController {

    private static final String URL_PREFIX = "/api/v1/uploads/";

    private final UploadProperties uploadProps;
    private final CakeDesignRepository repository;

    private Path uploadDir;

    @PostConstruct
    public void init() throws IOException {
        uploadDir = Paths.get(uploadProps.getDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    @PostMapping("/api/v1/cake-designs/{id}/image")
    @RequirePermission(screen = "CAKE_DESIGN", action = "UPDATE")
    @Transactional
    public ResponseEntity<?> upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        CakeDesign design = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CakeDesign", id));

        long maxBytes = (long) uploadProps.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File vượt quá giới hạn " + uploadProps.getMaxSizeMb() + "MB"));
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!uploadProps.getAllowedTypes().contains(ext)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Định dạng không được phép. Chấp nhận: " + uploadProps.getAllowedTypes()));
        }

        deleteFile(design.getImageUrl());
        String fileName = UUID.randomUUID() + "." + ext;
        Files.copy(file.getInputStream(), uploadDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        design.setImageUrl(URL_PREFIX + fileName);
        repository.save(design);
        return ResponseEntity.ok(Map.of("imageUrl", design.getImageUrl()));
    }

    @DeleteMapping("/api/v1/cake-designs/{id}/image")
    @RequirePermission(screen = "CAKE_DESIGN", action = "UPDATE")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        CakeDesign design = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CakeDesign", id));
        deleteFile(design.getImageUrl());
        design.setImageUrl(null);
        repository.save(design);
        return ResponseEntity.ok(Map.of("message", "Đã xóa ảnh"));
    }

    private void deleteFile(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(URL_PREFIX)) return;
        try {
            Files.deleteIfExists(uploadDir.resolve(imageUrl.substring(URL_PREFIX.length())).normalize());
        } catch (IOException ignored) {}
    }
}
