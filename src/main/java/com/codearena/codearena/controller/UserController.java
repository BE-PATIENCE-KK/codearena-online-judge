package com.codearena.codearena.controller;

import com.codearena.codearena.entity.Result;
import com.codearena.codearena.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/info")
    public Result<String> info(HttpServletRequest request) {

        String token = request.getHeader("Authorization");

        String username = JwtUtil.getUsername(token.substring(7));

        return Result.success(username);
    }
}
