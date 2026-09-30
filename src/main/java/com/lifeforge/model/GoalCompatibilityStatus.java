package com.lifeforge.model;

import com.lifeforge.view.ScreenKit.Line;
import com.lifeforge.view.Theme;
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
        ALIGNED("✓ GOAL ALIGNED", "Profile and goal aligned", false),
        CAUTION("⚠ GOAL REQUIRES CAUTION", "Profile requires caution for selected goal", true),
        CONFLICT("! GOAL MAY CONFLICT WITH PROFILE", "Selected goal may conflict with profile", true);

        // Aliases for compatibility
        public static final Level WELL_ALIGNED = ALIGNED;
        public static final Level WARNING = CONFLICT;
        public static final Level GOAL_ALIGNED = ALIGNED;
        public static final Level GOAL_REQUIRES_CAUTION = CAUTION;
        public static final Level GOAL_MAY_CONFLICT_WITH_PROFILE = CONFLICT;

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

        public boolean isConflict() {
            return this == CONFLICT;
        }

        public boolean isCaution() {
            return this == CAUTION;
        }

        public boolean isAligned() {
            return this == ALIGNED;
        }
    }

    private final Level level;
    private final String statusMessage;
    private final String goalName;
    private final String profileSummary;
    private final List<String> recommendations;

    public GoalCompatibilityStatus(Level level, String goalName, String profileSummary, List<String> recommendations) {
        this(level, level != null ? level.getDescription() : "", goalName, profileSummary, recommendations);
    }

    public GoalCompatibilityStatus(Level level, String statusMessage, String goalName, String profileSummary, List<String> recommendations) {
        this.level = level != null ? level : Level.ALIGNED;
        this.statusMessage = statusMessage != null ? statusMessage : (this.level != null ? this.level.getDescription() : "");
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
        String goalNameUpper = goalName.toUpperCase(Locale.ROOT);

        double height = user != null && user.getHeightCm() != null ? user.getHeightCm() : 0.0;
        double weight = user != null && user.getWeightKg() != null ? user.getWeightKg() : 0.0;
        Integer age = user != null ? user.getAge() : null;
        Gender gender = user != null ? user.getGender() : null;
        ActivityLevel activity = user != null ? user.getActivityLevel() : null;

        double bmi = (height > 0.0 && weight > 0.0)
                ? weight / ((height / 100.0) * (height / 100.0))
                : 0.0;

        StringBuilder profSb = new StringBuilder();
        if (age != null && age > 0) {
            profSb.append(age).append(" yrs");
        }
        if (gender != null) {
            if (!profSb.isEmpty()) profSb.append(" | ");
            profSb.append(gender == Gender.MALE ? "Male" : (gender == Gender.FEMALE ? "Female" : gender.name()));
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

        Level level = Level.ALIGNED;
        String statusMsg = null;
        List<String> recs = new ArrayList<>();

        boolean isGainWeight = (goalCode.contains("GAIN") && goalCode.contains("WEIGHT"))
                || (goalNameUpper.contains("GAIN") && goalNameUpper.contains("WEIGHT"));
        boolean isLoseWeight = goalCode.contains("LOSE")
                || goalNameUpper.contains("LOSE")
                || (goalCode.contains("WEIGHT") && !isGainWeight);
        boolean isMuscle = goalCode.contains("MUSCLE") || goalNameUpper.contains("MUSCLE");
        boolean isFitness = goalCode.contains("FITNESS") || goalNameUpper.contains("FITNESS");
        boolean isSleep = goalCode.contains("SLEEP") || goalNameUpper.contains("SLEEP");
        boolean isSkin = goalCode.contains("SKIN") || goalNameUpper.contains("SKIN");

        if (isGainWeight) {
            if (bmi >= 30.0) {
                level = Level.CONFLICT;
                statusMsg = String.format(Locale.ROOT, "BMI %.1f indicates higher-weight range; caloric surplus conflicts", bmi);
                recs.add(String.format(Locale.ROOT, "Caloric surplus conflicts with healthy weight management at BMI %.1f", bmi));
                recs.add("Focus on balanced nutrition, metabolic health, and daily movement");
                recs.add("Consider alternative goals: 'Lose Weight', 'Build Muscle', or 'Improve Fitness'");
            } else if (bmi >= 25.0) {
                level = Level.CAUTION;
                statusMsg = String.format(Locale.ROOT, "BMI %.1f is in overweight range (25.0–29.9); caution advised", bmi);
                recs.add(String.format(Locale.ROOT, "Profile indicates above-average weight (BMI %.1f); general weight gain requires caution", bmi));
                recs.add("Focus on progressive strength training to build lean mass rather than fat");
                recs.add("Consider alternative goals: 'Build Muscle' or 'Improve Fitness'");
            } else if (activity == ActivityLevel.SEDENTARY && bmi >= 23.5) {
                level = Level.CAUTION;
                statusMsg = "Sedentary activity with calorie surplus may promote fat accumulation";
                recs.add("Incorporate progressive resistance training before increasing calorie intake");
                recs.add("Focus on protein-rich nutrition to stimulate lean tissue synthesis");
                recs.add("Consider alternative goals: 'Build Muscle' or 'Improve Fitness'");
            } else if (bmi > 0.0 && bmi < 18.5) {
                level = Level.ALIGNED;
                recs.add("Continue with gradual, nutrient-dense caloric surplus");
                recs.add("Focus on balanced nutrition with healthy fats, complex carbs, and protein");
                recs.add("Support with progressive strength training to promote lean tissue");
            } else {
                level = Level.ALIGNED;
                recs.add("Continue with gradual weight gain through a nutrient-dense caloric surplus");
                recs.add("Focus on balanced, protein-rich nutrition and whole foods");
                recs.add("Support with consistent hydration and recovery");
            }
        } else if (isLoseWeight) {
            if (bmi > 0.0 && bmi < 18.5) {
                level = Level.CONFLICT;
                statusMsg = String.format(Locale.ROOT, "BMI %.1f is in underweight range (< 18.5); weight loss conflicts", bmi);
                recs.add(String.format(Locale.ROOT, "Weight loss not recommended for underweight profile (BMI %.1f)", bmi));
                recs.add("Caloric deficit risks energy depletion, immune drop, and lean tissue loss");
                recs.add("Consider alternative goals: 'Gain Weight', 'Build Muscle', or 'Improve Fitness'");
            } else if (bmi >= 18.5 && bmi < 20.0) {
                level = Level.CAUTION;
                statusMsg = String.format(Locale.ROOT, "BMI %.1f is near underweight threshold (18.5); caution advised", bmi);
                recs.add(String.format(Locale.ROOT, "Current BMI (%.1f) is near the lower boundary of healthy weight", bmi));
                recs.add("Avoid aggressive caloric restriction; prioritize nutrient density and strength");
                recs.add("Consider alternative goals: 'Build Muscle' or 'Improve Fitness'");
            } else if (age != null && age > 0 && age < 18 && bmi < 22.0 && bmi > 0.0) {
                level = Level.CAUTION;
                statusMsg = "Adolescent growth requires sufficient energy and nutrients";
                recs.add("Avoid aggressive caloric restriction during vital growth and development years");
                recs.add("Focus on nutrient-dense meals and consistent daily physical activity");
                recs.add("Consider alternative goals: 'Improve Fitness' or 'Build Muscle'");
            } else if (age != null && age >= 65 && bmi < 24.0 && bmi > 0.0) {
                level = Level.CAUTION;
                statusMsg = "Older adults should prioritize preserving lean mass and bone density";
                recs.add("Preserve lean muscle mass and bone mineral density with adequate protein");
                recs.add("Pair any moderate calorie adjustment with resistance and balance training");
                recs.add("Consider alternative goal: 'Improve Fitness' for functional longevity");
            } else if ((activity == ActivityLevel.VERY_ACTIVE || activity == ActivityLevel.EXTRA_ACTIVE) && bmi < 21.5 && bmi > 0.0) {
                level = Level.CAUTION;
                statusMsg = "High activity volume with lower-normal BMI increases energy deficiency risk";
                recs.add("Ensure sufficient carbohydrate and protein intake to fuel high activity levels");
                recs.add("Avoid deep caloric deficits that compromise recovery and performance");
                recs.add("Consider alternative goals: 'Improve Fitness' or 'Build Muscle'");
            } else {
                level = Level.ALIGNED;
                recs.add("Maintain a sustainable, gradual caloric deficit");
                recs.add("Focus on balanced, high-fiber nutrition and lean protein");
                recs.add("Support with regular cardiovascular and strength exercise");
            }
        } else if (isMuscle) {
            if (bmi > 0.0 && bmi < 16.5) {
                level = Level.CONFLICT;
                statusMsg = String.format(Locale.ROOT, "BMI %.1f indicates very low energy reserves; heavy lifting conflicts", bmi);
                recs.add(String.format(Locale.ROOT, "Intense training at BMI %.1f increases injury risk and energy depletion", bmi));
                recs.add("Prioritize restoring baseline weight with nutrient-dense meals first");
                recs.add("Consider alternative goal: 'Gain Weight' to build energy foundation");
            } else if (bmi >= 16.5 && bmi < 18.5) {
                level = Level.CAUTION;
                statusMsg = String.format(Locale.ROOT, "BMI %.1f is below standard range; muscle building requires calorie surplus", bmi);
                recs.add(String.format(Locale.ROOT, "Current BMI (%.1f) is below standard; pair lifting with nutrient-dense calories", bmi));
                recs.add("Consume adequate daily protein (1.6–2.2 g/kg) and healthy fats for recovery");
                recs.add("Consider alternative goal: 'Gain Weight' to support healthy mass gain");
            } else {
                level = Level.ALIGNED;
                recs.add("Execute progressive overload resistance training 3-5 sessions per week");
                recs.add("Consume adequate daily protein (1.6-2.2 g/kg) and maintain hydration");
                recs.add("Ensure 7-9 hours of quality sleep for muscular recovery and synthesis");
            }
        } else if (isFitness) {
            if (bmi > 0.0 && bmi < 16.0) {
                level = Level.CAUTION;
                statusMsg = String.format(Locale.ROOT, "BMI %.1f indicates low energy reserves; start with gentle conditioning", bmi);
                recs.add("Start with gentle mobility, walking, and low-impact cardiovascular activity");
                recs.add("Avoid high-intensity endurance bouts until energy reserves improve");
                recs.add("Consider alternative goal: 'Gain Weight' to build physical energy foundation");
            } else if (age != null && age >= 70 && activity == ActivityLevel.SEDENTARY) {
                level = Level.CAUTION;
                statusMsg = "Gradual progression is recommended for older sedentary adults";
                recs.add("Begin with gentle walking, balance exercises, and mobility movements");
                recs.add("Progress duration and intensity gradually over several weeks");
                recs.add("Stay well hydrated and ensure adequate recovery between sessions");
            } else {
                level = Level.ALIGNED;
                recs.add("Build aerobic endurance with consistent cardio sessions");
                recs.add("Incorporate mobility and core functional strength");
                recs.add("Maintain steady hydration and post-workout recovery");
            }
        } else if (isSleep) {
            level = Level.ALIGNED;
            recs.add("Maintain a fixed sleep and wake schedule daily");
            recs.add("Institute a 30-60 minute screen curfew before bedtime");
            recs.add("Optimize sleep environment (dark, quiet, cool temperature)");
        } else if (isSkin) {
            level = Level.ALIGNED;
            recs.add("Meet daily hydration target steadily throughout the day");
            recs.add("Eat antioxidant-rich fruits, vegetables, and omega-3s");
            recs.add("Protect recovery with consistent sleep and stress management");
        } else {
            level = Level.ALIGNED;
            recs.add("Maintain balanced whole-food nutrition daily");
            recs.add("Engage in 30+ minutes of moderate physical activity");
            recs.add("Prioritize quality sleep, hydration, and mental well-being");
        }

        if (statusMsg == null) {
            statusMsg = level.getDescription();
        }

        return new GoalCompatibilityStatus(level, statusMsg, goalName, profileSummary, recs);
    }

    /**
     * Renders a clean boxed card formatted for the TUI frame.
     */
    public List<Line> renderCard(int innerWidth) {
        List<Line> lines = new ArrayList<>();
        int cardWidth = Math.max(38, Math.min(innerWidth - 4, 72));
        String indent = "  ";

        Style borderStyle = level.isConflict() ? Theme.err() : (level.isCaution() ? Theme.warn() : Theme.headingGreen());
        Style titleStyle = level.isConflict() ? Theme.err() : (level.isCaution() ? Theme.warn() : Theme.ok());

        // 1. Top border
        lines.add(Line.of(borderStyle, indent + "┌" + "─".repeat(cardWidth - 2) + "┐"));

        // 2. Status Title
        String titleText = level.getTitle();
        lines.add(renderRow(indent, cardWidth, borderStyle, titleStyle, titleText));

        // 3. Subtitle (if caution or conflict)
        if (level.isWarning()) {
            String sub = (statusMessage != null && !statusMessage.isBlank()) ? statusMessage : level.getDescription();
            lines.add(renderRow(indent, cardWidth, borderStyle, Theme.dim(), sub));
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

    public String getStatusMessage() {
        return statusMessage;
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
