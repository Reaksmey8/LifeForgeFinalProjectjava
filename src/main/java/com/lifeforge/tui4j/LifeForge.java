package com.lifeforge.tui4j;

import com.lifeforge.AppContext;
import com.lifeforge.config.AppConfig;
import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.AuditLog;
import com.lifeforge.model.Gender;
import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.RecommendationCategory;
import com.lifeforge.model.Role;
import com.lifeforge.model.SavedRecommendation;
import com.lifeforge.model.User;
import com.lifeforge.service.AnalyticsService;
import com.lifeforge.service.CalorieService;
import com.lifeforge.service.RecommendationService;
import com.lifeforge.tui4j.ScreenKit.Line;
import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.message.WindowSizeMessage;
import com.williamcallahan.tui4j.compat.bubbletea.QuitMessage;
import com.williamcallahan.tui4j.compat.lipgloss.Style;

import java.util.*;

import com.lifeforge.service.ExplanationOutcome;
import com.lifeforge.service.AiChatResponse;

import static com.lifeforge.tui4j.Theme.truncate;

/**
 * Single Bubble Tea model driving the whole LifeForge terminal UI.
 *
 * Navigation model: a {@link Screen} enum plus a back stack. Reaching a
 * screen calls {@link #refresh()} once so the view method stays free of
 * controller/DAO calls. Forms are plain state arrays edited with the
 * keyboard; menus are label/action pairs. Global keys B=Back, H=Home,
 * Q=Quit are active everywhere except while typing inside a form.
 */
public final class LifeForge implements Model {

    private enum Screen {
        WELCOME,
        LOGIN,
        REGISTER,
        FORGOT_PASSWORD,
        VERIFY_CODE,
        NEW_PASSWORD,
        RESET_STATUS,
        RESET_APPROVED,
        RESET_SUCCESS,
        USER_HOME,
        GOAL_SELECT,
        RECOMMEND_HUB,
        CATEGORY,
        SUBCATEGORY,
        MASTER,
        EXPLANATION,
        RECOMMEND_DETAIL,
        OFFICIAL_RECOMMENDATION,
        AI_CHAT,
        SAVED,
        SAVED_DETAIL,
        PROFILE,
        PROFILE_EDIT,
        PROFILE_PASSWORD,
        ADMIN_HOME,
        ADMIN_USERS,
        ADMIN_USER_ACTIONS,
        ADMIN_SEARCH,
        ADMIN_RECS,
        ADMIN_REC_FORM,
        ADMIN_REC_DETAIL,
        ADMIN_REC_DELETE_CONFIRM,
        ADMIN_GOALS,
        ADMIN_GOAL_FORM,
        ADMIN_CATS,
        ADMIN_CAT_FORM,
        ADMIN_ANALYTICS,
        ADMIN_AUDIT,
        ADMIN_AUDIT_DETAIL,
        ADMIN_AUDIT_SEARCH,
        ADMIN_REC_SEARCH,
        ADMIN_RESET_REQUESTS,
        ADMIN_RESET_DETAIL,
        ADMIN_RESET_CONFIRM,
        ADMIN_SETTINGS
    }

    private enum FieldKind {
        TEXT, NUMERIC, SECRET, GENDER, ACTIVITY, ROLE, GOAL, CAT, CAT_PARENT, BOOL,
        CODE6, BUTTON_PRIMARY, BUTTON_SECONDARY
    }

    private final AppContext ctx;

    private Screen screen = Screen.WELCOME;
    private final Deque<Screen> back = new ArrayDeque<>();
    private int sel;
    private int width = 80;
    private String status = "";
    private boolean statusErr;
    private String pendingStatus;
    private boolean pendingErr;
    private boolean armed;
    private boolean quitting;

    // form state
    private String[] fLabels;
    private String[] fValues;
    private int[] fCursor;
    private int fFocus;
    private String[][] fChoices;
    private long[][] fChoiceIds;

    // navigation payloads
    private Long resetUserId;
    private boolean codeVerified;
    private String resetStatusKind;
    private boolean resetApproveReject;
    private RecommendationCategory currentCategory;
    private RecommendationService.RecommendationResult result;
    private String explanationText;
    private boolean explanationFromAi;
    private String chatDraft = "";
    private int chatCursor;
    private final List<String> chatConversation = new ArrayList<>();
    private char lastChatInsertChar;
    private int lastChatInsertEnd = -1;
    private long lastChatInsertTime;
    private Recommendation savedRec;
    private Long savedRecId;
    private User selUser;
    private long editRecId = -1;
    private long editGoalId = -1;
    private long editCatId = -1;
    private int recPage;
    private final int recPageSize = 8;
    private String recSearchQueryText = "";
    private List<Recommendation> recSearchResults;
    private boolean recSearchResultsMode;

    // cached lists
    private final List<String> menuLabels = new ArrayList<>();
    private final List<Runnable> menuActions = new ArrayList<>();
    private List<Goal> goals;
    private List<RecommendationCategory> catList;
    private List<Recommendation> recomms;
    private List<SavedRecommendation> savedList;
    private List<User> users;
    private List<AuditLog> logs;
    private List<AuditLog> allAuditLogs = new ArrayList<>();
    private int auditPage;
    private final int auditPageSize = 8;
    private String auditSearchQuery = "";
    private AuditLog selAudit;

    // user table pagination
    private int userPage;
    private final int userPageSize = 8;

    // password reset request management (admin approval workflow)
    private List<com.lifeforge.model.PasswordReset> resetList = new ArrayList<>();
    private int resetPage;
    private final int resetPageSize = 8;
    private com.lifeforge.model.PasswordReset selReset;
    private Map<Long, String[]> resetUserLabels = new HashMap<>();
    private static final java.time.format.DateTimeFormatter AUDIT_TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final java.time.format.DateTimeFormatter RESET_DETAIL_TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");
    private static final int AUDIT_TIME_W = 11;
    private static final int AUDIT_ADMIN_W = 6;
    private static final int AUDIT_ACTION_W = 20;
    private static final int AUDIT_TARGET_W = 8;
    private AnalyticsService.AnalyticsSummary analytics;
    private String searchQuery = "";
    private String recommendationSearchQuery = "";
    private List<Recommendation> displayedRecs;
    private boolean dbOk;

    public LifeForge(AppContext ctx) {
        this.ctx = ctx;
        // Populate the first screen's menu/actions so the very first view() is not
        // empty. refresh() for WELCOME only builds static entries (no DB access).
        refresh();
    }

    // ------------------------------------------------------------------
    // Bubble Tea lifecycle
    // ------------------------------------------------------------------
    @Override
    public Command init() {
        return Command.batch(Command.setWindowTitle(AppConfig.APP_NAME), Command.checkWindowSize());
    }

