package com.learnagent.config;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * 便携版由 Spring Boot 直接托管 Vue 构建产物（frontend/dist），前端使用 History 路由。
 * 直接刷新 /dashboard、/tasks 这类站内地址时后端没有对应资源，会走到 /error；
 * 这里对「非 /api、无扩展名、浏览器页面请求」的 404 回退到 index.html，交给前端路由处理。
 * 其它错误保持 Spring Boot 默认的 JSON 结构。
 */
@Controller
public class SpaErrorController implements ErrorController {
  private final ErrorAttributes errorAttributes;

  public SpaErrorController(ErrorAttributes errorAttributes) {
    this.errorAttributes = errorAttributes;
  }

  @RequestMapping("/error")
  public Object handleError(HttpServletRequest request, HttpServletResponse response) {
    Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    String uri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    int status = statusAttribute == null ? 500 : Integer.parseInt(statusAttribute.toString());
    String accept = request.getHeader("Accept") == null ? "" : request.getHeader("Accept");

    boolean pageRequest = status == 404
        && uri != null
        && !uri.startsWith("/api")
        && uri.indexOf('.') < 0
        && accept.contains("text/html");

    if (pageRequest) {
      response.setStatus(HttpServletResponse.SC_OK);
      return "forward:/index.html";
    }

    Map<String, Object> body = errorAttributes.getErrorAttributes(
        new ServletWebRequest(request), ErrorAttributeOptions.defaults());
    return ResponseEntity.status(status).body(body);
  }
}
