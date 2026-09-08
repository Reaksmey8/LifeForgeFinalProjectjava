-- ============================================================
-- LIFEFORGE SEED DATA (READY TO RUN)
-- ============================================================

-- 1. GOALS
INSERT INTO goals (code, name, description, active) VALUES
                                                        ('LOSE_WEIGHT', 'Lose Weight', 'Gradual, sustainable fat loss through balanced nutrition and activity.', TRUE),
                                                        ('GAIN_WEIGHT', 'Gain Weight', 'Healthy weight gain through calorie surplus and structured eating.', TRUE),
                                                        ('BUILD_MUSCLE', 'Build Muscle', 'Strength-focused training with protein-forward nutrition.', TRUE),
                                                        ('IMPROVE_FITNESS', 'Improve Fitness', 'General cardiovascular and functional fitness improvement.', TRUE),
                                                        ('IMPROVE_SKIN_HEALTH', 'Improve Skin Health', 'Lifestyle habits that support healthier skin.', TRUE),
                                                        ('IMPROVE_SLEEP', 'Improve Sleep', 'Better sleep quality and consistency through routine changes.', TRUE),
                                                        ('GENERAL_WELLNESS', 'General Wellness', 'Balanced, holistic lifestyle guidance.', TRUE)
    ON CONFLICT (code) DO NOTHING;

-- 2. RECOMMENDATION CATEGORIES (top-level)
INSERT INTO recommendation_categories (id, name, description, parent_category_id, display_order) VALUES
                                                                                                     (1, 'Nutrition', 'Food and eating guidance', NULL, 1),
                                                                                                     (2, 'Exercise', 'Physical activity guidance', NULL, 2),
                                                                                                     (3, 'Sleep & Recovery', 'Sleep quality and recovery guidance', NULL, 3),
                                                                                                     (4, 'Daily Micro-Habits', 'Small daily habits that support the goal', NULL, 4),
                                                                                                     (5, 'Complete Master Routine', 'A combined daily routine', NULL, 5),
                                                                                                     (6, 'Common Mistakes & Myths', 'Things to avoid or misconceptions', NULL, 6),
                                                                                                     (7, 'Hydration Guidance', 'Suggested daily water intake guidance', NULL, 7)
    ON CONFLICT DO NOTHING;

-- Nutrition sub-categories
INSERT INTO recommendation_categories (id, name, description, parent_category_id, display_order) VALUES
                                                                                                     (8, 'Breakfast', 'Breakfast guidance', 1, 1),
                                                                                                     (9, 'Lunch', 'Lunch guidance', 1, 2),
                                                                                                     (10, 'Dinner', 'Dinner guidance', 1, 3),
                                                                                                     (11, 'Healthy Snacks', 'Snack guidance', 1, 4),
                                                                                                     (12, 'Foods to Prefer', 'Foods that support the goal', 1, 5),
                                                                                                     (13, 'Foods to Limit', 'Foods to minimize', 1, 6),
                                                                                                     (14, 'Portion Guidance', 'Portion sizing guidance', 1, 7)
    ON CONFLICT DO NOTHING;

-- 3. SAMPLE RECOMMENDATIONS
INSERT INTO recommendations
(goal_id, category_id, activity_level, title, description, recommended_actions, suggested_target, examples, important_notes)
VALUES
    (1, 8, 'SEDENTARY', 'Beginner-Friendly Weight Loss Breakfast', 'A balanced, lower-calorie breakfast to start the day without a large calorie load.', 'Prioritize protein and fiber; avoid sugary cereals and pastries.', 'Approximately 400-500 kcal', '2 eggs + 1 bowl oatmeal + 1 apple', 'Adjust portions to your personal calorie target; this is a starting guideline, not a strict rule.'),
    (1, 2, 'SEDENTARY', 'Beginner Exercise for Weight Loss', 'Low-impact activity suitable for someone currently sedentary.', 'Start with brisk 20-30 minute walks, 4-5 times per week; add light bodyweight circuits as tolerated.', '150 minutes of moderate activity per week', 'Walking, stationary cycling, beginner bodyweight routines', 'Increase intensity gradually to avoid injury or burnout.'),
    (1, 3, 'SEDENTARY', 'Sleep Guidance for Weight Loss', 'Sleep quality strongly affects appetite regulation and recovery.', 'Aim for a consistent sleep and wake time; avoid screens 30-60 minutes before bed.', '7-9 hours per night', 'Fixed bedtime routine, dim lighting before sleep', 'Poor sleep is linked to increased hunger hormones.'),
    (1, 4, 'SEDENTARY', 'Daily Micro-Habits for Weight Loss', 'Small consistent habits that compound over time.', 'Drink a glass of water before meals; take a short walk after dinner; reduce screen time before bed.', NULL, 'Water before meals, post-dinner walk', 'These are suggestions to try, not a checklist to complete daily.'),
    (1, 7, 'SEDENTARY', 'Hydration Guidance for Weight Loss', 'Adequate hydration supports metabolism and can reduce unnecessary snacking.', 'Sip water throughout the day rather than large amounts at once.', 'Approximately 2.0-2.5 L/day depending on body weight', NULL, 'Individual needs vary with climate and activity.'),
    (3, 8, 'MODERATELY_ACTIVE', 'Protein-Focused Breakfast for Muscle Building', 'A higher-protein breakfast to support muscle repair and growth.', 'Include a strong protein source at breakfast; pair with complex carbohydrates.', 'Approximately 500-600 kcal, 30g+ protein', '3 eggs + Greek yogurt + whole grain toast', 'Spread protein intake evenly across meals throughout the day.'),
    (3, 2, 'MODERATELY_ACTIVE', 'Strength Training Routine', 'Structured resistance training to stimulate muscle growth.', 'Train each major muscle group 2x per week with progressive overload.', '3-4 strength sessions per week', 'Push/pull/legs split, compound lifts (squat, bench, deadlift, row)', 'Prioritize form before adding load; allow rest days for recovery.'),
    (3, 3, 'MODERATELY_ACTIVE', 'Recovery Guidance for Muscle Building', 'Muscle is built during recovery, not just during training.', 'Ensure adequate sleep and rest days between training the same muscle group.', '7-9 hours sleep, 48h between sessions per muscle group', NULL, 'Overtraining without recovery can stall progress.');

-- 4. DEFAULT ADMIN ACCOUNT (Password: Admin@123)
INSERT INTO users
(full_name, username, email, password_hash, age, gender, height_cm, weight_kg, activity_level, role, blocked)
VALUES
    ('LifeForge Admin', 'admin', 'admin@lifeforge.com',
     '$2a$10$wE99S3B6l.R6A14f/kWRg.Vl/9O29.M8Yt0Pz/V5K4hA/W6YnFwWG',
     30, 'OTHER', 170, 70, 'MODERATELY_ACTIVE', 'ADMIN', FALSE)
    ON CONFLICT (email) DO NOTHING;