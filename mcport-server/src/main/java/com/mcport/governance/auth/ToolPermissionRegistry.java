package com.mcport.governance.auth;

import com.mcport.governance.aspect.GovernedTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 从 {@code @McpTool} 方法上的 {@link GovernedTool#permission()} 建立
 * 工具名 → 所需权限的映射。
 *
 * <p>按 bean 定义逐个取类型，不实例化 bean。未登记的工具在 {@code tools/call} 时
 * 按拒绝处理，避免新增工具漏配权限后默认放行。
 */
@Component
public class ToolPermissionRegistry implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ToolPermissionRegistry.class);

    private final ApplicationContext applicationContext;
    private volatile Map<String, String> requiredPermissions = Map.of();

    public ToolPermissionRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, String> permissions = new LinkedHashMap<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> type = typeOf(beanName);
            if (type == null) {
                continue;
            }
            collect(ClassUtils.getUserClass(type), permissions);
        }
        requiredPermissions = Collections.unmodifiableMap(permissions);
        log.info("工具权限登记完成：{}", requiredPermissions);
    }

    private Class<?> typeOf(String beanName) {
        try {
            return applicationContext.getType(beanName);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void collect(Class<?> type, Map<String, String> permissions) {
        for (Method method : type.getMethods()) {
            McpTool tool = AnnotationUtils.findAnnotation(method, McpTool.class);
            if (tool == null) {
                continue;
            }
            GovernedTool governed = AnnotationUtils.findAnnotation(method, GovernedTool.class);
            if (governed == null) {
                log.warn("工具 {} 缺少 @GovernedTool，调用将被拒绝（{}#{}）",
                        tool.name(), type.getSimpleName(), method.getName());
                continue;
            }
            permissions.put(tool.name(), governed.permission());
        }
    }

    public Optional<String> required(String toolName) {
        return Optional.ofNullable(requiredPermissions.get(toolName));
    }

    public Map<String, String> registered() {
        return requiredPermissions;
    }
}
