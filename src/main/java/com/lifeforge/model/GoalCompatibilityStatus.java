package com.lifeforge.model;

import com.lifeforge.tui4j.ScreenKit.Line;
import com.lifeforge.tui4j.Theme;
import com.williamcallahan.tui4j.compat.lipgloss.Style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic Goal Compatibility Status for LIFEForge recommendations.
 * Replaces abstract numeric match scores with clear, guidance-oriented
 * feedback and recommended focus areas.
 *
 * Never blocks or diagnoses the user; provides informative guidance.
 */
public class GoalCompatibilityStatus {

    public enum Level {
        WARNING("⚠ WARNING", "Profile-goal mismatch", true),
        WELL_ALIGNED("✓ WELL ALIGNED", "Profile and goal aligned", false);

        private final String title;
        private final String description;
        private final boolean warning;

        Level(String title, String description, boolean warning) {
            this.title = title;
            this.description = description;
            this.warning = warning;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public boolean isWarning() {
            return warning;
        }
    }

    private final Level level;
    private final String goalName;
    private final String profileSummary;
    private final List<String> recommendations;

    public GoalCompatibilityStatus(Level level, String goalName, String profileSummary, List<String> recommendations) {
        this.level = level != null ? level : Level.WELL_ALIGNED;
        this.goalName = goalName != null ? goalName : "Selected Goal";
        this.profileSummary = profileSummary != null ? profileSummary : "";
        this.recommendations = recommendations != null
                ? Collections.unmodifiableList(new ArrayList<>(recommendations))
                : Collections.emptyList();
    }

    public static GoalCompatibilityStatus compute(User user, Goal goal) {
        String goalCode = goal != null && goal.getCode() != null
                ? goal.getCode().toUpperCase(Locale.ROOT)
                : "";
        String goalName = goal != null && goal.getName() != null
                ? goal.getName()
                : "Selected Goal";

        double height = user != null && user.getHeightCm() != null ? user.getHeightCm() : 0.0;
        double weight = user != null && user.getWeightKg() != null ? user.getWeightKg() : 0.0;
        Integer age = user != null ? user.getAge() : null;
        ActivityLevel activity = user != null ? user.getActivityLevel() : null;

        double bmi = (height > 0.0 && weight > 0.0)
                ? weight / ((height / 100.0) * (height / 100.0))
                : 0.0;

        StringBuilder profSb = new StringBuilder();
        if (age != null && age > 0) {
            profSb.append(age).append(" yrs");
        }
        if (height > 0.0) {
            if (!profSb.isEmpty()) profSb.append(" | ");
            profSb.append(String.format(Locale.ROOT, "%.0f cm", height));
        }
        if (weight > 0.0) {
            if (!profSb.isEmpty()) profSb.append(" | ");
            profSb.append(String.format(Locale.ROOT, "%.0f kg", weight));
        }
        if (bmi > 0.0) {
            profSb.append(String.format(Locale.ROOT, " (BMI %.1f)", bmi));
        }
        if (activity != null) {
            if (!profSb.isEmpty()) profSb.append(" | ");
            profSb.append(humanActivity(activity));
        }
        String profileSummary = profSb.toString();

        Level level = Level.WELL_ALIGNED;
        List<String> recs = new ArrayList<>();

        if (goalCode.contains("GAIN") && goalCode.contains("WEIGHT")) {
            if (bmi >= 25.0) {
                level = Level.WARNING;
                recs.add("Focus on healthy weight management rather than calorie surplus");
                recs.add("Improve fitness, strength, and daily activity level");
                recs.add("Prioritize balanced nutrition and consistent hydration");
            } else if (bmi < 18.5 && bmi > 0.0) {
                level = Level.WELL_ALIGNED;
                recs.add("Continue with gradual, nutrient-dense caloric surplus");
                recs.add("Focus on balanced nutrition with healthy fats and protein");
                recs.add("Support with progressive strength training");
            } else {
                level = Level.WELL_ALIGNED;
                recs.add("Continue with gradual weight gain (+0.25 to 0.5 kg/week)");
                recs.add("Focus on balanced, protein-rich nutrition");
                recs.add("Support with appropriate resistance exercise");
            }
        } else if (goalCode.contains("LOSE") || (goalCode.contains("WEIGHT") && !goalCode.contains("GAIN"))) {
            if (bmi > 0.0 && bmi < 18.5) {
                level = Level.WARNING;
                recs.add("Further weight reduction is not recommended for healthy maintenance");
                recs.add("Focus on nutrient-dense meals and functional fitness");
                recs.add("Consider switching to 'Gain Weight' or 'Improve Fitness'");
            } else {
                level = Level.WELL_ALIGNED;
                recs.add("Maintain a sustainable, gradual caloric deficit");
                recs.add("Focus on balanced, high-fiber nutrition and lean protein");
                recs.add("Support with regular cardiovascular and strength exercise");
            }
        } else if (goalCode.contains("MUSCLE")) {
            if (bmi > 0.0 && bmi < 17.0) {
                level = Level.WARNING;
                recs.add("Increase overall caloric and protein intake to support training");
                recs.add("Focus on nutrient density before high-volume heavy lifting");
                recs.add("Pair moderate resistance training with adequate rest");
            } else {
                level = Level.WELL_ALIGNED;
                recs.add("Execute progressive overload resistance training 3-5x/week");
                recs.add("Consume adequate daily protein (1.6-2.2 g/kg)");
                recs.add("Ensure 7-9 hours of quality sleep for muscular recovery");
            }
        } else if (goalCode.contains("FITNESS")) {
            level = Level.WELL_ALIGNED;
            recs.add("Build aerobic endurance with consistent cardio sessions");
            recs.add("Incorporate mobility and core functional strength");
            recs.add("Maintain steady hydration and post-workout recovery");
        } else if (goalCode.contains("SLEEP")) {
            level = Level.WELL_ALIGNED;
            recs.add("Maintain a fixed sleep and wake schedule daily");
            recs.add("Institute a 30-60 minute screen curfew before bedtime");
            recs.add("Optimize sleep environment (dark, quiet, cool temperature)");
        } else if (goalCode.contains("SKIN")) {
            level = Level.WELL_ALIGNED;
            recs.add("Meet daily hydration target steadily throughout the day");
            recs.add("Eat antioxidant-rich fruits, vegetables, and omega-3s");
            recs.add("Protect recovery with consistent sleep and stress management");
        } else {
            level = Level.WELL_ALIGNED;
            recs.add("Maintain balanced whole-food nutrition daily");
            recs.add("Engage in 30+ minutes of moderate physical activity");
            recs.add("Prioritize quality sleep, hydration, and mental well-being");
        }

        return new GoalCompatibilityStatus(level, goalName, profileSummary, recs);
    }

