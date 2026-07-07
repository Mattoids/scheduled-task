package com.mattoid.scheduled.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationAudit {

    String operationType();

    String resourceType();

    /**
     * 是否忽略请求参数中可能包含的敏感字段（如 password、secret 等）
     */
    boolean ignoreSensitive() default true;
}
