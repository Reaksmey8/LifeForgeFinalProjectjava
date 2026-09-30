package com.lifeforge.service;

import com.lifeforge.model.Goal;
import com.lifeforge.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Intelligent interpretation service for user-defined Custom Goals.
 *
 * <p>Rather than blindly polluting the database goals catalog with arbitrary user strings,
 * this service:
 * <ol>
 *   <li>Enforces ethical scope boundaries (e.g. flagging medical claims or professional sports contracts).</li>
 *   <li>Identifies relevant lifestyle pillars (Exercise, Hydration, Nutrition, Sleep, Habits).</li>
 *   <li>Synthesizes a virtual Goal backed by a scientifically calibrated baseline catalog Goal.</li>
 * </ol>
 * </p>
 */
public class CustomGoalService {

    public static class PillarGuidance {
        private final String emoji;
        private final String pillarName;
        private final String guidance;

        public PillarGuidance(String emoji, String pillarName, String guidance) {
            this.emoji = emoji;
            this.pillarName = pillarName;
            this.guidance = guidance;
        }

        public String getEmoji() {
            return emoji;
        }

        public String getPillarName() {
            return pillarName;
        }

        public String getGuidance() {
            return guidance;
        }
    }

    public static class CustomGoalAnalysisResult {
        private final String rawGoalText;
        private final String title;
        private final boolean supported;
        private final String scopeNotice;
        private final String contextSummary;
        private final List<PillarGuidance> pillars;
        private final Goal baselineGoal;
        private final Goal synthesizedGoal;

        public CustomGoalAnalysisResult(String rawGoalText, String title, boolean supported,
                                        String scopeNotice, String contextSummary,
                                        List<PillarGuidance> pillars, Goal baselineGoal, Goal synthesizedGoal) {
            this.rawGoalText = rawGoalText;
            this.title = title;
            this.supported = supported;
            this.scopeNotice = scopeNotice;
            this.contextSummary = contextSummary;
            this.pillars = pillars != null ? pillars : List.of();
            this.baselineGoal = baselineGoal;
            this.synthesizedGoal = synthesizedGoal;
        }

        public String getRawGoalText() {
            return rawGoalText;
        }

        public String getTitle() {
            return title;
        }

        public boolean isSupported() {
            return supported;
        }

        public String getScopeNotice() {
            return scopeNotice;
        }

        public String getContextSummary() {
            return contextSummary;
        }

        public List<PillarGuidance> getPillars() {
            return pillars;
        }

        public Goal getBaselineGoal() {
            return baselineGoal;
        }

        public Goal getSynthesizedGoal() {
            return synthesizedGoal;
        }
    }

