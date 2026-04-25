package  com.example.project.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;


/**
 * 统一响应结果包装类
 */
@Getter
@Schema(description = "统一响应结果")
public class Result<T> {

    @Schema(description = "响应码，200表示成功", example = "200")
    private final int code;

    @Schema(description = "响应消息", example = "操作成功")
    private final  String message;

    @Schema(description = "响应数据")
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static Result<Void> success(){
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static Result<Void> error(ErrorCode errorCode){
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static Result<Void> error(int code, String message){
        return new Result<>(code, message, null);
    }

    public static  Result<Void> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

}

