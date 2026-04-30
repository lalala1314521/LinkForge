package com.example.project.common;

/**
 * 全局异常处理器
 * 统一捕获业务异常，参数校验异常和未预期异常，返回标准Result格式
 */

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBusinessException(BusinessException e){
        log.info("业务异常：code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid注解触发）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e){
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.info("参数校验失败： {}", message);
        return Result.error(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * 处理其他未捕获异常（生产环境不暴露堆栈信息）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handlerException(Exception e) {
        log.info("未捕获异常： ", e);
        return Result.error(ErrorCode.INTERNAL_ERROR);
    }
}