    public CustomGoalAnalysisResult analyze(String rawText, User user, List<Goal> availableGoals) {
        if (rawText == null || rawText.trim().isEmpty()) {
            Goal defaultBase = resolveBaseline("GENERAL_WELLNESS", availableGoals);
            return new CustomGoalAnalysisResult("", "Custom Lifestyle Objective", true, null,
                    "General vitality & health rhythm", defaultPillars(), defaultBase,
                    createSynthesized(defaultBase, "Custom Lifestyle Objective", "General vitality"));
        }

        String trimmed = rawText.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String cleanTitle = formatGoalTitle(trimmed);

        boolean outOfScope = isOutOfScope(lower);
        String scopeNotice = null;
        if (outOfScope) {
            scopeNotice = "Professional sports contracts and medical diagnostics/cures are outside LIFEForge's lifestyle wellness scope. LIFEForge can still generate foundational lifestyle conditioning for this objective.";
        }

        // Domain Intent Matching
        String baselineCode;
        String contextSummary;
        List<PillarGuidance> pillars = new ArrayList<>();

        if (containsAny(lower, "run", "5k", "10k", "marathon", "endurance", "stamina", "cardio", "jog", "sprint")) {
            baselineCode = "IMPROVE_FITNESS";
            contextSummary = "Cardiovascular conditioning, aerobic capacity, and endurance pacing.";
            pillars.add(new PillarGuidance("🏃", "Exercise", "Base aerobic tempo runs & progressive interval conditioning"));
            pillars.add(new PillarGuidance("💧", "Hydration", "Electrolyte balance & pre/post run fluid replenishment timing"));
            pillars.add(new PillarGuidance("🍎", "Nutrition", "Complex carbohydrate loading & post-exercise glycogen restoration"));
            pillars.add(new PillarGuidance("😴", "Sleep & Recovery", "Tissue repair sleep windows & scheduled muscular rest days"));
            pillars.add(new PillarGuidance("🌱", "Daily Habits", "Stage running apparel the night prior & dynamic warm-up drills"));
        } else if (containsAny(lower, "muscle", "strength", "stronger", "hypertrophy", "bulk", "gym", "lift", "calisthenics", "abs")) {
            baselineCode = "BUILD_MUSCLE";
            contextSummary = "Muscular hypertrophy, resistance progression, and lean tissue synthesis.";
            pillars.add(new PillarGuidance("🏃", "Exercise", "Progressive overload compound resistance training (3–4 sessions/week)"));
            pillars.add(new PillarGuidance("🍎", "Nutrition", "Protein density (1.6–2.0 g/kg) and calibrated caloric surplus"));
            pillars.add(new PillarGuidance("💧", "Hydration", "Cellular hydration to maintain intra-workout volume and pumps"));
            pillars.add(new PillarGuidance("😴", "Sleep & Recovery", "Growth hormone optimization & 48-hour muscle recovery cycles"));
            pillars.add(new PillarGuidance("🌱", "Daily Habits", "Pre-portion protein meals and post-workout nutritional triggers"));
        } else if (containsAny(lower, "lose weight", "fat loss", "weight loss", "burn fat", "lean down", "slim", "belly fat", "cut")) {
            baselineCode = "LOSE_WEIGHT";
            contextSummary = "Moderate caloric deficit, metabolic preservation, and satiety optimization.";
            pillars.add(new PillarGuidance("🍎", "Nutrition", "High-fiber whole foods, lean protein, and portion satiety control"));
            pillars.add(new PillarGuidance("🏃", "Exercise", "Preservation resistance training paired with daily brisk walking"));
            pillars.add(new PillarGuidance("💧", "Hydration", "500 mL water pre-meals to optimize satiety and metabolic rate"));
            pillars.add(new PillarGuidance("😴", "Sleep & Recovery", "7–9 hours sleep to regulate ghrelin and leptin hunger hormones"));
            pillars.add(new PillarGuidance("🌱", "Daily Habits", "Eliminate liquid calories and remove low-nutrient visual triggers"));
        } else if (containsAny(lower, "gain weight", "healthy weight", "gain mass", "eat more")) {
            baselineCode = "GAIN_WEIGHT";
            contextSummary = "Caloric surplus density, nutrient-rich foods, and progressive hypertrophy.";
            pillars.add(new PillarGuidance("🍎", "Nutrition", "Energy-dense whole foods (nuts, oats, eggs) & consistent meal timing"));
            pillars.add(new PillarGuidance("🏃", "Exercise", "Heavy compound lifting to channel surplus calories into lean tissue"));
            pillars.add(new PillarGuidance("💧", "Hydration", "Hydrate between meals rather than blunting appetite right before food"));
            pillars.add(new PillarGuidance("😴", "Sleep & Recovery", "Protect 8 hours nightly sleep for anabolic hormonal recovery"));
            pillars.add(new PillarGuidance("🌱", "Daily Habits", "Incorporate liquid smoothie nutrition & structured snack windows"));
        } else if (containsAny(lower, "posture", "sit", "desk", "spine", "back pain", "neck", "slouch")) {
            baselineCode = "POSTURE_CORRECTION";
            contextSummary = "Spinal decompression, posterior chain activation, and ergonomic posture resets.";
            pillars.add(new PillarGuidance("🏃", "Exercise", "Thoracic extension, glute bridges, and core stabilization routines"));
            pillars.add(new PillarGuidance("🌱", "Daily Habits", "2-minute postural reset & shoulder retraction every 2 hours desk sit"));
            pillars.add(new PillarGuidance("😴", "Sleep & Recovery", "Ergonomic cervical pillow alignment & spinal decompression"));
            pillars.add(new PillarGuidance("💧", "Hydration", "Frequent hydration breaks to interrupt prolonged sedentary sitting"));
            pillars.add(new PillarGuidance("🍎", "Nutrition", "Anti-inflammatory whole food nutrition supporting joint health"));
        } else if (containsAny(lower, "sleep", "insomnia", "bedtime", "rest", "wake up", "wake refreshed", "circadian")) {
            baselineCode = "IMPROVE_SLEEP";
            contextSummary = "Circadian rhythm alignment, sleep hygiene architecture, and recovery depth.";
            pillars.add(new PillarGuidance("😴", "Sleep & Recovery", "Strict 45-min digital screen curfew & cool (~18–20°C) dark environment"));
            pillars.add(new PillarGuidance("🏃", "Exercise", "Morning or midday movement to build homeostatic sleep pressure"));
            pillars.add(new PillarGuidance("💧", "Hydration", "Taper large fluid volumes 90 mins before bed to reduce interruptions"));
            pillars.add(new PillarGuidance("🍎", "Nutrition", "Avoid caffeine past 2 PM and heavy high-fat late evening dinners"));
            pillars.add(new PillarGuidance("🌱", "Daily Habits", "10 minutes natural morning sunlight exposure within 30 mins waking"));
        } else if (containsAny(lower, "skin", "glow", "complexion", "acne", "radiant")) {
            baselineCode = "IMPROVE_SKIN_HEALTH";
            contextSummary = "Cellular hydration, antioxidant nutrition, and restorative dermis recovery.";
            pillars.add(new PillarGuidance("💧", "Hydration", "2.5–3.0 L daily hydration to maintain dermal barrier moisture"));
            pillars.add(new PillarGuidance("🍎", "Nutrition", "Antioxidants (vitamin C, E), zinc, and omega-3 fatty acids"));
            pillars.add(new PillarGuidance("😴", "Sleep & Recovery", "7–9 hours sleep for collagen renewal and cortisol suppression"));
            pillars.add(new PillarGuidance("🌱", "Daily Habits", "Consistent morning/evening skincare hygiene and sun protection"));
            pillars.add(new PillarGuidance("🏃", "Exercise", "Moderate cardio to stimulate capillary microcirculation"));
        } else if (containsAny(lower, "hike", "hiking", "trek", "outdoor", "climb", "mountain")) {
            baselineCode = "IMPROVE_FITNESS";
            contextSummary = "Leg muscular endurance, incline conditioning, and sustained energy pacing.";
            pillars.add(new PillarGuidance("🏃", "Exercise", "Incline treadmill, step-ups, walking lunges, and rucking"));
            pillars.add(new PillarGuidance("💧", "Hydration", "High-volume hydration with sodium/potassium electrolyte replenishment"));
            pillars.add(new PillarGuidance("🍎", "Nutrition", "Slow-burning complex carbohydrates and portable nutrient density"));
            pillars.add(new PillarGuidance("😴", "Sleep & Recovery", "Lower body muscle tissue repair & active mobility rest days"));
            pillars.add(new PillarGuidance("🌱", "Daily Habits", "Footwear prep, ankle mobility resets, and pacing cadence"));
        } else {
            baselineCode = "GENERAL_WELLNESS";
            contextSummary = "Balanced vitality, metabolic consistency, and sustainable wellness rhythms.";
            pillars.addAll(defaultPillars());
        }

        Goal baselineGoal = resolveBaseline(baselineCode, availableGoals);
        Goal synthesizedGoal = createSynthesized(baselineGoal, cleanTitle, contextSummary);

        return new CustomGoalAnalysisResult(
                trimmed, cleanTitle, !outOfScope, scopeNotice, contextSummary, pillars, baselineGoal, synthesizedGoal
        );
    }

