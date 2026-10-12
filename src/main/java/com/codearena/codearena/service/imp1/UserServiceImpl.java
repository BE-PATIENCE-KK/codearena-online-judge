package com.codearena.codearena.service.imp1;

import com.codearena.codearena.entity.User;
import com.codearena.codearena.mapper.UserMapper;
import com.codearena.codearena.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    @Override
    public String register(User user) {

        if (user == null || user.getUsername() == null || user.getUsername().isBlank()
                || user.getPassword() == null || user.getPassword().isBlank()) {
            return "用户名和密码不能为空";
        }

        User dbUser = userMapper.findByUsername(user.getUsername());

        if (dbUser != null) {
            return "用户名已存在";
        }

        user.setRole("USER");
        user.setStatus(1);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        int result = userMapper.insertUser(user);

        if (result > 0) {
            return "注册成功";
        }

        return "注册失败";
    }

    @Override
    public boolean verifyPassword(String username, String rawPassword) {
        if (username == null || rawPassword == null) {
            return false;
        }

        User user = userMapper.findByUsername(username);
        if (user == null || user.getPassword() == null) {
            return false;
        }

        String storedPassword = user.getPassword();
        if (isBcryptHash(storedPassword)) {
            try {
                return passwordEncoder.matches(rawPassword, storedPassword);
            } catch (IllegalArgumentException malformedHash) {
                return false;
            }
        }

        // Backward-compatible migration: only a successful legacy plaintext login is re-hashed.
        boolean matches = MessageDigest.isEqual(
                storedPassword.getBytes(StandardCharsets.UTF_8),
                rawPassword.getBytes(StandardCharsets.UTF_8));
        if (matches) {
            String upgradedHash = passwordEncoder.encode(rawPassword);
            // The condition avoids overwriting a password changed by a concurrent login/update.
            userMapper.updatePasswordHashIfUnchanged(user.getId(), storedPassword, upgradedHash);
        }
        return matches;
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }
}
