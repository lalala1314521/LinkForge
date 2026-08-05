package com.example.project.controller;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传接口（ADMIN-only，SecurityConfig /api/files/upload）
 * 仅支持 jpg/jpeg/png/webp 图片，≤5MB，落盘至 file.upload-dir（默认 ./uploads），返回相对 URL /uploads/&lt;文件名&gt;
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@Tag(name = "文件上传", description = "本地图片上传（ADMIN）")
public class FileController {

    /** 允许的图片扩展名白名单（小写） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    /** 允许的 Content-Type 白名单 */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");
    /** 上传大小上限：5MB */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    @Operation(summary = "上传图片", description = "仅支持 jpg/jpeg/png/webp，≤5MB；返回相对 URL /uploads/&lt;文件名&gt;",
            security = @SecurityRequirement(name = "Bearer"))
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
            ext = originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }

        // 类型白名单：扩展名 + Content-Type 双重校验
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext) || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 jpg/jpeg/png/webp 图片");
        }

        // 大小校验（multipart 配置 max-file-size 5MB 兜底）
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小不能超过 5MB");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target.toFile());
            log.info("文件上传成功：filename={}, size={}B, dir={}", filename, file.getSize(), dir);
        } catch (IOException e) {
            log.error("文件保存失败：filename={}", filename, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }

        return Result.success("/uploads/" + filename);
    }
}
