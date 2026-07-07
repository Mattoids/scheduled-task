package com.mattoid.scheduled.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.OperationAuditLog;
import com.mattoid.scheduled.service.OperationAuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.*;

@Slf4j
@Aspect
@Component
public class OperationAuditAspect {

    private static final List<String> SENSITIVE_KEYS = List.of("password", "secret", "token", "privatekey", "passphrase", "aeskey");

    private final OperationAuditLogService operationAuditLogService;
    private final ObjectMapper objectMapper;

    public OperationAuditAspect(OperationAuditLogService operationAuditLogService,
                                ObjectMapper objectMapper) {
        this.operationAuditLogService = operationAuditLogService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(com.mattoid.scheduled.audit.OperationAudit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationAudit annotation = method.getAnnotation(OperationAudit.class);

        OperationAuditLog auditLog = new OperationAuditLog();
        auditLog.setOperator(resolveOperator());
        auditLog.setOperationType(annotation.operationType());
        auditLog.setResourceType(annotation.resourceType());
        auditLog.setRequestUri(resolveRequestUri());
        auditLog.setRequestMethod(resolveRequestMethod());
        auditLog.setRequestParams(resolveRequestParams(joinPoint, annotation.ignoreSensitive()));
        auditLog.setIpAddress(resolveIpAddress());

        Object result;
        try {
            result = joinPoint.proceed();
            auditLog.setStatus("SUCCESS");
        } catch (Throwable t) {
            auditLog.setStatus("FAILED");
            auditLog.setErrorMessage(truncate(t.getMessage(), 1000));
            saveAuditLog(auditLog);
            throw t;
        }
        saveAuditLog(auditLog);
        return result;
    }

    private void saveAuditLog(OperationAuditLog auditLog) {
        try {
            operationAuditLogService.save(auditLog);
        } catch (Exception e) {
            log.error("保存操作审计日志失败", e);
        }
    }

    private String resolveOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "anonymous";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user.getUsername();
        }
        if (principal instanceof String username) {
            return username;
        }
        return principal.toString();
    }

    private String resolveRequestUri() {
        HttpServletRequest request = getCurrentRequest();
        return request != null ? request.getRequestURI() : null;
    }

    private String resolveRequestMethod() {
        HttpServletRequest request = getCurrentRequest();
        return request != null ? request.getMethod() : null;
    }

    private String resolveIpAddress() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip)) {
            ip = request.getRemoteAddr();
        }
        if (StringUtils.hasText(ip) && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String resolveRequestParams(ProceedingJoinPoint joinPoint, boolean ignoreSensitive) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            Map<Integer, Object> paramMap = new HashMap<>();
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    continue;
                }
                if (ignoreSensitive && isSensitiveType(arg)) {
                    paramMap.put(i, "***");
                } else if (ignoreSensitive) {
                    paramMap.put(i, sanitizeValue(arg));
                } else {
                    paramMap.put(i, arg);
                }
            }
            return objectMapper.writeValueAsString(paramMap);
        } catch (Exception e) {
            log.warn("序列化审计请求参数失败", e);
            return null;
        }
    }

    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Map<?, ?> map) {
                Map<Object, Object> sanitized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object key = entry.getKey();
                    Object val = entry.getValue();
                    if (key != null && isSensitiveKey(key.toString())) {
                        sanitized.put(key, "***");
                    } else {
                        sanitized.put(key, sanitizeValue(val));
                    }
                }
                return sanitized;
            }
            if (value instanceof Collection<?> collection) {
                List<Object> sanitized = new ArrayList<>();
                for (Object item : collection) {
                    sanitized.add(sanitizeValue(item));
                }
                return sanitized;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                return value;
            }
            // Treat as POJO: convert to Map and sanitize
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.convertValue(value, Map.class);
            return sanitizeValue(map);
        } catch (Exception e) {
            log.warn("脱敏审计参数失败: {}", value.getClass().getSimpleName(), e);
            return value;
        }
    }

    private boolean isSensitiveKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String lower = key.toLowerCase().replaceAll("[^a-z0-9]", "");
        return SENSITIVE_KEYS.stream().anyMatch(lower::contains);
    }

    private boolean isSensitiveType(Object arg) {
        String className = arg.getClass().getSimpleName().toLowerCase();
        return SENSITIVE_KEYS.stream().anyMatch(className::contains);
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