    /**
     * Renders a clean boxed card formatted for the TUI frame.
     */
    public List<Line> renderCard(int innerWidth) {
        List<Line> lines = new ArrayList<>();
        int cardWidth = Math.max(38, Math.min(innerWidth - 4, 72));
        String indent = "  ";

        Style borderStyle = level.isWarning() ? Theme.warn() : Theme.headingGreen();
        Style titleStyle = level.isWarning() ? Theme.warn() : Theme.ok();

        // 1. Top border
        lines.add(Line.of(borderStyle, indent + "┌" + "─".repeat(cardWidth - 2) + "┐"));

        // 2. Status Title
        lines.add(renderRow(indent, cardWidth, borderStyle, titleStyle, level.getTitle()));

        // 3. Subtitle (if warning)
        if (level.isWarning()) {
            lines.add(renderRow(indent, cardWidth, borderStyle, Theme.dim(), level.getDescription()));
        }

        // 4. Blank row
        lines.add(renderBlankRow(indent, cardWidth, borderStyle));

        // 5. Section Header
        lines.add(renderRow(indent, cardWidth, borderStyle, Theme.headingCyan(), "LIFEForge Recommendation"));

        // 6. Recommendation bullet items
        for (String rec : recommendations) {
            lines.add(renderRow(indent, cardWidth, borderStyle, Theme.text(), "→ " + rec));
        }

        // 7. Blank row
        lines.add(renderBlankRow(indent, cardWidth, borderStyle));

        // 8. Goal & Profile context
        lines.add(renderRow(indent, cardWidth, borderStyle, Theme.dim(), "Goal: " + goalName));
        lines.add(renderRow(indent, cardWidth, borderStyle, Theme.dim(), "Profile: " + profileSummary));

        // 9. Bottom border
        lines.add(Line.of(borderStyle, indent + "└" + "─".repeat(cardWidth - 2) + "┘"));

        return lines;
    }

    private static Line renderRow(String indent, int cardWidth, Style borderStyle, Style contentStyle, String text) {
        int cardInner = cardWidth - 2; // excluding │ and │
        String safe = text == null ? "" : text;
        int maxText = Math.max(1, cardInner - 4); // 2 spaces left, 2 spaces right
        if (Theme.width(safe) > maxText) {
            safe = Theme.truncate(safe, maxText);
        }
        int rightPad = Math.max(0, cardInner - 2 - Theme.width(safe));
        String row = indent + "│  " + safe + " ".repeat(rightPad) + "│";
        return Line.of(contentStyle, row);
    }

    private static Line renderBlankRow(String indent, int cardWidth, Style borderStyle) {
        int cardInner = cardWidth - 2;
        String row = indent + "│" + " ".repeat(cardInner) + "│";
        return Line.of(borderStyle, row);
    }

    private static String humanActivity(ActivityLevel act) {
        return switch (act) {
            case SEDENTARY -> "Sedentary";
            case LIGHTLY_ACTIVE -> "Lightly Active";
            case MODERATELY_ACTIVE -> "Moderately Active";
            case VERY_ACTIVE -> "Very Active";
            case EXTRA_ACTIVE -> "Extra Active";
        };
    }

    public Level getLevel() {
        return level;
    }

    public boolean isWarning() {
        return level.isWarning();
    }

    public String getGoalName() {
        return goalName;
    }

    public String getProfileSummary() {
        return profileSummary;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }
}
