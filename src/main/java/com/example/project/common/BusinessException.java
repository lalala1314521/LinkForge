package  com.example.project.common;

import  lombok.Getter;

/**
 * 业务异常基类
 */

@Getter
public class BusinessException extends  RuntimeException {
    private final ErrorCode errorCode;

    public  BusinessException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}