package com.soloeats.common.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在后台写接口上，由 {@link OperationLogAspect} 异步落一条操作日志。
 *
 * <p>日志接口自身（{@code /api/v1/admin/logs/**}）被切面按 URI 前缀排除，避免日志风暴。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 模块名，落到 t_sys_oper_log.module，长度不超过 32。 */
    String module();

    /** 动作名，落到 t_sys_oper_log.action，长度不超过 32。 */
    String action();

    /** 是否记录请求参数摘要；查询类或大对象入参可关闭。 */
    boolean recordParams() default true;
}
