package com.lifeforge.service;

import com.lifeforge.dao.RecommendationDao;
import com.lifeforge.model.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Rule-Based Recommendation Engine for LIFEForge.
 *
 * This engine is the authoritative SOURCE OF TRUTH for:
 * 1. Base recommendation matching from the database
 * 2. Deterministic priority resolution across recommendation categories
 * 3. Explainable Match Score calculation (40% completeness, 40% goal alignment, 20% activity synergy)
 * 4. Academic calculation trace generation
 * 5. Unified Daily Blueprint generation
 */
public class RecommendationEngine {

    private final RecommendationDao recommendationDao;
    private final RecommendationPriorityResolver priorityResolver;

    public RecommendationEngine(RecommendationDao recommendationDao) {
        this(recommendationDao, new RecommendationPriorityResolver());
    }

    public RecommendationEngine(RecommendationDao recommendationDao, RecommendationPriorityResolver priorityResolver) {
        this.recommendationDao = recommendationDao;
        this.priorityResolver = priorityResolver;
    }

    public Optional<Recommendation> generateBaseRecommendation(
            User user,
            Goal goal,
            Long categoryId
    ) throws SQLException {

        if (user == null || goal == null || categoryId == null) {
            return Optional.empty();
        }

        ActivityLevel activityLevel = user.getActivityLevel() != null ? user.getActivityLevel() : ActivityLevel.SEDENTARY;

        Optional<Recommendation> matchOpt = recommendationDao.findBestMatch(
                goal.getId(),
                categoryId,
                activityLevel
        );

        Recommendation base = matchOpt.orElseGet(() -> buildDefaultBaseRecommendation(goal, categoryId, activityLevel));
        if (base == null) {
            return Optional.empty();
        }

        return Optional.of(personalizeRecommendation(base, user, goal, categoryId));
    }

