package com.lifeforge.service;

import com.lifeforge.dao.AuditLogDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;
import com.lifeforge.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AuthServiceRegisterTest {

    private static UserDao userDao;
    private static AuthService authService;
    private static final List<Long> createdUserIds = new ArrayList<>();

    @BeforeAll
    public static void setUp() {
        userDao = new UserDao();
        AuditLogDao auditLogDao = new AuditLogDao();
        AuditLogService auditLogService = new AuditLogService(auditLogDao);
        authService = new AuthService(userDao, auditLogService);
    }

    @AfterEach
    public void cleanupEach() throws SQLException {
        for (Long id : createdUserIds) {
            userDao.delete(id);
        }
        createdUserIds.clear();
    }

    @AfterAll
    public static void tearDown() throws SQLException {
        for (Long id : createdUserIds) {
            userDao.delete(id);
        }
        createdUserIds.clear();
    }

    @Test
    public void testRegisterWithUsernameAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "testuser_" + suffix;
        String email = "test_" + suffix + "@example.com";
        String password = "Password123!";

        // 1. Register account with username
        AuthService.RegistrationResult result = authService.register(
                username,
                email,
                password,
                password,
                25,
                Gender.MALE,
                175.0,
                70.0,
                ActivityLevel.MODERATELY_ACTIVE
        );

        assertTrue(result.success, "Registration should succeed: " + result.errorMessage);
        assertNotNull(result.user);
        assertEquals(username, result.user.getUsername());
        createdUserIds.add(result.user.getId());

        // 2. Reject duplicate username
        String email2 = "another_" + suffix + "@example.com";
        AuthService.RegistrationResult duplicateUser = authService.register(
                username.toUpperCase(), // case-insensitive check
                email2,
                password,
                password,
                25,
                Gender.MALE,
                175.0,
                70.0,
                ActivityLevel.MODERATELY_ACTIVE
        );
        assertFalse(duplicateUser.success);
        assertEquals("An account with this username already exists.", duplicateUser.errorMessage);

        // 3. Login using username
        AuthService.LoginResult loginByUsername = authService.login(username, password);
        assertTrue(loginByUsername.success, "Should be able to login using username");
        assertEquals(result.user.getId(), loginByUsername.user.getId());

        // 4. Login using username with different casing
        AuthService.LoginResult loginByUsernameUpper = authService.login(username.toUpperCase(), password);
        assertTrue(loginByUsernameUpper.success, "Should be able to login using username case-insensitively");
        assertEquals(result.user.getId(), loginByUsernameUpper.user.getId());

        // 5. Login using email
        AuthService.LoginResult loginByEmail = authService.login(email, password);
        assertTrue(loginByEmail.success, "Should be able to login using email");
        assertEquals(result.user.getId(), loginByEmail.user.getId());
    }

    @Test
    public void testRegisterValidationFailsForInvalidUsername() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "test_" + suffix + "@example.com";
        String password = "Password123!";

        // Too short
        AuthService.RegistrationResult shortResult = authService.register(
                "ab", email, password, password, 25, Gender.MALE, 175.0, 70.0, ActivityLevel.MODERATELY_ACTIVE
        );
        assertFalse(shortResult.success);
        assertEquals("Username must be at least 3 characters.", shortResult.errorMessage);

        // Invalid characters (contains space)
        AuthService.RegistrationResult spaceResult = authService.register(
                "user name", email, password, password, 25, Gender.MALE, 175.0, 70.0, ActivityLevel.MODERATELY_ACTIVE
        );
        assertFalse(spaceResult.success);
        assertEquals("Username can only contain letters, numbers, '.', '-', and '_'.", spaceResult.errorMessage);
    }
}
