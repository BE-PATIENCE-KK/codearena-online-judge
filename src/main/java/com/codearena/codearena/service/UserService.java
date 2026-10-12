package com.codearena.codearena.service;

import com.codearena.codearena.entity.User;

public interface UserService {

    User findByUsername(String username);
    String register(User user);
    boolean verifyPassword(String username, String rawPassword);

}
