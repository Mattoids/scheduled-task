package com.mattoid.scheduled.config;

import com.mattoid.scheduled.common.Result;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.quartz.SchedulerException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthenticationException(AuthenticationException e) {
        return Result.error(401, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDeniedException(AccessDeniedException e) {
        return Result.error(403, "无权限访问");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidationException(Exception e) {
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException ex) {
            message = ex.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ":" + error.getDefaultMessage())
                    .findFirst()
                    .orElse(message);
        }
        return Result.error(400, message);
    }

    @ExceptionHandler(SchedulerException.class)
    public Result<Void> handleSchedulerException(SchedulerException e) {
        log.error("调度异常", e);
        return Result.error("任务调度异常: " + e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpServletRequest request, HttpRequestMethodNotSupportedException e) {
        log.error("请求方法不支持: {} {}", request.getMethod(), request.getRequestURI(), e);
        return Result.error("请求方法不支持: " + request.getMethod() + " " + request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(e.getMessage());
    }
}
