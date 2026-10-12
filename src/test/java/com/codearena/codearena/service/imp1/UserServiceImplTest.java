package com.codearena.codearena.service.imp1;

import com.codearena.codearena.entity.User;
import com.codearena.codearena.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserServiceImpl service = new UserServiceImpl(userMapper);

    @Test
    void registrationStoresBcryptHash() {
        when(userMapper.findByUsername("new-user")).thenReturn(null);
        when(userMapper.insertUser(any(User.class))).thenAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            assertNotEquals("clear-text-test-value", inserted.getPassword());
            assertTrue(encoder.matches("clear-text-test-value", inserted.getPassword()));
            assertTrue(inserted.getPassword().startsWith("$2"));
            return 1;
        });

        User request = user("new-user", "clear-text-test-value");
        assertEquals("注册成功", service.register(request));
        assertNotEquals("clear-text-test-value", request.getPassword());
    }

    @Test
    void verifiesBcryptLoginAndRejectsWrongPassword() {
        User stored = user("bcrypt-user", encoder.encode("correct-value"));
        when(userMapper.findByUsername("bcrypt-user")).thenReturn(stored);

        assertTrue(service.verifyPassword("bcrypt-user", "correct-value"));
        assertFalse(service.verifyPassword("bcrypt-user", "incorrect-value"));
        verify(userMapper, never()).updatePasswordHashIfUnchanged(any(), any(), any());
    }

    @Test
    void upgradesLegacyPlaintextOnlyAfterSuccessfulLogin() {
        User legacy = user("legacy-user", "legacy-value");
        legacy.setId(42L);
        when(userMapper.findByUsername("legacy-user")).thenReturn(legacy);
        when(userMapper.updatePasswordHashIfUnchanged(eq(42L), eq("legacy-value"), any())).thenReturn(1);

        assertFalse(service.verifyPassword("legacy-user", "wrong-value"));
        verify(userMapper, never()).updatePasswordHashIfUnchanged(any(), any(), any());

        assertTrue(service.verifyPassword("legacy-user", "legacy-value"));
        var hashCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePasswordHashIfUnchanged(eq(42L), eq("legacy-value"), hashCaptor.capture());
        assertNotEquals("legacy-value", hashCaptor.getValue());
        assertTrue(encoder.matches("legacy-value", hashCaptor.getValue()));
    }

    private User user(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(username + "@example.invalid");
        return user;
    }
}