    public Recommendation personalizeRecommendation(Recommendation base, User user, Goal goal, Long categoryId) {
        if (base == null) return null;
        if (user == null) return base;

        int age = (user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;
        Gender gender = user.getGender() != null ? user.getGender() : Gender.OTHER;
        double weight = (user.getWeightKg() != null && user.getWeightKg() > 0) ? user.getWeightKg() : 70.0;
        double height = (user.getHeightCm() != null && user.getHeightCm() > 0) ? user.getHeightCm() : 170.0;
        ActivityLevel activity = user.getActivityLevel() != null ? user.getActivityLevel() : ActivityLevel.MODERATELY_ACTIVE;
        String goalCode = goal != null && goal.getCode() != null ? goal.getCode().toUpperCase(java.util.Locale.ROOT) : "";

        Long catId = categoryId != null ? categoryId : base.getCategoryId();
        int pillar = determinePillar(catId, base);

        Recommendation personalized = new Recommendation(
                base.getId(),
                base.getGoalId(),
                catId,
                base.getActivityLevel() != null ? base.getActivityLevel() : activity,
                base.getTitle(),
                base.getDescription(),
                base.getRecommendedActions(),
                base.getSuggestedTarget(),
                base.getExamples(),
                base.getImportantNotes()
        );

        switch (pillar) {
            case 1 -> personalizeNutrition(personalized, age, gender, weight, height, activity, goalCode);
            case 2 -> personalizeExercise(personalized, age, gender, weight, height, activity, goalCode);
            case 3 -> personalizeSleep(personalized, age, gender, weight, height, activity, goalCode);
            case 4 -> personalizeHabits(personalized, age, gender, weight, height, activity, goalCode);
            case 7 -> personalizeHydration(personalized, age, gender, weight, height, activity, goalCode);
            default -> {}
        }

        return personalized;
    }

    private int determinePillar(Long categoryId, Recommendation base) {
        if (categoryId != null) {
            if (categoryId == 1L || (categoryId >= 8L && categoryId <= 14L)) return 1;
            if (categoryId == 2L) return 2;
            if (categoryId == 3L) return 3;
            if (categoryId == 4L) return 4;
            if (categoryId == 5L) return 5;
            if (categoryId == 7L) return 7;
        }
        if (base != null) {
            if (base.getCategoryId() != null) {
                long cid = base.getCategoryId();
                if (cid == 1L || (cid >= 8L && cid <= 14L)) return 1;
                if (cid == 2L) return 2;
                if (cid == 3L) return 3;
                if (cid == 4L) return 4;
                if (cid == 5L) return 5;
                if (cid == 7L) return 7;
            }
            String title = base.getTitle() != null ? base.getTitle().toLowerCase(java.util.Locale.ROOT) : "";
            String desc = base.getDescription() != null ? base.getDescription().toLowerCase(java.util.Locale.ROOT) : "";
            String combined = title + " " + desc;
            if (combined.contains("nutrition") || combined.contains("breakfast") || combined.contains("protein") || combined.contains("diet")) return 1;
            if (combined.contains("exercise") || combined.contains("strength") || combined.contains("training") || combined.contains("cardio") || combined.contains("workout")) return 2;
            if (combined.contains("sleep") || combined.contains("recovery") || combined.contains("rest")) return 3;
            if (combined.contains("habit") || combined.contains("micro")) return 4;
            if (combined.contains("hydration") || combined.contains("water")) return 7;
            if (combined.contains("master") || combined.contains("routine")) return 5;
        }
        return 0;
    }

    private void personalizeNutrition(Recommendation r, int age, Gender gender, double weight, double height, ActivityLevel activity, String goalCode) {
        String ageAction;
        String ageTarget;
        String ageNotes;

        if (goalCode.contains("MUSCLE")) {
            if (age < 35) {
                ageAction = "Distribute ~25–30g of high-quality protein per meal to trigger muscle protein synthesis; prioritize complex carbohydrates around workouts for rapid glycogen replenishment and training energy.";
                ageTarget = String.format(java.util.Locale.ROOT, "Target 1.6–2.0 g protein/kg (~%.0f–%.0f g/day), spaced into 25–30g per meal.", weight * 1.6, weight * 2.0);
                ageNotes = "At age " + age + ", muscle protein synthesis responds efficiently to standard 25–30g protein feedings. Focus on consistent meal timing and adequate training fuel.";
            } else if (age >= 50) {
                String genderMicros = (gender == Gender.FEMALE) ? "calcium, vitamin D, and iron" : "calcium, vitamin D, and anti-inflammatory omega-3s";
                ageAction = "Aim for 35–40g+ protein per meal to overcome age-related anabolic resistance and stimulate muscle protein synthesis; incorporate " + genderMicros + " for bone and joint support.";
                ageTarget = String.format(java.util.Locale.ROOT, "Target 1.6–2.0 g protein/kg (~%.0f–%.0f g/day), with higher per-meal boluses of 35–40g+ to stimulate muscle synthesis.", weight * 1.6, weight * 2.0);
                ageNotes = "At age " + age + ", muscle tissue exhibits anabolic resistance requiring higher single-meal protein thresholds (35–40g) to stimulate synthesis. Prioritize micronutrient density and joint-supportive healthy fats.";
            } else {
                ageAction = "Maintain 30–35g protein per meal to support ongoing muscle protein synthesis and metabolic health; balance complex carbs and healthy fats for steady energy.";
                ageTarget = String.format(java.util.Locale.ROOT, "Target 1.6–1.8 g protein/kg (~%.0f–%.0f g/day) with 30–35g per meal.", weight * 1.6, weight * 1.8);
                ageNotes = "Maintain consistent protein distribution across meals to support muscle preservation and recovery.";
            }
        } else if (goalCode.contains("WEIGHT")) {
            if (age >= 50) {
                ageAction = "Prioritize lean protein (25–30g+/meal) to protect lean muscle mass during caloric restriction; include fiber-rich vegetables, calcium, and vitamin D for bone integrity.";
                ageTarget = String.format(java.util.Locale.ROOT, "Moderate deficit (~300–400 kcal/day), 1.2–1.5 g protein/kg (~%.0f–%.0f g/day).", weight * 1.2, weight * 1.5);
                ageNotes = "Preserving lean muscle mass is critical as metabolism slows with age; avoid extreme caloric deficits.";
            } else if (age < 35) {
                ageAction = "Prioritize lean protein and high-fiber carbohydrates for sustained satiety; maintain an active caloric deficit with whole foods.";
                ageTarget = String.format(java.util.Locale.ROOT, "Caloric deficit (~400–500 kcal/day), 1.4–1.8 g protein/kg (~%.0f–%.0f g/day).", weight * 1.4, weight * 1.8);
                ageNotes = "Support active metabolic rate with regular protein feedings and high dietary volume from fiber.";
            } else {
                ageAction = "Structure balanced meals emphasizing protein and fiber to maintain steady glycemic control and consistent satiety.";
                ageTarget = String.format(java.util.Locale.ROOT, "Moderate deficit (~350–450 kcal/day), ~%.0f g protein/day.", weight * 1.4);
                ageNotes = "Focus on sustainable portion control and whole foods consistency.";
            }
        } else {
            if (age >= 50) {
                ageAction = "Emphasize nutrient-dense whole foods with adequate protein (25–30g+/meal) to protect lean muscle mass, plus calcium and vitamin D for bone and joint health.";
                ageTarget = "1.2–1.5 g protein/kg, nutrient-dense whole foods.";
                ageNotes = "Preserving muscle mass and bone mineral density is essential as metabolism changes with age.";
            } else if (age < 35) {
                ageAction = "Balance lean protein and high-fiber complex carbohydrates to fuel active days and promote steady satiety.";
                ageTarget = "Balanced whole foods with consistent protein distribution.";
                ageNotes = "Consistent meal structure supports metabolic energy and prevents afternoon energy dips.";
            } else {
                ageAction = "Prioritize balanced meals with lean protein and fiber, moderating processed sugars to support steady glycemic control.";
                ageTarget = "Balanced macronutrient distribution aligned with calorie targets.";
                ageNotes = "Focus on sustainable portion control and nutrient density.";
            }
        }

        applyPillarEnhancement(r, age, ageAction, ageTarget, ageNotes);
    }

    private void personalizeExercise(Recommendation r, int age, Gender gender, double weight, double height, ActivityLevel activity, String goalCode) {
        String ageAction;
        String ageTarget;
        String ageNotes;

        if (goalCode.contains("MUSCLE")) {
            if (age < 35) {
                ageAction = "Focus on moderate-to-heavy compound loading (6–10 rep range) with weekly progressive overload; standard 5–10 min dynamic warm-up is sufficient before working sets.";
                ageTarget = "3–4 strength sessions/week, 6–10 reps per set, progressive overload weekly.";
                ageNotes = "At age " + age + ", neuromuscular recovery and connective tissues adapt rapidly. Capitalize on high training capacity while maintaining strict form to prevent injury.";
            } else if (age >= 50) {
                ageAction = "Prioritize joint-friendly resistance training in the 8–15 rep range with controlled 2–3 second eccentric lowering; include 10–15 min of dedicated dynamic joint mobility prep before lifting.";
                ageTarget = "3 strength sessions/week, 8–15 reps with controlled tempo, progression every 2–3 weeks.";
                ageNotes = "At age " + age + ", joint longevity and tendon resilience dictate long-term progress. Controlled eccentrics and dedicated joint prep maximize muscle stimulation while protecting joints.";
            } else {
                ageAction = "Balance strength development across 8–12 reps with progressive overload; dedicate 8–10 minutes to dynamic joint mobility prior to loaded movements.";
                ageTarget = "3–4 strength sessions/week, 8–12 reps per set, steady bi-weekly progression.";
                ageNotes = "Balance training intensity with joint preservation and consistent mobility work.";
            }
        } else if (goalCode.contains("WEIGHT") || goalCode.contains("FITNESS")) {
            if (age >= 50) {
                ageAction = "Combine joint-friendly low-impact aerobic activity (brisk walking, cycling) with 2–3 moderate resistance sessions; dedicate 10–15 min to joint mobility prep.";
                ageTarget = "150 minutes low-impact activity/week + 2–3 functional strength sessions.";
                ageNotes = "Low-impact exercise preserves articular joints while elevating metabolic expenditure and cardiovascular stamina.";
            } else if (age < 35) {
                ageAction = "Combine moderate-to-high intensity intervals with compound resistance training; standard 5–10 min dynamic warm-up.";
                ageTarget = "150–200 minutes mixed cardio and strength training per week.";
                ageNotes = "Higher training volume and aerobic intensity effectively maximize metabolic burn and conditioning.";
            } else {
                ageAction = "Maintain structured aerobic and strength sessions, integrating dynamic stretching to preserve mobility.";
                ageTarget = "150 minutes moderate activity per week.";
                ageNotes = "Consistency and joint mobility support sustainable athletic longevity.";
            }
        } else {
            if (age >= 50) {
                ageAction = "Prioritize low-impact aerobic conditioning and functional mobility exercises; dedicate 10–15 minutes to joint warm-up and mobility.";
                ageTarget = "150 minutes moderate low-impact activity/week + 2 mobility sessions.";
                ageNotes = "Low-impact training preserves joint integrity while improving cardiovascular efficiency and stability.";
            } else if (age < 35) {
                ageAction = "Combine moderate-to-high intensity cardiovascular intervals with functional resistance movements; standard 5–10 min dynamic warm-up.";
                ageTarget = "150–200 minutes mixed cardio and strength training per week.";
                ageNotes = "High aerobic conditioning and functional training build a durable metabolic base.";
            } else {
                ageAction = "Maintain structured aerobic and strength sessions, integrating dynamic stretching to preserve mobility.";
                ageTarget = "150 minutes moderate activity per week.";
                ageNotes = "Consistency and joint mobility support sustainable athletic longevity.";
            }
        }

        applyPillarEnhancement(r, age, ageAction, ageTarget, ageNotes);
    }

    private void personalizeHydration(Recommendation r, int age, Gender gender, double weight, double height, ActivityLevel activity, String goalCode) {
        String ageAction;
        String ageTarget;
        String ageNotes;

        double baseLiters = Math.max(2.0, weight * 0.035);
        if (activity == ActivityLevel.MODERATELY_ACTIVE) baseLiters += 0.3;
        else if (activity == ActivityLevel.VERY_ACTIVE || activity == ActivityLevel.EXTRA_ACTIVE) baseLiters += 0.6;

        if (age < 35) {
            ageAction = "Hydrate actively around workout sessions (drink 400–500 mL pre-workout and sip during training); replace sweat losses promptly to sustain muscular power and cellular volumization.";
            ageTarget = String.format(java.util.Locale.ROOT, "Baseline ~%.1f L/day + 400–500 mL per hour of intense training.", baseLiters);
            ageNotes = "At age " + age + ", maintaining cellular hydration directly supports muscle fullness and workout power output. Drink readily to satisfy exercise-induced fluid losses.";
        } else if (age >= 50) {
            ageAction = "Follow a proactive, clock-based hydration schedule rather than waiting for thirst (thirst cues naturally decline with age); drink steadily during daytime hours to lubricate joints and spinal discs, and taper fluids 2 hours before bed for undisturbed sleep.";
            ageTarget = String.format(java.util.Locale.ROOT, "Baseline ~%.1f L/day consumed steadily across daytime hours; taper fluids 90–120 min before sleep.", baseLiters);
            ageNotes = "At age " + age + ", thirst sensation is naturally blunted, so drink on a proactive schedule. Steady daytime hydration cushions joints and spinal discs, while evening tapering protects deep sleep.";
        } else {
            ageAction = "Sip water steadily throughout the workday and around training; keep a water container visible to maintain consistent baseline hydration.";
            ageTarget = String.format(java.util.Locale.ROOT, "Target ~%.1f L/day distributed evenly across active hours.", baseLiters);
            ageNotes = "Consistent daytime hydration maintains energy levels and joint tissue lubrication throughout busy days.";
        }

        applyPillarEnhancement(r, age, ageAction, ageTarget, ageNotes);
    }

    private void personalizeSleep(Recommendation r, int age, Gender gender, double weight, double height, ActivityLevel activity, String goalCode) {
        String ageAction;
        String ageTarget;
        String ageNotes;

        if (goalCode.contains("MUSCLE")) {
            if (age < 35) {
                ageAction = "Capitalize on natural deep-sleep growth hormone pulses with 7–9 hours of sleep; allow a standard 48-hour recovery window between targeting the same muscle group.";
                ageTarget = "7–9 hours sleep nightly, 48h recovery window per muscle group.";
                ageNotes = "At age " + age + ", natural endocrine recovery and deep slow-wave sleep hormone release are high. Maintain consistent sleep timing to maximize muscle adaptation.";
            } else if (age >= 50) {
                ageAction = "Allow 48–72 hours of recovery between intense sessions on the same muscle group for tendon repair; establish a relaxing 45–60 min evening wind-down to support deep restorative sleep.";
                ageTarget = "7–8.5 hours sleep nightly, 48–72h recovery window per muscle group, structured evening wind-down.";
                ageNotes = "At age " + age + ", tendon and systemic recovery require 48–72 hours between intense bouts. Quality restorative sleep and active recovery days are essential to sustain training progress.";
            } else {
                ageAction = "Aim for 7–8.5 hours of consistent sleep nightly; allow 48–60 hours of recovery between intense sessions for muscle and central nervous system replenishment.";
                ageTarget = "7–8.5 hours sleep nightly, 48–60h recovery window.";
                ageNotes = "Protect consistent sleep and wake times to support hormonal balance and tissue recovery.";
            }
        } else {
            if (age >= 50) {
                ageAction = "Aim for 7–8.5 hours of restorative sleep; cultivate a 45–60 minute screen-free wind-down routine and maintain a cool bedroom to support deep sleep phases.";
                ageTarget = "7–8.5 hours sleep nightly, consistent bedtime.";
                ageNotes = "A structured wind-down helps counterbalance age-related reductions in slow-wave deep sleep.";
            } else if (age < 35) {
                ageAction = "Maintain 7–9 hours of consistent sleep with a 45-minute screen curfew before bed to promote restorative sleep architecture.";
                ageTarget = "7–9 hours sleep nightly, fixed sleep/wake times.";
                ageNotes = "Circadian regularity optimizes nocturnal cellular recovery and daytime focus.";
            } else {
                ageAction = "Target 7–8 hours of uninterrupted sleep; dim lights and avoid evening stressors 60 minutes before bedtime.";
                ageTarget = "7–8 hours sleep nightly.";
                ageNotes = "Consistent sleep hygiene supports metabolic health and daily stress resilience.";
            }
        }

        applyPillarEnhancement(r, age, ageAction, ageTarget, ageNotes);
    }

    private void personalizeHabits(Recommendation r, int age, Gender gender, double weight, double height, ActivityLevel activity, String goalCode) {
        String habitBullets;
        String ageTarget;
        String ageNotes;

        if (age < 35) {
            habitBullets = "• Prepare portable high-protein snacks or shakes ahead of time.\n"
                    + "• Stage workout gear and water bottle the night before for friction-free training.\n"
                    + "• Take a brisk 10-minute walk after your largest meal to support glucose disposal.\n"
                    + "• Set a digital screen curfew 45 minutes before sleep to protect sleep onset.";
            ageTarget = "3–4 actionable daily micro-habits";
            ageNotes = "At age " + age + ", low-friction habit triggers help maintain nutritional and training consistency around a dynamic schedule.";
        } else if (age >= 50) {
            habitBullets = "• Complete a 5-minute morning mobility routine to lubricate joints and spine.\n"
                    + "• Keep a water bottle at your workspace and sip on a scheduled hourly basis.\n"
                    + "• Spend 5–10 minutes on gentle spinal decompression or stretching after training.\n"
                    + "• Dim overhead lights 60 minutes before bed and taper liquids for uninterrupted sleep.";
            ageTarget = "3–4 joint-friendly daily micro-habits";
            ageNotes = "At age " + age + ", daily mobility and scheduled hydration habits safeguard joint health, spinal alignment, and restorative sleep.";
        } else {
            habitBullets = "• Pre-portion high-protein meals or snacks to avoid impulsive eating.\n"
                    + "• Take a 5-minute movement break every 90 minutes of desk work.\n"
                    + "• Hydrate with a full glass of water upon waking and before each meal.\n"
                    + "• Dim screens 45 minutes before bed to initiate sleep prep.";
            ageTarget = "3–4 sustainable daily micro-habits";
            ageNotes = "Micro-habits reduce decision fatigue and maintain healthy momentum amidst daily demands.";
        }

        String curAction = r.getRecommendedActions() != null ? r.getRecommendedActions().trim() : "";
        if (curAction.isEmpty()) {
            r.setRecommendedActions(habitBullets);
        } else if (!curAction.contains(habitBullets)) {
            r.setRecommendedActions(curAction + "\n" + habitBullets);
        }

        if (r.getSuggestedTarget() == null || r.getSuggestedTarget().isBlank()) {
            r.setSuggestedTarget(ageTarget);
        }

        String curNotes = r.getImportantNotes() != null ? r.getImportantNotes().trim() : "";
        if (curNotes.isEmpty()) {
            r.setImportantNotes(ageNotes);
        } else if (!curNotes.contains(ageNotes)) {
            r.setImportantNotes(curNotes + " " + ageNotes);
        }
    }

    private void applyPillarEnhancement(Recommendation r, int age, String ageAction, String ageTarget, String ageNotes) {
        String curAction = r.getRecommendedActions() != null ? r.getRecommendedActions().trim() : "";
        String ageLine = "• Age Guidance (Age " + age + "): " + ageAction;

        if (curAction.isEmpty()) {
            r.setRecommendedActions(ageLine);
        } else if (!curAction.contains(ageAction)) {
            r.setRecommendedActions(curAction + "\n" + ageLine);
        }

        if (r.getSuggestedTarget() == null || r.getSuggestedTarget().isBlank() || r.getSuggestedTarget().contains("Approximately")) {
            r.setSuggestedTarget(ageTarget);
        }

        String curNotes = r.getImportantNotes() != null ? r.getImportantNotes().trim() : "";
        if (curNotes.isEmpty()) {
            r.setImportantNotes(ageNotes);
        } else if (!curNotes.contains(ageNotes)) {
            r.setImportantNotes(curNotes + " " + ageNotes);
        }
    }

    private Recommendation buildDefaultBaseRecommendation(Goal goal, Long categoryId, ActivityLevel activityLevel) {
        if (categoryId == null) return null;
        String goalName = goal != null && goal.getName() != null ? goal.getName() : "Daily Wellness";
        String goalCode = goal != null && goal.getCode() != null ? goal.getCode().toUpperCase(java.util.Locale.ROOT) : "";
        long gid = goal != null && goal.getId() != null ? goal.getId() : 1L;

        if (categoryId == 1L || (categoryId >= 8L && categoryId <= 14L)) {
            String actions = goalCode.contains("MUSCLE")
                    ? "Include a strong protein source with each meal; pair with complex carbohydrates."
                    : (goalCode.contains("WEIGHT")
                    ? "Prioritize lean protein and abundant dietary fiber; minimize added sugars."
                    : "Structure balanced meals with lean protein, vegetables, and complex carbohydrates.");
            return new Recommendation(
                    -1L, gid, categoryId, activityLevel,
                    "Nutritional Guidance (" + goalName + ")",
                    "Nutritional strategies calibrated to support your " + goalName + " goal.",
                    actions,
                    "Balanced macronutrient intake aligned with daily target",
                    "Eggs, poultry, fish, legumes, oats, quinoa, vegetables",
                    "Consistent meal timing supports steady metabolic energy."
            );
        }

        if (categoryId == 2L) {
            String actions = goalCode.contains("MUSCLE")
                    ? "Train each major muscle group 2x per week with progressive overload."
                    : "Maintain 3–4 weekly training sessions balancing strength development and cardiovascular conditioning.";
            return new Recommendation(
                    -1L, gid, categoryId, activityLevel,
                    "Exercise & Training Routine (" + goalName + ")",
                    "Physical training protocol calibrated for " + goalName + " and your activity level.",
                    actions,
                    "3–4 sessions per week",
                    "Compound movements, brisk walking, bodyweight circuits",
                    "Prioritize proper movement execution before increasing intensity."
            );
        }

        if (categoryId == 3L) {
            return new Recommendation(
                    -1L, gid, categoryId, activityLevel,
                    "Sleep & Recovery Hygiene (" + goalName + ")",
                    "Rest and recovery protocols to support cellular restoration and hormonal balance.",
                    "Ensure adequate sleep and structured rest days between challenging sessions.",
                    "7–9 hours sleep per night",
                    "Cool dark bedroom, regular sleep schedule, screen-free wind-down",
                    "Tissue adaptation and recovery occur primarily during restorative sleep."
            );
        }

        if (categoryId == 4L) {
            return new Recommendation(
                    -1L, gid, categoryId, activityLevel,
                    "Daily Micro-Habits (" + goalName + ")",
                    "Practical daily habits that compound over time to support " + goalName + ".",
                    "Drink a glass of water upon waking; prepare wholesome meals ahead of time.",
                    "3–4 daily micro-actions",
                    "Morning hydration, planned snack prep, consistent bedtime",
                    "Consistency across small actions produces lasting results."
            );
        }

        if (categoryId == 7L) {
            return new Recommendation(
                    -1L, gid, categoryId, activityLevel,
                    "Hydration Guidance (" + goalName + ")",
                    "Suggested daily water intake guidance to support cellular health and performance.",
                    "Sip water steadily throughout the day rather than large amounts at once.",
                    "Approximately 2.5–3.0 L/day depending on activity",
                    "Water bottle at workstation, glass upon waking, hydration during workouts",
                    "Proper hydration optimizes physical stamina, cognitive clarity, and cellular function."
            );
        }

        return null;
    }

    public RecommendationPriority resolvePriority(Goal goal, ActivityLevel activityLevel, RecommendationCategory category) {
        return priorityResolver.resolve(goal, activityLevel, category);
    }

    public List<String> getPrimaryFocusAreas(Goal goal) {
        return priorityResolver.getPrimaryFocusAreas(goal);
    }

    public List<String> getSupportingAreas(Goal goal) {
        return priorityResolver.getSupportingAreas(goal);
    }

    public String getPersonalizedFocusNarrative(Goal goal, User user) {
        return priorityResolver.getPersonalizedFocusNarrative(goal, user);
    }

    public MatchScoreBreakdown computeMatchScore(User user, Goal goal) {
        boolean hasGoalRecs = false;
        boolean hasActivityRec = false;
        if (goal != null && goal.getId() != null) {
            try {
                hasGoalRecs = recommendationDao.hasRecommendationsForGoal(goal.getId());
                if (user != null && user.getActivityLevel() != null) {
                    hasActivityRec = recommendationDao.hasActivitySpecificRecommendation(goal.getId(), user.getActivityLevel());
                }
            } catch (SQLException ignored) {
                hasGoalRecs = true;
            }
        }
        return MatchScoreBreakdown.compute(user, goal, hasGoalRecs, hasActivityRec);
    }

    public CalculationTrace buildCalculationTrace(User user, Goal goal, CalorieService calorieService) {
        MatchScoreBreakdown score = computeMatchScore(user, goal);
        return new CalculationTrace(user, goal, calorieService, score);
    }

    public DailyBlueprint buildDailyBlueprint(User user, Goal goal) {
        ActivityLevel activity = user != null && user.getActivityLevel() != null
                ? user.getActivityLevel()
                : ActivityLevel.SEDENTARY;

        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        DailyBlueprint blueprint = new DailyBlueprint(goal, activity);
        String goalCode = goal != null && goal.getCode() != null ? goal.getCode().toUpperCase() : "";

        // Morning
        if (age >= 50) {
            blueprint.addMorning("\uD83D\uDCA7", "Hydration", // 💧
                    "Drink 400\u2013500 mL of room-temperature water upon waking; hydrate proactively on a schedule rather than waiting for thirst.");
            if (goalCode.contains("MUSCLE")) {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Include 35\u201340g+ protein at breakfast (e.g. eggs, Greek yogurt) with calcium and vitamin D to stimulate muscle protein synthesis.");
            } else if (goalCode.contains("WEIGHT")) {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Choose a nutrient-dense, high-protein breakfast (25\u201330g+ protein) with ample fiber to protect lean mass during weight loss.");
            } else if (goalCode.contains("SKIN")) {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Include antioxidant-rich foods, healthy fats, and hydrating fruits with breakfast.");
            } else {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Enjoy a wholesome, protein-rich breakfast with anti-inflammatory healthy fats and complex carbohydrates.");
            }
            blueprint.addMorning("\uD83C\uDFC3", "Activity", // 🏃
                    "10\u201315 minutes of dedicated joint mobility, spinal decompression, and dynamic joint lubrication.");
        } else if (age < 35) {
            blueprint.addMorning("\uD83D\uDCA7", "Hydration", // 💧
                    "Drink 400\u2013500 mL of water upon waking to kickstart cellular hydration and metabolism.");
            if (goalCode.contains("MUSCLE")) {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Include ~25\u201330g high-quality protein at breakfast paired with complex carbohydrates to fuel training glycogen.");
            } else if (goalCode.contains("WEIGHT")) {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Choose a balanced, lower-calorie breakfast emphasizing lean protein and high fiber for satiety.");
            } else if (goalCode.contains("SKIN")) {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Include antioxidant-rich foods, healthy fats, and hydrating fruits with breakfast.");
            } else {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Enjoy a nourishing, balanced breakfast to provide steady morning energy.");
            }
            blueprint.addMorning("\uD83C\uDFC3", "Activity", // 🏃
                    "10\u201315 minutes of light morning mobility, dynamic stretching, or an easy wake-up walk.");
        } else {
            blueprint.addMorning("\uD83D\uDCA7", "Hydration", // 💧
                    "Drink 400\u2013500 mL of room-temperature water upon waking to kickstart hydration and metabolism.");
            if (goalCode.contains("MUSCLE")) {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Include 30\u201335g protein at breakfast paired with complex carbohydrates to sustain muscle protein synthesis.");
            } else if (goalCode.contains("WEIGHT")) {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Choose a balanced, lower-calorie breakfast emphasizing lean protein and high fiber for satiety.");
            } else if (goalCode.contains("SKIN")) {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Include antioxidant-rich foods, healthy fats, and hydrating fruits with breakfast.");
            } else {
                blueprint.addMorning("\uD83C\uDF4E", "Nutrition", // 🍎
                        "Enjoy a nourishing, balanced breakfast to provide steady morning energy.");
            }
            blueprint.addMorning("\uD83C\uDFC3", "Activity", // 🏃
                    "10\u201315 minutes of dynamic stretching and core activation to prepare for the day.");
        }

        // Midday
        blueprint.addMidday("\uD83E\uDD57", "Nutrition", // 🥗
                "Follow a balanced lunch structure with lean protein, colorful vegetables, and portion control.");
        if (age >= 50) {
            if (goalCode.contains("MUSCLE")) {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Execute joint-friendly resistance training in the 8\u201315 rep range with controlled eccentrics and 10\u201315 min mobility prep.");
            } else if (goalCode.contains("FITNESS") || goalCode.contains("WEIGHT")) {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Complete joint-friendly low-impact cardio or brisk 25\u201330 minute walking to elevate heart rate safely.");
            } else if (goalCode.contains("SLEEP")) {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Engage in moderate daytime physical activity to reinforce natural circadian drive.");
            } else {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Maintain joint-friendly physical movement appropriate for your current activity level.");
            }
            blueprint.addMidday("\uD83D\uDCA7", "Hydration", // 💧
                    "Sip water steadily on a scheduled hourly cadence to maintain joint lubrication and avoid afternoon fatigue.");
        } else if (age < 35) {
            if (goalCode.contains("MUSCLE")) {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Execute scheduled resistance training focusing on progressive overload, compound lifts (6\u201310 rep range), and high volume.");
            } else if (goalCode.contains("FITNESS") || goalCode.contains("WEIGHT")) {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Complete a scheduled workout or brisk 25\u201330 minute walk to elevate heart rate safely.");
            } else if (goalCode.contains("SLEEP")) {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Engage in moderate daytime physical activity to reinforce natural circadian drive.");
            } else {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Maintain structured physical movement appropriate for your current activity level.");
            }
            blueprint.addMidday("\uD83D\uDCA7", "Hydration", // 💧
                    "Hydrate actively before and during training sessions; replenish sweat losses promptly.");
        } else {
            if (goalCode.contains("MUSCLE")) {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Execute scheduled resistance training balancing strength progression (8\u201312 reps) with joint integrity.");
            } else if (goalCode.contains("FITNESS") || goalCode.contains("WEIGHT")) {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Complete a scheduled workout or brisk 25\u201330 minute walk to elevate heart rate safely.");
            } else if (goalCode.contains("SLEEP")) {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Engage in moderate daytime physical activity to reinforce natural circadian drive.");
            } else {
                blueprint.addMidday("\uD83C\uDFC3", "Exercise", // 🏃
                        "Maintain structured physical movement appropriate for your current activity level.");
            }
            blueprint.addMidday("\uD83D\uDCA7", "Hydration", // 💧
                    "Sip water steadily through midday hours to avoid afternoon fatigue and dehydration.");
        }

        // Evening
        if (age >= 50) {
            blueprint.addEvening("\uD83C\uDF72", "Dinner", // 🍲
                    "Wholesome dinner with lean protein and anti-inflammatory healthy fats; taper large liquid intake 2 hours before bed.");
            blueprint.addEvening("\uD83E\uDDD8", "Recovery", // 🧘
                    "Cultivate a relaxing 45\u201360 min wind-down routine (dim lights, gentle stretching, low stimulation) to support deep sleep.");
            blueprint.addEvening("\uD83D\uDE34", "Sleep", // 😴
                    "Target 7\u20138.5 hours of uninterrupted sleep; allow 48\u201372 hours of recovery between intense training sessions.");
        } else if (age < 35) {
            blueprint.addEvening("\uD83C\uDF72", "Dinner", // 🍲
                    "Wholesome dinner with protein and complex carbs 2\u20133 hours before bed; avoid heavy refined sugars.");
            blueprint.addEvening("\uD83E\uDDD8", "Recovery", // 🧘
                    "Set a digital screen curfew 45\u201360 minutes before bed to allow natural melatonin release.");
            blueprint.addEvening("\uD83D\uDE34", "Sleep", // 😴
                    "Target 7\u20139 hours of deep sleep to capitalize on nocturnal growth hormone release; allow 48h muscle recovery.");
        } else {
            blueprint.addEvening("\uD83C\uDF72", "Dinner", // 🍲
                    "Enjoy a lighter, wholesome dinner 2\u20133 hours before bed; avoid heavy refined sugars.");
            blueprint.addEvening("\uD83E\uDDD8", "Recovery", // 🧘
                    "Dim lights 60 minutes before bed, limit blue-light screens, and allow mental decompression.");
            blueprint.addEvening("\uD83D\uDE34", "Sleep", // 😴
                    "Target 7\u20139 hours of uninterrupted restorative sleep in a cool, quiet, dark environment.");
        }

        return blueprint;
    }
}