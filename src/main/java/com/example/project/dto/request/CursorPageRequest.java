package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Data;

/**
 * 游标分页请求DTO
 * 当offer > 1000 时，改用 WHERE id > lastId ORDER BY id LIMIT size 方式
 */
@Data
@Schema(description = "游标分页请求，深分页优化，替代DFFSET分页")
public class CursorPageRequest {

    @Schema(description = "游标ID， 第一页传ID或不传", example = "0", defaultValue = "0")
    private Long lastId = 0L;

    @Min(value = 1, message = "每页最少为1条")
    @Max(value = 100, message = "每页最多100条")
    @Schema(description = "每页大小（1-100）", example = "20", defaultValue = "20")
    private int size = 20;
}