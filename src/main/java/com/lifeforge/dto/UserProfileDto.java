package com.lifeforge.dto;

import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Gender;
import com.lifeforge.model.Role;
import com.lifeforge.model.User;

import java.time.LocalDateTime;

/**
 * Data Transfer Object (DTO) for presenting user profile details.
 * Omits sensitive internal information such as password hashes.
 */
public class UserProfileDto {

    private Long id;
    private String fullName;
    private String username;
    private String email;
    private int age;
    private Gender gender;
    private double heightCm;
    private double weightKg;
    private ActivityLevel activityLevel;
    private Role role;
    private boolean blocked;
    private LocalDateTime createdAt;

    public UserProfileDto() {
    }

    public static UserProfileDto fromEntity(User user) {
        if (user == null) {
            return null;
        }
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setAge(user.getAge());
        dto.setGender(user.getGender());
        dto.setHeightCm(user.getHeightCm());
        dto.setWeightKg(user.getWeightKg());
        dto.setActivityLevel(user.getActivityLevel());
        dto.setRole(user.getRole());
        dto.setBlocked(user.isBlocked());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
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

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public double getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(double heightCm) {
        this.heightCm = heightCm;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
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
}
