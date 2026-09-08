package com.lifeforge.controller;

import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.User;
import com.lifeforge.service.JasperReportsService;
import com.lifeforge.service.RecommendationService;
import net.sf.jasperreports.engine.JRException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the exportable health report (PDF/HTML) for a user. The
 * report only contains recommendation guidance - profile, goal,
 * calorie/hydration figures and the recommendations relevant to the
 * user's current goal. Never tracking data.
 */
public class ReportController extends BaseController {

    private final JasperReportsService jasperReportsService;
    private final RecommendationService recommendationService;

    public ReportController(JasperReportsService jasperReportsService,
                            RecommendationService recommendationService) {
        this.jasperReportsService = jasperReportsService;
        this.recommendationService = recommendationService;
    }

    public boolean exportHealthReportPdf(User user, Goal goal, Path outputFile) {
        return export(user, goal, outputFile, true);
    }

    public boolean exportHealthReportHtml(User user, Goal goal, Path outputFile) {
        return export(user, goal, outputFile, false);
    }

    private boolean export(User user, Goal goal, Path outputFile, boolean asPdf) {
        try {
            if (outputFile.getParent() != null && !Files.exists(outputFile.getParent())) {
                Files.createDirectories(outputFile.getParent());
            }
            List<Recommendation> recommendations = recommendationsForGoal(goal);
            if (asPdf) {
                jasperReportsService.exportHealthReportToPdf(user, goal, recommendations, outputFile);
            } else {
                jasperReportsService.exportHealthReportToHtml(user, goal, recommendations, outputFile);
            }
            return true;
        } catch (SQLException | JRException | IOException e) {
            setError(e, "Report export failed.");
            return false;
        }
    }

    private List<Recommendation> recommendationsForGoal(Goal goal) throws SQLException {
        List<Recommendation> relevant = new ArrayList<>();
        for (Recommendation recommendation : recommendationService.listAllRecommendations()) {
            if (goal.getId().equals(recommendation.getGoalId())) {
                relevant.add(recommendation);
            }
        }
        return relevant;
    }
}