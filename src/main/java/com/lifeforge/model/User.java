package com.lifeforge.model;

import java.time.LocalDateTime;

/**
 * Represents a LifeForge user account and personal profile.
 * The password field always holds a BCrypt hash, never plain text.
 */
public class User {

    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String passwordHash;
    private Integer age;
    private Gender gender;
    private Double heightCm;
    private Double weightKg;
    private ActivityLevel activityLevel;
    private Role role;
    private boolean blocked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User() {
    }

    public User(Long id, String fullName, String username, String email, String passwordHash, Integer age,
                Gender gender, Double heightCm, Double weightKg, ActivityLevel activityLevel,
                Role role, boolean blocked, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.age = age;
        this.gender = gender;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.activityLevel = activityLevel;
        this.role = role;
        this.blocked = blocked;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
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

    public Double getHeight() {
        return heightCm != null ? heightCm : 0.0;
    }

    public void setHeightCm(Double heightCm) {
        this.heightCm = heightCm;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public Double getWeight() {
        return weightKg != null ? weightKg : 0.0;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public static double calculateBmi(double weightKg, double heightCm) {
        if (heightCm <= 0 || weightKg <= 0) {
            return 0.0;
        }
        double hM = heightCm / 100.0;
        return weightKg / (hM * hM);
    }

    public Double getBmi() {
        if (heightCm == null || weightKg == null) {
            return 0.0;
        }
        return calculateBmi(weightKg, heightCm);
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(ActivityLevel activityLevel) {
        this.activityLevel = activityLevel;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", fullName='" + fullName + "', email='" + email
                + "', role=" + role + ", blocked=" + blocked + "}";
    }
}