    private boolean isOutOfScope(String lower) {
        // High-risk clinical/medical diagnoses or prescription pharmacological claims
        if (containsAny(lower, "cure", "cancer", "diabetes", "prescription", "steroid", "surgery",
                "tumor", "medication", "disease", "depression", "therapy", "antidepressant")) {
            return true;
        }
        // Professional competitive athletic contracts
        if (containsAny(lower, "nba", "pro athlete", "professional basketball", "olympic gold",
                "premier league", "nfl", "super bowl")) {
            return true;
        }
        // Non-health domain requests
        if (containsAny(lower, "crypto", "bitcoin", "money", "rich", "coding", "pass exam", "lottery")) {
            return true;
        }
        // Dangerous physical extremes
        if (containsAny(lower, "starve", "crash diet", "anorexia", "throw up", "laxative")) {
            return true;
        }
        return false;
    }

    private List<PillarGuidance> defaultPillars() {
        return List.of(
                new PillarGuidance("🍎", "Nutrition", "Balanced whole foods with consistent meal timing and portion awareness"),
                new PillarGuidance("💧", "Hydration", "Calibrated baseline hydration (33 mL/kg) distributed through active hours"),
                new PillarGuidance("🏃", "Exercise", "Daily 30-minute moderate physical activity or purposeful walking"),
                new PillarGuidance("😴", "Sleep & Recovery", "Consistent 7–9 hour restorative sleep window and evening wind-down"),
                new PillarGuidance("🌱", "Daily Habits", "Low-friction anchors that compound into lasting health habits")
        );
    }

    private Goal resolveBaseline(String code, List<Goal> availableGoals) {
        if (availableGoals != null) {
            for (Goal g : availableGoals) {
                if (g.getCode() != null && g.getCode().equalsIgnoreCase(code)) {
                    return g;
                }
            }
            if (!availableGoals.isEmpty()) {
                return availableGoals.get(0);
            }
        }
        return new Goal(4L, "IMPROVE_FITNESS", "Improve Fitness", "General physical conditioning", true);
    }

    private Goal createSynthesized(Goal baseline, String title, String contextSummary) {
        Long id = (baseline != null && baseline.getId() != null) ? baseline.getId() : 4L;
        String code = (baseline != null && baseline.getCode() != null) ? baseline.getCode() : "IMPROVE_FITNESS";
        String displayTitle = title + " (Custom)";
        return new Goal(id, code, displayTitle, contextSummary, true);
    }

    private boolean containsAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private String formatGoalTitle(String input) {
        String clean = input.replaceAll("\\s+", " ").trim();
        if (clean.length() <= 50) {
            return capitalizeWords(clean);
        }
        return capitalizeWords(clean.substring(0, 47).trim()) + "...";
    }

    private String capitalizeWords(String str) {
        if (str.isEmpty()) return str;
        StringBuilder sb = new StringBuilder();
        boolean capNext = true;
        for (char c : str.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-' || c == '/') {
                capNext = true;
                sb.append(c);
            } else if (capNext) {
                sb.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