    @Override
    public UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof QuitMessage) {
            return UpdateResult.from(this, Command.quit());
        }
        if (msg instanceof WindowSizeMessage wsm) {
            width = Math.max(40, wsm.width());
            return UpdateResult.from(this);
        }
        if (msg instanceof KeyPressMessage kpm) {
            handleKey(kpm);
            if (quitting) {
                quitting = false;
                return UpdateResult.from(this, Command.quit());
            }
        }
        return UpdateResult.from(this);
    }

    @Override
    public String view() {
        List<Line> body = new ArrayList<>();
        String subtitle = "";
        switch (screen) {
            case WELCOME -> subtitle = viewWelcome(body);
            case LOGIN -> subtitle = viewLogin(body);
            case REGISTER -> subtitle = viewRegister(body);
            case FORGOT_PASSWORD -> subtitle = viewForgotPassword(body);
            case VERIFY_CODE -> subtitle = viewVerifyCode(body);
            case NEW_PASSWORD -> subtitle = viewNewPassword(body);
            case RESET_STATUS -> subtitle = viewResetStatus(body);
            case RESET_APPROVED -> subtitle = viewResetApproved(body);
            case RESET_SUCCESS -> subtitle = viewResetSuccess(body);
            case USER_HOME -> subtitle = viewUserHome(body);
            case GOAL_SELECT -> subtitle = viewGoalSelect(body);
            case RECOMMEND_HUB -> subtitle = viewRecommendHub(body);
            case CATEGORY, SUBCATEGORY, MASTER -> subtitle = viewCategory(body);
            case EXPLANATION -> subtitle = viewExplanation(body);
            case RECOMMEND_DETAIL -> subtitle = viewRecommendDetail(body);
            case OFFICIAL_RECOMMENDATION -> subtitle = viewOfficialRecommendation(body);
            case AI_CHAT -> subtitle = viewAiChat(body);
            case SAVED -> subtitle = viewSaved(body);
            case SAVED_DETAIL -> subtitle = viewSavedDetail(body);
            case PROFILE -> subtitle = viewProfile(body);
            case PROFILE_EDIT -> subtitle = viewProfileEdit(body);
            case PROFILE_PASSWORD -> subtitle = viewProfilePassword(body);
            case ADMIN_HOME -> subtitle = viewAdminHome(body);
            case ADMIN_USERS -> subtitle = viewAdminUsers(body);
            case ADMIN_USER_ACTIONS -> subtitle = viewAdminUserActions(body);
            case ADMIN_SEARCH -> subtitle = viewAdminSearch(body);
            case ADMIN_RECS -> subtitle = viewAdminRecs(body);
            case ADMIN_REC_SEARCH -> subtitle = viewAdminRecSearch(body);
            case ADMIN_REC_FORM -> subtitle = viewAdminRecForm(body);
            case ADMIN_REC_DETAIL -> subtitle = viewAdminRecDetail(body);
            case ADMIN_REC_DELETE_CONFIRM -> subtitle = viewAdminRecDeleteConfirm(body);
            case ADMIN_GOALS -> subtitle = viewAdminGoals(body);
            case ADMIN_GOAL_FORM -> subtitle = viewAdminGoalForm(body);
            case ADMIN_CATS -> subtitle = viewAdminCats(body);
            case ADMIN_CAT_FORM -> subtitle = viewAdminCatForm(body);
            case ADMIN_ANALYTICS -> subtitle = viewAdminAnalytics(body);
            case ADMIN_AUDIT -> subtitle = viewAdminAudit(body);
            case ADMIN_AUDIT_DETAIL -> subtitle = viewAdminAuditDetail(body);
            case ADMIN_AUDIT_SEARCH -> subtitle = viewAdminAuditSearch(body);
            case ADMIN_RESET_REQUESTS -> subtitle = viewAdminResetRequests(body);
            case ADMIN_RESET_DETAIL -> subtitle = viewAdminResetDetail(body);
            case ADMIN_RESET_CONFIRM -> subtitle = viewAdminResetConfirm(body);
            case ADMIN_SETTINGS -> subtitle = viewAdminSettings(body);
        }
        return ScreenKit.page(title(), subtitle, body, status, statusErr, footer(), width);
    }

    private String viewAdminRecSearch(List<Line> body) {
        if (recSearchResultsMode) {
            return viewAdminRecSearchResults(body);
        }

        body.add(Line.of(Theme.headingCyan(), "  \uD83D\uDD0E SEARCH RECOMMENDATIONS"));
        body.add(Line.of(Theme.dim(),
                "  Search by title, goal, category, or activity level."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "Type a query, then pick an action below";
    }

    private String viewAdminRecSearchResults(List<Line> body) {
        List<Recommendation> results = safe(recSearchResults);

        body.add(Line.of(Theme.headingGreen(),
                "  \uD83D\uDCCB Results \u2014 Found " + results.size() + " recommendation(s)"));
        body.add(Line.blank());

        int inner = Math.max(40, width - 6);
        int titleW = Math.max(8, inner - (4 + 16 + 20 + 10 + 5));

        String hdr = String.format("  %-4s %-16s %-20s %-10s %s",
                "#", "GOAL", "CATEGORY", "ACTIVITY", "TITLE");
        body.add(Line.of(Theme.headingPurple(), hdr));
        body.add(Line.of(Theme.bar(),
                "  " + Theme.dup('\u2500', Math.max(10, width - 6))));

        if (results.isEmpty()) {
            body.add(Line.of(Theme.warn(),
                    "  No recommendations match your query."));
            if (sel > 0) {
                sel = 0;
            }
        } else {
            if (sel >= results.size()) {
                sel = results.size() - 1;
            }
            for (int i = 0; i < results.size(); i++) {
                Recommendation r = results.get(i);
                String id = String.valueOf(r.getId());
                String goal = Theme.truncate(nameOfGoal(r.getGoalId()), 16);
                String category = Theme.truncate(nameOfCategory(r.getCategoryId()), 20);
                String activity = r.getActivityLevel() == null ? "ALL"
                        : human(r.getActivityLevel());
                activity = Theme.truncate(activity, 10);
                String title = Theme.truncate(nvl(r.getTitle()), titleW);

                String row = String.format("  %-4s %-16s %-20s %-10s %s",
                        id, goal, category, activity, title);

                if (i == sel) {
                    body.add(Line.of(Theme.selected(), "> " + row.substring(2)));
                } else {
                    body.add(Line.of(Theme.text(), row));
                }
            }
        }

        body.add(Line.blank());
        body.add(Line.of(Theme.dim(),
                "  [ S ] New search    [ B ] Back to list"));

        return "Select a result with Enter to view/edit";
    }


    private String title() {
        return switch (screen) {
            case WELCOME -> "Welcome";
            case LOGIN -> "Login";
            case REGISTER -> "Register Account";
            case FORGOT_PASSWORD -> "Reset Password";
            case VERIFY_CODE -> "Verify Account";
            case NEW_PASSWORD -> "New Password";
            case RESET_STATUS -> "Password Reset Status";
            case RESET_APPROVED -> "Request Approved";
            case RESET_SUCCESS -> "Password Reset";
            case USER_HOME -> "Dashboard";
            case GOAL_SELECT -> "Choose Your Goal";
            case RECOMMEND_HUB -> "Recommendation Hub";
            case CATEGORY, SUBCATEGORY, MASTER ->
                    currentCategory == null ? "Category" : currentCategory.getName();
            case EXPLANATION -> "Full Explanation";
            case RECOMMEND_DETAIL -> result == null ? "Recommendation"
                    : (currentCategory == null ? "Recommendation" : currentCategory.getName());
            case OFFICIAL_RECOMMENDATION -> "Official Recommendation";
            case AI_CHAT -> "AI Assistant";
            case SAVED -> "My Saved Recommendations";
            case SAVED_DETAIL -> "Saved Recommendation";
            case PROFILE -> "My Profile";
            case PROFILE_EDIT -> "Edit Profile";
            case PROFILE_PASSWORD -> "Change Password";
            case ADMIN_HOME -> "Admin Dashboard";
            case ADMIN_USERS -> "User Management";
            case ADMIN_USER_ACTIONS -> "User Details";
            case ADMIN_SEARCH -> "Search Users";
            case ADMIN_RECS -> "Recommendation CMS";
            case ADMIN_REC_SEARCH -> "Search Recommendations";
            case ADMIN_REC_FORM -> "Recommendation Editor";
            case ADMIN_REC_DETAIL -> "Recommendation Detail";
            case ADMIN_REC_DELETE_CONFIRM -> "Delete Recommendation";
            case ADMIN_GOALS -> "Goal Management";
            case ADMIN_GOAL_FORM -> "Goal Form";
            case ADMIN_CATS -> "Category Management";
            case ADMIN_CAT_FORM -> "Category Form";
            case ADMIN_ANALYTICS -> "Analytics";
            case ADMIN_AUDIT -> "Audit Logs";
            case ADMIN_AUDIT_DETAIL -> "Audit Log Details";
            case ADMIN_AUDIT_SEARCH -> "Search Audit Logs";
            case ADMIN_RESET_REQUESTS -> "Password Reset Requests";
            case ADMIN_RESET_DETAIL -> "Reset Request Details";
            case ADMIN_RESET_CONFIRM -> resetApproveReject ? "Approve Request"
                    : "Reject Request";
            case ADMIN_SETTINGS -> "System Settings";
        };
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------
    /** Navigate pushing the current screen. pendingStatus survives to the next refresh. */
    private void goTo(Screen target) {
        status = "";
        back.push(screen);
        screen = target;
        refresh();
    }

    private void goClean(Screen target) {
        status = "";
        back.clear();
        screen = target;
        refresh();
    }

    private void goBack() {
        status = "";
        if (screen == Screen.RESET_SUCCESS) {
            // After a successful reset, don't return into the completed flow.
            goClean(Screen.LOGIN);
            return;
        }
        if (!back.isEmpty()) {
            screen = back.pop();
        }
        refresh();
    }

    private void home() {
        if (!ctx.session.isLoggedIn()) {
            goClean(Screen.WELCOME);
        } else if (ctx.authController.isAdmin()) {
            goClean(Screen.ADMIN_HOME);
        } else {
            goClean(Screen.USER_HOME);
        }
    }

    private void refresh() {
        sel = 0;
        armed = false;
        menuLabels.clear();
        menuActions.clear();

        // ហៅបង្កើត Menu logic តែមួយដងនៅត្រង់នេះ
        refreshMenu();

        switch (screen) {
            // លុប case WELCOME, USER_HOME ... -> refreshMenu() ចោល ព្រោះបានហៅនៅខាងលើរួចហើយ
            case SAVED -> refreshSaved();
            case ADMIN_USERS -> refreshUsers();
            case ADMIN_RECS -> {
                recPage = 0;
                refreshAdminRecs();
            }
            case ADMIN_GOALS -> refreshAdminGoals();
            case ADMIN_CATS -> refreshAdminCats();
            case ADMIN_ANALYTICS -> refreshAnalytics();
            case ADMIN_AUDIT -> refreshLogs();
            case ADMIN_RESET_REQUESTS -> refreshAdminResets();
            case ADMIN_SETTINGS -> refreshSettings();
            case LOGIN -> buildForm(new String[] { "Email or Username", "Password", "Forgot Password", "Login", "Back" },
                    new FieldKind[] { FieldKind.TEXT, FieldKind.SECRET, FieldKind.BUTTON_SECONDARY,
                            FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY },
                    new String[][] { null, null, null, null, null },
                    new long[][] { null, null, null, null, null },
                    new String[] { "", "", "", "", "" });
            case REGISTER -> buildForm(
                    new String[] { "Full Name", "Email", "Password", "Confirm Password", "Age",
                            "Gender", "Height (cm)", "Weight (kg)", "Activity Level",
                            "Register Account", "Back" },
                    new FieldKind[] { FieldKind.TEXT, FieldKind.TEXT, FieldKind.SECRET,
                            FieldKind.SECRET, FieldKind.NUMERIC, FieldKind.GENDER,
                            FieldKind.NUMERIC, FieldKind.NUMERIC, FieldKind.ACTIVITY,
                            FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY },
                    new String[][] { null, null, null, null, null, genderLabels(), null, null,
                            activityLabels(), null, null },
                    new long[][] { null, null, null, null, null, genderIds(), null, null,
                            activityIds(), null, null },
                    new String[] { "", "", "", "", "", String.valueOf(Gender.MALE.ordinal()), "",
                            "", String.valueOf(ActivityLevel.MODERATELY_ACTIVE.ordinal()),
                            "", "" });
            case PROFILE_EDIT -> buildProfileEdit();
            case PROFILE_PASSWORD -> buildForm(
                    new String[] { "Current Password", "New Password", "Confirm New Password",
                            "Change Password", "Back" },
                    new FieldKind[] { FieldKind.SECRET, FieldKind.SECRET, FieldKind.SECRET,
                            FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY },
                    new String[][] { null, null, null, null, null },
                    new long[][] { null, null, null, null, null },
                    new String[] { "", "", "", "", "" });
            case ADMIN_SEARCH -> buildForm(
                    new String[] { "Search by name or email", "Search", "Back" },
                    new FieldKind[] { FieldKind.TEXT, FieldKind.BUTTON_PRIMARY,
                            FieldKind.BUTTON_SECONDARY },
                    new String[][] { null, null, null },
                    new long[][] { null, null, null },
                    new String[] { searchQuery, "", "" });
            case ADMIN_AUDIT_SEARCH -> buildForm(
                    new String[] { "Search action / admin / target / details", "Search", "Back" },
                    new FieldKind[] { FieldKind.TEXT, FieldKind.BUTTON_PRIMARY,
                            FieldKind.BUTTON_SECONDARY },
                    new String[][] { null, null, null },
                    new long[][] { null, null, null },
                    new String[] { auditSearchQuery, "", "" });
            case ADMIN_REC_FORM -> buildAdminRecForm();
            case ADMIN_REC_DETAIL -> { /* no form — view-only screen */ }
            case ADMIN_REC_DELETE_CONFIRM -> { /* confirmation menu, no form */ }
            case ADMIN_REC_SEARCH -> {
                if (!recSearchResultsMode) {
                    recSearchResults = null;
                    buildAdminRecSearchForm();
                }
            }
            case FORGOT_PASSWORD -> buildForm(
                    new String[] { "Email", "Continue", "Back" },
                    new FieldKind[] { FieldKind.TEXT, FieldKind.BUTTON_PRIMARY,
                            FieldKind.BUTTON_SECONDARY },
                    new String[][] { null, null, null },
                    new long[][] { null, null, null },
                    new String[] { "", "", "" });
            case VERIFY_CODE -> buildForm(
                    new String[] { "Code (6 digits)", "Verify", "Resend Code", "Back" },
                    new FieldKind[] { FieldKind.CODE6, FieldKind.BUTTON_PRIMARY,
                            FieldKind.BUTTON_SECONDARY, FieldKind.BUTTON_SECONDARY },
                    new String[][] { null, null, null, null },
                    new long[][] { null, null, null, null },
                    new String[] { "", "", "", "" });
            case NEW_PASSWORD -> buildForm(
                    new String[] { "New Password", "Confirm Password", "Reset Password", "Back" },
                    new FieldKind[] { FieldKind.SECRET, FieldKind.SECRET,
                            FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY },
                    new String[][] { null, null, null, null },
                    new long[][] { null, null, null, null },
                    new String[] { "", "", "", "" });
            case ADMIN_GOAL_FORM -> buildAdminGoalForm();
            case ADMIN_CAT_FORM -> buildAdminCatForm();
        }
        if (pendingStatus != null) {
            status = pendingStatus;
            statusErr = pendingErr;
            pendingStatus = null;
            pendingErr = false;
        }
    }


//    private void refreshMenu() {
//        switch (screen) {
//            case WELCOME -> {
//                menuLabels.add("Sign In");
//                menuActions.add(() -> goTo(Screen.LOGIN));
//                menuLabels.add("Create Account");
//                menuActions.add(() -> goTo(Screen.REGISTER));
//                menuLabels.add("Quit");
//                menuActions.add(this::quitApp);
//            }
//            case USER_HOME -> {
//                User u = ctx.session.getCurrentUser();
//                Optional<Goal> current = ctx.goalController.getCurrentGoal();
//                if (current.isEmpty()) {
//                    menuLabels.add("Choose a Goal");
//                    menuActions.add(() -> goTo(Screen.GOAL_SELECT));
//                    menuLabels.add("Recommendation Hub (select a goal first)");
//                    menuActions.add(() -> goTo(Screen.RECOMMEND_HUB));
//                } else {
//                    menuLabels.add("Recommendation Hub");
//                    menuActions.add(() -> goTo(Screen.RECOMMEND_HUB));
//                    menuLabels.add("Change Goal");
//                    menuActions.add(() -> goTo(Screen.GOAL_SELECT));
//                }
//                menuLabels.add("My Saved Recommendations");
//                menuActions.add(() -> goTo(Screen.SAVED));
//                menuLabels.add("My Profile");
//                menuActions.add(() -> goTo(Screen.PROFILE));
//                menuLabels.add("Log Out");
//                menuActions.add(this::logout);
//                menuLabels.add("Quit");
//                menuActions.add(this::quitApp);
//            }
//            case RECOMMEND_HUB -> {
//                Optional<Goal> goal = ctx.goalController.getCurrentGoal();
//                if (goal.isEmpty()) {
//                    menuLabels.add("Select a Goal Now");
//                    menuActions.add(() -> goTo(Screen.GOAL_SELECT));
//                } else {
//                    List<RecommendationCategory> cats = ctx.recommendationController.getTopCategories();
//                    if (cats == null) {
//                        menuLabels.add("Error: " + errText("", ctx.recommendationController.getLastError()));
//                        menuActions.add(this::goBack);
//                    } else {
//                        for (RecommendationCategory c : cats) {
//                            if (isWhyThisRecommendation(c.getName())) {
//                                continue;
//                            }
//                            boolean master = isMasterRoutine(c.getName());
//                            menuLabels.add(c.getName());
//                            menuActions.add(() -> openCategory(master, c));
//                        }
//                    }
//                }
//            }
//            case GOAL_SELECT -> {
//                goals = ctx.goalController.listActiveGoals();
//                if (goals == null) {
//                    goals = new ArrayList<>();
//                }
//                Optional<Goal> current = ctx.goalController.getCurrentGoal();
//                long curId = current.map(Goal::getId).orElse(-1L);
//                for (Goal g : goals) {
//                    String mark = g.getId() == curId ? " v (current)" : "";
//                    menuLabels.add(g.getName() + mark);
//                    menuActions.add(() -> selectGoal(g.getId()));
//                }
//            }
//            case CATEGORY, SUBCATEGORY, MASTER -> {
//                if (currentCategory == null) {
//                    menuLabels.add("No category selected.");
//                    menuActions.add(this::goBack);
//                    return;
//                }
//                List<RecommendationCategory> children =
//                        ctx.recommendationController.getSubCategories(currentCategory.getId());
//                for (RecommendationCategory c : safe(children)) {
//                    menuLabels.add(c.getName());
//                    menuActions.add(() -> openSub(c));
//                }
//                boolean leaf = safe(children).isEmpty();
//                if (screen == Screen.MASTER || leaf) {
//                    menuLabels.add(screen == Screen.MASTER
//                            ? "Generate Complete Master Routine"
//                            : "Generate Recommendation");
//                    menuActions.add(() -> generate(currentCategory));
//                }
//            }
//            case RECOMMEND_DETAIL -> {
//                if (result == null) {
//                    menuLabels.add("No recommendation in memory.");
//                    menuActions.add(this::goBack);
//                } else {
//                    menuLabels.add("Save This Recommendation");
//                    menuActions.add(this::saveCurrent);
//                    menuLabels.add("Why This Recommendation?");
//                    menuActions.add(this::showWhyThisFits);
//                }
//            }
//            case EXPLANATION -> { /* info screen, empty menu */ }
//            case SAVED_DETAIL -> {
//                if (savedRec == null) {
//                    menuLabels.add("Saved item no longer exists.");
//                    menuActions.add(this::goBack);
//                } else {
//                    menuLabels.add("Remove This Saved Item");
//                    menuActions.add(this::removeSaved);
//                }
//            }
//            case PROFILE -> {
//                menuLabels.add("Edit Profile");
//                menuActions.add(() -> goTo(Screen.PROFILE_EDIT));
//                menuLabels.add("Change Password");
//                menuActions.add(() -> goTo(Screen.PROFILE_PASSWORD));
//                if (armed) {
//                    menuLabels.add("! Delete Account -- press Enter again to confirm");
//                    menuActions.add(this::deleteAccount);
//                } else {
//                    menuLabels.add("Delete My Account");
//                    menuActions.add(this::armDelete);
//                }
//            }
//            case ADMIN_HOME -> {
//                menuLabels.add("Manage Users");
//                menuActions.add(() -> goTo(Screen.ADMIN_USERS));
//                menuLabels.add("Recommendation CMS");
//                menuActions.add(() -> goTo(Screen.ADMIN_RECS));
//                menuLabels.add("Manage Goals");
//                menuActions.add(() -> goTo(Screen.ADMIN_GOALS));
//                menuLabels.add("Manage Categories");
//                menuActions.add(() -> goTo(Screen.ADMIN_CATS));
//                menuLabels.add("Analytics");
//                menuActions.add(() -> goTo(Screen.ADMIN_ANALYTICS));
//                menuLabels.add("Audit Logs");
//                menuActions.add(() -> goTo(Screen.ADMIN_AUDIT));
//                menuLabels.add("System Settings");
//                menuActions.add(() -> goTo(Screen.ADMIN_SETTINGS));
//                menuLabels.add("Log Out");
//                menuActions.add(this::logout);
//                menuLabels.add("Quit");
//                menuActions.add(this::quitApp);
//            }
//            case ADMIN_USER_ACTIONS -> {
//                if (selUser == null) {
//                    menuLabels.add("No user selected.");
//                    menuActions.add(this::goBack);
//                    return;
//                }
//                if (selUser.isBlocked()) {
//                    menuLabels.add("Unblock User");
//                    menuActions.add(() -> toggleBlock(false));
//                } else {
//                    menuLabels.add("Block User");
//                    menuActions.add(() -> toggleBlock(true));
//                }
//
//                if (armed) {
//                    menuLabels.add("! Delete User -- press Enter again to confirm");
//                    menuActions.add(this::confirmDeleteUser);
//                } else {
//                    menuLabels.add("Delete User");
//                    menuActions.add(this::armDelete);
//                }
//            }
//            default -> { /* nothing */ }
//        }
//    }
    /**
     * Prepares an explanation only when requested. The controller/service path
     * tries AI first and always falls back to the standard rule-based
     * explanation. The detail view never renders this text automatically.
     */
    private void prepareWhyThisFits() {

        if (result == null || result.recommendation == null) {
            return;
        }
        Optional<ExplanationOutcome> outcome =
                ctx.recommendationController.explainWhyThisFits(result.recommendation);
        if (outcome.isEmpty()) {
            status = errText("Could not generate an explanation",
                    ctx.recommendationController.getLastError());
            statusErr = true;
            return;
        }
        explanationText = outcome.get().text;
        explanationFromAi = outcome.get().fromAi;
        status = explanationFromAi
                ? "AI explanation is ready. Select View Full Explanation to read it."
                : "Standard explanation is ready. Select View Full Explanation to read it.";
        statusErr = false;
    }

    private void viewFullExplanation() {
        if (explanationText == null || explanationText.isBlank()) {
            status = "Select Why This Recommendation? first to generate an explanation.";
            statusErr = true;
            return;
        }
        goTo(Screen.EXPLANATION);
    }

    private void showOfficialRecommendation() {
        if (result == null || result.recommendation == null) {
            status = "Generate a LifeForge recommendation before opening it.";
            statusErr = true;
            return;
        }
        goTo(Screen.OFFICIAL_RECOMMENDATION);
    }

    private void openAiChat() {
        if (result == null || result.recommendation == null) {
            status = "Generate a LifeForge recommendation before opening AI assistance.";
            statusErr = true;
            return;
        }
        chatDraft = "";
        chatCursor = 0;
        resetChatInsertTracking();
        chatConversation.clear();
        goTo(Screen.AI_CHAT);
    }

    private void handleAiChatKey(KeyType t, String typed) {
        if (esc(t)) { goBack(); return; }
        if (enter(t)) { sendAiChat(); return; }
        if (t == KeyType.keyETX) {
            chatDraft = ""; chatCursor = 0; status = "Question cleared."; statusErr = false;
            resetChatInsertTracking(); return;
        }
        if (t == KeyType.keyBS && chatCursor > 0) {
            chatDraft = chatDraft.substring(0, chatCursor - 1) + chatDraft.substring(chatCursor);
            chatCursor--; resetChatInsertTracking(); return;
        }
        if (t == KeyType.KeyDelete && chatCursor < chatDraft.length()) {
            chatDraft = chatDraft.substring(0, chatCursor) + chatDraft.substring(chatCursor + 1);
            resetChatInsertTracking(); return;
        }
        if (t == KeyType.KeyLeft) { chatCursor = Math.max(0, chatCursor - 1); resetChatInsertTracking(); return; }
        if (t == KeyType.KeyRight) { chatCursor = Math.min(chatDraft.length(), chatCursor + 1); resetChatInsertTracking(); return; }
        if (t == KeyType.KeyHome) { chatCursor = 0; resetChatInsertTracking(); return; }
        if (t == KeyType.KeyEnd) { chatCursor = chatDraft.length(); resetChatInsertTracking(); return; }
        if (t == KeyType.KeySpace) {
            resetChatInsertTracking();
            insertChatText(" ");
            return;
        }
        if (t == KeyType.KeyRunes && typed != null && !typed.isEmpty()) {
            if (typed.length() == 1) {
                char c = typed.charAt(0);
                long now = System.nanoTime();
                if (lastChatInsertChar != 0
                        && chatCursor == lastChatInsertEnd
                        && Character.toLowerCase(c) == Character.toLowerCase(lastChatInsertChar)
                        && (now - lastChatInsertTime) < 40_000_000L) {
                    if (c == lastChatInsertChar) {
                        return;
                    }
                    chatDraft = chatDraft.substring(0, chatCursor - 1) + c
                            + chatDraft.substring(chatCursor);
                    lastChatInsertChar = c;
                    return;
                }
                lastChatInsertChar = c;
                lastChatInsertEnd = chatCursor + 1;
                lastChatInsertTime = now;
                insertChatText(typed);
            } else {
                resetChatInsertTracking();
                insertChatText(typed);
            }
        }
    }

    private void resetChatInsertTracking() {
        lastChatInsertChar = 0;
        lastChatInsertEnd = -1;
        lastChatInsertTime = 0;
    }

    private void insertChatText(String text) {
        chatDraft = chatDraft.substring(0, chatCursor) + text + chatDraft.substring(chatCursor);
        chatCursor += text.length();
    }

    private void sendAiChat() {
        if (chatDraft.isBlank()) {
            status = "Enter a question before sending it to the AI assistant."; statusErr = true; return;
        }
        String question = chatDraft.trim();
        Optional<AiChatResponse> response = ctx.recommendationController.chatWithAi(
                currentCategory, result.recommendation, chatConversation, question);
        if (response.isEmpty()) {
            status = errText("AI assistance is unavailable", ctx.recommendationController.getLastError());
            statusErr = true; return;
        }
        chatConversation.add("USER: " + question);
        chatConversation.add("AI: " + response.get().text);
        chatDraft = ""; chatCursor = 0;
        resetChatInsertTracking();
        status = response.get().fromAi
                ? "AI response received. The LifeForge recommendation remains unchanged."
                : "AI is unavailable. Showing safe LifeForge guidance instead.";
        statusErr = false;
    }

    private void resendCode() {
        var result = ctx.passwordResetController.resendCode(resetUserId);
        if (!result.accepted) {
            status = errText("Could not resend the code", ctx.passwordResetController.getLastError());
            statusErr = true;
        } else {
            status = "A new verification code has been sent to your email.";
            statusErr = false;
            if (AppConfig.isDevResetCodeLoggingEnabled() && resetUserId != null) {
                String dev = ctx.passwordResetController.devLastCode(resetUserId);
                if (dev != null) {
                    status += " (DEV code: " + dev + ")";
                }
            }
        }
    }







    // ------------------------------------------------------------------
    // Key handling
    // ------------------------------------------------------------------
    private void handleKey(KeyPressMessage msg) {
        KeyType t = msg.type();
        char[] runes = msg.runes();
        String s = runes == null ? "" : new String(runes);

        // ==============================
        // FORM SCREENS
        // ==============================
        if (isFormScreen()) {
            if (esc(t)) {
                onFormCancel();
                return;
            }

            formKey(t, s);
            return;
        }

        if (screen == Screen.AI_CHAT) {
            handleAiChatKey(t, s);
            return;
        }

        // ==============================
        // RECOMMENDATION SEARCH — RESULTS PHASE
        // (the query-input phase is a form, handled above)
        // ==============================
        if (screen == Screen.ADMIN_REC_SEARCH) {
            int n = recSearchResults == null ? 0 : recSearchResults.size();

            if (up(t)) {
                if (n > 0) {
                    sel = Math.max(0, sel - 1);
                }
                return;
            }

            if (down(t)) {
                if (n > 0) {
                    sel = Math.min(n - 1, sel + 1);
                }
                return;
            }

            if (t == KeyType.KeyHome) {
                sel = 0;
                return;
            }

            if (t == KeyType.KeyEnd) {
                sel = Math.max(0, n - 1);
                return;
            }

            if (enter(t)) {
                if (n > 0 && sel >= 0 && sel < n) {
                    editRecId = recSearchResults.get(sel).getId();
                    goTo(Screen.ADMIN_REC_DETAIL);
                }
                return;
            }

            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 's' -> {
                        recSearchResults = null;
                        recSearchResultsMode = false;
                        buildAdminRecSearchForm();
                        status = "";
                        statusErr = false;
                    }
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // RECOMMENDATION CMS TABLE
        // ==============================
        if (screen == Screen.ADMIN_RECS) {

            int total = displayedRecs == null ? 0 : displayedRecs.size();
            int pages = Math.max(1, (int) Math.ceil((double) total / recPageSize));
            int pageCount = Math.min(recPageSize, Math.max(0, total - recPage * recPageSize));
            int maxSel = Math.max(0, pageCount - 1);
            if (sel > maxSel) {
                sel = maxSel;
            }

            // UP / DOWN select within the page
            if (up(t)) {
                if (pageCount > 0) {
                    sel = Math.max(0, sel - 1);
                }
                return;
            }

            if (down(t)) {
                if (pageCount > 0) {
                    sel = Math.min(pageCount - 1, sel + 1);
                }
                return;
            }

            // LEFT / RIGHT page navigation
            if (t == KeyType.KeyLeft) {
                if (recPage > 0) {
                    recPage--;
                    sel = 0;
                }
                return;
            }

            if (t == KeyType.KeyRight) {
                if (recPage < pages - 1) {
                    recPage++;
                    sel = 0;
                }
                return;
            }

            // HOME / END
            if (t == KeyType.KeyHome) {
                recPage = 0;
                sel = 0;
                return;
            }

            if (t == KeyType.KeyEnd) {
                recPage = pages - 1;
                sel = pageCount - 1;
                return;
            }

            // ENTER = OPEN DETAIL / EDIT
            if (enter(t)) {
                if (displayedRecs != null
                        && !displayedRecs.isEmpty()
                        && sel >= 0
                        && sel < pageCount) {
                    int globalIndex = recPage * recPageSize + sel;
                    editRecId = displayedRecs.get(globalIndex).getId();
                    goTo(Screen.ADMIN_REC_DETAIL);
                }
                return;
            }

            // LETTER SHORTCUTS
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {

                char c = s.isEmpty()
                        ? ' '
                        : Character.toLowerCase(s.charAt(0));

                switch (c) {
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    case 'n' -> menuNew();
                    case 'd' -> menuDelete();
                    case 's' -> menuSearch();
                    case 'r' -> menuRefresh();
                    default -> {
                        // Ignore
                    }
                }
            }

            return;
        }

        // ==============================
        // RECOMMENDATION DETAIL
        // ==============================
        if (screen == Screen.ADMIN_REC_DETAIL) {
            if (enter(t) || ePressed(t, s)) {
                if (editRecId >= 0) {
                    goTo(Screen.ADMIN_REC_FORM);
                }
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 'e' -> {
                        if (editRecId >= 0) {
                            goTo(Screen.ADMIN_REC_FORM);
                        }
                    }
                    case 'd' -> {
                        if (editRecId >= 0) {
                            goTo(Screen.ADMIN_REC_DELETE_CONFIRM);
                        }
                    }
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // RECOMMENDATION DELETE CONFIRM
        // ==============================
        if (screen == Screen.ADMIN_REC_DELETE_CONFIRM) {
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 'y' -> {
                        int idx = findDisplayedRecIndex(editRecId);
                        deleteSelectedRec(idx);
                    }
                    case 'n', 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // ADMIN USER MANAGEMENT TABLE
        // ==============================
        if (screen == Screen.ADMIN_USERS) {
            int total = users == null ? 0 : users.size();
            int pageStart = userPage * userPageSize;
            int pageCount = Math.min(userPageSize, Math.max(0, total - pageStart));
            int maxSel = Math.max(0, pageCount - 1);
            if (sel > maxSel) {
                sel = maxSel;
            }
            int pages = Math.max(1, (int) Math.ceil((double) total / userPageSize));

            if (up(t)) {
                if (pageCount > 0) {
                    sel = Math.max(0, sel - 1);
                }
                return;
            }
            if (down(t)) {
                if (pageCount > 0) {
                    sel = Math.min(pageCount - 1, sel + 1);
                }
                return;
            }
            if (t == KeyType.KeyLeft) {
                if (userPage > 0) {
                    userPage--;
                    sel = 0;
                }
                return;
            }
            if (t == KeyType.KeyRight) {
                if (userPage < pages - 1) {
                    userPage++;
                    sel = 0;
                }
                return;
            }
            if (t == KeyType.KeyHome) {
                userPage = 0;
                sel = 0;
                return;
            }
            if (t == KeyType.KeyEnd) {
                userPage = pages - 1;
                sel = Math.max(0, pageCount - 1);
                return;
            }
            if (enter(t)) {
                if (total > 0 && sel >= 0 && sel < pageCount) {
                    selUser = users.get(pageStart + sel);
                    goTo(Screen.ADMIN_USER_ACTIONS);
                }
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 's' -> goTo(Screen.ADMIN_SEARCH);
                    case 'x' -> {
                        searchQuery = "";
                        goTo(Screen.ADMIN_USERS);
                    }
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // ADMIN GOALS TABLE
        // ==============================
        if (screen == Screen.ADMIN_GOALS) {
            int total = goals == null ? 0 : goals.size();
            int pageStart = userPage * userPageSize;
            int pageCount = Math.min(userPageSize, Math.max(0, total - pageStart));
            int maxSel = Math.max(0, pageCount - 1);
            if (sel > maxSel) {
                sel = maxSel;
            }
            int pages = Math.max(1, (int) Math.ceil((double) total / userPageSize));

            if (up(t)) {
                if (pageCount > 0) {
                    sel = Math.max(0, sel - 1);
                }
                return;
            }
            if (down(t)) {
                if (pageCount > 0) {
                    sel = Math.min(pageCount - 1, sel + 1);
                }
                return;
            }
            if (t == KeyType.KeyLeft) {
                if (userPage > 0) {
                    userPage--;
                    sel = 0;
                }
                return;
            }
            if (t == KeyType.KeyRight) {
                if (userPage < pages - 1) {
                    userPage++;
                    sel = 0;
                }
                return;
            }
            if (t == KeyType.KeyHome) {
                userPage = 0;
                sel = 0;
                return;
            }
            if (t == KeyType.KeyEnd) {
                userPage = pages - 1;
                sel = Math.max(0, pageCount - 1);
                return;
            }
            if (enter(t)) {
                if (total > 0 && sel >= 0 && sel < pageCount) {
                    Goal g = goals.get(pageStart + sel);
                    editGoalId = g.getId();
                    goTo(Screen.ADMIN_GOAL_FORM);
                }
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 'n' -> menuNew();
                    case 'd' -> menuDelete();
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // ADMIN CATEGORY TABLE
        // ==============================
        if (screen == Screen.ADMIN_CATS) {
            int total = catList == null ? 0 : catList.size();
            int pageStart = userPage * userPageSize;
            int pageCount = Math.min(userPageSize, Math.max(0, total - pageStart));
            int maxSel = Math.max(0, pageCount - 1);
            if (sel > maxSel) {
                sel = maxSel;
            }
            int pages = Math.max(1, (int) Math.ceil((double) total / userPageSize));

            if (up(t)) {
                if (pageCount > 0) {
                    sel = Math.max(0, sel - 1);
                }
                return;
            }
            if (down(t)) {
                if (pageCount > 0) {
                    sel = Math.min(pageCount - 1, sel + 1);
                }
                return;
            }
            if (t == KeyType.KeyLeft) {
                if (userPage > 0) {
                    userPage--;
                    sel = 0;
                }
                return;
            }
            if (t == KeyType.KeyRight) {
                if (userPage < pages - 1) {
                    userPage++;
                    sel = 0;
                }
                return;
            }
            if (t == KeyType.KeyHome) {
                userPage = 0;
                sel = 0;
                return;
            }
            if (t == KeyType.KeyEnd) {
                userPage = pages - 1;
                sel = Math.max(0, pageCount - 1);
                return;
            }
            if (enter(t)) {
                if (total > 0 && sel >= 0 && sel < pageCount) {
                    RecommendationCategory c = catList.get(pageStart + sel);
                    editCatId = c.getId();
                    goTo(Screen.ADMIN_CAT_FORM);
                }
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 'n' -> menuNew();
                    case 'd' -> menuDelete();
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // PASSWORD RESET REQUESTS TABLE
        // ==============================
        if (screen == Screen.ADMIN_RESET_REQUESTS) {
            int total = resetList == null ? 0 : resetList.size();
            int pageStart = resetPage * resetPageSize;
            int pageCount = Math.min(resetPageSize, Math.max(0, total - pageStart));
            int maxSel = Math.max(0, pageCount - 1);
            if (sel > maxSel) {
                sel = maxSel;
            }
            int pages = Math.max(1, (int) Math.ceil((double) total / resetPageSize));

            if (up(t)) {
                if (pageCount > 0) {
                    sel = Math.max(0, sel - 1);
                }
                return;
            }
            if (down(t)) {
                if (pageCount > 0) {
                    sel = Math.min(pageCount - 1, sel + 1);
                }
                return;
            }
            if (t == KeyType.KeyLeft) {
                if (resetPage > 0) {
                    resetPage--;
                    sel = 0;
                }
                return;
            }
            if (t == KeyType.KeyRight) {
                if (resetPage < pages - 1) {
                    resetPage++;
                    sel = 0;
                }
                return;
            }
            if (t == KeyType.KeyHome) {
                resetPage = 0;
                sel = 0;
                return;
            }
            if (t == KeyType.KeyEnd) {
                resetPage = pages - 1;
                sel = Math.max(0, pageCount - 1);
                return;
            }
            if (enter(t)) {
                if (total > 0 && sel >= 0 && sel < pageCount) {
                    selReset = resetList.get(pageStart + sel);
                    goTo(Screen.ADMIN_RESET_DETAIL);
                }
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 'a' -> offerResetDecision(true);
                    case 'r' -> offerResetDecision(false);
                    case 'f' -> {
                        refreshAdminResets();
                        status = "Password reset requests refreshed.";
                        statusErr = false;
                    }
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // RESET REQUEST DETAIL uses the standard menu (Approve/Reject/Back).
        // ==============================

        // ==============================
        // AUDIT LOG LIST
        // ==============================
        if (screen == Screen.ADMIN_AUDIT) {
            int total = logs == null ? 0 : logs.size();
            int pageStart = auditPage * auditPageSize;
            int pageCount = Math.min(auditPageSize, Math.max(0, total - pageStart));
            int maxSel = Math.max(0, pageCount - 1);
            if (sel > maxSel) {
                sel = maxSel;
            }
            int pages = Math.max(1, (int) Math.ceil((double) total / auditPageSize));

            if (up(t)) {
                if (pageCount > 0) {
                    sel = Math.max(0, sel - 1);
                }
                return;
            }

            if (down(t)) {
                if (pageCount > 0) {
                    sel = Math.min(pageCount - 1, sel + 1);
                }
                return;
            }

            if (t == KeyType.KeyLeft) {
                if (auditPage > 0) {
                    auditPage--;
                    sel = 0;
                }
                return;
            }

            if (t == KeyType.KeyRight) {
                if (auditPage < pages - 1) {
                    auditPage++;
                    sel = 0;
                }
                return;
            }

            if (t == KeyType.KeyHome) {
                auditPage = 0;
                sel = 0;
                return;
            }

            if (t == KeyType.KeyEnd) {
                auditPage = pages - 1;
                sel = pageCount - 1;
                return;
            }

            if (enter(t)) {
                if (total > 0 && sel >= 0 && sel < pageCount) {
                    selAudit = logs.get(pageStart + sel);
                    goTo(Screen.ADMIN_AUDIT_DETAIL);
                }
                return;
            }

            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 's' -> goTo(Screen.ADMIN_AUDIT_SEARCH);
                    case 'r' -> refreshLogs();
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // AUDIT LOG DETAIL (read-only)
        // ==============================
        if (screen == Screen.ADMIN_AUDIT_DETAIL) {
            if (esc(t) || enter(t)) {
                goBack();
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // ALL OTHER SCREENS
        // ==============================

        if (up(t)) {
            sel = sel <= 0 ? 0 : sel - 1;
            return;
        }

        if (down(t)) {
            sel = menuActions.isEmpty()
                    ? 0
                    : Math.min(menuActions.size() - 1, sel + 1);
            return;
        }

        if (t == KeyType.KeyHome) {
            sel = 0;
            return;
        }

        if (t == KeyType.KeyEnd) {
            sel = menuActions.isEmpty()
                    ? 0
                    : menuActions.size() - 1;
            return;
        }

        if (enter(t)) {
            if (!menuActions.isEmpty()
                    && sel >= 0
                    && sel < menuActions.size()) {

                menuActions.get(sel).run();
            }
            return;
        }

        if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {

            char c = s.isEmpty()
                    ? ' '
                    : Character.toLowerCase(s.charAt(0));

            switch (c) {
                case 'b' -> goBack();
                case 'h' -> home();
                case 'q' -> quitApp();
                case 'n' -> menuNew();
                case 'd' -> menuDelete();
                case 's' -> menuSearch();
                default -> {
                    // Ignore
                }
            }
        }
    }

    private void menuRefresh() {
        if (screen == Screen.ADMIN_ANALYTICS) {
            refreshAnalytics();
        }
    }

    private void menuNew() {
        switch (screen) {
            case ADMIN_RECS -> {
                editRecId = -1;
                recommendationSearchQuery = "";
                goTo(Screen.ADMIN_REC_FORM);
            }
            case ADMIN_GOALS -> {
                editGoalId = -1;
                goTo(Screen.ADMIN_GOAL_FORM);
            }
            case ADMIN_CATS -> {
                editCatId = -1;
                goTo(Screen.ADMIN_CAT_FORM);
            }
            default -> { /* not supported here */ }
        }
    }

    private void menuDelete() {
        switch (screen) {
            case ADMIN_RECS -> {
                if (displayedRecs == null || displayedRecs.isEmpty()) {
                    return;
                }
                int total = displayedRecs.size();
                int pageCount = Math.min(recPageSize, Math.max(0, total - recPage * recPageSize));
                if (sel >= pageCount) {
                    return;
                }
                int globalIndex = recPage * recPageSize + sel;
                editRecId = displayedRecs.get(globalIndex).getId();
                goTo(Screen.ADMIN_REC_DELETE_CONFIRM);
            }
            case ADMIN_GOALS -> {
                if (goals == null || goals.isEmpty()) {
                    return;
                }
                int total = goals.size();
                int pageCount = Math.min(userPageSize, Math.max(0, total - userPage * userPageSize));
                if (sel >= pageCount) {
                    return;
                }
                deleteSelectedGoal(userPage * userPageSize + sel);
            }
            case ADMIN_CATS -> {
                if (catList == null || catList.isEmpty()) {
                    return;
                }
                int total = catList.size();
                int pageCount = Math.min(userPageSize, Math.max(0, total - userPage * userPageSize));
                if (sel >= pageCount) {
                    return;
                }
                deleteSelectedCat(userPage * userPageSize + sel);
            }
            default -> { /* not supported here */ }
        }
    }

    private void menuSearch() {
        if (screen == Screen.ADMIN_USERS) {
            goTo(Screen.ADMIN_SEARCH);
            return;
        }

        if (screen == Screen.ADMIN_RECS) {
            recSearchQueryText = recommendationSearchQuery == null
                    ? "" : recommendationSearchQuery;
            recSearchResults = null;
            recSearchResultsMode = false;
            goTo(Screen.ADMIN_REC_SEARCH);
        }
    }

    private void quitApp() {
        quitting = true;
    }

    private void onFormCancel() {
        switch (screen) {
            case LOGIN -> {
                // "Forgot Password" button is at field index 2
                if (fFocus == 2) {
                    resetUserId = null;
                    codeVerified = false;
                    goTo(Screen.FORGOT_PASSWORD);
                } else {
                    goBack();
                }
            }
            case REGISTER -> goBack();
            case PROFILE_EDIT, PROFILE_PASSWORD -> goBack();
            case FORGOT_PASSWORD -> goBack();
            case VERIFY_CODE -> {
                if (fFocus == 2) {
                    // Resend Code button
                    resendCode();
                } else {
                    // Back button
                    resetUserId = null;
                    goBack();
                }
            }
            case NEW_PASSWORD -> goBack();
            case ADMIN_REC_FORM -> goTo(Screen.ADMIN_RECS);
            case ADMIN_GOAL_FORM -> goTo(Screen.ADMIN_GOALS);
            case ADMIN_CAT_FORM -> goTo(Screen.ADMIN_CATS);
            case ADMIN_SEARCH -> goTo(Screen.ADMIN_USERS);
            case ADMIN_AUDIT_SEARCH -> goTo(Screen.ADMIN_AUDIT);
            case ADMIN_REC_SEARCH -> {
                if (fFocus == 2) {
                    recSearchQueryText = "";
                    fValues[0] = "";
                    status = "Search query cleared. Type a new query below.";
                    statusErr = false;
                } else {
                    goBack();
                }
            }
            default -> goBack();
        }
    }

    private void formKey(KeyType t, String s) {
        if (fValues == null) {
            return;
        }
        FieldKind focusKind = fKinds()[fFocus];
        boolean onButton = isButton(focusKind);
        if (up(t) || t == KeyType.KeyShiftTab) {
            fFocus = Math.max(0, fFocus - 1);
            return;
        }
        if (down(t) || t == KeyType.keyHT) {
            fFocus = Math.min(fLabels.length - 1, fFocus + 1);
            return;
        }
        if (enter(t)) {
            if (onButton) {
                if (focusKind == FieldKind.BUTTON_PRIMARY) {
                    submitForm();
                } else {
                    onFormCancel();
                }
            } else {
                // Move to the next field; reaching the end lands on the submit (primary) button.
                fFocus = Math.min(fLabels.length - 1, fFocus + 1);
            }
            return;
        }
        if (onButton) {
            // Left/Right/Home/End/Backspace/Delete/text do not edit a button.
            return;
        }
        if (t == KeyType.keyBS) {
            int c = fCursor[fFocus];
            if (c > 0) {
                fValues[fFocus] = fValues[fFocus].substring(0, c - 1)
                        + fValues[fFocus].substring(c);
                fCursor[fFocus]--;
            }
            return;
        }
        if (t == KeyType.KeyDelete) {
            int c = fCursor[fFocus];
            if (c < fValues[fFocus].length()) {
                fValues[fFocus] = fValues[fFocus].substring(0, c)
                        + fValues[fFocus].substring(c + 1);
            }
            return;
        }
        if (t == KeyType.KeyLeft) {
            if (isChoice(fKinds()[fFocus])) {
                cycleChoice(fFocus, -1);
            } else {
                fCursor[fFocus] = Math.max(0, fCursor[fFocus] - 1);
            }
            return;
        }
        if (t == KeyType.KeyRight) {
            if (isChoice(fKinds()[fFocus])) {
                cycleChoice(fFocus, 1);
            } else {
                fCursor[fFocus] = Math.min(fValues[fFocus].length(), fCursor[fFocus] + 1);
            }
            return;
        }
        if (t == KeyType.KeyHome) {
            fCursor[fFocus] = 0;
            return;
        }
        if (t == KeyType.KeyEnd) {
            fCursor[fFocus] = fValues[fFocus].length();
            return;
        }
        if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
            String text = s.isEmpty() ? " " : s;
            if (isChoice(fKinds()[fFocus])) {
                return;
            }
            if (fKinds()[fFocus] == FieldKind.NUMERIC) {
                if (text.chars().allMatch(c -> c == '.' || Character.isDigit(c))) {
                    insertText(text);
                }
            } else if (fKinds()[fFocus] == FieldKind.CODE6) {
                if (text.chars().allMatch(Character::isDigit)
                        && fValues[fFocus].length() + text.length() <= 6) {
                    insertText(text);
                }
            } else {
                insertText(text);
            }
        }
    }

    private static boolean isButton(FieldKind k) {
        return k == FieldKind.BUTTON_PRIMARY || k == FieldKind.BUTTON_SECONDARY;
    }

    private FieldKind[] fKinds() {
        FieldKind[] kinds = new FieldKind[fLabels.length];
        // kinds are re-derived from the current form through a side table
        switch (screen) {
            case LOGIN -> setKinds(kinds, FieldKind.TEXT, FieldKind.SECRET,
                    FieldKind.BUTTON_SECONDARY, FieldKind.BUTTON_PRIMARY,
                    FieldKind.BUTTON_SECONDARY);
            case FORGOT_PASSWORD -> setKinds(kinds, FieldKind.TEXT,
                    FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY);
            case VERIFY_CODE -> setKinds(kinds, FieldKind.CODE6,
                    FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY,
                    FieldKind.BUTTON_SECONDARY);
            case NEW_PASSWORD -> setKinds(kinds, FieldKind.SECRET, FieldKind.SECRET,
                    FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY);
            case REGISTER -> setKinds(kinds, FieldKind.TEXT, FieldKind.TEXT, FieldKind.SECRET,
                    FieldKind.SECRET, FieldKind.NUMERIC, FieldKind.GENDER, FieldKind.NUMERIC,
                    FieldKind.NUMERIC, FieldKind.ACTIVITY, FieldKind.BUTTON_PRIMARY,
                    FieldKind.BUTTON_SECONDARY);
            case PROFILE_EDIT -> setKinds(kinds, FieldKind.TEXT, FieldKind.NUMERIC,
                    FieldKind.GENDER, FieldKind.NUMERIC, FieldKind.NUMERIC, FieldKind.ACTIVITY,
                    FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY);
            case PROFILE_PASSWORD -> setKinds(kinds, FieldKind.SECRET, FieldKind.SECRET,
                    FieldKind.SECRET, FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY);
            case ADMIN_SEARCH -> setKinds(kinds, FieldKind.TEXT, FieldKind.BUTTON_PRIMARY,
                    FieldKind.BUTTON_SECONDARY);
            case ADMIN_AUDIT_SEARCH -> setKinds(kinds, FieldKind.TEXT,
                    FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY);
            case ADMIN_REC_SEARCH -> setKinds(kinds, FieldKind.TEXT,
                    FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY,
                    FieldKind.BUTTON_SECONDARY);
            case ADMIN_REC_FORM -> setKinds(kinds, FieldKind.GOAL, FieldKind.CAT,
                    FieldKind.ACTIVITY, FieldKind.TEXT, FieldKind.TEXT, FieldKind.TEXT,
                    FieldKind.TEXT, FieldKind.TEXT, FieldKind.TEXT, FieldKind.BUTTON_PRIMARY,
                    FieldKind.BUTTON_SECONDARY);
            case ADMIN_GOAL_FORM -> setKinds(kinds, FieldKind.TEXT, FieldKind.TEXT,
                    FieldKind.TEXT, FieldKind.BOOL, FieldKind.BUTTON_PRIMARY,
                    FieldKind.BUTTON_SECONDARY);
            case ADMIN_CAT_FORM -> setKinds(kinds, FieldKind.TEXT, FieldKind.TEXT,
                    FieldKind.CAT_PARENT, FieldKind.NUMERIC, FieldKind.BUTTON_PRIMARY,
                    FieldKind.BUTTON_SECONDARY);
            default -> setKinds(kinds);
        }
        return kinds;
    }

    private static void setKinds(FieldKind[] arr, FieldKind... kinds) {
        for (int i = 0; i < kinds.length && i < arr.length; i++) {
            arr[i] = kinds[i];
        }
    }

    private void insertText(String text) {
        int c = fCursor[fFocus];
        fValues[fFocus] = fValues[fFocus].substring(0, c) + text + fValues[fFocus].substring(c);
        fCursor[fFocus] = c + text.length();
    }

    private void cycleChoice(int field, int delta) {
        String[] choices = fChoices[field];
        int idx = Integer.parseInt(fValues[field]);
        int next = ((idx + delta) % choices.length + choices.length) % choices.length;
        fValues[field] = String.valueOf(next);
    }

    // ------------------------------------------------------------------
    // Form builders
    // ------------------------------------------------------------------
    private void buildForm(String[] labels, FieldKind[] kinds, String[][] choices,
                           long[][] choiceIds, String[] values) {
        fLabels = labels;
        fFocus = 0;
        fValues = new String[labels.length];
        fCursor = new int[labels.length];
        fChoices = new String[labels.length][];
        fChoiceIds = new long[labels.length][];
        for (int i = 0; i < labels.length; i++) {
            String v = values[i] == null ? "" : values[i];
            fValues[i] = v;
            fCursor[i] = v.length();
            fChoices[i] = choices[i];
            fChoiceIds[i] = choiceIds[i];
        }
    }

    private void buildProfileEdit() {
        User u = ctx.session.getCurrentUser();
        String[] labels = { "Full Name", "Age", "Gender", "Height (cm)", "Weight (kg)",
                "Activity Level", "Save", "Back" };
        FieldKind[] kinds = { FieldKind.TEXT, FieldKind.NUMERIC, FieldKind.GENDER,
                FieldKind.NUMERIC, FieldKind.NUMERIC, FieldKind.ACTIVITY,
                FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY };
        String[] values = {
                u == null ? "" : nvl(u.getFullName()),
                u == null || u.getAge() == null ? "" : String.valueOf(u.getAge()),
                u == null || u.getGender() == null ? String.valueOf(Gender.MALE.ordinal())
                        : String.valueOf(u.getGender().ordinal()),
                u == null || u.getHeightCm() == null ? "" : String.valueOf(u.getHeightCm()),
                u == null || u.getWeightKg() == null ? "" : String.valueOf(u.getWeightKg()),
                u == null || u.getActivityLevel() == null
                        ? String.valueOf(ActivityLevel.MODERATELY_ACTIVE.ordinal())
                        : String.valueOf(u.getActivityLevel().ordinal()),
                "", ""
        };
        buildForm(labels, kinds,
                new String[][] { null, null, genderLabels(), null, null, activityLabels(),
                        null, null },
                new long[][] { null, null, genderIds(), null, null, activityIds(), null, null },
                values);
    }

    private void buildAdminRecForm() {
        List<Goal> goalList = safe(ctx.goalController.listAllGoals());
        List<RecommendationCategory> catAll = safe(ctx.recommendationController.listAllCategories());
        String[] gLabels = new String[goalList.size() + 1];
        long[] gIds = new long[goalList.size() + 1];
        gLabels[0] = "- None -";
        gIds[0] = -1;
        for (int i = 0; i < goalList.size(); i++) {
            gLabels[i + 1] = goalList.get(i).getName();
            gIds[i + 1] = goalList.get(i).getId();
        }
        String[] cLabels = new String[catAll.size() + 1];
        long[] cIds = new long[catAll.size() + 1];
        cLabels[0] = "- None -";
        cIds[0] = -1;
        for (int i = 0; i < catAll.size(); i++) {
            cLabels[i + 1] = catAll.get(i).getName();
            cIds[i + 1] = catAll.get(i).getId();
        }

        String[] labels = { "Goal", "Category", "Activity Level", "Title", "Description",
                "Recommended Actions", "Suggested Target", "Examples", "Important Notes",
                "Save Changes", "Cancel" };
        String[] defs = new String[11];
        String[] activity = activityLabels();
        long[] activityIds = activityIdsArray();
        int gIdx = gLabels.length > 0 ? 0 : 0;
        int cIdx = cLabels.length > 0 ? 0 : 0;
        int aIdx = 2; // MODERATELY_ACTIVE
        if (editRecId >= 0) {
            Recommendation r = findRecById(editRecId);
            if (r != null) {
                gIdx = idx(gIds, r.getGoalId());
                cIdx = idx(cIds, r.getCategoryId());
                aIdx = r.getActivityLevel() == null ? 2 : r.getActivityLevel().ordinal();
                defs[3] = nvl(r.getTitle());
                defs[4] = nvl(r.getDescription());
                defs[5] = nvl(r.getRecommendedActions());
                defs[6] = nvl(r.getSuggestedTarget());
                defs[7] = nvl(r.getExamples());
                defs[8] = nvl(r.getImportantNotes());
            }
        }
        defs[0] = String.valueOf(gIdx);
        defs[1] = String.valueOf(cIdx);
        defs[2] = String.valueOf(aIdx);
        defs[9] = "";
        defs[10] = "";

        buildForm(labels,
                new FieldKind[] { FieldKind.GOAL, FieldKind.CAT, FieldKind.ACTIVITY,
                        FieldKind.TEXT, FieldKind.TEXT, FieldKind.TEXT, FieldKind.TEXT,
                        FieldKind.TEXT, FieldKind.TEXT, FieldKind.BUTTON_PRIMARY,
                        FieldKind.BUTTON_SECONDARY },
                new String[][] { gLabels, cLabels, activity, null, null, null, null, null,
                        null, null, null },
                new long[][] { gIds, cIds, activityIds, null, null, null, null, null, null,
                        null, null },
                defs);
    }

    private void buildAdminRecSearchForm() {
        buildForm(
                new String[] { "Query",
                        "\uD83D\uDD0E Search Recommendations",
                        "\uD83E\uDDF9 Clear Search",
                        "\uD83D\uDD19 Back" },
                new FieldKind[] { FieldKind.TEXT, FieldKind.BUTTON_PRIMARY,
                        FieldKind.BUTTON_SECONDARY, FieldKind.BUTTON_SECONDARY },
                new String[][] { null, null, null, null },
                new long[][] { null, null, null, null },
                new String[] { recSearchQueryText == null ? "" : recSearchQueryText,
                        "", "", "" });
    }

    private void buildAdminGoalForm() {
        String[] labels = { "Code", "Name", "Description", "Active", "Save", "Back" };
        String[] defs = new String[6];
        int activeIdx = 0;
        if (editGoalId >= 0) {
            Goal g = findGoalById(editGoalId);
            if (g != null) {
                defs[0] = nvl(g.getCode());
                defs[1] = nvl(g.getName());
                defs[2] = nvl(g.getDescription());
                activeIdx = g.isActive() ? 0 : 1;
            }
        }
        defs[3] = String.valueOf(activeIdx);
        defs[4] = "";
        defs[5] = "";
        buildForm(labels,
                new FieldKind[] { FieldKind.TEXT, FieldKind.TEXT, FieldKind.TEXT,
                        FieldKind.BOOL, FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY },
                new String[][] { null, null, null, new String[] { "Yes", "No" }, null, null },
                new long[][] { null, null, null, new long[] { 1, 0 }, null, null },
                defs);
    }

    private void buildAdminCatForm() {
        List<RecommendationCategory> all = safe(ctx.recommendationController.listAllCategories());
        List<String> pLabels = new ArrayList<>();
        List<Long> pIds = new ArrayList<>();
        pLabels.add("- None -");
        pIds.add(-1L);
        for (RecommendationCategory c : all) {
            if (editCatId >= 0 && c.getId() == editCatId) {
                continue;
            }
            pLabels.add(c.getName());
            pIds.add(c.getId());
        }
        String[] labels = { "Name", "Description", "Parent Category", "Display Order",
                "Save", "Back" };
        String[] defs = new String[6];
        int pIdx = 0;
        if (editCatId >= 0) {
            RecommendationCategory c = findCatById(editCatId);
            if (c != null) {
                defs[0] = nvl(c.getName());
                defs[1] = nvl(c.getDescription());
                pIdx = pIds.indexOf(c.getParentCategoryId());
                defs[3] = String.valueOf(c.getDisplayOrder());
            }
        }
        defs[2] = String.valueOf(Math.max(0, pIdx));
        defs[4] = "";
        defs[5] = "";
        buildForm(labels,
                new FieldKind[] { FieldKind.TEXT, FieldKind.TEXT, FieldKind.CAT_PARENT,
                        FieldKind.NUMERIC, FieldKind.BUTTON_PRIMARY,
                        FieldKind.BUTTON_SECONDARY },
                new String[][] { null, null, pLabels.toArray(new String[0]), null, null, null },
                new long[][] { null, null,
                        pIds.stream().mapToLong(Long::longValue).toArray(), null, null, null },
                defs);
    }

    // ------------------------------------------------------------------
    // Form submit
    // ------------------------------------------------------------------
    private void submitForm() {
        switch (screen) {
            case LOGIN -> submitLogin();
            case REGISTER -> submitRegister();
            case FORGOT_PASSWORD -> submitForgotPassword();
            case VERIFY_CODE -> submitVerifyCode();
            case NEW_PASSWORD -> submitNewPassword();
            case PROFILE_EDIT -> submitProfileEdit();
            case PROFILE_PASSWORD -> submitProfilePassword();
            case ADMIN_SEARCH -> submitSearch();
            case ADMIN_AUDIT_SEARCH -> submitAuditSearch();
            case ADMIN_REC_SEARCH -> submitRecommendationSearch();
            case ADMIN_REC_FORM -> submitRecForm();
            case ADMIN_GOAL_FORM -> submitGoalForm();
            case ADMIN_CAT_FORM -> submitCatForm();
            default -> { /* nothing */ }
        }
    }

    private void submitRecommendationSearch() {
        String query = fValues[0].trim();
        if (query.isEmpty()) {
            status = "Enter a search query first.";
            statusErr = true;
            return;
        }
        recSearchQueryText = query;
        recommendationSearchQuery = query;
        if (recomms == null || recomms.isEmpty()) {
            recomms = safe(ctx.recommendationController.listAllRecommendations());
        }
        recSearchResults = filterRecommendations(query);
        recSearchResultsMode = true;
        sel = 0;
        status = "";
        statusErr = false;
    }

    private void submitLogin() {
        String login = fValues[0].trim();
        String pass = fValues[1];
        if (login.isEmpty() || pass.isEmpty()) {
            status = "Please enter your email/username and password.";
            statusErr = true;
            return;
        }
        if (!ctx.authController.login(login, pass)) {
            status = errText("Login failed", ctx.authController.getLastError());
            statusErr = true;
            return;
        }
        if (ctx.authController.isAdmin()) {
            goClean(Screen.ADMIN_HOME);
        } else {
            goClean(Screen.USER_HOME);
        }
    }

    private void submitRegister() {
        Integer age = parseInt(fValues[4]);
        double height = parseDouble(fValues[6]);
        double weight = parseDouble(fValues[7]);
        boolean ok = ctx.authController.register(
                fValues[0].trim(),
                fValues[1].trim(),
                fValues[2],
                fValues[3],
                age,
                Gender.values()[choice(fValues[5], 0)],
                height,
                weight,
                ActivityLevel.values()[choice(fValues[8], 2)]);
        if (ok) {
            pendingStatus = "Account created. Welcome to " + AppConfig.APP_NAME + "!";
            goClean(Screen.USER_HOME);
        } else {
            status = errText("Registration failed", ctx.authController.getLastError());
            statusErr = true;
        }
    }

    // ------------------------------------------------------------------
    // Forgot Password / Password Reset flow
    // ------------------------------------------------------------------
    private void submitForgotPassword() {
        String email = fValues[0].trim();
        String emailCheck = com.lifeforge.util.ValidationUtil.validateEmail(email);
        if (emailCheck != null) {
            status = emailCheck;
            statusErr = true;
            return;
        }
        var result = ctx.passwordResetController.startReset(email);
        if (!result.accepted) {
            status = result.message;
            statusErr = true;
            return;
        }
        resetUserId = result.userId;
        status = result.message;
        statusErr = false;
        if (resetUserId != null
                && com.lifeforge.service.PasswordResetService.STATUS_APPROVED.equals(result.status)) {
            resetStatusKind = "approved";
            goTo(Screen.RESET_APPROVED);
            return;
        }
        if (resetUserId != null && result.status != null) {
            resetStatusKind = result.status.toLowerCase(Locale.ROOT);
            goTo(Screen.RESET_STATUS);
            return;
        }
        resetStatusKind = "unknown";
        goTo(Screen.RESET_STATUS);
    }

    private void submitVerifyCode() {
        String code = fValues[0].trim();
        if (code.length() != 6 || !code.chars().allMatch(Character::isDigit)) {
            status = "The verification code must be exactly 6 digits.";
            statusErr = true;
            return;
        }
        boolean ok = ctx.passwordResetController.verifyCode(resetUserId, code);
        if (ok) {
            codeVerified = true;
            pendingStatus = "Identity verified. Create your new password.";
            status = "";
            statusErr = false;
            goTo(Screen.NEW_PASSWORD);
        } else {
            status = errText("Verification failed", ctx.passwordResetController.getLastError());
            statusErr = true;
        }
    }

    private void submitNewPassword() {
        if (!codeVerified || resetUserId == null) {
            status = "Your verification has expired. Please start over.";
            statusErr = true;
            return;
        }
        String newPass = fValues[0];
        String confirmPass = fValues[1];
        if (newPass.isEmpty() || confirmPass.isEmpty()) {
            status = "Both password fields are required.";
            statusErr = true;
            return;
        }
        if (!newPass.equals(confirmPass)) {
            status = "The new password and its confirmation do not match.";
            statusErr = true;
            return;
        }
        if (newPass.length() < AppConfig.MIN_PASSWORD_LENGTH) {
            status = "Password must be at least " + AppConfig.MIN_PASSWORD_LENGTH + " characters.";
            statusErr = true;
            return;
        }
        boolean ok = ctx.passwordResetController.resetPassword(resetUserId, newPass, confirmPass);
        if (ok) {
            resetUserId = null;
            codeVerified = false;
            pendingStatus = "Password reset successfully! Login with your new password.";
            status = "";
            statusErr = false;
            goTo(Screen.RESET_SUCCESS);
        } else {
            status = errText("Reset failed", ctx.passwordResetController.getLastError());
            statusErr = true;
        }
    }

    private void submitProfileEdit() {
        Integer age = parseInt(fValues[1]);
        double height = parseDouble(fValues[3]);
        double weight = parseDouble(fValues[4]);
        boolean ok = ctx.userController.updateProfile(
                fValues[0].trim(),
                age,
                Gender.values()[choice(fValues[2], 0)],
                height,
                weight,
                ActivityLevel.values()[choice(fValues[5], 2)]);
        if (ok) {
            pendingStatus = "Profile updated.";
            goBack();
        } else {
            status = errText("Update failed", ctx.userController.getLastError());
            statusErr = true;
        }
    }

    private void submitProfilePassword() {
        if (!fValues[1].equals(fValues[2])) {
            status = "The new password and its confirmation do not match.";
            statusErr = true;
            return;
        }
        boolean ok = ctx.userController.changePassword(fValues[0], fValues[1], fValues[2]);
        if (ok) {
            pendingStatus = "Password changed.";
            goBack();
        } else {
            status = errText("Change failed", ctx.userController.getLastError());
            statusErr = true;
        }
    }

    private void submitSearch() {
        searchQuery = fValues[0].trim();
        goTo(Screen.ADMIN_USERS);
    }

    private void submitAuditSearch() {
        auditSearchQuery = fValues[0].trim();
        goTo(Screen.ADMIN_AUDIT);
    }

    private void submitRecForm() {
        int gIdx = choice(fValues[0], 0);
        int cIdx = choice(fValues[1], 0);
        long goalId = fChoiceIds[0][gIdx];
        long catId = fChoiceIds[1][cIdx];
        if (goalId < 0 || catId < 0) {
            status = "Please choose a goal and a category.";
            statusErr = true;
            return;
        }
        Recommendation rec = new Recommendation();
        rec.setGoalId(goalId);
        rec.setCategoryId(catId);
        rec.setActivityLevel(ActivityLevel.values()[choice(fValues[2], 2)]);
        rec.setTitle(fValues[3].trim());
        rec.setDescription(fValues[4].trim());
        rec.setRecommendedActions(fValues[5].trim());
        rec.setSuggestedTarget(fValues[6].trim());
        rec.setExamples(fValues[7].trim());
        rec.setImportantNotes(fValues[8].trim());
        if (rec.getTitle().isEmpty()) {
            status = "A title is required.";
            statusErr = true;
            return;
        }
        boolean ok;
        if (editRecId >= 0) {
            rec.setId(editRecId);
            ok = ctx.recommendationController.updateRecommendation(rec);
        } else {
            ok = ctx.recommendationController.createRecommendation(rec);
        }
        if (ok) {
            pendingStatus = editRecId >= 0 ? "Recommendation updated." : "Recommendation created.";
            goTo(Screen.ADMIN_RECS);
        } else {
            status = errText("Save failed", ctx.recommendationController.getLastError());
            statusErr = true;
        }
    }

    private void submitGoalForm() {
        String code = fValues[0].trim();
        String name = fValues[1].trim();
        String desc = fValues[2].trim();
        boolean active = choice(fValues[3], 0) == 0;
        if (code.isEmpty() || name.isEmpty()) {
            status = "Code and name are required.";
            statusErr = true;
            return;
        }
        boolean ok;
        if (editGoalId >= 0) {
            Goal g = new Goal();
            g.setId(editGoalId);
            g.setCode(code);
            g.setName(name);
            g.setDescription(desc);
            g.setActive(active);
            ok = ctx.goalController.updateGoal(g);
        } else {
            ok = ctx.goalController.createGoal(code, name, desc) != null;
        }
        if (ok) {
            pendingStatus = editGoalId >= 0 ? "Goal updated." : "Goal created.";
            goTo(Screen.ADMIN_GOALS);
        } else {
            status = errText("Save failed", ctx.goalController.getLastError());
            statusErr = true;
        }
    }

    private void submitCatForm() {
        String name = fValues[0].trim();
        String desc = fValues[1].trim();
        int pIdx = choice(fValues[2], 0);
        long parentId = fChoiceIds[2][pIdx];
        int order = parseInt(fValues[3]) == null ? 0 : parseInt(fValues[3]);
        if (name.isEmpty()) {
            status = "A category name is required.";
            statusErr = true;
            return;
        }
        RecommendationCategory cat = new RecommendationCategory();
        cat.setName(name);
        cat.setDescription(desc);
        cat.setParentCategoryId(parentId < 0 ? null : parentId);
        cat.setDisplayOrder(order);
        boolean ok;
        if (editCatId >= 0) {
            cat.setId(editCatId);
            ok = ctx.recommendationController.updateCategory(cat);
        } else {
            ok = ctx.recommendationController.createCategory(cat);
        }
        if (ok) {
            pendingStatus = editCatId >= 0 ? "Category updated." : "Category created.";
            goTo(Screen.ADMIN_CATS);
        } else {
            status = errText("Save failed", ctx.recommendationController.getLastError());
            statusErr = true;
        }
    }

    // ------------------------------------------------------------------
    // Refresh helpers (menu screens)
    // ------------------------------------------------------------------
    private void refreshMenu() {
        switch (screen) {
            case WELCOME -> {
                menuLabels.add("Login");
                menuActions.add(() -> goTo(Screen.LOGIN));
                menuLabels.add("Register Account");
                menuActions.add(() -> goTo(Screen.REGISTER));
                menuLabels.add("Quit");
                menuActions.add(this::quitApp);
            }
            case RESET_SUCCESS -> {
                menuLabels.add("Login");
                menuActions.add(() -> {
                    resetUserId = null;
                    codeVerified = false;
                    goClean(Screen.LOGIN);
                });
                menuLabels.add("Quit");
                menuActions.add(this::quitApp);
            }
            case RESET_STATUS -> {
                menuLabels.add("Back");
                menuActions.add(this::goBack);
            }
            case RESET_APPROVED -> {
                menuLabels.add("Continue");
                menuActions.add(() -> {
                    resetStatusKind = "approved";
                    codeVerified = true;
                    pendingStatus = "Your request has been approved. Create your new password.";
                    status = "";
                    statusErr = false;
                    goTo(Screen.NEW_PASSWORD);
                });
                menuLabels.add("Back");
                menuActions.add(this::goBack);
            }
            case USER_HOME -> {
                User u = ctx.session.getCurrentUser();
                Optional<Goal> current = ctx.goalController.getCurrentGoal();
                if (current.isEmpty()) {
                    menuLabels.add("Choose a Goal");
                    menuActions.add(() -> goTo(Screen.GOAL_SELECT));
                    menuLabels.add("Recommendation Hub (select a goal first)");
                    menuActions.add(() -> goTo(Screen.RECOMMEND_HUB));
                } else {
                    menuLabels.add("Recommendation Hub");
                    menuActions.add(() -> goTo(Screen.RECOMMEND_HUB));
                    menuLabels.add("Change Goal");
                    menuActions.add(() -> goTo(Screen.GOAL_SELECT));
                }
                menuLabels.add("My Saved Recommendations");
                menuActions.add(() -> goTo(Screen.SAVED));
                menuLabels.add("My Profile");
                menuActions.add(() -> goTo(Screen.PROFILE));
                menuLabels.add("Logout");
                menuActions.add(this::logout);
                menuLabels.add("Quit");
                menuActions.add(this::quitApp);
            }
            case RECOMMEND_HUB -> {
                Optional<Goal> goal = ctx.goalController.getCurrentGoal();
                if (goal.isEmpty()) {
                    menuLabels.add("Select a Goal Now");
                    menuActions.add(() -> goTo(Screen.GOAL_SELECT));
                } else {
                    List<RecommendationCategory> cats = ctx.recommendationController.getTopCategories();
                    if (cats == null) {
                        menuLabels.add("Error: " + errText("", ctx.recommendationController.getLastError()));
                        menuActions.add(this::goBack);
                    } else {
                        for (RecommendationCategory c : cats) {
                            boolean master = isMasterRoutine(c.getName());
                            menuLabels.add(c.getName());
                            menuActions.add(() -> openCategory(master, c));
                        }
                    }
                }
            }
            case GOAL_SELECT -> {
                goals = ctx.goalController.listActiveGoals();
                if (goals == null) {
                    goals = new ArrayList<>();
                }
                Optional<Goal> current = ctx.goalController.getCurrentGoal();
                long curId = current.map(Goal::getId).orElse(-1L);
                for (Goal g : goals) {
                    String mark = g.getId() == curId ? " v (current)" : "";
                    menuLabels.add(g.getName() + mark);
                    menuActions.add(() -> selectGoal(g.getId()));
                }
            }
            case CATEGORY, SUBCATEGORY, MASTER -> {
                if (currentCategory == null) {
                    menuLabels.add("No category selected.");
                    menuActions.add(this::goBack);
                    return;
                }
                List<RecommendationCategory> children =
                        ctx.recommendationController.getSubCategories(currentCategory.getId());
                for (RecommendationCategory c : safe(children)) {
                    menuLabels.add(c.getName());
                    menuActions.add(() -> openSub(c));
                }
                boolean leaf = safe(children).isEmpty();
                if (screen == Screen.MASTER || leaf) {
                    menuLabels.add(screen == Screen.MASTER
                            ? "Generate Complete Master Routine"
                            : "Generate Recommendation");
                    menuActions.add(() -> generate(currentCategory));
                }
            }
            case RECOMMEND_DETAIL -> {
                if (result == null) {
                    menuLabels.add("No recommendation in memory.");
                    menuActions.add(this::goBack);
                } else {
                    menuLabels.add("LIFEForge Recommendation");
                    menuActions.add(this::showOfficialRecommendation);
                    menuLabels.add("Chat with AI");
                    menuActions.add(this::openAiChat);
                    menuLabels.add("Save This Recommendation");
                    menuActions.add(this::saveCurrent);
                    menuLabels.add("Back");
                    menuActions.add(this::goBack);
                }
            }
            case OFFICIAL_RECOMMENDATION -> { /* read-only detail screen */ }
            case EXPLANATION -> { /* info screen, empty menu */ }
            case SAVED_DETAIL -> {
                if (savedRec == null) {
                    menuLabels.add("Saved item no longer exists.");
                    menuActions.add(this::goBack);
                } else {
                    menuLabels.add("Remove This Saved Item");
                    menuActions.add(this::removeSaved);
                }
            }
            case PROFILE -> {
                menuLabels.add("Edit Profile");
                menuActions.add(() -> goTo(Screen.PROFILE_EDIT));
                menuLabels.add("Change Password");
                menuActions.add(() -> goTo(Screen.PROFILE_PASSWORD));
                if (armed) {
                    menuLabels.add("! Delete Account -- press Enter again to confirm");
                    menuActions.add(this::deleteAccount);
                } else {
                    menuLabels.add("Delete My Account");
                    menuActions.add(this::armDelete);
                }
            }
            case ADMIN_HOME -> {
                menuLabels.add("Manage Users");
                menuActions.add(() -> goTo(Screen.ADMIN_USERS));
                menuLabels.add("Recommendation CMS");
                menuActions.add(() -> goTo(Screen.ADMIN_RECS));
                menuLabels.add("Manage Goals");
                menuActions.add(() -> goTo(Screen.ADMIN_GOALS));
                menuLabels.add("Manage Categories");
                menuActions.add(() -> goTo(Screen.ADMIN_CATS));
                menuLabels.add("Password Reset Requests");
                menuActions.add(() -> goTo(Screen.ADMIN_RESET_REQUESTS));
                menuLabels.add("Analytics");
                menuActions.add(() -> goTo(Screen.ADMIN_ANALYTICS));
                menuLabels.add("Audit Logs");
                menuActions.add(() -> goTo(Screen.ADMIN_AUDIT));
                menuLabels.add("System Settings");
                menuActions.add(() -> goTo(Screen.ADMIN_SETTINGS));
                menuLabels.add("Logout");
                menuActions.add(this::logout);
                menuLabels.add("Quit");
                menuActions.add(this::quitApp);
            }
            case ADMIN_USER_ACTIONS -> {
                if (selUser == null) {
                    menuLabels.add("No user selected.");
                    menuActions.add(this::goBack);
                    return;
                }
                if (selUser.isBlocked()) {
                    menuLabels.add("Unblock User");
                    menuActions.add(() -> toggleBlock(false));
                } else {
                    menuLabels.add("Block User");
                    menuActions.add(() -> toggleBlock(true));
                }

                if (armed) {
                    menuLabels.add("! Delete User -- press Enter again to confirm");
                    menuActions.add(this::confirmDeleteUser);
                } else {
                    menuLabels.add("Delete User");
                    menuActions.add(this::armDelete);
                }
            }
            case ADMIN_RESET_DETAIL -> {
                if (selReset == null) {
                    menuLabels.add("No reset request selected.");
                    menuActions.add(this::goBack);
                } else {
                    boolean pending = com.lifeforge.service.PasswordResetService
                            .STATUS_PENDING.equals(selReset.getStatus());
                    if (pending) {
                        menuLabels.add("Approve Request");
                        menuActions.add(() -> offerResetDecision(true));
                        menuLabels.add("Reject Request");
                        menuActions.add(() -> offerResetDecision(false));
                    }
                    menuLabels.add("Back");
                    menuActions.add(this::goBack);
                }
            }
            case ADMIN_RESET_CONFIRM -> {
                if (selReset == null) {
                    menuLabels.add("Back");
                    menuActions.add(this::goBack);
                } else if (resetApproveReject) {
                    menuLabels.add("Yes, Approve Request");
                    menuActions.add(() -> performResetDecision(true));
                    menuLabels.add("Cancel");
                    menuActions.add(this::goBack);
                } else {
                    menuLabels.add("Yes, Reject Request");
                    menuActions.add(() -> performResetDecision(false));
                    menuLabels.add("Cancel");
                    menuActions.add(this::goBack);
                }
            }
            default -> { /* nothing */ }
        }
    }

    private void refreshSaved() {
        savedList = ctx.savedRecommendationController.listSaved();
        if (savedList == null) {
            savedList = new ArrayList<>();
            status = errText("Could not load saved items",
                    ctx.savedRecommendationController.getLastError());
            statusErr = true;
        }
        for (SavedRecommendation item : safe(savedList)) {
            Recommendation r = ctx.recommendationController.findRecommendationById(
                    item.getRecommendationId()).orElse(null);
            String when = item.getSavedAt() == null ? ""
                    : "  -  saved " + item.getSavedAt().toString().replace("T", " ");
            menuLabels.add((r == null ? "Recommendation #" + item.getRecommendationId()
                    : r.getTitle()) + when);
            menuActions.add(() -> openSaved(item));
        }
    }

    private void refreshUsers() {
        menuActions.clear();
        menuLabels.clear();
        users = searchQuery.isEmpty()
                ? ctx.adminController.listAllUsers()
                : ctx.adminController.searchUsers(searchQuery);
        if (users == null) {
            users = new ArrayList<>();
        }
        if (users.size() > 0 && sel >= users.size()) {
            sel = users.size() - 1;
        }
        int pages = Math.max(1, (int) Math.ceil((double) users.size() / userPageSize));
        if (userPage < 0) {
            userPage = 0;
        }
        if (userPage >= pages) {
            userPage = pages - 1;
        }
        int pageCount = Math.min(userPageSize, Math.max(0, users.size() - userPage * userPageSize));
        if (sel >= pageCount) {
            sel = Math.max(0, pageCount - 1);
        }
    }

    private void refreshAdminRecs() {
        if (catList == null || catList.isEmpty()) {
            catList = safe(ctx.recommendationController.listAllCategories());
        }

        recomms = ctx.recommendationController.listAllRecommendations();

        if (recomms == null) {
            recomms = new ArrayList<>();
        }

        displayedRecs = filterRecommendations(recommendationSearchQuery);

        // pagination bounds
        int total = displayedRecs.size();
        int pages = Math.max(1, (int) Math.ceil((double) total / recPageSize));
        if (recPage < 0) recPage = 0;
        if (recPage >= pages) recPage = pages - 1;
    }

    /** In-memory subset search on title, goal, category and activity level. */
    private List<Recommendation> filterRecommendations(String query) {
        List<Recommendation> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        for (Recommendation r : safe(recomms)) {
            String goalName = nameOfGoal(r.getGoalId());
            String categoryName = nameOfCat(r.getCategoryId());
            String activity = r.getActivityLevel() == null
                    ? "ALL"
                    : r.getActivityLevel().name();
            String title = r.getTitle() == null
                    ? ""
                    : r.getTitle();

            if (!q.isEmpty()) {
                String searchable = (
                        title + " " +
                                goalName + " " +
                                categoryName + " " +
                                activity
                ).toLowerCase(Locale.ROOT);

                if (!searchable.contains(q)) {
                    continue;
                }
            }

            out.add(r);
        }
        return out;
    }

    private void refreshAdminGoals() {
        menuActions.clear();
        menuLabels.clear();
        goals = ctx.goalController.listAllGoals();
        if (goals == null) {
            goals = new ArrayList<>();
        }
        int pages = Math.max(1, (int) Math.ceil((double) goals.size() / userPageSize));
        if (userPage >= pages) {
            userPage = pages - 1;
        }
        int pageCount = Math.min(userPageSize, Math.max(0, goals.size() - userPage * userPageSize));
        if (sel >= pageCount) {
            sel = Math.max(0, pageCount - 1);
        }
    }

    private void refreshAdminCats() {
        menuActions.clear();
        menuLabels.clear();
        catList = ctx.recommendationController.listAllCategories();
        if (catList == null) {
            catList = new ArrayList<>();
        }
        int pages = Math.max(1, (int) Math.ceil((double) catList.size() / userPageSize));
        if (userPage >= pages) {
            userPage = pages - 1;
        }
        int pageCount = Math.min(userPageSize, Math.max(0, catList.size() - userPage * userPageSize));
        if (sel >= pageCount) {
            sel = Math.max(0, pageCount - 1);
        }
    }

    private void refreshAnalytics() {
        analytics = ctx.adminController.buildAnalytics();
    }

    private void refreshLogs() {
        allAuditLogs = safe(ctx.adminController.getAllLogs());
        applyAuditFilter();
    }

    private void refreshAdminResets() {
        resetList = safe(ctx.adminController.listPasswordResets());
        resetUserLabels.clear();
        List<User> allUsers = safe(ctx.adminController.listAllUsers());
        for (User u : allUsers) {
            resetUserLabels.put(u.getId(), new String[] {
                    u.getFullName(),
                    u.getEmail() == null ? "" : u.getEmail()
            });
        }
        int pages = Math.max(1, (int) Math.ceil((double) resetList.size() / resetPageSize));
        if (resetPage < 0) {
            resetPage = 0;
        }
        if (resetPage >= pages) {
            resetPage = pages - 1;
        }
        int maxSel = Math.min(resetPageSize, Math.max(0, resetList.size() - resetPage * resetPageSize)) - 1;
        if (sel > maxSel) {
            sel = Math.max(0, maxSel);
        }
    }

    /**
     * Opens the approve/reject confirmation for a request from the admin list
     * (A/R keys) or from the detail screen menu. Only PENDING requests qualify.
     */
    private void offerResetDecision(boolean approve) {
        com.lifeforge.model.PasswordReset request = null;
        if (screen == Screen.ADMIN_RESET_DETAIL) {
            request = selReset;
        } else {
            int total = resetList == null ? 0 : resetList.size();
            int pageStart = resetPage * resetPageSize;
            int pageCount = Math.min(resetPageSize, Math.max(0, total - pageStart));
            if (total > 0 && sel >= 0 && sel < pageCount) {
                request = resetList.get(pageStart + sel);
            }
        }
        if (request == null) {
            status = "Select a password reset request first.";
            statusErr = true;
            return;
        }
        if (!com.lifeforge.service.PasswordResetService.STATUS_PENDING.equals(request.getStatus())) {
            status = approve
                    ? "Only pending requests can be approved."
                    : "Only pending requests can be rejected.";
            statusErr = true;
            return;
        }
        selReset = request;
        resetApproveReject = approve;
        pendingStatus = (approve ? "Approve" : "Reject")
                + " password reset request #" + request.getId() + "?";
        status = "";
        statusErr = false;
        goTo(Screen.ADMIN_RESET_CONFIRM);
    }

    /** Executes the confirmed approve/reject decision, then returns to the previous screen. */
    private void performResetDecision(boolean approve) {
        if (selReset == null) {
            goBack();
            return;
        }
        Long id = selReset.getId();
        boolean ok = approve
                ? ctx.adminController.approveResetRequest(id)
                : ctx.adminController.rejectResetRequest(id);
        if (ok) {
            refreshSelReset();
            resetApproveReject = false;
            pendingStatus = approve
                    ? "Password reset request #" + id + " approved. "
                            + "The user can now create a new password."
                    : "Password reset request #" + id + " rejected. "
                            + "The user cannot reset their password.";
            status = "";
            statusErr = false;
            goBack();
        } else {
            resetApproveReject = false;
            status = errText(approve ? "Approval failed" : "Rejection failed",
                    ctx.adminController.getLastError());
            statusErr = true;
            goBack();
        }
    }

    /** Re-reads the selected request so the detail screen reflects an admin decision. */
    private void refreshSelReset() {
        if (selReset == null) {
            return;
        }
        Long id = selReset.getId();
        for (com.lifeforge.model.PasswordReset r : safe(ctx.adminController.listPasswordResets())) {
            if (r.getId().equals(id)) {
                selReset = r;
                return;
            }
        }
    }

    private void applyAuditFilter() {
        String q = auditSearchQuery == null
                ? "" : auditSearchQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            logs = new ArrayList<>(allAuditLogs);
        } else {
            logs = new ArrayList<>();
            for (AuditLog log : allAuditLogs) {
                if (matchesAuditSearch(log, q)) {
                    logs.add(log);
                }
            }
        }
        int pages = Math.max(1, (int) Math.ceil((double) logs.size() / auditPageSize));
        if (auditPage < 0) {
            auditPage = 0;
        }
        if (auditPage >= pages) {
            auditPage = pages - 1;
        }
        if (sel >= Math.min(auditPageSize, Math.max(0, logs.size() - auditPage * auditPageSize))) {
            sel = Math.max(0, Math.min(auditPageSize - 1,
                    Math.max(0, logs.size() - auditPage * auditPageSize) - 1));
        }
    }

    private boolean matchesAuditSearch(AuditLog log, String q) {
        if (contains(log.getAction(), q)) {
            return true;
        }
        Object[] actionLabel = auditActionLabel(log.getAction());
        if (contains((String) actionLabel[0], q)) {
            return true;
        }
        if (contains("admin #" + (log.getActorUserId() == null ? "" : log.getActorUserId()), q)
                || contains(log.getActorUserId() == null ? "" : "#" + log.getActorUserId(), q)) {
            return true;
        }
        if (contains(log.getTargetType(), q)) {
            return true;
        }
        if (contains(log.getTargetId() == null ? "" : String.valueOf(log.getTargetId()), q)) {
            return true;
        }
        return contains(log.getDetails(), q);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private void refreshSettings() {
        dbOk = DatabaseConfig.testConnection();
    }

    // ------------------------------------------------------------------
    // Device actions used by menus
    // ------------------------------------------------------------------
    private void openCategory(boolean master, RecommendationCategory cat) {
        currentCategory = cat;
        result = null;
        if (master) {
            goTo(Screen.MASTER);
        } else {
            goTo(Screen.CATEGORY);
        }
    }

    private void openSub(RecommendationCategory child) {
        currentCategory = child;
        result = null;
        goTo(Screen.SUBCATEGORY);
    }

    private void generate(RecommendationCategory cat) {
        Optional<RecommendationService.RecommendationResult> r =
                ctx.recommendationController.generate(cat.getId());
        if (r.isEmpty()) {
            String error = ctx.recommendationController.getLastError();
            status = error == null
                    ? "No recommendation is available for this category yet. Try another category."
                    : error;
            statusErr = true;
            return;
        }
        currentCategory = cat;
        result = r.get();
        // Never reuse an explanation prepared for a previous recommendation.
        explanationText = null;
        explanationFromAi = false;
        chatConversation.clear();
        chatDraft = "";
        chatCursor = 0;
        resetChatInsertTracking();
        goTo(Screen.RECOMMEND_DETAIL);
    }

    private void saveCurrent() {
        if (result == null || result.recommendation == null) {
            return;
        }
        boolean ok = ctx.savedRecommendationController.save(result.recommendation.getId());
        status = ok ? "Recommendation saved to your list. *"
                : errText("Save failed", ctx.savedRecommendationController.getLastError());
        statusErr = !ok;
    }

    private void openSaved(SavedRecommendation item) {
        Recommendation r = ctx.recommendationController
                .findRecommendationById(item.getRecommendationId()).orElse(null);
        if (r == null) {
            status = "This saved recommendation no longer exists.";
            statusErr = true;
            return;
        }
        savedRec = r;
        savedRecId = item.getId();
        goTo(Screen.SAVED_DETAIL);
    }

    private void removeSaved() {
        if (savedRecId == null) {
            return;
        }
        boolean ok = ctx.savedRecommendationController.delete(savedRecId);
        if (ok) {
            pendingStatus = "Saved recommendation removed.";
            goBack();
        } else {
            status = errText("Remove failed",
                    ctx.savedRecommendationController.getLastError());
            statusErr = true;
        }
    }

    private void selectGoal(long id) {
        if (ctx.goalController.selectGoal(id)) {
            pendingStatus = "Goal selected: " + nameOfGoal(id);
            goTo(Screen.USER_HOME);
        } else {
            status = errText("Could not select goal", ctx.goalController.getLastError());
            statusErr = true;
        }
    }

    private void logout() {
        ctx.authController.logout();
        pendingStatus = "You have been logged out.";
        pendingErr = false;
        goClean(Screen.WELCOME);
    }

    private void armDelete() {
        armed = true;
        menuLabels.clear();
        menuActions.clear();
        refreshMenu();

        // Keep the selection on the delete option.
        if (!menuActions.isEmpty()) {
            sel = Math.min(sel, menuActions.size() - 1);
        }
    }

    private void deleteAccount() {
        boolean ok = ctx.userController.deleteAccount();
        if (ok) {
            pendingStatus = "Your account has been deleted. Goodbye.";
            goClean(Screen.WELCOME);
        } else {
            status = errText("Delete failed", ctx.userController.getLastError());
            statusErr = true;
            armed = false;
        }
    }

    private void toggleBlock(boolean block) {
        boolean ok = block
                ? ctx.adminController.blockUser(selUser.getId())
                : ctx.adminController.unblockUser(selUser.getId());
        if (ok) {
            pendingStatus = (block ? "Blocked" : "Unblocked") + " " + selUser.getFullName();
            goBack();
        } else {
            status = errText("Action failed", ctx.adminController.getLastError());
            statusErr = true;
        }
    }

//    private void setRole(Role role) {
//        boolean ok = ctx.adminController.changeRole(selUser.getId(), role);
//        if (ok) {
//            pendingStatus = "Role changed to " + role.name() + " for " + selUser.getFullName();
//            goBack();
//        } else {
//            status = errText("Action failed", ctx.adminController.getLastError());
//            statusErr = true;
//        }
//    }

    private void confirmDeleteUser() {
        boolean ok = ctx.adminController.deleteUser(selUser.getId());
        if (ok) {
            pendingStatus = "User " + selUser.getFullName() + " deleted.";
            goBack();
        } else {
            status = errText("Delete failed", ctx.adminController.getLastError());
            statusErr = true;
            armed = false;
        }
    }

    private void deleteSelectedRec(int index) {
        if (displayedRecs == null || index >= displayedRecs.size() || index < 0) {
            return;
        }
        Recommendation r = displayedRecs.get(index);
        boolean ok = ctx.recommendationController.deleteRecommendation(r.getId());
        if (ok) {
            pendingStatus = "Recommendation " + r.getTitle() + " deleted.";
            // return to the list that opened this confirmation (no extra back entry)
            editRecId = -1;
            goBack();
        } else {
            status = errText("Delete failed", ctx.recommendationController.getLastError());
            statusErr = true;
        }
    }

    private void deleteSelectedGoal(int index) {
        if (index >= goals.size()) {
            return;
        }
        Goal g = goals.get(index);
        boolean ok = ctx.goalController.deleteGoal(g.getId());
        if (ok) {
            pendingStatus = "Goal " + g.getName() + " deleted.";
            goTo(Screen.ADMIN_GOALS);
        } else {
            status = errText("Delete failed", ctx.goalController.getLastError());
            statusErr = true;
        }
    }

    private void deleteSelectedCat(int index) {
        if (index >= catList.size()) {
            return;
        }
        RecommendationCategory c = catList.get(index);
        boolean ok = ctx.recommendationController.deleteCategory(c.getId());
        if (ok) {
            pendingStatus = "Category " + c.getName() + " deleted.";
            goTo(Screen.ADMIN_CATS);
        } else {
            status = errText("Delete failed", ctx.recommendationController.getLastError());
            statusErr = true;
        }
    }

    // ------------------------------------------------------------------
    // View builders
    // ------------------------------------------------------------------
    private String viewWelcome(List<Line> body) {

        String logoAscii = """
            ██╗      ██╗███████╗███████╗███████╗ ██████╗ ██████╗  ██████╗ ███████╗
            ██║      ██║██╔════╝██╔════╝██╔════╝██╔═══██╗██╔══██╗██╔════╝ ██╔════╝
            ██║      ██║█████╗  █████╗  █████╗  ██║   ██║██████╔╝██║  ███╗█████╗
            ██║      ██║██╔══╝  ██╔══╝  ██╔══╝  ██║   ██║██╔══██╗██║   ██║██╔══╝
            ███████╗ ██║██║     ███████╗██║     ╚██████╔╝██║  ██║╚██████╔╝███████╗
            ╚══════╝ ╚═╝╚═╝     ╚══════╝╚═╝      ╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚══════╝
            """;

        for (String line : logoAscii.stripTrailing().split("\n")) {

            body.add(
                    Line.of(
                            Theme.title(),
                            Theme.padCenter(line.trim(), width - 2)
                    )
            );
        }

        body.add(
                Line.of(
                        Theme.pivot(),
                        Theme.padCenter(
                                "Personalized Health & Lifestyle Recommendation System",
                                width - 2
                        )
                )
        );

        body.add(
                Line.of(
                        Theme.dim(),
                        Theme.padCenter("v1.0.0", width - 2)
                )
        );

        body.addAll(menuLines());

        return "Personalized health, one goal at a time";
    }

    private String viewLogin(List<Line> body) {
        body.add(Line.of(Theme.dim(),
                "  Enter your email or username and password to continue."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "Login to continue";
    }

    private String viewRegister(List<Line> body) {
        body.add(Line.of(Theme.dim(),
                "  One account, fully personalized recommendations. Password fields are hidden."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "All fields are required";
    }

    private String viewForgotPassword(List<Line> body) {
        body.add(Line.of(Theme.headingCyan(), "  \uD83D\uDCE7  ENTER YOUR EMAIL"));
        body.add(Line.of(Theme.dim(),
                "  Submit a password reset request for administrator approval."));
        body.add(Line.of(Theme.dim(),
                "  If an account with this email exists, a request will be created."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "We never reveal whether an email belongs to an account";
    }

    private String viewVerifyCode(List<Line> body) {
        body.add(Line.of(Theme.headingCyan(), "  \uD83D\uDD10  ENTER VERIFICATION CODE"));
        body.add(Line.of(Theme.dim(),
                "  A 6-digit code was sent to your email. Enter it below."));
        body.add(Line.of(Theme.dim(),
                "  The code expires in " + AppConfig.getResetCodeExpirationMinutes() + " minutes."));
        if (AppConfig.isDevResetCodeLoggingEnabled() && resetUserId != null) {
            String dev = ctx.passwordResetController.devLastCode(resetUserId);
            if (dev != null) {
                body.add(Line.of(Theme.warn(), "  DEV: code = " + dev));
            }
        }
        body.add(Line.blank());
        body.addAll(formLines());
        return "Check your email for the 6-digit code";
    }

    private String viewNewPassword(List<Line> body) {
        body.add(Line.of(Theme.headingCyan(), "  \uD83D\uDD11  CREATE NEW PASSWORD"));
        body.add(Line.of(Theme.dim(),
                "  Enter a new password of at least "
                        + AppConfig.MIN_PASSWORD_LENGTH + " characters."));
        body.add(Line.of(Theme.dim(), "  Both fields must match."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "Minimum " + AppConfig.MIN_PASSWORD_LENGTH + " characters";
    }

    private String viewResetSuccess(List<Line> body) {
        body.add(Line.of(Theme.headingGreen(), "  \u2705  PASSWORD RESET COMPLETE"));
        body.add(Line.blank());
        body.add(Line.of(Theme.ok(),
                "  Your password has been successfully updated."));
        body.add(Line.of(Theme.dim(),
                "  You can now login with your new password or username."));
        body.add(Line.blank());
        body.addAll(menuLines());
        return "Your account is ready";
    }

    private String viewResetStatus(List<Line> body) {
        String kind = resetStatusKind == null ? "unknown" : resetStatusKind;
        switch (kind) {
            case "pending" -> {
                body.add(Line.of(Theme.headingCyan(), "  \u23F3  REQUEST PENDING"));
                body.add(Line.blank());
                body.add(Line.of(Theme.text(),
                        "  Your password reset request has been submitted and is"));
                body.add(Line.of(Theme.text(),
                        "  awaiting administrator approval."));
                body.add(Line.blank());
                body.add(Line.of(Theme.dim(),
                        "  You cannot create a new password until an administrator approves it."));
            }
            case "rejected" -> {
                body.add(Line.of(Theme.err(), "  \u2715  REQUEST REJECTED"));
                body.add(Line.blank());
                body.add(Line.of(Theme.text(),
                        "  Your password reset request was rejected by an administrator."));
                body.add(Line.blank());
                body.add(Line.of(Theme.dim(),
                        "  If you believe this is a mistake, please contact support."));
            }
            case "completed" -> {
                body.add(Line.of(Theme.headingGreen(), "  \u2705  RESET COMPLETED"));
                body.add(Line.blank());
                body.add(Line.of(Theme.text(),
                        "  Your password has already been reset."));
                body.add(Line.blank());
                body.add(Line.of(Theme.dim(),
                        "  You can now login with your new password."));
            }
            default -> {
                body.add(Line.of(Theme.headingCyan(), "  \uD83D\uDCE7  REQUEST SUBMITTED"));
                body.add(Line.blank());
                body.add(Line.of(Theme.text(),
                        "  If an account matches this email, a password reset request has"));
                body.add(Line.of(Theme.text(),
                        "  been submitted for administrator approval."));
                body.add(Line.blank());
                body.add(Line.of(Theme.dim(),
                        "  You will be notified once the request is decided."));
            }
        }
        body.add(Line.blank());
        body.addAll(menuLines());
        return "Password resets require administrator approval";
    }

    private String viewResetApproved(List<Line> body) {
        body.add(Line.of(Theme.headingGreen(), "  \u2705  REQUEST APPROVED"));
        body.add(Line.blank());
        body.add(Line.of(Theme.ok(),
                "  An administrator has approved your password reset request."));
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(),
                "  You can now create a new password for your account."));
        body.add(Line.blank());
        body.addAll(menuLines());
        return "Continue to create your new password";
    }

    private String viewUserHome(List<Line> body) {
        User u = ctx.session.getCurrentUser();
        String name = u == null ? "Friend" : u.getFullName();
        body.add(Line.of(Theme.headingGreen(), "  [ HELLO, " + name.toUpperCase(Locale.ROOT) + " ]"));
        Optional<Goal> g = ctx.goalController.getCurrentGoal();
        if (g.isPresent()) {
            body.add(Line.of(Theme.ok(), "  Current goal: " + g.get().getName()));
        } else {
            body.add(Line.of(Theme.warn(),
                    "  You have not selected a goal yet. Pick one to unlock recommendations."));
        }
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(), "  What would you like to do?"));
        body.addAll(menuLines());
        return getRoleBadge();
    }

    private String viewGoalSelect(List<Line> body) {
        if (goals.isEmpty()) {
            body.add(Line.of(Theme.warn(),
                    "  No active goals are configured. Ask an administrator to add some."));
        } else {
            body.add(Line.of(Theme.dim(), "  Arrow keys to choose, Enter to select."));
            body.addAll(menuLines());
        }
        return "Current goal uses v";
    }

    private String viewRecommendHub(List<Line> body) {
        Optional<Goal> g = ctx.goalController.getCurrentGoal();
        if (g.isEmpty()) {
            body.add(Line.of(Theme.warn(),
                    "  You need a goal before recommendations can be generated."));
            body.add(Line.blank());
            body.addAll(menuLines());
            return "Select a goal to unlock recommendations";
        }
        body.add(Line.of(Theme.ok(), "  Goal: " + g.get().getName()));
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(), "  Pick a category:"));
        if (safe(ctx.recommendationController.getTopCategories()).isEmpty() && menuActions.isEmpty()) {
            body.add(Line.of(Theme.warn(),
                    "  No recommendation categories are configured yet. Ask an administrator."));
        }
        body.addAll(menuLines());
        return "Generate personalized health recommendations";
    }

    private String viewCategory(List<Line> body) {
        RecommendationCategory c = currentCategory;
        if (c != null && c.getDescription() != null && !c.getDescription().isBlank()) {
            body.addAll(ScreenKit.paragraph(c.getDescription(), width - 2));
            body.add(Line.blank());
        }
        if (screen == Screen.MASTER) {
            body.add(Line.of(Theme.dim(), "  Steps of your complete routine:"));
        } else if (!menuActions.isEmpty()) {
            String hint = screen == Screen.CATEGORY ? "Subcategories:" : "Choose a step or generate:";
            body.add(Line.of(Theme.dim(), "  " + hint));
        }
        body.addAll(menuLines());
        return (screen == Screen.MASTER ? "Complete Master Routine" : "Category") + " description";
    }

    private String viewExplanation(List<Line> body) {
        String text = nvl(explanationText);
        if (text.isEmpty()) {
            body.add(Line.of(Theme.warn(), " No explanation is available for this recommendation."));
        } else {
            body.add(Line.of(Theme.headingPurple(), " [ WHY THIS RECOMMENDATION? ]"));
            body.addAll(ScreenKit.paragraph(text, width - 2));
            body.add(Line.blank());
            if (explanationFromAi) {
                body.add(Line.of(Theme.ok(), " Source: AI Personalized Explanation"));
            } else {
                body.add(Line.of(Theme.dim(),
                        " AI explanation is currently unavailable. Showing the standard Logic explanation."));
            }
        }
        return "Personalized reasoning behind your recommendation";
    }

    private String viewRecommendDetail(List<Line> body) {
        if (result == null) {
            body.add(Line.of(Theme.warn(), " No recommendation loaded."));
            return "";
        }
        Recommendation r = result.recommendation;
        body.add(Line.of(Theme.headingCyan(), "  RECOMMENDATION  /  " + nvl(r.getTitle())));
        addSection(body, "Description", r.getDescription());
        addSection(body, "Recommended Actions", r.getRecommendedActions());
        addSection(body, "Suggested Target", r.getSuggestedTarget());
        addSection(body, "Examples", r.getExamples());
        addSection(body, "Important Notes", r.getImportantNotes());
        body.add(Line.blank());
        body.add(ScreenKit.section("Personalized Guidance"));
        CalorieService.CalorieSummary cs = result.calorieSummary;
        if (result.calorieRelevant && cs != null) {
            body.add(ScreenKit.labelValue("BMR", String.format("%.0f kcal/day", cs.bmr)));
            body.add(ScreenKit.labelValue("TDEE", String.format("%.0f kcal/day", cs.tdee)));
            body.add(ScreenKit.labelValue("Suggested target",
                    String.format("%.0f kcal/day", cs.suggestedTarget)));
        }
        body.add(ScreenKit.labelValue("Hydration",
                String.format("%.2f L/day", result.suggestedHydrationLiters)));
        body.add(Line.blank());
        body.addAll(menuLines());
        return "Tuned to your age, body and activity level";
    }

    /**
     * Read-only view of the recommendation selected by the deterministic engine.
     * This deliberately reads only the already-loaded result; it never invokes AI
     * or creates replacement recommendation content.
     */
    private String viewOfficialRecommendation(List<Line> body) {
        if (result == null || result.recommendation == null) {
            body.add(Line.of(Theme.warn(), " No official recommendation is available."));
            return "Return to the recommendation hub and generate one first";
        }
        Recommendation r = result.recommendation;
        Optional<Goal> goal = ctx.recommendationController.currentGoal();
        body.add(Line.of(Theme.headingPurple(), "  [ OFFICIAL LIFEForge RECOMMENDATION ]"));
        body.add(Line.of(Theme.dim(), "  Generated by the LifeForge rule-based system"));
        body.add(Line.blank());
        body.add(ScreenKit.labelValue("Goal", goal.map(Goal::getName).orElse("-")));
        body.add(ScreenKit.labelValue("Category", currentCategory == null ? "-" : currentCategory.getName()));
        body.add(Line.blank());
        body.add(ScreenKit.hr(width - 2));
        body.add(Line.blank());
        body.add(Line.of(Theme.headingCyan(), "  " + nvl(r.getTitle())));
        addSection(body, "Description", r.getDescription());
        addSection(body, "Recommended Actions", r.getRecommendedActions());
        addSection(body, "Suggested Target", r.getSuggestedTarget());
        addSection(body, "Examples", r.getExamples());
        addSection(body, "Important Notes", r.getImportantNotes());
        return "Official recommendation — AI cannot change it";
    }

    private String viewAiChat(List<Line> body) {
        if (result == null || result.recommendation == null) {
            body.add(Line.of(Theme.warn(), "  No LifeForge recommendation is available."));
            return "Return to the recommendation hub and generate one first";
        }
        Recommendation recommendation = result.recommendation;
        Optional<Goal> goal = ctx.recommendationController.currentGoal();
        body.add(ScreenKit.labelValue("Goal", goal.map(Goal::getName).orElse("-")));
        body.add(ScreenKit.labelValue("Category",
                currentCategory == null ? "-" : currentCategory.getName()));
        body.add(Line.blank());
        body.add(ScreenKit.section("Current LifeForge Recommendation"));
        body.add(Line.of(Theme.headingPurple(), "  " + nvl(recommendation.getTitle())));
        body.add(Line.blank());

        if (chatConversation.isEmpty()) {
            body.add(Line.of(Theme.muted(),
                    "  Ask about your goal, recommendation, or a lifestyle situation."));
        } else {
            body.add(ScreenKit.section("Conversation"));
            int start = Math.max(0, chatConversation.size() - 6);
            for (int i = start; i < chatConversation.size(); i++) {
                String entry = chatConversation.get(i);
                boolean ai = entry.startsWith("AI: ");
                String label = ai ? "  LIFEForge AI" : "  YOU";
                String text = entry.substring(ai ? 4 : 6);
                body.add(Line.of(ai ? Theme.pivot() : Theme.headingGreen(), label));
                body.addAll(ScreenKit.paragraph(text, width - 2));
                body.add(Line.blank());
            }
        }

        body.add(ScreenKit.section("Your Question"));
        String draft = chatDraft.substring(0, Math.min(chatCursor, chatDraft.length())) + "|"
                + chatDraft.substring(Math.min(chatCursor, chatDraft.length()));
        for (String line : Theme.wrap(draft, Math.max(12, width - 8))) {
            body.add(Line.of(Theme.text(), "  > " + line));
        }
        return "Optional AI assistance — it cannot change the official recommendation";
    }

    private String viewSaved(List<Line> body) {
        if (savedList == null) {
            savedList = new ArrayList<>();
        }
        if (savedList.isEmpty()) {
            body.add(Line.of(Theme.warn(),
                    "  You have not saved any recommendations yet. Generate one from the "
                            + "Recommendation Hub and save it to see it here."));
        } else {
            body.add(Line.of(Theme.dim(), "  " + savedList.size() + " saved item(s)."));
            body.add(Line.blank());
            body.addAll(menuLines());
        }
        return "Everything you bookmarked";
    }

    private String viewSavedDetail(List<Line> body) {
        if (savedRec == null) {
            return "";
        }
        Recommendation r = savedRec;
        body.add(Line.of(Theme.headingCyan(), "  " + nvl(r.getTitle())));
        addSection(body, "Description", r.getDescription());
        addSection(body, "Recommended Actions", r.getRecommendedActions());
        addSection(body, "Suggested Target", r.getSuggestedTarget());
        addSection(body, "Examples", r.getExamples());
        addSection(body, "Important Notes", r.getImportantNotes());
        body.add(Line.blank());
        body.addAll(menuLines());
        return "Saved " + (savedRecId == null ? "" : savedRecId);
    }

    private String viewProfile(List<Line> body) {
        User u = ctx.session.getCurrentUser();
        if (u == null) {
            return "";
        }
        body.add(Line.of(Theme.headingGreen(), "  [ " + u.getFullName().toUpperCase(Locale.ROOT) + " ]"));
        body.add(ScreenKit.labelValueStyled("Email", u.getEmail(), Theme.pivot()));
        body.add(ScreenKit.labelValue("Role", u.getRole().name()));
        body.add(ScreenKit.labelValue("Age", u.getAge() == null ? "-" : String.valueOf(u.getAge())));
        body.add(ScreenKit.labelValue("Gender", human(u.getGender())));
        body.add(ScreenKit.labelValue("Height", u.getHeightCm() == null ? "-"
                : String.format("%.0f cm", u.getHeightCm())));
        body.add(ScreenKit.labelValue("Weight", u.getWeightKg() == null ? "-"
                : String.format("%.1f kg", u.getWeightKg())));
        body.add(ScreenKit.labelValue("Activity", human(u.getActivityLevel())));
        body.add(ScreenKit.labelValue("Member since", u.getCreatedAt() == null ? "-"
                : u.getCreatedAt().toString().replace("T", " ")));
        body.add(Line.blank());
        body.addAll(menuLines());
        return "Your profile and account";
    }

    private String viewProfileEdit(List<Line> body) {
        body.add(Line.of(Theme.dim(), "  Update your details below."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "Empty fields are ignored";
    }

    private String viewProfilePassword(List<Line> body) {
        body.add(Line.of(Theme.dim(), "  Change your password. All fields are required."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "Min " + AppConfig.MIN_PASSWORD_LENGTH + " characters";
    }

    private String viewAdminHome(List<Line> body) {
        User u = ctx.session.getCurrentUser();
        body.add(Line.of(Theme.headingPurple(),
                "  [ ADMINISTRATOR: " + ((u == null) ? "?" : u.getFullName().toUpperCase(Locale.ROOT)) + " ]"));
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(), "  Choose an area to manage:"));
        body.addAll(menuLines());
        return "Full control panel";
    }

    private String viewAdminUsers(List<Line> body) {
        if (!searchQuery.isEmpty()) {
            body.add(Line.of(Theme.pivot(), "  Filter: \"" + searchQuery + "\""));
            body.add(Line.of(Theme.dim(), "  Press X to clear the filter."));
        } else {
            body.add(Line.of(Theme.dim(),
                    "  Enter = open details   S = search   \u2191/\u2193 = move   \u2190/\u2192 = page"));
        }
        body.add(Line.blank());

        int totalAll = users == null ? 0 : users.size();
        int pageStart = userPage * userPageSize;
        int pageEnd = Math.min(pageStart + userPageSize, totalAll);

        String[] headers = { "ID", "NAME", "EMAIL", "STATUS" };
        String[][] rows = new String[pageEnd - pageStart][4];
        for (int i = pageStart; i < pageEnd; i++) {
            User u = users.get(i);
            String id = "#" + u.getId();
            String name = u.getFullName();
            String email = u.getEmail();
            String status = u.isBlocked() ? "\uD83D\uDD12 Blocked" : "\u25CF Active";
            rows[i - pageStart] = new String[] { id, name, email, status };
        }

        body.addAll(ScreenKit.proTable(headers, rows, Math.max(0, sel),
                width - 4));

        if (totalAll > 0) {
            body.add(Line.of(Theme.dim(), String.format(
                    "  Page %d/%d   \u2014   %s%d user(s)%s",
                    userPage + 1, Math.max(1, (int) Math.ceil((double) totalAll / userPageSize)),
                    searchQuery.isEmpty() ? "" : "filtered ", totalAll,
                    searchQuery.isEmpty() ? "" : " total")));
        }
        return users.size() + " user(s)";
    }

    private String viewAdminUserActions(List<Line> body) {
        if (selUser == null) {
            return "";
        }
        body.add(Line.of(Theme.headingGreen(), "  [ " + selUser.getFullName() + " ]"));
        body.add(ScreenKit.labelValueStyled("Username", selUser.getUsername() == null
                ? "(not set)" : selUser.getUsername(), Theme.pivot()));
        body.add(ScreenKit.labelValueStyled("Email", selUser.getEmail(), Theme.pivot()));
        body.add(ScreenKit.labelValue("User ID", String.valueOf(selUser.getId())));
        body.add(ScreenKit.labelValue("Role", selUser.getRole().name()));
        body.add(ScreenKit.labelValue("Status", selUser.isBlocked() ? "\uD83D\uDD12 Blocked" : "\u25CF Active"));
        body.add(ScreenKit.labelValue("Age", selUser.getAge() == null ? "-"
                : String.valueOf(selUser.getAge())));
        Gender gender = selUser.getGender();
        body.add(ScreenKit.labelValue("Gender", gender == null ? "-" : gender.name().substring(0, 1)
                + gender.name().substring(1).toLowerCase(Locale.ROOT)));
        Double h = selUser.getHeightCm();
        body.add(ScreenKit.labelValue("Height", h == null ? "-" : String.valueOf(h) + " cm"));
        Double w = selUser.getWeightKg();
        body.add(ScreenKit.labelValue("Weight", w == null ? "-" : String.valueOf(w) + " kg"));
        body.add(Line.blank());
        body.addAll(menuLines());
        return "Manage this user account";
    }

    private String viewAdminSearch(List<Line> body) {
        body.add(Line.of(Theme.dim(), "  Type a name or email fragment."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "Enter to filter the user list";
    }

private String viewAdminRecs(List<Line> body) {

        int totalAll = displayedRecs == null ? 0 : displayedRecs.size();
        int pages = Math.max(1, (int) Math.ceil((double) totalAll / recPageSize));
        int pageStart = recPage * recPageSize;
        int pageEnd = Math.min(pageStart + recPageSize, totalAll);

        // -- search bar --
        String searchLabel = recommendationSearchQuery != null && !recommendationSearchQuery.isEmpty()
                ? recommendationSearchQuery
                : "";
        String searchDisplay = Theme.padRight(searchLabel, width - 20);
        body.add(Line.of(Theme.headingCyan(),
                "  \uD83D\uDD0E Search: [ " + searchDisplay + " ]"));
        if (recommendationSearchQuery != null && !recommendationSearchQuery.isEmpty()) {
            body.add(Line.of(Theme.dim(),
                    "  Active filter: \"" + recommendationSearchQuery + "\"   (S to clear)"));
        }
        body.add(Line.blank());

        // -- professional table --
        String[] headers = { "ID", "GOAL", "CATEGORY", "ACTIVITY", "STATUS" };
        String[][] rows = new String[pageEnd - pageStart][5];
        for (int i = pageStart; i < pageEnd; i++) {
            Recommendation r = displayedRecs.get(i);
            String activity = r.getActivityLevel() == null ? "ALL"
                    : human(r.getActivityLevel());
            rows[i - pageStart] = new String[] {
                    "#" + r.getId(),
                    nameOfGoal(r.getGoalId()),
                    nameOfCategory(r.getCategoryId()),
                    activity,
                    "\u25CF Active"
            };
        }
        body.addAll(ScreenKit.proTable(headers, rows,
                totalAll == 0 ? 0 : Math.max(0, sel), width - 4));
        body.add(Line.blank());

        // -- pagination info --
        if (totalAll > 0) {
            body.add(Line.of(Theme.dim(),
                    "  Showing " + (pageStart + 1) + "\u2013" + pageEnd
                            + " of " + totalAll + "       Page " + (recPage + 1)
                            + " / " + pages));
        }

        return totalAll + " recommendation(s)";
    }

    private String nameOfCategory(Long categoryId) {
        if (categoryId == null) {
            return "No Category";
        }

        for (RecommendationCategory c : safe(catList)) {
            if (categoryId.equals(c.getId())) {
                return c.getName();
            }
        }

        List<RecommendationCategory> categories =
                safe(ctx.recommendationController.listAllCategories());
        for (RecommendationCategory c : categories) {
            if (categoryId.equals(c.getId())) {
                return c.getName();
            }
        }

        return "Unknown Category";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }

        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }

        return value.substring(0, maxLength - 3) + "...";
    }
    private String viewAdminRecForm(List<Line> body) {
        if (editRecId >= 0) {
            body.add(Line.of(Theme.headingCyan(), "  \u270F EDIT RECOMMENDATION #" + editRecId));
        } else {
            body.add(Line.of(Theme.headingCyan(), "  \u2795 NEW RECOMMENDATION"));
        }
        body.add(Line.of(Theme.dim(),
                editRecId >= 0
                        ? "  Adjust the fields below, then Save Changes."
                        : "  Fill in the fields, then Save Changes."));
        body.add(Line.blank());
        body.addAll(formLines());
        return editRecId >= 0
                ? "Edit Recommendation #" + editRecId
                : "Create Recommendation";
    }

    private String viewAdminRecDetail(List<Line> body) {
        Recommendation r = findRecById(editRecId);
        if (r == null) {
            body.add(Line.of(Theme.warn(), "  This recommendation no longer exists."));
            body.add(Line.of(Theme.dim(), "  Press B to go back to the list."));
            return "Recommendation not found";
        }

        int inner = Math.max(30, width - 2);

        body.add(Line.of(Theme.headingCyan(), "  \uD83D\uDCA1 RECOMMENDATION #" + r.getId()));
        body.add(Line.blank());

        // ---- BASIC INFORMATION ----
        body.add(ScreenKit.section("Basic Information"));
        body.add(ScreenKit.labelValueStyled("Goal", nvl(nameOfGoal(r.getGoalId())), Theme.pivot()));
        body.add(ScreenKit.labelValue("Category", nvl(nameOfCategory(r.getCategoryId()))));
        String activityLabel = r.getActivityLevel() == null ? "ALL"
                : human(r.getActivityLevel());
        body.add(ScreenKit.labelValue("Activity Level", activityLabel));
        body.add(ScreenKit.labelValueStyled("Status", "\u25CF Active", Theme.ok()));
        body.add(Line.blank());

        // ---- CONTENT ----
        body.add(ScreenKit.section("Content"));
        body.add(Line.blank());
        body.add(ScreenKit.labelValueStyled("Title", nvl(r.getTitle()), Theme.headingGreen()));
        body.addAll(addDetailSectionLines(body, "Description", r.getDescription(), inner));
        body.addAll(addDetailSectionLines(body, "Recommended Actions", r.getRecommendedActions(), inner));
        body.addAll(addDetailSectionLines(body, "Suggested Target", r.getSuggestedTarget(), inner));
        body.addAll(addDetailSectionLines(body, "Examples", r.getExamples(), inner));
        body.addAll(addDetailSectionLines(body, "Important Notes", r.getImportantNotes(), inner));

        body.add(Line.blank());
        body.add(Line.of(Theme.headingCyan(), "  [ E ] Edit this recommendation"));
        body.add(Line.of(Theme.dim(), "  [ B ] Back to list"));

        return "Press E to edit, B to go back";
    }

    private String viewAdminRecDeleteConfirm(List<Line> body) {
        Recommendation r = findRecById(editRecId);
        if (r == null) {
            body.add(Line.of(Theme.warn(), "  This recommendation no longer exists."));
            body.add(Line.of(Theme.dim(), "  Press B to go back to the list."));
            return "Recommendation not found";
        }

        body.add(Line.of(Theme.err(), "  \u26A0 DELETE RECOMMENDATION"));
        body.add(Line.blank());
        body.add(Line.of(Theme.warn(), "  Are you sure you want to delete:"));
        body.add(Line.blank());
        body.add(Line.of(Theme.text(), "  #" + r.getId() + "  " + nvl(r.getTitle())));
        body.add(Line.of(Theme.dim(), "  " + nvl(nameOfGoal(r.getGoalId()))
                + " / " + nvl(nameOfCategory(r.getCategoryId()))));
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(),
                "  This action cannot be undone."));

        // -- confirmation buttons --
        body.add(Line.of(Theme.err(), "  [ Y ] Yes, Delete"));
        body.add(Line.of(Theme.text(), "  [ N ] Cancel"));

        return "Delete confirmation required";
    }

    private List<Line> addDetailSectionLines(List<Line> body, String label, String value, int inner) {
        if (value != null && !value.isBlank()) {
            body.add(Line.blank());
            body.add(Line.of(Theme.headingCyan(), "  \u270F " + label));
            body.addAll(ScreenKit.paragraph(value, inner));
        }
        return body;
    }

    private String viewAdminGoals(List<Line> body) {
        int totalAll = goals == null ? 0 : goals.size();
        if (totalAll == 0) {
            body.add(Line.of(Theme.warn(), "  No goals yet. Press N to create one."));
            return "0 goal(s)";
        }

        body.add(Line.of(Theme.dim(),
                "  Enter = edit   N = new   D = delete   \u2191/\u2193 = move   \u2190/\u2192 = page"));
        body.add(Line.blank());

        int pageStart = userPage * userPageSize;
        int pageEnd = Math.min(pageStart + userPageSize, totalAll);

        String[] headers = { "ID", "CODE", "GOAL NAME", "STATUS" };
        String[][] rows = new String[pageEnd - pageStart][4];
        for (int i = pageStart; i < pageEnd; i++) {
            Goal g = goals.get(i);
            rows[i - pageStart] = new String[] {
                    "#" + g.getId(),
                    g.getCode(),
                    g.getName(),
                    g.isActive() ? "\u25CF Active" : "\u25CB Inactive"
            };
        }
        body.addAll(ScreenKit.proTable(headers, rows, Math.max(0, sel), width - 4));
        body.add(Line.of(Theme.dim(), String.format("  Page %d/%d   \u2014   %d goal(s)",
                userPage + 1, Math.max(1, (int) Math.ceil((double) totalAll / userPageSize)),
                totalAll)));
        return totalAll + " goal(s)";
    }

    private String viewAdminGoalForm(List<Line> body) {
        body.add(Line.of(Theme.dim(), "  Code is the short slug used to reference this goal."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "e.g. WEIGHT_LOSS -> code WEIGHT_LOSS";
    }

    private String viewAdminCats(List<Line> body) {
        int totalAll = catList == null ? 0 : catList.size();
        if (totalAll == 0) {
            body.add(Line.of(Theme.warn(), "  No categories yet. Press N to create one."));
            return "0 category(ies)";
        }

        body.add(Line.of(Theme.dim(),
                "  Enter = edit   N = new   D = delete   \u2191/\u2193 = move   \u2190/\u2192 = page"));
        body.add(Line.blank());

        int pageStart = userPage * userPageSize;
        int pageEnd = Math.min(pageStart + userPageSize, totalAll);

        String[] headers = { "ID", "CATEGORY", "PARENT", "STATUS" };
        String[][] rows = new String[pageEnd - pageStart][4];
        Map<Long, String> parentNames = new HashMap<>();
        for (RecommendationCategory c : catList) {
            parentNames.put(c.getId(), c.getName());
        }
        for (int i = pageStart; i < pageEnd; i++) {
            RecommendationCategory c = catList.get(i);
            String parent = c.getParentCategoryId() == null
                    ? "-" : parentNames.getOrDefault(c.getParentCategoryId(), "#" + c.getParentCategoryId());
            rows[i - pageStart] = new String[] {
                    "#" + c.getId(),
                    c.getName(),
                    parent,
                    "\u25CF Active"
            };
        }
        body.addAll(ScreenKit.proTable(headers, rows, Math.max(0, sel), width - 4));
        body.add(Line.of(Theme.dim(), String.format("  Page %d/%d   \u2014   %d category(ies)",
                userPage + 1, Math.max(1, (int) Math.ceil((double) totalAll / userPageSize)),
                totalAll)));
        return totalAll + " category(ies)";
    }

    private String viewAdminCatForm(List<Line> body) {
        body.add(Line.of(Theme.dim(), "  Parent categories build the sub-topics tree."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "Lower order = shown first";
    }

    private String viewAdminAnalytics(List<Line> body) {
        if (analytics == null) {
            body.add(Line.of(Theme.warn(), "  Analytics are not available."));
            return "";
        }

        int innerWidth = Math.max(30, width - 2);

        // ---- 1. PLATFORM SNAPSHOT ----
        body.add(Line.of(Theme.headingCyan(), "  [ PLATFORM SNAPSHOT ]"));
        body.add(Line.blank());
        body.addAll(kpiRow("Total Users", String.valueOf(analytics.totalUsers),
                "Active Users", String.valueOf(analytics.activeUsers), innerWidth));
        body.addAll(kpiRow("Recommendations", String.valueOf(analytics.totalRecommendations),
                "Saved Recommendations", String.valueOf(analytics.totalSavedRecommendations), innerWidth));
        body.add(Line.blank());

        // ---- 2. GOAL DISTRIBUTION ----
        body.add(ScreenKit.section("Goal Distribution"));
        List<AnalyticsService.GoalDistributionEntry> gd = safe(analytics.goalDistribution);
        if (gd.isEmpty()) {
            body.add(Line.of(Theme.dim(), "  No goal selections yet."));
        } else {
            long totalGoalUsers = 0;
            for (AnalyticsService.GoalDistributionEntry e : gd) {
                totalGoalUsers += e.userCount;
            }
            String[] gdHeaders = { "GOAL", "USERS", "PERCENT" };
            String[][] gdRows = new String[gd.size()][3];
            for (int i = 0; i < gd.size(); i++) {
                AnalyticsService.GoalDistributionEntry e = gd.get(i);
                double pct = totalGoalUsers == 0 ? 0 : (e.userCount * 100.0 / totalGoalUsers);
                gdRows[i] = new String[] {
                        e.goalName,
                        String.valueOf(e.userCount),
                        String.format("%.1f%%", pct)
                };
            }
            body.addAll(ScreenKit.proTable(gdHeaders, gdRows, -1, width - 4));
            if (totalGoalUsers > 0) {
                body.add(Line.of(Theme.dim(),
                        "  Based on " + totalGoalUsers + " user goal selection(s)."));
            }
        }
        body.add(Line.blank());

        // ---- 3. POPULAR CATEGORIES ----
        body.add(ScreenKit.section("Popular Categories"));
        List<AnalyticsService.CategoryPopularityEntry> pc = safe(analytics.popularCategories);
        if (pc.isEmpty()) {
            body.add(Line.of(Theme.dim(), "  Nothing saved yet."));
        } else {
            long maxSaves = 0;
            long totalSaves = 0;
            for (AnalyticsService.CategoryPopularityEntry e : pc) {
                maxSaves = Math.max(maxSaves, e.saveCount);
                totalSaves += e.saveCount;
            }
            String[] pcHeaders = { "CATEGORY", "SAVES", "SHARE" };
            String[][] pcRows = new String[pc.size()][3];
            for (int i = 0; i < pc.size(); i++) {
                AnalyticsService.CategoryPopularityEntry e = pc.get(i);
                double pct = totalSaves == 0 ? 0 : (e.saveCount * 100.0 / totalSaves);
                pcRows[i] = new String[] {
                        e.categoryName,
                        String.valueOf(e.saveCount),
                        String.format("%.1f%%", pct)
                };
            }
            body.addAll(ScreenKit.proTable(pcHeaders, pcRows, -1, width - 4));
        }

        return "Platform health at a glance";
    }

    private List<Line> kpiRow(String label1, String value1, String label2, String value2, int totalInnerWidth) {
        List<Line> out = new ArrayList<>();
        int gap = 1;
        int boxWidth = Math.max(16, (totalInnerWidth - gap) / 2);

        String top = "┌" + "─".repeat(boxWidth - 2) + "┐";
        String bottom = "└" + "─".repeat(boxWidth - 2) + "┘";

        out.add(Line.of(Theme.dim(), "  " + top + " " + top));
        out.add(Line.of(Theme.headingCyan(),
                "  " + kpiContentLine(label1, value1, boxWidth) + " " + kpiContentLine(label2, value2, boxWidth)));
        out.add(Line.of(Theme.dim(), "  " + bottom + " " + bottom));
        return out;
    }

    private String kpiContentLine(String label, String value, int boxWidth) {
        int innerWidth = Math.max(4, boxWidth - 2);
        String lbl = label.toUpperCase(Locale.ROOT);

        int pad = innerWidth - lbl.length() - value.length();
        if (pad < 1) {
            int maxLabelLen = Math.max(1, innerWidth - value.length() - 1);
            lbl = truncate(lbl, maxLabelLen);
            pad = Math.max(0, innerWidth - lbl.length() - value.length());
        }

        String content = lbl + " ".repeat(pad) + value;
        content = Theme.padRight(content, innerWidth);
        return "│" + content + "│";
    }

    private String viewAdminAudit(List<Line> body) {
        List<AuditLog> view = safe(logs);
        int rowW = 55 + auditDetailsWidth();

        String count = "  📊 " + view.size() + " audit event" + (view.size() == 1 ? "" : "s");
        if (!auditSearchQuery.isEmpty()) {
            count += "    ·    filter: \"" + auditSearchQuery + "\"";
        }
        body.add(Line.of(Theme.dim(), count));
        body.add(Line.blank());

        body.add(Line.of(Theme.headingPurple(), auditHeader()));
        body.add(Line.of(Theme.bar(), "  " + Theme.dup('\u2500', rowW - 2)));

        if (view.isEmpty()) {
            body.add(Line.of(Theme.warn(),
                    auditSearchQuery.isEmpty()
                            ? "  No audit events recorded yet."
                            : "  No audit events match your search."));
            body.add(Line.blank());
            return "Administrative activity and security events";
        }

        int pageStart = auditPage * auditPageSize;
        int pageEnd = Math.min(pageStart + auditPageSize, view.size());
        for (int i = pageStart; i < pageEnd; i++) {
            body.add(auditRow(view.get(i), i == sel));
        }

        body.add(Line.of(Theme.bar(), "  " + Theme.dup('\u2500', rowW - 2)));

        int pages = Math.max(1, (int) Math.ceil((double) view.size() / auditPageSize));
        String showing = "Showing " + (pageStart + 1) + "\u2013" + pageEnd + " of " + view.size();
        body.add(Line.of(Theme.dim(), "  " + Theme.padRight(showing, Math.max(20, rowW - 14))
                + "Page " + (auditPage + 1) + " / " + pages));
        body.add(Line.blank());
        return "Administrative activity and security events";
    }

    private String viewAdminAuditDetail(List<Line> body) {
        AuditLog l = selAudit;
        if (l == null) {
            body.add(Line.of(Theme.warn(), "  No audit record selected."));
            return "";
        }
        body.add(Line.of(Theme.headingGreen(), "  [ AUDIT EVENT #" + l.getId() + " ]"));
        body.add(Line.blank());
        body.add(ScreenKit.labelValueStyled("Log ID", "#" + l.getId(), Theme.pivot()));
        body.add(ScreenKit.labelValue("Timestamp",
                l.getCreatedAt() == null ? "-" : l.getCreatedAt().toString().replace("T", " ")));
        body.add(ScreenKit.labelValue("Admin",
                l.getActorUserId() == null ? "-" : "#" + l.getActorUserId()));
        Object[] labeled = auditActionLabel(l.getAction());
        body.add(ScreenKit.labelValueStyled("Action", (String) labeled[0], (Style) labeled[1]));
        body.add(ScreenKit.labelValue("Target Type",
                nvl(l.getTargetType()).isEmpty() ? "-" : l.getTargetType()));
        body.add(ScreenKit.labelValue("Target ID",
                l.getTargetId() == null ? "-" : "#" + l.getTargetId()));
        body.add(Line.blank());
        body.add(ScreenKit.section("Details"));
        String details = nvl(l.getDetails());
        if (details.isEmpty()) {
            body.add(Line.of(Theme.dim(), "  - no details -"));
        } else {
            for (String line : Theme.wrap(details, Math.max(20, width - 8))) {
                body.add(Line.of(Theme.text(), "  " + line));
            }
        }
        return "Complete audit event information";
    }

    private String viewAdminAuditSearch(List<Line> body) {
        body.add(Line.of(Theme.dim(), "  Match action, admin, target or details."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "Enter to filter the audit log list";
    }

    private String viewAdminResetRequests(List<Line> body) {
        int totalAll = resetList == null ? 0 : resetList.size();
        body.add(Line.of(Theme.dim(),
                "  Review and approve password reset requests."));
        body.add(Line.of(Theme.dim(),
                "  \u2191/\u2193 select   Enter details   A approve   R reject   F refresh"));
        body.add(Line.of(Theme.dim(),
                "  Only pending requests can be approved or rejected."));
        body.add(Line.blank());

        if (totalAll == 0) {
            body.add(Line.of(Theme.warn(), "  No password reset requests recorded yet."));
            return "0 request(s)";
        }

        int pageStart = resetPage * resetPageSize;
        int pageEnd = Math.min(pageStart + resetPageSize, totalAll);

        String[] headers = { "ID", "USER", "EMAIL", "REQUESTED", "STATUS" };
        String[][] rows = new String[pageEnd - pageStart][5];
        for (int i = pageStart; i < pageEnd; i++) {
            com.lifeforge.model.PasswordReset r = resetList.get(i);
            String[] labels = resetUserLabels.getOrDefault(r.getUserId(),
                    new String[] { "#" + r.getUserId(), "-" });
            String requested = r.getCreatedAt() == null
                    ? "-" : r.getCreatedAt().format(AUDIT_TIME_FMT);
            rows[i - pageStart] = new String[] {
                    "#" + r.getId(),
                    labels[0],
                    labels[1],
                    requested,
                    resetStatusBadge(r)
            };
        }
        body.addAll(ScreenKit.proTable(headers, rows, Math.max(0, sel), width - 4));
        body.add(Line.of(Theme.dim(), String.format("  Page %d/%d   \u2014   %d request(s)",
                resetPage + 1, Math.max(1, (int) Math.ceil((double) totalAll / resetPageSize)),
                totalAll)));
        return totalAll + " request(s)";
    }

    private String resetStatusBadge(com.lifeforge.model.PasswordReset r) {
        String st = r.getStatus();
        if (st == null) {
            st = r.isUsed()
                    ? com.lifeforge.service.PasswordResetService.STATUS_COMPLETED
                    : com.lifeforge.service.PasswordResetService.STATUS_PENDING;
        }
        switch (st) {
            case "APPROVED":
                return "\u2713 Approved";
            case "REJECTED":
                return "\u2715 Rejected";
            case "COMPLETED":
                return "\u2713 Completed";
            case "PENDING":
            default:
                return "\u231B Pending";
        }
    }

    private String resetStatusMessage(com.lifeforge.model.PasswordReset r) {
        String st = r.getStatus();
        if (st == null) {
            st = r.isUsed()
                    ? com.lifeforge.service.PasswordResetService.STATUS_COMPLETED
                    : com.lifeforge.service.PasswordResetService.STATUS_PENDING;
        }
        switch (st) {
            case "APPROVED":
                return "This request has been approved. The user can now create a new password.";
            case "REJECTED":
                return "This request was rejected. The user cannot reset their password.";
            case "COMPLETED":
                return "This request has already been completed. It cannot be reused.";
            case "PENDING":
            default:
                return "This request is waiting for administrator approval.";
        }
    }

    private String viewAdminResetDetail(List<Line> body) {
        com.lifeforge.model.PasswordReset r = selReset;
        if (r == null) {
            body.add(Line.of(Theme.warn(), "  No reset request selected."));
            return "";
        }
        body.add(Line.of(Theme.headingGreen(), "  [ RESET REQUEST #" + r.getId() + " ]"));
        body.add(Line.blank());
        String[] labels = resetUserLabels.getOrDefault(r.getUserId(),
                new String[] { "#" + r.getUserId(), "-" });
        body.add(ScreenKit.labelValueStyled("User", labels[0], Theme.pivot()));
        body.add(ScreenKit.labelValueStyled("Email", labels[1], Theme.pivot()));
        body.add(ScreenKit.labelValue("User ID", "#" + r.getUserId()));
        body.add(ScreenKit.labelValue("Requested",
                r.getCreatedAt() == null ? "-" : r.getCreatedAt().format(RESET_DETAIL_TIME_FMT)));
        body.add(ScreenKit.labelValue("Expires",
                r.getExpiresAt() == null ? "-" : r.getExpiresAt().format(RESET_DETAIL_TIME_FMT)));
        body.add(ScreenKit.labelValue("Attempts", String.valueOf(r.getAttemptCount())));
        body.add(ScreenKit.labelValue("Status", resetStatusBadge(r)));
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(), "  " + resetStatusMessage(r)));
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(), "  Menu:"));
        body.addAll(menuLines());
        return "Review password reset request";
    }

    private String viewAdminResetConfirm(List<Line> body) {
        com.lifeforge.model.PasswordReset r = selReset;
        if (r == null) {
            body.add(Line.of(Theme.warn(), "  No reset request selected."));
            body.addAll(menuLines());
            return "";
        }
        String verb = resetApproveReject ? "approve" : "reject";
        body.add(Line.of(Theme.warn(),
                "  " + (resetApproveReject ? "Approve" : "Reject")
                        + " password reset request #" + r.getId() + "?"));
        body.add(Line.blank());
        String[] labels = resetUserLabels.getOrDefault(r.getUserId(),
                new String[] { "#" + r.getUserId(), "-" });
        body.add(ScreenKit.labelValueStyled("User", labels[0], Theme.pivot()));
        body.add(ScreenKit.labelValue("Requested",
                r.getCreatedAt() == null ? "-" : r.getCreatedAt().format(RESET_DETAIL_TIME_FMT)));
        if (r.getUserId() != null
                && ctx.session.getCurrentUser() != null
                && r.getUserId().equals(ctx.session.getCurrentUser().getId())) {
            body.add(Line.of(Theme.err(),
                    "  You cannot " + verb + " your own password reset request."));
        }
        body.add(Line.blank());
        body.addAll(menuLines());
        return "This action cannot be undone";
    }

    private int auditDetailsWidth() {
        int inner = Math.max(40, width - 2);
        return Math.max(8, inner - 55);
    }

    private String auditHeader() {
        int detailsW = auditDetailsWidth();
        return "  "
                + Theme.padRight("TIME", AUDIT_TIME_W) + "  "
                + Theme.padRight("ADMIN", AUDIT_ADMIN_W) + "  "
                + Theme.padRight("ACTION", AUDIT_ACTION_W) + "  "
                + Theme.padRight("TARGET", AUDIT_TARGET_W) + "  "
                + Theme.padRight("DETAILS", detailsW);
    }

    private Line auditRow(AuditLog l, boolean selected) {
        int detailsW = auditDetailsWidth();
        String time = l.getCreatedAt() == null ? ""
                : l.getCreatedAt().format(AUDIT_TIME_FMT);
        String admin = l.getActorUserId() == null ? "-" : "#" + l.getActorUserId();
        Object[] labeled = auditActionLabel(l.getAction());
        String action = Theme.truncate((String) labeled[0], AUDIT_ACTION_W);
        String target = Theme.truncate(nvl(l.getTargetType()), AUDIT_TARGET_W);
        String details = Theme.truncate(nvl(l.getDetails()).replace('\n', ' '), detailsW);

        String text = "  "
                + Theme.padRight(time, AUDIT_TIME_W) + "  "
                + Theme.padRight(admin, AUDIT_ADMIN_W) + "  "
                + Theme.padRight(action, AUDIT_ACTION_W) + "  "
                + Theme.padRight(target, AUDIT_TARGET_W) + "  "
                + Theme.padRight(details, detailsW);
        if (selected) {
            return Line.of(Theme.selected(), "> " + text.substring(2));
        }
        return Line.of((Style) labeled[1], text);
    }

    private Object[] auditActionLabel(String action) {
        if (action == null) {
            return new Object[] { "-", Theme.dim() };
        }
        switch (action) {
            case "LOGIN":
                return new Object[] { "\uD83D\uDD10 LOGIN", Theme.dim() };
            case "PASSWORD_RESET":
                return new Object[] { "\uD83D\uDD11 PASSWORD_RESET", Theme.pivot() };
            case "PASSWORD_RESET_REQUESTED":
                return new Object[] { "\uD83D\uDD11 PASSWORD_RESET_REQ", Theme.pivot() };
            case "USER_BLOCKED":
            case "BLOCK_USER":
                return new Object[] { "\u26A0 USER_BLOCKED", Theme.warn() };
            case "USER_UNBLOCKED":
            case "UNBLOCK_USER":
                return new Object[] { "\u2713 USER_UNBLOCKED", Theme.ok() };
            case "USER_DELETED":
                return new Object[] { "\u2715 USER_DELETED", Theme.err() };
            case "ROLE_CHANGED":
                return new Object[] { "\u2699 ROLE_CHANGED", Theme.warn() };
            default:
                return new Object[] { action, Theme.text() };
        }
    }

    private String viewAdminSettings(List<Line> body) {
        body.add(Line.of(Theme.headingCyan(), "  [ SYSTEM ]"));
        body.add(ScreenKit.labelValue("Name", AppConfig.APP_NAME));
        body.add(ScreenKit.labelValue("Version", AppConfig.APP_VERSION));
        body.add(ScreenKit.labelValue("Tagline", AppConfig.APP_TAGLINE));
        body.add(ScreenKit.labelValue("Database reachable", dbOk ? "Yes" : "No"));
        body.add(Line.blank());
        body.add(ScreenKit.section("AI assistant"));
        body.add(ScreenKit.labelValue("AI enabled", AppConfig.isAiEnabled() ? "Yes" : "No"));
        body.add(ScreenKit.labelValue("Provider", "Ollama (local)"));
        body.add(ScreenKit.labelValue("Model", AppConfig.getOllamaModel()));
        body.add(ScreenKit.labelValue("Endpoint", AppConfig.getOllamaBaseUrl()));
        body.add(ScreenKit.labelValue("Source", AppConfig.isAiEnabled()
                ? "Ollama enabled - falls back automatically if unreachable"
                : "Rule-based explanations only (AI disabled)"));
        body.add(Line.blank());
        body.add(ScreenKit.section("Validation limits"));
        body.add(ScreenKit.labelValue("Min password length",
                String.valueOf(AppConfig.MIN_PASSWORD_LENGTH)));
        body.add(ScreenKit.labelValue("Age range",
                AppConfig.MIN_AGE + " - " + AppConfig.MAX_AGE));
        body.add(ScreenKit.labelValue("Height range",
                AppConfig.MIN_HEIGHT_CM + " - " + AppConfig.MAX_HEIGHT_CM + " cm"));
        body.add(ScreenKit.labelValue("Weight range",
                AppConfig.MIN_WEIGHT_KG + " - " + AppConfig.MAX_WEIGHT_KG + " kg"));
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(),
                "  Set LIFEFORGE_OLLAMA_URL / LIFEFORGE_OLLAMA_MODEL to customize, or"));
        body.add(Line.of(Theme.dim(),
                "  Set LIFEFORGE_OLLAMA_URL / LIFEFORGE_OLLAMA_MODEL to customize, or"));
        body.add(Line.of(Theme.dim(),
                "  LIFEFORGE_AI_ENABLED=false to force rule-based explanations only."));
        return "Configuration and environment";
    }

    // ------------------------------------------------------------------
    // Small renderers
    // ------------------------------------------------------------------
    private List<Line> menuLines() {
        return ScreenKit.menu(menuLabels, sel, width - 2);
    }

    private List<Line> formLines() {
        List<Line> out = new ArrayList<>();
        FieldKind[] kinds = fKinds();
        for (int i = 0; i < fLabels.length; i++) {
            boolean foc = i == fFocus;
            FieldKind kind = kinds[i];
            if (kind == FieldKind.BUTTON_PRIMARY || kind == FieldKind.BUTTON_SECONDARY) {
                out.add(Line.of(Theme.dim(), ""));
                out.add(renderButton(fLabels[i], kind == FieldKind.BUTTON_PRIMARY, foc));
                continue;
            }
            if (isChoice(kind)) {
                String value = fChoices[i][choice(fValues[i], 0)];
                out.add(Line.of(foc ? Theme.headingGreen() : Theme.dim(), "   " + fLabels[i]));
                String box = Theme.padRight("  < " + value + " >", width - 6);
                out.add(Line.of(foc ? Theme.pivot() : Theme.dim(), box));
                continue;
            }
            String display = kind == FieldKind.SECRET ? mask(fValues[i]) : fValues[i];
            int max = Math.max(2, width - 8);
            if (foc) {
                display = display.substring(0, Math.min(display.length(), max))
                        + (display.length() > max ? "" : "|");
            }
            display = truncate(display, max);
            out.add(Line.of(foc ? Theme.headingCyan() : Theme.dim(), "   " + fLabels[i]
                    + (foc ? " |" : "")));
            out.add(Line.of(foc ? Theme.text() : Theme.dim(),
                    "[ " + Theme.padRight(display, max) + " ]"));
        }
        return out;
    }

    /** Render a single focusable button as its own row. */
    private Line renderButton(String label, boolean primary, boolean focused) {
        Style box;
        if (focused) {
            box = primary ? Theme.accentOn() : Theme.accentPurple();
        } else {
            box = primary ? Theme.headingCyan() : Theme.headingPurple();
        }
        return Line.of(box, "  [ " + label + " ]");
    }

    private void addSection(List<Line> body, String name, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        body.add(Line.blank());
        body.add(ScreenKit.section(name));
        body.addAll(ScreenKit.paragraph(content, width - 2));
    }

    private String getRoleBadge() {
        return ctx.authController.isAdmin() ? "Administrator session" : "Member session";
    }

    private List<String[]> footer() {
        List<String[]> out = new ArrayList<>();
        switch (screen) {
            case LOGIN, REGISTER, FORGOT_PASSWORD, VERIFY_CODE, NEW_PASSWORD,
                 PROFILE_EDIT, PROFILE_PASSWORD, ADMIN_SEARCH, ADMIN_AUDIT_SEARCH,
                 ADMIN_REC_FORM, ADMIN_GOAL_FORM, ADMIN_CAT_FORM -> {
                out.add(new String[] { "Up/Down/Tab", "Move field" });
                out.add(new String[] { "Shift+Tab", "Previous field" });
                out.add(new String[] { "Left/Right", "Cursor / choice" });
                out.add(new String[] { "Enter", "Select / Submit" });
                out.add(new String[] { "Esc/Back", "Cancel" });
            }
            case CATEGORY, SUBCATEGORY, MASTER, RECOMMEND_DETAIL, SAVED_DETAIL -> {
                out.add(new String[] { "Up/Down", "Choose" });
                out.add(new String[] { "Enter", "Open / action" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case OFFICIAL_RECOMMENDATION -> {
                out.add(new String[] { "B/Esc", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case GOAL_SELECT, SAVED, PROFILE, ADMIN_USER_ACTIONS, RESET_SUCCESS,
                 RESET_STATUS, RESET_APPROVED -> {
                out.add(new String[] { "Up/Down", "Choose" });
                out.add(new String[] { "Enter", "Select" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_RECS -> {
                out.add(new String[] { "Up/Down", "Select" });
                out.add(new String[] { "Left/Right", "Page" });
                out.add(new String[] { "Enter", "View/Edit" });
                out.add(new String[] { "N", "New" });
                out.add(new String[] { "S", "Search" });
                out.add(new String[] { "D", "Delete" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_REC_DETAIL -> {
                out.add(new String[] { "Enter/E", "Edit" });
                out.add(new String[] { "D", "Delete" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_REC_DELETE_CONFIRM -> {
                out.add(new String[] { "Y", "Yes, Delete" });
                out.add(new String[] { "N", "Cancel" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_REC_SEARCH -> {
                if (recSearchResultsMode) {
                    out.add(new String[] { "Up/Down", "Navigate" });
                    out.add(new String[] { "Enter", "Select" });
                    out.add(new String[] { "S", "New Search" });
                    out.add(new String[] { "B", "Back" });
                    out.add(new String[] { "H", "Home" });
                    out.add(new String[] { "Q", "Quit" });
                } else {
                    out.add(new String[] { "Up/Down/Tab", "Move field" });
                    out.add(new String[] { "Enter", "Select / Submit" });
                    out.add(new String[] { "Esc/Back", "Cancel" });
                }
            }

            case ADMIN_GOALS, ADMIN_CATS -> {
                out.add(new String[] { "Up/Down", "Select" });
                out.add(new String[] { "Left/Right", "Page" });
                out.add(new String[] { "Enter", "Edit" });
                out.add(new String[] { "N", "New" });
                out.add(new String[] { "D", "Delete" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_USERS -> {
                out.add(new String[] { "Up/Down", "Select" });
                out.add(new String[] { "Left/Right", "Page" });
                out.add(new String[] { "Enter", "Details" });
                out.add(new String[] { "S", "Search" });
                out.add(new String[] { "X", "Clear Filter" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_RESET_REQUESTS -> {
                out.add(new String[] { "Up/Down", "Select" });
                out.add(new String[] { "Left/Right", "Page" });
                out.add(new String[] { "Enter", "Details" });
                out.add(new String[] { "A", "Approve" });
                out.add(new String[] { "R", "Reject" });
                out.add(new String[] { "F", "Refresh" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_RESET_DETAIL -> {
                out.add(new String[] { "Up/Down", "Choose action" });
                out.add(new String[] { "Enter", "Run action" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_RESET_CONFIRM -> {
                out.add(new String[] { "Up/Down", "Choose" });
                out.add(new String[] { "Enter", "Confirm / Cancel" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_ANALYTICS -> {
                out.add(new String[] { "R", "Refresh" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_AUDIT -> {
                out.add(new String[] { "↑↓", "Select" });
                out.add(new String[] { "←/→", "Page" });
                out.add(new String[] { "Enter", "Details" });
                out.add(new String[] { "S", "Search" });
                out.add(new String[] { "R", "Refresh" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_AUDIT_DETAIL -> {
                out.add(new String[] { "B/Esc", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case AI_CHAT -> {
                out.add(new String[] { "Enter", "Send" });
                out.add(new String[] { "Ctrl+C", "Clear question" });
                out.add(new String[] { "Left/Right", "Move cursor" });
                out.add(new String[] { "Esc", "Back" });
            }
            case EXPLANATION, ADMIN_SETTINGS -> {
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            default -> {
                out.add(new String[] { "Up/Down", "Navigate" });
                out.add(new String[] { "Enter", "Select" });
                out.add(new String[] { "Q", "Quit" });
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Utils
    // ------------------------------------------------------------------
    private boolean isFormScreen() {
        return switch (screen) {
            case LOGIN, REGISTER, FORGOT_PASSWORD, VERIFY_CODE, NEW_PASSWORD,
                 PROFILE_EDIT, PROFILE_PASSWORD, ADMIN_SEARCH, ADMIN_AUDIT_SEARCH,
                 ADMIN_REC_FORM, ADMIN_GOAL_FORM, ADMIN_CAT_FORM -> true;
            case ADMIN_REC_SEARCH -> !recSearchResultsMode;
            default -> false;
        };
    }

    private static boolean enter(KeyType t) {
        return t == KeyType.keyCR || t == KeyType.keyLF;
    }

    private static boolean ePressed(KeyType t, String s) {
        return (t == KeyType.KeyRunes || t == KeyType.KeySpace)
                && s != null && s.equalsIgnoreCase("e");
    }

    private static boolean esc(KeyType t) {
        return t == KeyType.keyESC;
    }

    private static boolean up(KeyType t) {
        return t == KeyType.KeyUp;
    }

    private static boolean down(KeyType t) {
        return t == KeyType.KeyDown;
    }

    private static boolean isChoice(FieldKind k) {
        return k == FieldKind.GENDER || k == FieldKind.ACTIVITY || k == FieldKind.ROLE
                || k == FieldKind.GOAL || k == FieldKind.CAT || k == FieldKind.CAT_PARENT
                || k == FieldKind.BOOL;
    }

    private static int choice(String value, int def) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Integer parseInt(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double parseDouble(String v) {
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String mask(String v) {
        return v == null ? "" : Theme.dup('*', v.length());
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String shortLimit(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n) + "...");
    }

    private static String shorten(String s, int n) {
        return shortLimit(s, n);
    }

    private static String human(Object o) {
        if (o == null) {
            return "-";
        }
        String s = o.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return sb.toString();
    }

    private static String[] genderLabels() {
        String[] out = new String[Gender.values().length];
        for (int i = 0; i < Gender.values().length; i++) {
            out[i] = human(Gender.values()[i]);
        }
        return out;
    }

    private static long[] genderIds() {
        long[] out = new long[Gender.values().length];
        for (int i = 0; i < out.length; i++) {
            out[i] = i;
        }
        return out;
    }

    private static String[] activityLabels() {
        String[] out = new String[ActivityLevel.values().length];
        for (int i = 0; i < ActivityLevel.values().length; i++) {
            out[i] = human(ActivityLevel.values()[i]);
        }
        return out;
    }

    private static long[] activityIds() {
        return activityIdsArray();
    }

    private static long[] activityIdsArray() {
        long[] out = new long[ActivityLevel.values().length];
        for (int i = 0; i < out.length; i++) {
            out[i] = i;
        }
        return out;
    }

    private static int idx(long[] ids, long target) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == target) {
                return i;
            }
        }
        return 0;
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private String errText(String prefix, String detail) {
        if (detail == null || detail.isBlank()) {
            return prefix;
        }
        return prefix + ": " + detail;
    }

    private static boolean isMasterRoutine(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("master") || lower.contains("routine");
    }

    private static boolean isWhyThisRecommendation(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("why this recommendation") || lower.contains("why this fits");
    }

    private String nameOfGoal(Long goalId) {
        if (goalId == null) {
            return "No Goal";
        }

        if (goals == null || goals.isEmpty()) {
            goals = safe(ctx.goalController.listAllGoals());
        }

        for (Goal g : safe(goals)) {
            if (goalId.equals(g.getId())) {
                return g.getName();
            }
        }

        return "Unknown Goal";
    }

    private String nameOfCat(long id) {
        for (RecommendationCategory c : safe(catList)) {
            if (c.getId() == id) {
                return c.getName();
            }
        }
        List<RecommendationCategory> all =
                safe(ctx.recommendationController.listAllCategories());
        for (RecommendationCategory c : all) {
            if (c.getId() == id) {
                return c.getName();
            }
        }
        return "#" + id;
    }

    private Recommendation findRecById(long id) {
        for (Recommendation r : safe(recomms)) {
            if (r.getId() == id) {
                return r;
            }
        }
        return ctx.recommendationController.findRecommendationById(id).orElse(null);
    }

    private int findDisplayedRecIndex(long id) {
        List<Recommendation> list = safe(displayedRecs);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }

    private Goal findGoalById(long id) {
        for (Goal g : safe(goals)) {
            if (g.getId() == id) {
                return g;
            }
        }
        return null;
    }

    private RecommendationCategory findCatById(long id) {
        for (RecommendationCategory c : safe(catList)) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }
}
