package com.example.project.dto.response;


/**
 * 深度游标分页响应DTO
 * 不返回 total 避免COUNT查询， 通过 hashMore判断是否还有下一页
 */

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "深度游标分页响应")
public class CursorPageResponse<T> {

    @Schema(description = "当前分页数据列表")
    private List<T> records;

    @Schema(description = "下一页游标ID（既本页最后一条记录的ID, 无下一页时为null")
    private Long nextLastId;

    @Schema(description = "是否有下一页")
    private boolean hasMore;
}