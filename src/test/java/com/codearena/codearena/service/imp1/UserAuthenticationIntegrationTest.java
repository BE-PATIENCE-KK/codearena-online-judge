package com.codearena.codearena.service.imp1;

import com.codearena.codearena.entity.User;
import com.codearena.codearena.mapper.UserMapper;
import com.codearena.codearena.mapper.ProblemMapper;
import com.codearena.codearena.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAuthenticationIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProblemMapper problemMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registrationPersistsHashAndLoginVerifiesIt() {
        String username = "hash_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "integration-password-value";
        User request = user(username, password);

        assertEquals("注册成功", userService.register(request));
        String stored = storedPassword(username);
        assertNotEquals(password, stored);
        assertTrue(stored.startsWith("$2"));
        assertTrue(userService.verifyPassword(username, password));
        assertFalse(userService.verifyPassword(username, "wrong-password"));
    }

    @Test
    void legacyPlaintextPasswordIsUpgradedAfterCorrectLogin() {
        String username = "legacy_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String legacyPassword = "legacy-integration-value";
        userMapper.insertUser(user(username, legacyPassword));
        assertEquals(legacyPassword, storedPassword(username));

        assertFalse(userService.verifyPassword(username, "wrong-value"));
        assertEquals(legacyPassword, storedPassword(username));

        assertTrue(userService.verifyPassword(username, legacyPassword));
        String upgraded = storedPassword(username);
        assertNotEquals(legacyPassword, upgraded);
        assertTrue(upgraded.startsWith("$2"));
        assertTrue(userService.verifyPassword(username, legacyPassword));
    }

    @Test
    void userSerializationNeverReturnsPasswordOrHash() throws Exception {
        User user = user("serialization-test", "$2a$10$should-not-be-exposed");
        String json = new ObjectMapper().writeValueAsString(user);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("should-not-be-exposed"));
    }

    @Test
    void authenticatedHttpFlowKeepsProblemAndJudgingEndpointsWorking() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String username = "route_test_" + suffix;
        String password = "route-integration-password";
        String credentials = objectMapper.writeValueAsString(java.util.Map.of(
                "username", username, "password", password, "email", username + "@example.invalid"));
        String registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("注册成功", objectMapper.readTree(registration).path("data").asText());

        String tokenResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String token = objectMapper.readTree(tokenResponse).path("data").asText();
        assertTrue(token.startsWith("eyJ"));

        String wrongLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("用户名或密码错误", objectMapper.readTree(wrongLogin).path("message").asText());

        mockMvc.perform(get("/user/info").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(username));
        mockMvc.perform(get("/submission/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/problem/list")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));

        var problem = problemMapper.findAll().stream()
                .filter(candidate -> candidate.getTitle() != null && candidate.getTitle().contains("A+B"))
                .findFirst().orElseThrow();
        mockMvc.perform(get("/problem/{id}", problem.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(problem.getId()));

        String[] statuses = {"AC", "WA", "CE", "RE", "TLE"};
        String[] codes = {
                "public class Main { public static void main(String[] a) { java.util.Scanner s=new java.util.Scanner(System.in); System.out.println(s.nextInt()+s.nextInt()); } }",
                "public class Main { public static void main(String[] a) { java.util.Scanner s=new java.util.Scanner(System.in); System.out.println(s.nextInt()+s.nextInt()+1); } }",
                "public class Main { public static void main(String[] a) { this is not Java; } }",
                "public class Main { public static void main(String[] a) { throw new RuntimeException(); } }",
                "public class Main { public static void main(String[] a) { while(true) {} } }"
        };
        long[] submissionIds = new long[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            String submitBody = objectMapper.writeValueAsString(java.util.Map.of(
                    "problemId", problem.getId(), "answer", codes[i], "language", "Java"));
            var response = mockMvc.perform(post("/submission")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(submitBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value(statuses[i]))
                    .andExpect(jsonPath("$.data.timeUsed").exists())
                    .andReturn().getResponse().getContentAsString();
            submissionIds[i] = objectMapper.readTree(response).path("data").path("id").asLong();
            assertTrue(submissionIds[i] > 0);
            mockMvc.perform(get("/submission/{id}", submissionIds[i])
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(statuses[i]));
        }
        mockMvc.perform(get("/submission/mine").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(5));

        String otherName = "route_other_" + suffix;
        userService.register(user(otherName, password));
        String otherToken = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", otherName, "password", password))))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        otherToken = objectMapper.readTree(otherToken).path("data").asText();
        mockMvc.perform(get("/submission/mine").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/submission/{id}", submissionIds[0]).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private String storedPassword(String username) {
        return jdbcTemplate.queryForObject("SELECT password FROM user WHERE username = ?", String.class, username);
    }

    private User user(String username, String password) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmail(username + "@example.invalid");
        user.setRole("USER");
        user.setStatus(1);
        return user;
    }
}
