package com.lifeforge.service;

import com.lifeforge.model.*;
import net.sf.jasperreports.engine.JRException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JasperReportsServiceTest {

    @Test
    void testExportHealthReportToPdf(@TempDir Path tempDir) throws Exception {
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        ExportService exportService = new ExportService(calorieService, hydrationService);
        JasperReportsService jasperReportsService = new JasperReportsService(exportService);

        User user = new User();
        user.setId(1L);
        user.setFullName("John Doe");
        user.setUsername("johndoe");
        user.setEmail("john@example.com");
        user.setAge(25);
        user.setGender(Gender.MALE);
        user.setHeightCm(175.0);
        user.setWeightKg(70.0);
        user.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        user.setRole(Role.USER);

        Goal goal = new Goal(1L, "BUILD_MUSCLE", "Build Muscle", "Hypertrophy training", true);

        Path pdfPath = tempDir.resolve("test-health-report.pdf");

        jasperReportsService.exportHealthReportToPdf(user, goal, Collections.emptyList(), pdfPath);

        assertTrue(Files.exists(pdfPath), "PDF file should exist after export");
        assertTrue(Files.size(pdfPath) > 0, "PDF file should not be empty");
    }

    @Test
    void testExportSavedRecommendationsToPdf(@TempDir Path tempDir) throws Exception {
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        ExportService exportService = new ExportService(calorieService, hydrationService);
        JasperReportsService jasperReportsService = new JasperReportsService(exportService);

        User user = new User();
        user.setId(1L);
        user.setFullName("Jane Doe");
        user.setUsername("janedoe");
        user.setEmail("jane@example.com");

        Path pdfPath = tempDir.resolve("test-saved-recs.pdf");
        jasperReportsService.exportSavedRecommendationsToPdf(user, 5, pdfPath);

        assertTrue(Files.exists(pdfPath), "Saved recommendations PDF file should exist after export");
        assertTrue(Files.size(pdfPath) > 0, "Saved recommendations PDF file should not be empty");
    }

    @Test
    void testClinicalMisalignmentInterceptionUnderweightDeficit(@TempDir Path tempDir) throws Exception {
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        ExportService exportService = new ExportService(calorieService, hydrationService);
        JasperReportsService jasperReportsService = new JasperReportsService(exportService);

        // Underweight user: 175cm, 50kg -> BMI ~16.3 (< 18.5)
        User user = new User();
        user.setId(2L);
        user.setFullName("Sophea Underweight");
        user.setUsername("sophea");
        user.setEmail("sophea@example.com");
        user.setAge(22);
        user.setGender(Gender.FEMALE);
        user.setHeightCm(175.0);
        user.setWeightKg(50.0);
        user.setActivityLevel(ActivityLevel.SEDENTARY);
        user.setRole(Role.USER);

        Goal loseGoal = new Goal(2L, "LOSE_WEIGHT", "Lose Weight", "Aggressive weight loss", true);

        var data = exportService.buildReportData(user, loseGoal, Collections.emptyList());

        // Assert clinical safety interception
        assertTrue((Boolean) data.get("isUnderweightDeficitWarning"),
                "isUnderweightDeficitWarning should be TRUE when BMI < 18.5 and goal is Lose Weight");
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", data.get("goalStatusLevel"));
        org.junit.jupiter.api.Assertions.assertEquals("CLINICAL MISALIGNMENT DETECTED", data.get("goalStatusTitle"));

        // Calorie target must be overridden to maintenance (TDEE) rather than deficit
        double tdee = (Double) data.get("tdee");
        double suggestedTarget = (Double) data.get("suggestedCalorieTarget");
        org.junit.jupiter.api.Assertions.assertEquals(tdee, suggestedTarget, 0.01,
                "Calorie target must be overridden to TDEE maintenance baseline for safety");

        // Rationale must note safety interception
        String explanation = (String) data.get("AI_EXPLANATION");
        assertTrue(explanation.contains("CRITICAL SAFETY INTERCEPTION"),
                "AI clinical rationale must explain the safety override");

        Path pdfPath = tempDir.resolve("test-underweight-warning.pdf");
        jasperReportsService.exportHealthReportToPdf(user, loseGoal, Collections.emptyList(), pdfPath);
        assertTrue(Files.exists(pdfPath) && Files.size(pdfPath) > 0);
    }

    @Test
    void testNormalWeightLossAlignment() {
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        ExportService exportService = new ExportService(calorieService, hydrationService);

        // Normal/Overweight user: 175cm, 80kg -> BMI ~26.1
        User user = new User();
        user.setId(3L);
        user.setFullName("Chantu Healthy");
        user.setUsername("chantu");
        user.setEmail("chantu@example.com");
        user.setAge(28);
        user.setGender(Gender.MALE);
        user.setHeightCm(175.0);
        user.setWeightKg(80.0);
        user.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        user.setRole(Role.USER);

        Goal loseGoal = new Goal(2L, "LOSE_WEIGHT", "Lose Weight", "Caloric deficit regimen", true);

        var data = exportService.buildReportData(user, loseGoal, Collections.emptyList());

        org.junit.jupiter.api.Assertions.assertFalse((Boolean) data.get("isUnderweightDeficitWarning"));
        org.junit.jupiter.api.Assertions.assertEquals("WELL_ALIGNED", data.get("goalStatusLevel"));

        double tdee = (Double) data.get("tdee");
        double suggestedTarget = (Double) data.get("suggestedCalorieTarget");
        assertTrue(suggestedTarget < tdee, "Normal weight user should have a calorie deficit target");
    }

    @Test
    void testReportGeneratesExactlyTwoPages() throws Exception {
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        ExportService exportService = new ExportService(calorieService, hydrationService);

        User user = new User();
        user.setId(1L);
        user.setFullName("John Doe");
        user.setAge(25);
        user.setGender(Gender.MALE);
        user.setHeightCm(175.0);
        user.setWeightKg(70.0);
        user.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);

        Goal goal = new Goal(1L, "BUILD_MUSCLE", "Build Muscle", "Hypertrophy", true);
        var data = exportService.buildReportData(user, goal, Collections.emptyList());

        var is = getClass().getResourceAsStream("/reports/lifeForge_report.jrxml");
        org.junit.jupiter.api.Assertions.assertNotNull(is, "lifeForge_report.jrxml must be available on classpath");
        var jasperReport = net.sf.jasperreports.engine.JasperCompileManager.compileReport(is);
        var jasperPrint = net.sf.jasperreports.engine.JasperFillManager.fillReport(
                jasperReport, data, new net.sf.jasperreports.engine.JREmptyDataSource(1));

        org.junit.jupiter.api.Assertions.assertEquals(2, jasperPrint.getPages().size(),
                "The LIFEForge Health Report must generate exactly 2 pages");
    }

    @Test
    void testDynamicMacronutrientBlueprintCalculations() {
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        ExportService exportService = new ExportService(calorieService, hydrationService);

        // Profile A: Chantu (72kg, BUILD_MUSCLE, 2900 kcal)
        User chantu = new User();
        chantu.setId(10L);
        chantu.setFullName("Chantu Health Enthusiast");
        chantu.setAge(28);
        chantu.setGender(Gender.MALE);
        chantu.setHeightCm(175.0);
        chantu.setWeightKg(72.0);
        chantu.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);

        Goal buildMuscleGoal = new Goal(1L, "BUILD_MUSCLE", "Build Lean Muscle", "Hypertrophy", true);
        var chantuData = exportService.buildReportData(chantu, buildMuscleGoal, Collections.emptyList());

        org.junit.jupiter.api.Assertions.assertEquals("2,900", chantuData.get("TARGET_CALORIES"));
        org.junit.jupiter.api.Assertions.assertEquals("145 - 165", chantuData.get("PROTEIN_RANGE"));
        org.junit.jupiter.api.Assertions.assertEquals("145 - 165", chantuData.get("PROTEIN_GRAMS"));
        org.junit.jupiter.api.Assertions.assertEquals("2.0 - 2.2", chantuData.get("PROTEIN_PER_KG"));
        org.junit.jupiter.api.Assertions.assertEquals("2.0 - 2.2", chantuData.get("PROTEIN_RATIO"));
        org.junit.jupiter.api.Assertions.assertEquals("340 - 365", chantuData.get("CARB_RANGE"));
        org.junit.jupiter.api.Assertions.assertEquals("340 - 365", chantuData.get("CARB_GRAMS"));
        org.junit.jupiter.api.Assertions.assertEquals("65 - 75", chantuData.get("FAT_RANGE"));
        org.junit.jupiter.api.Assertions.assertEquals("65 - 75", chantuData.get("FAT_GRAMS"));
        org.junit.jupiter.api.Assertions.assertNotNull(chantuData.get("iconProtein"));
        org.junit.jupiter.api.Assertions.assertNotNull(chantuData.get("iconCarbs"));
        org.junit.jupiter.api.Assertions.assertNotNull(chantuData.get("iconFats"));
        org.junit.jupiter.api.Assertions.assertTrue(((String) chantuData.get("MACRO_CADENCE")).contains("Fueling Cadence"));

        // Profile B: Chhom Sovann (65kg, BUILD_MUSCLE, dynamically scaled macros)
        User chhom = new User();
        chhom.setId(12L);
        chhom.setFullName("Chhom Sovann");
        chhom.setAge(28);
        chhom.setGender(Gender.MALE);
        chhom.setHeightCm(172.0);
        chhom.setWeightKg(65.0);
        chhom.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);

        var chhomData = exportService.buildReportData(chhom, buildMuscleGoal, Collections.emptyList());
        org.junit.jupiter.api.Assertions.assertEquals("130 - 145", chhomData.get("PROTEIN_RANGE"));
        org.junit.jupiter.api.Assertions.assertEquals("130 - 145", chhomData.get("PROTEIN_GRAMS"));
        org.junit.jupiter.api.Assertions.assertEquals("2.0 - 2.2", chhomData.get("PROTEIN_RATIO"));
        org.junit.jupiter.api.Assertions.assertEquals("310 - 330", chhomData.get("CARB_RANGE"));
        org.junit.jupiter.api.Assertions.assertEquals("310 - 330", chhomData.get("CARB_GRAMS"));
        org.junit.jupiter.api.Assertions.assertEquals("55 - 65", chhomData.get("FAT_RANGE"));
        org.junit.jupiter.api.Assertions.assertEquals("55 - 65", chhomData.get("FAT_GRAMS"));

        // Safe Math & Edge Cases Guardrail test
        User edgeUser = new User();
        edgeUser.setId(99L);
        edgeUser.setFullName("Edge Case User");
        edgeUser.setHeightCm(-10.0);
        edgeUser.setWeightKg(0.0);
        var edgeData = exportService.buildReportData(edgeUser, buildMuscleGoal, Collections.emptyList());
        org.junit.jupiter.api.Assertions.assertNotNull(edgeData.get("PROTEIN_RANGE"));
        org.junit.jupiter.api.Assertions.assertNotNull(edgeData.get("CARB_RANGE"));
        org.junit.jupiter.api.Assertions.assertNotNull(edgeData.get("FAT_RANGE"));
        org.junit.jupiter.api.Assertions.assertTrue((Double) edgeData.get("USER_BMI") > 0);
    }

    @Test
    void testNutritionCardBugFixAndCategoryRouting() {
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        ExportService exportService = new ExportService(calorieService, hydrationService);

        User user = new User();
        user.setId(10L);
        user.setFullName("Chhom Sovann");
        user.setAge(26);
        user.setGender(Gender.MALE);
        user.setHeightCm(172.0);
        user.setWeightKg(65.0);
        user.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);

        Goal goal = new Goal(1L, "BUILD_MUSCLE", "Build Muscle", "Hypertrophy", true);

        Recommendation r1 = new Recommendation(1L, 1L, 1L, ActivityLevel.MODERATELY_ACTIVE,
                "Hypertrophy Fueling Protocol",
                "Consume 2.0g protein/kg body mass distributed across 4 structured meals.",
                "Target 35g protein per meal; include whey and lean poultry",
                "140g protein/day", "Chicken breast, Greek yogurt, eggs", "Maintain adequate hydration");

        Recommendation r2 = new Recommendation(2L, 1L, 2L, ActivityLevel.MODERATELY_ACTIVE,
                "Compound Resistance Protocol",
                "Execute 4 weekly sessions focusing on squats, deadlifts, and bench presses.",
                "Focus on progressive overload with 8-12 rep ranges; allow 2 min rest",
                "4 sessions/week", "Barbell squat 4x8", "Prioritize form");

        // Note: Sleep description contains "protein synthesis" — previously bugged into Nutrition card!
        Recommendation r3 = new Recommendation(3L, 1L, 3L, ActivityLevel.MODERATELY_ACTIVE,
                "Deep Sleep & Growth Hormone Architecture",
                "Target 8.0 hours of deep sleep to maximize nocturnal muscular protein synthesis.",
                "Maintain 19°C bedroom temperature; cease screens 45 min before sleep",
                "8 hours/night", "Magnesium glycinate", "Avoid late-night caffeine");

        var data = exportService.buildReportData(user, goal, List.of(r1, r2, r3));

        // Verify bug fix: Nutrition card must display nutrition text, NOT sleep text!
        String nutTitle = (String) data.get("recNutritionTitle");
        String nutDesc = (String) data.get("recNutritionDesc");
        org.junit.jupiter.api.Assertions.assertEquals("Hypertrophy Fueling Protocol", nutTitle);
        org.junit.jupiter.api.Assertions.assertTrue(nutDesc.contains("Consume 2.0g protein/kg"),
                "Nutrition description must display nutrition protocol, not sleep protocol");
        org.junit.jupiter.api.Assertions.assertFalse(nutDesc.contains("deep sleep"),
                "Nutrition card must NOT contain sleep text");

        // Verify sleep card receives the sleep recommendation
        String sleepTitle = (String) data.get("recSleepTitle");
        String sleepDesc = (String) data.get("recSleepDesc");
        org.junit.jupiter.api.Assertions.assertEquals("Deep Sleep & Growth Hormone Architecture", sleepTitle);
        org.junit.jupiter.api.Assertions.assertTrue(sleepDesc.contains("Target 8.0 hours of deep sleep"));
    }

    @Test
    void testUnifiedTwoPageLayoutAcrossAllProfiles() throws Exception {
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        ExportService exportService = new ExportService(calorieService, hydrationService);

        var is = getClass().getResourceAsStream("/reports/lifeForge_report.jrxml");
        org.junit.jupiter.api.Assertions.assertNotNull(is, "lifeForge_report.jrxml must be available on classpath");
        var jasperReport = net.sf.jasperreports.engine.JasperCompileManager.compileReport(is);

        Goal buildMuscleGoal = new Goal(1L, "BUILD_MUSCLE", "Build Lean Muscle", "Hypertrophy", true);
        Goal wellnessGoal = new Goal(7L, "GENERAL_WELLNESS", "General Wellness", "Holistic health", true);

        // Profile A: Chantu (72kg, Muscle Gain)
        User chantu = new User();
        chantu.setId(10L);
        chantu.setFullName("Chantu Health Enthusiast");
        chantu.setAge(28);
        chantu.setGender(Gender.MALE);
        chantu.setHeightCm(175.0);
        chantu.setWeightKg(72.0);
        chantu.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        var chantuData = exportService.buildReportData(chantu, buildMuscleGoal, Collections.emptyList());
        var chantuPrint = net.sf.jasperreports.engine.JasperFillManager.fillReport(
                jasperReport, chantuData, new net.sf.jasperreports.engine.JREmptyDataSource(1));
        org.junit.jupiter.api.Assertions.assertEquals(2, chantuPrint.getPages().size(),
                "Profile A (Chantu) must generate exactly 2 pages");

        // Profile B: Chhom Sovann (65kg, Muscle Gain)
        User chhom = new User();
        chhom.setId(12L);
        chhom.setFullName("Chhom Sovann");
        chhom.setAge(28);
        chhom.setGender(Gender.MALE);
        chhom.setHeightCm(172.0);
        chhom.setWeightKg(65.0);
        chhom.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);
        var chhomData = exportService.buildReportData(chhom, buildMuscleGoal, Collections.emptyList());
        var chhomPrint = net.sf.jasperreports.engine.JasperFillManager.fillReport(
                jasperReport, chhomData, new net.sf.jasperreports.engine.JREmptyDataSource(1));
        org.junit.jupiter.api.Assertions.assertEquals(2, chhomPrint.getPages().size(),
                "Profile B (Chhom Sovann) must generate exactly 2 pages");

        // Profile C: Arbitrary New User (52kg female, General Wellness)
        User newUser = new User();
        newUser.setId(99L);
        newUser.setFullName("Dany NewUser");
        newUser.setAge(24);
        newUser.setGender(Gender.FEMALE);
        newUser.setHeightCm(162.0);
        newUser.setWeightKg(52.0);
        newUser.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        var newUserData = exportService.buildReportData(newUser, wellnessGoal, Collections.emptyList());
        var newUserPrint = net.sf.jasperreports.engine.JasperFillManager.fillReport(
                jasperReport, newUserData, new net.sf.jasperreports.engine.JREmptyDataSource(1));
        org.junit.jupiter.api.Assertions.assertEquals(2, newUserPrint.getPages().size(),
                "Profile C (New User) must generate exactly 2 pages");
    }

    @Test
    void testExportDemoPdfs() throws Exception {
        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        ExportService exportService = new ExportService(calorieService, hydrationService);
        JasperReportsService jasperReportsService = new JasperReportsService(exportService);

        Goal buildMuscleGoal = new Goal(1L, "BUILD_MUSCLE", "Build Lean Muscle", "Progressive hypertrophy & strength", true);

        Recommendation r1 = new Recommendation(1L, 1L, 1L, ActivityLevel.MODERATELY_ACTIVE,
                "Hypertrophy Fueling Protocol",
                "Consume 2.0g protein/kg body mass distributed across 4 structured meals.",
                "Target 35g protein per meal; include whey/casein and chicken breast",
                "150g protein/day", "Chicken breast, Greek yogurt, eggs", "Maintain adequate hydration");

        Recommendation r2 = new Recommendation(2L, 1L, 2L, ActivityLevel.MODERATELY_ACTIVE,
                "Compound Progressive Hypertrophy Routine",
                "Execute 4 weekly sessions focusing on squats, deadlifts, and bench presses.",
                "Focus on progressive overload with 8-12 rep ranges; 2 min rest",
                "4 sessions/week", "Barbell squat 4x8, Bench press 4x10", "Prioritize spinal stability");

        Recommendation r3 = new Recommendation(3L, 1L, 3L, ActivityLevel.MODERATELY_ACTIVE,
                "Deep Sleep & Growth Hormone Architecture",
                "Target 8.0 hours of deep sleep to maximize nocturnal muscular protein synthesis.",
                "Maintain 19°C bedroom temperature; cease screens 45 min before sleep",
                "8 hours/night", "Magnesium glycinate, blackout curtains", "Avoid late-night caffeine");

        List<Recommendation> recs = List.of(r1, r2, r3);

        Path outDir = Path.of("reports");
        if (!Files.exists(outDir)) Files.createDirectories(outDir);

        // Export Profile A: Chantu
        User chantu = new User();
        chantu.setId(10L);
        chantu.setFullName("Chantu Health Enthusiast");
        chantu.setUsername("chantu");
        chantu.setEmail("chantu@lifeforge.app");
        chantu.setAge(28);
        chantu.setGender(Gender.MALE);
        chantu.setHeightCm(175.0);
        chantu.setWeightKg(72.0);
        chantu.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        Path chantuPdf = outDir.resolve("LifeForge_Health_Report_chantu.pdf");
        jasperReportsService.exportHealthReportToPdf(chantu, buildMuscleGoal, recs, chantuPdf);
        assertTrue(Files.exists(chantuPdf) && Files.size(chantuPdf) > 0);

        // Export Profile B: Chhom Sovann
        User chhom = new User();
        chhom.setId(12L);
        chhom.setFullName("Chhom Sovann");
        chhom.setUsername("chhom_sovann");
        chhom.setEmail("chhom.sovann@lifeforge.app");
        chhom.setAge(28);
        chhom.setGender(Gender.MALE);
        chhom.setHeightCm(172.0);
        chhom.setWeightKg(65.0);
        chhom.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);
        Path chhomPdf = outDir.resolve("LifeForge_Health_Report_chhom_sovann.pdf");
        jasperReportsService.exportHealthReportToPdf(chhom, buildMuscleGoal, recs, chhomPdf);
        assertTrue(Files.exists(chhomPdf) && Files.size(chhomPdf) > 0);

        // Export Profile C: New User
        User newUser = new User();
        newUser.setId(99L);
        newUser.setFullName("Dany NewUser");
        newUser.setUsername("dany_newuser");
        newUser.setEmail("dany@lifeforge.app");
        newUser.setAge(24);
        newUser.setGender(Gender.FEMALE);
        newUser.setHeightCm(162.0);
        newUser.setWeightKg(52.0);
        newUser.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        Goal wellnessGoal = new Goal(7L, "GENERAL_WELLNESS", "General Wellness", "Holistic health", true);
        Path newUserPdf = outDir.resolve("LifeForge_Health_Report_new_user.pdf");
        jasperReportsService.exportHealthReportToPdf(newUser, wellnessGoal, recs, newUserPdf);
        assertTrue(Files.exists(newUserPdf) && Files.size(newUserPdf) > 0);

        // Render preview images of Page 1 and Page 2 for visual validation
        try {
            var is = getClass().getResourceAsStream("/reports/lifeForge_report.jrxml");
            var jr = net.sf.jasperreports.engine.JasperCompileManager.compileReport(is);
            var data = exportService.buildReportData(chantu, buildMuscleGoal, recs);
            var jp = net.sf.jasperreports.engine.JasperFillManager.fillReport(
                    jr, data, new net.sf.jasperreports.engine.JREmptyDataSource(1));
            java.awt.image.BufferedImage p1 = (java.awt.image.BufferedImage) net.sf.jasperreports.engine.JasperPrintManager.printPageToImage(jp, 0, 1.5f);
            javax.imageio.ImageIO.write(p1, "PNG", outDir.resolve("preview_page_1.png").toFile());
            java.awt.image.BufferedImage p2 = (java.awt.image.BufferedImage) net.sf.jasperreports.engine.JasperPrintManager.printPageToImage(jp, 1, 1.5f);
            javax.imageio.ImageIO.write(p2, "PNG", outDir.resolve("preview_page_2.png").toFile());

            // Also render Chhom Sovann preview images to prove identical SaaS 2-page template
            var chhomData = exportService.buildReportData(chhom, buildMuscleGoal, recs);
            var chhomJp = net.sf.jasperreports.engine.JasperFillManager.fillReport(
                    jr, chhomData, new net.sf.jasperreports.engine.JREmptyDataSource(1));
            java.awt.image.BufferedImage cp1 = (java.awt.image.BufferedImage) net.sf.jasperreports.engine.JasperPrintManager.printPageToImage(chhomJp, 0, 1.5f);
            javax.imageio.ImageIO.write(cp1, "PNG", outDir.resolve("preview_chhom_p1.png").toFile());
            java.awt.image.BufferedImage cp2 = (java.awt.image.BufferedImage) net.sf.jasperreports.engine.JasperPrintManager.printPageToImage(chhomJp, 1, 1.5f);
            javax.imageio.ImageIO.write(cp2, "PNG", outDir.resolve("preview_chhom_p2.png").toFile());
        } catch (Exception ignored) {
        }

        // Export underweight caution PDF for clinical verification
        User underweightUser = new User();
        underweightUser.setId(11L);
        underweightUser.setFullName("Sophea Caution Case");
        underweightUser.setUsername("sophea_caution");
        underweightUser.setEmail("sophea@lifeforge.app");
        underweightUser.setAge(21);
        underweightUser.setGender(Gender.FEMALE);
        underweightUser.setHeightCm(170.0);
        underweightUser.setWeightKg(46.0); // BMI = 15.9 (< 18.5)
        underweightUser.setActivityLevel(ActivityLevel.SEDENTARY);

        Goal loseGoal = new Goal(2L, "LOSE_WEIGHT", "Lose Weight", "Caloric deficit regimen", true);
        Path cautionPdf = outDir.resolve("LifeForge_Health_Report_underweight_caution.pdf");
        jasperReportsService.exportHealthReportToPdf(underweightUser, loseGoal, recs, cautionPdf);
        assertTrue(Files.exists(cautionPdf) && Files.size(cautionPdf) > 0);
    }
}

