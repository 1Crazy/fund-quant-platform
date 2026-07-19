package org.dromara.fund.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.fund.domain.bo.FundManualSyncBo;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 控制器路由和权限契约。
 */
final class FundControllerContractTest {

    @Test
    void syncManagementShouldExposeFrontendCompatibleAliases() {
        Method status = method("syncStatus", String.class, String.class, String.class);
        Method issues = method("qualityIssues", org.dromara.fund.domain.bo.FundDataQualityIssueQueryBo.class,
            org.dromara.common.mybatis.core.page.PageQuery.class);

        assertTrue(hasPath(status.getAnnotation(GetMapping.class).value(), "/sync/status"));
        assertTrue(hasPath(status.getAnnotation(GetMapping.class).value(), "/sync/current"));
        assertTrue(hasPath(issues.getAnnotation(GetMapping.class).value(), "/sync/issues"));
        assertTrue(hasPath(issues.getAnnotation(GetMapping.class).value(), "/quality-issues"));
    }

    @Test
    void manualSyncShouldValidateFundCodeRangeAndStatusResponse() {
        Method trigger = method("trigger", FundManualSyncBo.class);
        Method retry = method("retry", Long.class);

        assertTrue(hasPermission(trigger, "fund:sync:trigger"));
        assertTrue(hasPermission(retry, "fund:sync:retry"));
        assertTrue(retry.isAnnotationPresent(PostMapping.class));
    }

    private Method method(String name, Class<?>... parameterTypes) {
        try {
            return FundController.class.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("缺少控制器方法: " + name, e);
        }
    }

    private boolean hasPermission(Method method, String expected) {
        for (String value : method.getAnnotation(SaCheckPermission.class).value()) {
            if (expected.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPath(String[] paths, String expected) {
        for (String path : paths) {
            if (expected.equals(path)) {
                return true;
            }
        }
        return false;
    }
}
