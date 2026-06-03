package com.knowledgeforge.system.auth;

import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.core.shared.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 简易认证控制器（演示用途）。
 * 默认账号: admin / admin123
 */
@Slf4j
@RestController
@RequestMapping(SystemConstants.API_V1 + "/auth")
public class AuthController {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ApiResponse.error(400, "用户名和密码不能为空");
        }

        if (DEFAULT_USERNAME.equals(username) && DEFAULT_PASSWORD.equals(password)) {
            String token = UUID.randomUUID().toString();
            log.info("用户 [{}] 登录成功", username);
            return ApiResponse.success(Map.of(
                    "token", token,
                    "username", username,
                    "role", "admin"
            ));
        }

        return ApiResponse.error(401, "用户名或密码错误");
    }

    @GetMapping("/userInfo")
    public ApiResponse<Map<String, Object>> userInfo() {
        return ApiResponse.success(Map.of(
                "username", DEFAULT_USERNAME,
                "role", "admin",
                "avatar", ""
        ));
    }
}