package com.lifeforge.dao;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.Goal;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the goals table.
 */
public class GoalDao {

    public Goal create(Goal goal) throws SQLException {
        String sql = "INSERT INTO goals (code, name, description, active) VALUES (?, ?, ?, ?) RETURNING id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, goal.getCode());
            ps.setString(2, goal.getName());
            ps.setString(3, goal.getDescription());
            ps.setBoolean(4, goal.isActive());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    goal.setId(rs.getLong("id"));
                }
            }
        }
        return goal;
    }

    public Optional<Goal> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM goals WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Goal> findAllActive() throws SQLException {
        String sql = "SELECT * FROM goals WHERE active = TRUE ORDER BY id";
        return runListQuery(sql);
    }

    public List<Goal> findAll() throws SQLException {
        String sql = "SELECT * FROM goals ORDER BY id";
        return runListQuery(sql);
    }

    private List<Goal> runListQuery(String sql) throws SQLException {
        List<Goal> goals = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                goals.add(mapRow(rs));
            }
        }
        return goals;
    }

    public void update(Goal goal) throws SQLException {
        String sql = "UPDATE goals SET name = ?, description = ?, active = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, goal.getName());
            ps.setString(2, goal.getDescription());
            ps.setBoolean(3, goal.isActive());
            ps.setLong(4, goal.getId());
            ps.executeUpdate();
        }
    }

    public void delete(Long id) throws SQLException {
        String sql = "DELETE FROM goals WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private Goal mapRow(ResultSet rs) throws SQLException {
        return new Goal(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("active")
        );
    }
}