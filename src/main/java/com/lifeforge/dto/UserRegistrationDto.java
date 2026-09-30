package com.lifeforge.dto;

import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;

/**
 * Data Transfer Object (DTO) for user registration.
 * Encapsulates all input data required to register a new account.
 */
public class UserRegistrationDto {

    private String username;
    private String email;
    private String password;
    private String confirmPassword;
    private Integer age;
    private Gender gender;
    private Double heightCm;
    private Double weightKg;
    private ActivityLevel activityLevel;

    public UserRegistrationDto() {
    }

    public UserRegistrationDto(String username, String email, String password, String confirmPassword,
                               Integer age, Gender gender, Double heightCm, Double weightKg,
                               ActivityLevel activityLevel) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.age = age;
        this.gender = gender;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.activityLevel = activityLevel;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public Double getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Double heightCm) {
        this.heightCm = heightCm;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(ActivityLevel activityLevel) {
        this.activityLevel = activityLevel;
    }
}
