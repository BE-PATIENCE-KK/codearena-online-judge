
package com.codearena.codearena.controller;
import com.codearena.codearena.utils.JwtUtil;

import com.codearena.codearena.entity.User;
import com.codearena.codearena.entity.Result;
import com.codearena.codearena.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<Result<String>> login(@RequestBody User user) {

        if (user.getUsername() == null || user.getPassword() == null
                || !userService.verifyPassword(user.getUsername(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.error(401, "用户名或密码错误"));
        }

        return ResponseEntity.ok(Result.success(JwtUtil.createToken(user.getUsername())));
    }
    @PostMapping("/register")
    public ResponseEntity<Result<String>> register(@RequestBody User user) {
        String message = userService.register(user);
        if ("注册成功".equals(message)) {
            return ResponseEntity.ok(Result.success(message));
        }
        return ResponseEntity.badRequest().body(Result.error(400, message));
    }
}
