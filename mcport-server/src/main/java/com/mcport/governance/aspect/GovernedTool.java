package com.mcport.governance.aspect;

import com.mcport.governance.auth.ApiKeyPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要经过治理切面（审计、指标）的 MCP 工具方法，并声明调用它所需的权限。
 *
 * <p>不直接对 {@code @McpTool} 做切点：注解扫描器生成的是同一个 bean 上的代理调用，
 * 显式注解能让切点更稳定、也能避免切到框架内部的调用。
 *
 * <p>权限在这里声明而不是维护一份独立的名字映射表：新增工具必然要写本注解，
 * 漏写时默认按 WRITE 处理（失败关闭），不会出现「新写入工具被只读 Key 调用」的情况。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GovernedTool {

    /**
     * 调用该工具所需的权限，取值见 {@link ApiKeyPrincipal}。
     */
    String permission() default ApiKeyPrincipal.WRITE;
}
