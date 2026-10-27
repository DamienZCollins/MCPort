package com.mcport.governance.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要经过治理切面（鉴权上下文、权限、限流、审计、指标）的 MCP 工具方法。
 *
 * <p>不直接对 {@code @McpTool} 做切点：注解扫描器生成的是同一个 bean 上的代理调用，
 * 显式注解能让切点更稳定、也能避免切到框架内部的调用。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GovernedTool {
}
