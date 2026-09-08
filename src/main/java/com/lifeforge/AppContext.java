package com.lifeforge;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.controller.AdminController;
import com.lifeforge.controller.AuthController;
import com.lifeforge.controller.GoalController;
import com.lifeforge.controller.PasswordResetController;
import com.lifeforge.controller.RecommendationController;
import com.lifeforge.controller.ReportController;
import com.lifeforge.controller.SavedRecommendationController;
import com.lifeforge.controller.UserController;
import com.lifeforge.dao.AuditLogDao;
import com.lifeforge.dao.GoalDao;
import com.lifeforge.dao.PasswordResetDao;
import com.lifeforge.dao.RecommendationCategoryDao;
import com.lifeforge.dao.RecommendationDao;
import com.lifeforge.dao.SavedRecommendationDao;
import com.lifeforge.dao.UserDao;
import com.lifeforge.dao.UserGoalDao;
import com.lifeforge.service.AdminService;
import com.lifeforge.service.AiExplanationService;
import com.lifeforge.service.AnalyticsService;
import com.lifeforge.service.AuditLogService;
import com.lifeforge.service.AuthService;
import com.lifeforge.service.CalorieService;
import com.lifeforge.service.CodeDeliveryService;
import com.lifeforge.service.ConsoleCodeDeliveryService;
import com.lifeforge.service.ExportService;
import com.lifeforge.service.GoalService;
import com.lifeforge.service.HydrationService;
import com.lifeforge.service.JasperReportsService;
import com.lifeforge.service.PasswordResetService;
import com.lifeforge.service.RecommendationEngine;
import com.lifeforge.service.RecommendationExplanationService;
import com.lifeforge.service.RecommendationService;
import com.lifeforge.service.RuleBasedExplanationService;
import com.lifeforge.service.SavedRecommendationService;
import com.lifeforge.service.UserService;

/**
 * Builds the full dependency graph once at application start.
 * Controllers are exposed as public final fields so TUI screens can
 * navigate the whole app through a single shared context.
 */
public class AppContext {

    public final Session session;
    public final AuthController authController;
    public final UserController userController;
    public final GoalController goalController;
    public final PasswordResetController passwordResetController;
    public final RecommendationController recommendationController;
    public final SavedRecommendationController savedRecommendationController;
    public final AdminController adminController;
    public final ReportController reportController;

    private AppContext(Session session,
                       AuthController authController,
                       UserController userController,
                       GoalController goalController,
                       PasswordResetController passwordResetController,
                       RecommendationController recommendationController,
                       SavedRecommendationController savedRecommendationController,
                       AdminController adminController,
                       ReportController reportController) {
        this.session = session;
        this.authController = authController;
        this.userController = userController;
        this.goalController = goalController;
        this.passwordResetController = passwordResetController;
        this.recommendationController = recommendationController;
        this.savedRecommendationController = savedRecommendationController;
        this.adminController = adminController;
        this.reportController = reportController;
    }   

    /**
     * Assembles DAOs -> services -> controllers.
     */
    public static AppContext build() {
        Session session = new Session();

        // ---- DAOs ----
        UserDao userDao = new UserDao();
        GoalDao goalDao = new GoalDao();
        UserGoalDao userGoalDao = new UserGoalDao();
        RecommendationCategoryDao categoryDao = new RecommendationCategoryDao();
        RecommendationDao recommendationDao = new RecommendationDao();
        SavedRecommendationDao savedRecommendationDao = new SavedRecommendationDao();
        AuditLogDao auditLogDao = new AuditLogDao();
        PasswordResetDao passwordResetDao = new PasswordResetDao();

        // ---- Services ----
        AuditLogService auditLogService = new AuditLogService(auditLogDao);

        AuthService authService = new AuthService(userDao, auditLogService);
        UserService userService = new UserService(userDao, auditLogService);
        GoalService goalService = new GoalService(goalDao, userGoalDao);

        CodeDeliveryService codeDeliveryService = new ConsoleCodeDeliveryService();
        PasswordResetService passwordResetService =
                new PasswordResetService(passwordResetDao, userDao, auditLogService, codeDeliveryService);

        CalorieService calorieService = new CalorieService();
        HydrationService hydrationService = new HydrationService();
        RecommendationExplanationService aiExplanationService = new AiExplanationService();
        RecommendationExplanationService ruleBasedExplanationService = new RuleBasedExplanationService();

        RecommendationEngine recommendationEngine = new RecommendationEngine(recommendationDao);
        RecommendationService recommendationService = new RecommendationService(
                recommendationEngine, categoryDao, recommendationDao,
                calorieService, hydrationService, aiExplanationService, ruleBasedExplanationService);

        SavedRecommendationService savedRecommendationService =
                new SavedRecommendationService(savedRecommendationDao);

        AdminService adminService = new AdminService(userDao, auditLogService, passwordResetDao);
        AnalyticsService analyticsService = new AnalyticsService(
                userDao, userGoalDao, goalDao, recommendationDao, savedRecommendationDao);

        ExportService exportService = new ExportService(calorieService, hydrationService);
        JasperReportsService jasperReportsService = new JasperReportsService(exportService);

        // ---- Controllers ----
        AuthController authController = new AuthController(authService, session);
        UserController userController = new UserController(userService, session);
        GoalController goalController = new GoalController(goalService, session);
        PasswordResetController passwordResetController =
                new PasswordResetController(passwordResetService);
        RecommendationController recommendationController =
                new RecommendationController(recommendationService, goalService, session);
        SavedRecommendationController savedRecommendationController =
                new SavedRecommendationController(savedRecommendationService, session);
        AdminController adminController =
                new AdminController(adminService, analyticsService, auditLogService,
                        passwordResetDao, session);
        ReportController reportController =
                new ReportController(jasperReportsService, recommendationService);

        return new AppContext(
                session,
                authController,
                userController,
                goalController,
                passwordResetController,
                recommendationController,
                savedRecommendationController,
                adminController,
                reportController);
    }
}