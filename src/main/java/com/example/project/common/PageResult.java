package  com.example.project.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

/**
 * 分页查询结果包装类
 */
@Getter
@Schema(description = "分页查询结果")
public class PageResult<T> {

    @Schema(description = "当前页数据列表")
    private final List<T> records;

    @Schema(description = "总记录数", example = "100")
    private final long total;

    @Schema(description = "当前页码", example = "1")
    private final int page;

    @Schema(description = "每页大小", example = "20")
    private final int size;

    @Schema(description = "总页数", example = "5")
    private final int totalPages;

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = (int)Math.ceil((double) total / size);
    }
}