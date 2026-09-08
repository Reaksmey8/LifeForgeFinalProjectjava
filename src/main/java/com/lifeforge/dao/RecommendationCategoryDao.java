package com.lifeforge.dao;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.RecommendationCategory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for recommendation_categories, including
 * parent/child hierarchy lookups (e.g. Nutrition -> Breakfast).
 */
public class RecommendationCategoryDao {

    public RecommendationCategory create(RecommendationCategory category) throws SQLException {
        String sql = "INSERT INTO recommendation_categories (name, description, parent_category_id, display_order) " +
                "VALUES (?, ?, ?, ?) RETURNING id";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            if (category.getParentCategoryId() != null) {
                ps.setLong(3, category.getParentCategoryId());
            } else {
                ps.setNull(3, Types.BIGINT);
            }
            ps.setInt(4, category.getDisplayOrder());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    category.setId(rs.getLong("id"));
                }
            }
        }
        return category;
    }

    public Optional<RecommendationCategory> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM recommendation_categories WHERE id = ?";
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

    public List<RecommendationCategory> findTopLevel() throws SQLException {
        String sql = "SELECT * FROM recommendation_categories WHERE parent_category_id IS NULL " +
                "ORDER BY display_order";
        return runListQuery(sql, null);
    }

    public List<RecommendationCategory> findChildren(Long parentId) throws SQLException {
        String sql = "SELECT * FROM recommendation_categories WHERE parent_category_id = ? " +
                "ORDER BY display_order";
        return runListQuery(sql, parentId);
    }

    public List<RecommendationCategory> findAll() throws SQLException {
        String sql = "SELECT * FROM recommendation_categories ORDER BY parent_category_id NULLS FIRST, display_order";
        return runListQuery(sql, null);
    }

    private List<RecommendationCategory> runListQuery(String sql, Long param) throws SQLException {
        List<RecommendationCategory> categories = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) {
                ps.setLong(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    categories.add(mapRow(rs));
                }
            }
        }
        return categories;
    }

    public void update(RecommendationCategory category) throws SQLException {
        String sql = "UPDATE recommendation_categories SET name = ?, description = ?, display_order = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.setInt(3, category.getDisplayOrder());
            ps.setLong(4, category.getId());
            ps.executeUpdate();
        }
    }

    public void delete(Long id) throws SQLException {
        String sql = "DELETE FROM recommendation_categories WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private RecommendationCategory mapRow(ResultSet rs) throws SQLException {
        long parentId = rs.getLong("parent_category_id");
        Long parent = rs.wasNull() ? null : parentId;
        return new RecommendationCategory(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                parent,
                rs.getInt("display_order")
        );
    }
}