package com.lifeforge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationUtilTest {

    @Test
    public void testValidateUsername() {
        // Required
        assertEquals("Username is required.", ValidationUtil.validateUsername(null));
        assertEquals("Username is required.", ValidationUtil.validateUsername(""));
        assertEquals("Username is required.", ValidationUtil.validateUsername("   "));

        // Min length
        assertEquals("Username must be at least 3 characters.", ValidationUtil.validateUsername("ab"));

        // Max length
        String longUsername = "a".repeat(51);
        assertEquals("Username must be under 50 characters.", ValidationUtil.validateUsername(longUsername));

        // Invalid characters
        assertEquals("Username can only contain letters, numbers, '.', '-', and '_'.",
                ValidationUtil.validateUsername("user name"));
        assertEquals("Username can only contain letters, numbers, '.', '-', and '_'.",
                ValidationUtil.validateUsername("user@email"));
        assertEquals("Username can only contain letters, numbers, '.', '-', and '_'.",
                ValidationUtil.validateUsername("user#123"));
        assertEquals("Username can only contain letters, numbers, '.', '-', and '_'.",
                ValidationUtil.validateUsername("user!name"));

        // Valid usernames
        assertNull(ValidationUtil.validateUsername("johndoe"));
        assertNull(ValidationUtil.validateUsername("john_doe"));
        assertNull(ValidationUtil.validateUsername("john.doe"));
        assertNull(ValidationUtil.validateUsername("john-doe"));
        assertNull(ValidationUtil.validateUsername("User_123"));
        assertNull(ValidationUtil.validateUsername("  alex99  ")); // trims properly
    }

    @Test
    public void testValidateFullName() {
        assertNull(ValidationUtil.validateFullName("John Doe"));
        assertEquals("Full name is required.", ValidationUtil.validateFullName(""));
        assertEquals("Full name is required.", ValidationUtil.validateFullName(null));
        assertEquals("Full name must be at least 2 characters.", ValidationUtil.validateFullName("J"));
        assertEquals("Full name must be under 100 characters.", ValidationUtil.validateFullName("A".repeat(101)));
    }

    @Test
    public void testValidateEmail() {
        assertNull(ValidationUtil.validateEmail("user@example.com"));
        assertNull(ValidationUtil.validateEmail("test.user+tag@domain.co.uk"));
        assertEquals("Email is required.", ValidationUtil.validateEmail(null));
        assertEquals("Email is required.", ValidationUtil.validateEmail("  "));
        assertEquals("Please enter a valid email address.", ValidationUtil.validateEmail("invalid-email"));
        assertEquals("Please enter a valid email address.", ValidationUtil.validateEmail("@nodomain.com"));
        assertEquals("Please enter a valid email address.", ValidationUtil.validateEmail("user@"));
    }

    @Test
    public void testValidatePassword() {
        assertNull(ValidationUtil.validatePassword("12345678"));
        assertEquals("Password is required.", ValidationUtil.validatePassword(null));
        assertEquals("Password is required.", ValidationUtil.validatePassword(""));
        assertEquals("Password must be at least 8 characters.", ValidationUtil.validatePassword("short"));
    }

    @Test
    public void testValidatePasswordConfirmation() {
        assertNull(ValidationUtil.validatePasswordConfirmation("secret123", "secret123"));
        assertEquals("Passwords do not match.", ValidationUtil.validatePasswordConfirmation("secret123", "different"));
        assertEquals("Passwords do not match.", ValidationUtil.validatePasswordConfirmation("secret123", null));
    }

    @Test
    public void testValidateAge() {
        assertNull(ValidationUtil.validateAge(25));
        assertNull(ValidationUtil.validateAge(13));
        assertNull(ValidationUtil.validateAge(100));
        assertEquals("Age is required.", ValidationUtil.validateAge(null));
        assertEquals("Age must be between 13 and 100.", ValidationUtil.validateAge(12));
        assertEquals("Age must be between 13 and 100.", ValidationUtil.validateAge(101));
    }

    @Test
    public void testValidateHeight() {
        assertNull(ValidationUtil.validateHeight(175.0));
        assertEquals("Height is required.", ValidationUtil.validateHeight(null));
        assertEquals("Height must be between 100.0 and 250.0 cm.", ValidationUtil.validateHeight(99.0));
        assertEquals("Height must be between 100.0 and 250.0 cm.", ValidationUtil.validateHeight(251.0));
    }

    @Test
    public void testValidateWeight() {
        assertNull(ValidationUtil.validateWeight(70.0));
        assertEquals("Weight is required.", ValidationUtil.validateWeight(null));
        assertEquals("Weight must be between 30.0 and 300.0 kg.", ValidationUtil.validateWeight(29.0));
        assertEquals("Weight must be between 30.0 and 300.0 kg.", ValidationUtil.validateWeight(301.0));
    }

    @Test
    public void testValidateRequired() {
        assertNull(ValidationUtil.validateRequired("value", "Field"));
        assertEquals("Field is required.", ValidationUtil.validateRequired(null, "Field"));
        assertEquals("Field is required.", ValidationUtil.validateRequired("  ", "Field"));
    }

    @Test
    public void testIsValidMenuChoice() {
        assertTrue(ValidationUtil.isValidMenuChoice("1", 1, 5));
        assertTrue(ValidationUtil.isValidMenuChoice("5", 1, 5));
        assertFalse(ValidationUtil.isValidMenuChoice("0", 1, 5));
        assertFalse(ValidationUtil.isValidMenuChoice("6", 1, 5));
        assertFalse(ValidationUtil.isValidMenuChoice("abc", 1, 5));
        assertFalse(ValidationUtil.isValidMenuChoice(null, 1, 5));
        assertFalse(ValidationUtil.isValidMenuChoice("", 1, 5));
    }
}
