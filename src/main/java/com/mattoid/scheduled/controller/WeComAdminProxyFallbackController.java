package com.mattoid.scheduled.controller;

import com.mattoid.scheduled.service.wecom.WeComAdminSsoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

/**
 * 兜底路由：拦截绕过代理路径重写的请求（如浏览器直接导航到 /wework_admin/**），
 * 重定向到正确的代理路径。仅处理 GET 请求（页面导航），API 请求由拦截脚本处理。
 */
@Slf4j
@Controller
public class WeComAdminProxyFallbackController {

    @RequestMapping("/wework_admin/**")
    public void fallbackWeWorkAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        redirectToProxy(request, response);
    }

    @RequestMapping("/cgi-bin/**")
    public void fallbackCgiBin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        redirectToProxy(request, response);
    }

    private void redirectToProxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String query = request.getQueryString();
        String redirectUrl = WeComAdminSsoService.PROXY_BASE + "/work" + path
                + (StringUtils.hasText(query) ? "?" + query : "");
        log.debug("兜底重定向: {} -> {}", path, redirectUrl);
        response.sendRedirect(redirectUrl);
    }
}
