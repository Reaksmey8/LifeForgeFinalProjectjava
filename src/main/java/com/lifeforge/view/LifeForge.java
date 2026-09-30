package com.lifeforge.view;

import com.lifeforge.AppContext;
import com.lifeforge.config.AppConfig;
import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.model.*;
import com.lifeforge.service.AnalyticsService;
import com.lifeforge.service.CalorieService;
import com.lifeforge.service.RecommendationService;
import com.lifeforge.view.ScreenKit.Line;
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
import com.lifeforge.service.CustomGoalService;

import static com.lifeforge.view.Theme.truncate;

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
        USER_HOME,
        GOAL_SELECT,
        CUSTOM_GOAL_INPUT,
        CUSTOM_GOAL_ANALYSIS,
        PERSONALIZED_ANALYSIS,
        CALCULATION_TRACE,
        PERSONALIZED_PLAN,
        DAILY_BLUEPRINT,
        RECOMMEND_HUB,
        CATEGORY,
        SUBCATEGORY,
        MASTER,
        EXPLANATION,
        RECOMMEND_DETAIL,
        WHY_RECOMMENDATION,
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
        ADMIN_SETTINGS
    }

    private enum FieldKind {
        TEXT, NUMERIC, SECRET, GENDER, ACTIVITY, ROLE, GOAL, CAT, CAT_PARENT, BOOL,
        CODE6, BUTTON_PRIMARY, BUTTON_SECONDARY
    }

    private final AppContext ctx;

    // Frame sizing: TARGET_FRAME_WIDTH (80) is the target width every screen builds
    // its content against. The frame is dynamically clamped to min(80, terminalWidth).
    // `termWidth`/`termHeight` hold the raw terminal size so view() can center the
    // rendered frame horizontally and vertically.
    private static final int TARGET_FRAME_WIDTH = 80;
    private static final int MIN_FRAME_WIDTH = 40;
    private static final int ADMIN_USERS_PAGE_SIZE = 4;
    private int termWidth = 80;
    private int termHeight = 24;

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
    private RecommendationCategory currentCategory;
    private RecommendationService.RecommendationResult result;
    private PersonalizedAnalysisResult analysisResult;
    private PersonalizedPlanResult planResult;
    private CalculationTrace calculationTrace;
    private DailyBlueprint dailyBlueprint;
    private int dailyBlueprintPage = 0;
    private int adminRecDetailPage = 0;
    private boolean showMatchBreakdown;
    private CustomGoalService.CustomGoalAnalysisResult customGoalAnalysis;
    private String explanationText;
    private boolean explanationFromAi;
    private String chatDraft = "";
    private int chatCursor;
    private final List<String> chatConversation = new ArrayList<>();
    private int chatScrollOffset = 0;
    private boolean isGlobalAiAssistant = false;
    private Long lastNavCategoryId = null;
    private String lastNavCategoryName = null;
    private boolean aiThinking = false;
    private char lastChatInsertChar;
    private int lastChatInsertEnd = -1;
    private long lastChatInsertTime;
    private Recommendation savedRec;
    private Long savedRecId;
    private User selUser;
    private long editRecId = -1;
    private long editGoalId = -1;
    private long editCatId = -1;
    private int recommendDetailPage = 0;
    private int recPage;
    private final int recPageSize = 8;
    private String recSearchQueryText = "";
    private List<Recommendation> recSearchResults;
    private boolean recSearchResultsMode;
    private final Set<Long> inactiveRecIds = new HashSet<>();
    private final Set<Long> inactiveCatIds = new HashSet<>();

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
    private final Map<Long, User> auditUserCache = new HashMap<>();

    // user table pagination
    private int userPage;
    private final int userPageSize = 8;

    private static final java.time.format.DateTimeFormatter AUDIT_TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final java.time.format.DateTimeFormatter SAVED_TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final java.time.format.DateTimeFormatter MEMBER_DATE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int AUDIT_TIME_W = 11;
    private static final int AUDIT_ADMIN_W = 8;
    private static final int AUDIT_ACTION_W = 17;
    private static final int AUDIT_TARGET_W = 10;
    private AnalyticsService.AnalyticsSummary analytics;
    private String searchQuery = "";
    private String recommendationSearchQuery = "";
    private List<Recommendation> displayedRecs;
    private boolean dbOk;

    public boolean isDbOk() {
        return dbOk;
    }

    public void setDbOk(boolean dbOk) {
        this.dbOk = dbOk;
    }

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
            termWidth = Math.max(1, wsm.width());
            termHeight = Math.max(1, wsm.height());
            width = Math.min(TARGET_FRAME_WIDTH, termWidth);
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
        if (screen == Screen.ADMIN_USERS) {
            int adminWidth = Math.max(MIN_FRAME_WIDTH, Math.min(120, termWidth));
            String page = renderAdminUsersPage(adminWidth);
            return centerFrame(page, adminWidth);
        }
        if (screen == Screen.ADMIN_RECS) {
            int adminWidth = Math.min(TARGET_FRAME_WIDTH, termWidth);
            String page = renderAdminRecsPage(adminWidth);
            return centerFrame(page, adminWidth);
        }
        if (screen == Screen.ADMIN_REC_DETAIL) {
            int adminWidth = Math.min(TARGET_FRAME_WIDTH, termWidth);
            String page = renderAdminRecDetailPage(adminWidth);
            return centerFrame(page, adminWidth);
        }
        if (screen == Screen.ADMIN_GOALS) {
            int adminWidth = Math.min(TARGET_FRAME_WIDTH, termWidth);
            String page = renderAdminGoalsPage(adminWidth);
            return centerFrame(page, adminWidth);
        }
        if (screen == Screen.ADMIN_CATS) {
            int adminWidth = Math.min(TARGET_FRAME_WIDTH, termWidth);
            String page = renderAdminCatsPage(adminWidth);
            return centerFrame(page, adminWidth);
        }
        if (screen == Screen.ADMIN_ANALYTICS) {
            int adminWidth = Math.min(TARGET_FRAME_WIDTH, termWidth);
            String page = renderAdminAnalyticsPage(adminWidth);
            return centerFrame(page, adminWidth);
        }
        if (screen == Screen.ADMIN_AUDIT) {
            int adminWidth = Math.min(TARGET_FRAME_WIDTH, termWidth);
            String page = renderAdminAuditPage(adminWidth);
            return centerFrame(page, adminWidth);
        }
        if (screen == Screen.ADMIN_SETTINGS) {
            int adminWidth = Math.min(TARGET_FRAME_WIDTH, termWidth);
            String page = renderAdminSettingsPage(adminWidth);
            return centerFrame(page, adminWidth);
        }
        if (screen == Screen.FORGOT_PASSWORD) {
            int cardWidth = Math.min(TARGET_FRAME_WIDTH, termWidth);
            String page = renderForgotPasswordPage(cardWidth);
            return centerFrame(page, cardWidth);
        }
        if (screen == Screen.VERIFY_CODE) {
            int cardWidth = Math.min(TARGET_FRAME_WIDTH, termWidth);
            String page = renderVerifyResetPage(cardWidth);
            return centerFrame(page, cardWidth);
        }

        int frameWidth = (screen == Screen.RECOMMEND_DETAIL)
                ? Math.max(MIN_FRAME_WIDTH, Math.min(96, termWidth))
                : Math.min(TARGET_FRAME_WIDTH, termWidth);
        this.width = frameWidth;

        List<Line> body = new ArrayList<>();
        String subtitle = "";
        switch (screen) {
            case WELCOME -> subtitle = viewWelcome(body);
            case LOGIN -> subtitle = viewLogin(body);
            case REGISTER -> subtitle = viewRegister(body);
            case FORGOT_PASSWORD -> subtitle = "";
            case VERIFY_CODE -> subtitle = "";
            case USER_HOME -> subtitle = viewUserHome(body);
            case GOAL_SELECT -> subtitle = viewGoalSelect(body);
            case CUSTOM_GOAL_INPUT -> subtitle = viewCustomGoalInput(body);
            case CUSTOM_GOAL_ANALYSIS -> subtitle = viewCustomGoalAnalysis(body);
            case PERSONALIZED_ANALYSIS -> subtitle = viewPersonalizedAnalysis(body);
            case CALCULATION_TRACE -> subtitle = viewCalculationTrace(body);
            case PERSONALIZED_PLAN -> subtitle = viewPersonalizedPlan(body);
            case DAILY_BLUEPRINT -> subtitle = viewDailyBlueprint(body);
            case RECOMMEND_HUB -> subtitle = viewRecommendHub(body);
            case CATEGORY, SUBCATEGORY, MASTER -> subtitle = viewCategory(body);
            case EXPLANATION -> subtitle = viewExplanation(body);
            case RECOMMEND_DETAIL -> subtitle = viewRecommendDetail(body);
            case WHY_RECOMMENDATION -> subtitle = viewWhyRecommendation(body);
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
            case ADMIN_SETTINGS -> subtitle = viewAdminSettings(body);
        }
        String page = ScreenKit.page(title(), subtitle, body, status, statusErr, footer(), frameWidth);
        return centerFrame(page, frameWidth);
    }

    /**
     * Centers the fully-rendered frame without modifying its contents.
     * ScreenKit already owns the frame width and guarantees that every row
     * contains its right border. Do not truncate the ANSI-rendered result here:
     * doing so can treat ANSI escape sequences as printable text and replace
     * the right side of the frame with an ellipsis.
     */
    private String centerFrame(String page, int frameWidth) {
        boolean clearScreen = false;
        if (page.startsWith("\u001B[H\u001B[2J")) {
            clearScreen = true;
            page = page.substring("\u001B[H\u001B[2J".length());
            if (page.startsWith("\n")) {
                page = page.substring(1);
            }
        }
        String[] lines = page.split("\n", -1);

        int leftPad = Math.max(0, (termWidth - frameWidth) / 2);
        if (leftPad > 0) {
            String pad = " ".repeat(leftPad);
            for (int i = 0; i < lines.length; i++) {
                lines[i] = pad + lines[i];
            }
        }

        int topPad = Math.max(0, (termHeight - lines.length) / 2);

        StringBuilder out = new StringBuilder();
        if (clearScreen) {
            out.append("\u001B[H\u001B[2J");
        }
        for (int i = 0; i < topPad; i++) {
            out.append('\n');
        }
        for (int i = 0; i < lines.length; i++) {
            out.append(lines[i]);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
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
            case FORGOT_PASSWORD -> "Account Recovery";
            case VERIFY_CODE -> "Password Reset";
            case USER_HOME -> "Dashboard";
            case GOAL_SELECT -> "Choose Your Goal";
            case CUSTOM_GOAL_INPUT -> "Custom Goal Definition";
            case CUSTOM_GOAL_ANALYSIS -> "Custom Goal Analysis";
            case PERSONALIZED_ANALYSIS -> "PERSONALIZED ANALYSIS";
            case CALCULATION_TRACE -> "CALCULATION TRACE";
            case PERSONALIZED_PLAN -> "PERSONALIZED PLAN";
            case DAILY_BLUEPRINT -> "DAILY BLUEPRINT";
            case RECOMMEND_HUB -> "Recommendation Hub";
            case CATEGORY, SUBCATEGORY, MASTER ->
                    currentCategory == null ? "Category" : currentCategory.getName();
            case EXPLANATION -> "Full Explanation";
            case RECOMMEND_DETAIL -> "RECOMMENDATION DETAIL";
            case WHY_RECOMMENDATION -> "WHY THIS RECOMMENDATION?";
            case OFFICIAL_RECOMMENDATION -> "Official Recommendation";
            case AI_CHAT -> isGlobalAiAssistant ? "AI Assistant" : "Chat with AI";
            case SAVED -> "My Saved Recommendations";
            case SAVED_DETAIL -> "Saved Recommendation";
            case PROFILE -> "My Profile";
            case PROFILE_EDIT -> "Edit Profile";
            case PROFILE_PASSWORD -> "Change Password";
            case ADMIN_HOME -> "Admin Dashboard";
            case ADMIN_USERS -> "ADMIN - USER MANAGEMENT";
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
            case ADMIN_SETTINGS -> "System Settings";
        };
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------
    /** Navigate pushing the current screen. pendingStatus survives to the next refresh. */
    private void goTo(Screen target) {
        status = "";
        Screen prev = screen;
        back.push(screen);
        screen = target;
        if (target == Screen.DAILY_BLUEPRINT) {
            dailyBlueprintPage = 0;
        }
        if (target == Screen.ADMIN_REC_DETAIL) {
            adminRecDetailPage = 0;
        }
        if (target == Screen.ADMIN_RECS && !isRecScreen(prev)) {
            resetAdminRecSearch();
        }
        refresh();
    }

    private void goClean(Screen target) {
        status = "";
        back.clear();
        screen = target;
        if (target == Screen.DAILY_BLUEPRINT) {
            dailyBlueprintPage = 0;
        }
        if (target == Screen.ADMIN_REC_DETAIL) {
            adminRecDetailPage = 0;
        }
        if (target == Screen.ADMIN_RECS) {
            resetAdminRecSearch();
        }
        refresh();
    }

    private void goBack() {
        status = "";
        if (screen == Screen.AI_CHAT) {
            ctx.recommendationController.resetAiConversation();
        }
        if (screen == Screen.ADMIN_RECS) {
            resetAdminRecSearch();
        }
        if (!back.isEmpty()) {
            Screen popped = back.pop();
            if (popped == Screen.ADMIN_RECS && !isRecScreen(screen)) {
                resetAdminRecSearch();
            }
            screen = popped;
        }
        refresh();
    }

    private void home() {
        resetAdminRecSearch();
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
        recommendDetailPage = 0;
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
            case ADMIN_SETTINGS -> refreshSettings();
            case LOGIN -> buildForm(new String[] { "Email or Username", "Password", "Forgot Password", "Login", "Back" },
                    new FieldKind[] { FieldKind.TEXT, FieldKind.SECRET, FieldKind.BUTTON_SECONDARY,
                            FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY },
                    new String[][] { null, null, null, null, null },
                    new long[][] { null, null, null, null, null },
                    new String[] { "", "", "", "", "" });
            case REGISTER -> buildForm(
                    new String[] { "Username", "Email", "Password", "Confirm Password", "Age",
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
                    new String[] { "Username or Email" },
                    new FieldKind[] { FieldKind.TEXT },
                    new String[][] { null },
                    new long[][] { null },
                    new String[] { "" });
            case VERIFY_CODE -> buildForm(
                    new String[] { "Verification Code", "New Password", "Confirm Password" },
                    new FieldKind[] { FieldKind.CODE6, FieldKind.SECRET, FieldKind.SECRET },
                    new String[][] { null, null, null },
                    new long[][] { null, null, null },
                    new String[] { "", "", "" });
            case ADMIN_GOAL_FORM -> buildAdminGoalForm();
            case ADMIN_CAT_FORM -> buildAdminCatForm();
            case CUSTOM_GOAL_INPUT -> buildCustomGoalInputForm();
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
//                    menuActions.add(this::prepareWhyThisFits);
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
                ? "AI explanation generated successfully."
                : "Standard explanation generated successfully.";
        statusErr = false;
        goTo(Screen.WHY_RECOMMENDATION);
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

    private void openGlobalAiAssistant() {
        isGlobalAiAssistant = true;
        chatDraft = "";
        chatCursor = 0;
        chatConversation.clear();
        ctx.recommendationController.resetAiConversation();
        resetChatInsertTracking();
        goTo(Screen.AI_CHAT);
    }

    private void openAiChat() {
        if (result == null || result.recommendation == null) {
            status = "Generate a LifeForge recommendation before opening AI assistance.";
            statusErr = true;
            return;
        }
        isGlobalAiAssistant = false;
        if (currentCategory != null) {
            lastNavCategoryId = currentCategory.getId();
            lastNavCategoryName = currentCategory.getName();
        }
        chatDraft = "";
        chatCursor = 0;
        chatConversation.clear();
        ctx.recommendationController.resetAiConversation();
        resetChatInsertTracking();
        goTo(Screen.AI_CHAT);
    }

    private void handleAiChatKey(KeyType t, String typed) {
        if (esc(t)) {
            chatScrollOffset = 0;
            ctx.recommendationController.resetAiConversation();
            goBack();
            return;
        }
        if (enter(t)) {
            chatScrollOffset = 0;
            sendAiChat();
            return;
        }
        if (t == KeyType.keyETX) {
            chatDraft = ""; chatCursor = 0; status = "Question cleared."; statusErr = false;
            resetChatInsertTracking(); return;
        }

        // Viewport scrolling: PageUp/PageDown or Up/Down arrows
        if (t == KeyType.KeyPgUp) {
            chatScrollOffset += 3;
            return;
        }
        if (t == KeyType.KeyPgDown) {
            chatScrollOffset = Math.max(0, chatScrollOffset - 3);
            return;
        }
        if (up(t)) {
            chatScrollOffset++;
            return;
        }
        if (down(t)) {
            chatScrollOffset = Math.max(0, chatScrollOffset - 1);
            return;
        }

        // When the input buffer is EMPTY, support single-digit shortcuts 1-5:
        if (chatDraft.isEmpty() && !aiThinking) {
            if (t == KeyType.KeyRunes && typed != null && typed.length() == 1) {
                char c = typed.charAt(0);
                boolean hasGoal = ctx.goalController.getCurrentGoal().isPresent();
                if (isGlobalAiAssistant) {
                    // Mode 1: Dashboard → 🤖 AI Assistant (Free / General)
                    switch (c) {
                        case '1' -> { executeAiQuestion("What should I focus on?"); return; }
                        case '2' -> { executeAiQuestion("What should I eat?"); return; }
                        case '3' -> { executeAiQuestion("What exercise should I do?"); return; }
                        case '4' -> { executeAiQuestion("How can I improve my sleep?"); return; }
                        case '5' -> { executeAiQuestion("How much water should I drink?"); return; }
                    }
                } else {
                    // Mode 2: Recommendation Detail → 🤖 Chat with AI (Goal-Specific)
                    String goalName = hasGoal ? ctx.goalController.getCurrentGoal().get().getName() : "my goal";
                    switch (c) {
                        case '1' -> { executeAiQuestion("How do I apply this to my daily routine?"); return; }
                        case '2' -> { executeAiQuestion("What daily habits best support my " + goalName + " goal?"); return; }
                        case '3' -> { executeAiQuestion("How does this recommendation support my " + goalName + " goal?"); return; }
                        case '4' -> { executeAiQuestion("What foods or nutrients best support my " + goalName + " goal?"); return; }
                        case '5' -> { executeAiQuestion("How much water should I drink for my " + goalName + " goal?"); return; }
                    }
                }
            }
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
            resetChatInsertTracking();
            insertChatText(typed);
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
        if (aiThinking) {
            return;
        }
        if (chatDraft.isBlank()) {
            status = "Enter a question or press 1-5 for quick prompts."; statusErr = true; return;
        }
        String question = chatDraft.trim();
        executeAiQuestion(question);
    }

    private void executeAiQuestion(String question) {
        aiThinking = true;
        chatDraft = "";
        chatCursor = 0;
        chatScrollOffset = 0;
        resetChatInsertTracking();

        Optional<AiChatResponse> response;
        if (isGlobalAiAssistant || result == null || result.recommendation == null) {
            response = ctx.recommendationController.chatWithGlobalAssistant(chatConversation, question);
        } else {
            response = ctx.recommendationController.chatWithAi(
                    currentCategory, result.recommendation, chatConversation, question);
        }

        aiThinking = false;
        if (response.isEmpty()) {
            status = errText("AI assistance is unavailable", ctx.recommendationController.getLastError());
            statusErr = true;
            return;
        }

        AiChatResponse resp = response.get();
        chatConversation.add("USER: " + question);
        chatConversation.add("AI: " + resp.text);

        if (resp.suggestedCategoryId != null) {
            lastNavCategoryId = resp.suggestedCategoryId;
            lastNavCategoryName = resp.suggestedCategoryName;
        }

        // Limit conversation history in TUI to latest 20 exchanges (40 items) so user can scroll back
        while (chatConversation.size() > 40) {
            chatConversation.remove(0);
        }

        status = resp.fromAi
                ? "AI response received. Official Rule Engine recommendations remain unchanged."
                : "[AI Offline: Rule-based engine active. Check if Ollama is running on port 11434.]";
        statusErr = false;
    }

    private void openRecommendationCategory(Long categoryId) {
        if (categoryId == null) return;
        List<RecommendationCategory> cats = ctx.recommendationController.listAllCategories();
        RecommendationCategory matched = null;
        if (cats != null) {
            for (RecommendationCategory c : cats) {
                if (c.getId().equals(categoryId)) {
                    matched = c;
                    break;
                }
            }
        }
        if (matched != null) {
            generate(matched);
        } else {
            if (planResult != null) {
                for (PersonalizedPlanResult.AreaItem a : planResult.getAreas()) {
                    if (a.category() != null && a.category().getId().equals(categoryId)) {
                        generate(a.category());
                        return;
                    }
                }
            }
            goTo(Screen.PERSONALIZED_PLAN);
        }
    }







    // ------------------------------------------------------------------
    // Key handling
    // ------------------------------------------------------------------
    private void handleKey(KeyPressMessage msg) {
        KeyType t = msg.type();
        char[] runes = msg.runes();
        String s = runes == null ? "" : new String(runes);

        if (screen == Screen.WELCOME) {
            if (t == KeyType.KeyLeft || up(t)) {
                sel = Math.max(0, sel - 1);
                return;
            }
            if (t == KeyType.KeyRight || down(t)) {
                sel = Math.min(2, sel + 1);
                return;
            }
            if (enter(t)) {
                switch (sel) {
                    case 0 -> goTo(Screen.LOGIN);
                    case 1 -> goTo(Screen.REGISTER);
                    case 2 -> quitApp();
                }
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 'l' -> goTo(Screen.LOGIN);
                    case 'r' -> goTo(Screen.REGISTER);
                    case 'q' -> quitApp();
                    default -> {}
                }
            }
            return;
        }

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

            if (esc(t)) {
                if (recommendationSearchQuery != null && !recommendationSearchQuery.isEmpty()) {
                    resetAdminRecSearch();
                    status = "Search filter cleared.";
                    statusErr = false;
                    return;
                }
                resetAdminRecSearch();
                goBack();
                return;
            }

            // LETTER SHORTCUTS
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {

                char c = s.isEmpty()
                        ? ' '
                        : Character.toLowerCase(s.charAt(0));

                switch (c) {
                    case 'b' -> {
                        resetAdminRecSearch();
                        goBack();
                    }
                    case 'h' -> {
                        resetAdminRecSearch();
                        home();
                    }
                    case 'q' -> quitApp();
                    case 'n' -> menuNew();
                    case 'd' -> menuDelete();
                    case 's' -> menuSearch();
                    case 'c', 'x' -> {
                        if (recommendationSearchQuery != null && !recommendationSearchQuery.isEmpty()) {
                            resetAdminRecSearch();
                            status = "Search filter cleared.";
                            statusErr = false;
                        }
                    }
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
            if (esc(t)) {
                goTo(Screen.ADMIN_RECS);
                return;
            }
            if (t == KeyType.KeyLeft || t == KeyType.KeyUp) {
                if (adminRecDetailPage > 0) {
                    adminRecDetailPage--;
                }
                return;
            }
            if (t == KeyType.KeyRight || t == KeyType.KeyDown) {
                if (adminRecDetailPage < 1) {
                    adminRecDetailPage++;
                }
                return;
            }
            if (t == KeyType.keyHT || t == KeyType.KeyShiftTab) {
                adminRecDetailPage = (adminRecDetailPage + 1) % 2;
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case '1' -> {
                        adminRecDetailPage = 0;
                        return;
                    }
                    case '2' -> {
                        adminRecDetailPage = 1;
                        return;
                    }
                    case 'n', 'p' -> {
                        adminRecDetailPage = (adminRecDetailPage + 1) % 2;
                        return;
                    }
                    case 'e' -> {
                        if (editRecId >= 0) {
                            goTo(Screen.ADMIN_REC_FORM);
                        }
                        return;
                    }
                    case 't' -> {
                        if (editRecId >= 0) {
                            if (inactiveRecIds.contains(editRecId)) {
                                inactiveRecIds.remove(editRecId);
                                status = "Recommendation #" + editRecId + " set to ACTIVE";
                                statusErr = false;
                            } else {
                                inactiveRecIds.add(editRecId);
                                status = "Recommendation #" + editRecId + " set to INACTIVE";
                                statusErr = false;
                            }
                        }
                        return;
                    }
                    case 'd' -> {
                        if (editRecId >= 0) {
                            goTo(Screen.ADMIN_REC_DELETE_CONFIRM);
                        }
                        return;
                    }
                    case 'b' -> {
                        goTo(Screen.ADMIN_RECS);
                        return;
                    }
                    case 'h' -> { home(); return; }
                    case 'q' -> { quitApp(); return; }
                    default -> { /* ignore */ }
                }
            }
            if (enter(t)) {
                if (editRecId >= 0) {
                    goTo(Screen.ADMIN_REC_FORM);
                }
                return;
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
            int pageSize = ADMIN_USERS_PAGE_SIZE;
            int total = users == null ? 0 : users.size();
            int pageStart = userPage * pageSize;
            int pageCount = Math.min(pageSize, Math.max(0, total - pageStart));
            int maxSel = Math.max(0, pageCount - 1);
            if (sel > maxSel) {
                sel = maxSel;
            }
            int pages = Math.max(1, (int) Math.ceil((double) total / pageSize));

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
                    case 'u' -> {
                        if (pageCount > 0) {
                            sel = Math.max(0, sel - 1);
                        }
                    }
                    case 'd' -> {
                        if (pageCount > 0) {
                            sel = Math.min(pageCount - 1, sel + 1);
                        }
                    }
                    case 'l' -> {
                        if (userPage > 0) {
                            userPage--;
                            sel = 0;
                        }
                    }
                    case 'r' -> {
                        if (userPage < pages - 1) {
                            userPage++;
                            sel = 0;
                        }
                    }
                    case 'e', 'v' -> {
                        if (total > 0 && sel >= 0 && sel < pageCount) {
                            selUser = users.get(pageStart + sel);
                            goTo(Screen.ADMIN_USER_ACTIONS);
                        }
                    }
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
            if (esc(t)) {
                goBack();
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 't' -> toggleSelectedGoal();
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
            if (esc(t)) {
                goBack();
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 't' -> toggleSelectedCat();
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
                if (logs != null && !logs.isEmpty()) {
                    int globalIdx = auditPage * auditPageSize + sel;
                    if (globalIdx >= 0 && globalIdx < logs.size()) {
                        selAudit = logs.get(globalIdx);
                        goTo(Screen.ADMIN_AUDIT_DETAIL);
                    }
                }
                return;
            }

            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 's' -> goTo(Screen.ADMIN_AUDIT_SEARCH);
                    case 'c' -> {
                        if (auditSearchQuery != null && !auditSearchQuery.isEmpty()) {
                            auditSearchQuery = "";
                            refreshLogs();
                        }
                    }
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
        // ADMIN SETTINGS
        // ==============================
        if (screen == Screen.ADMIN_SETTINGS) {
            if (esc(t)) {
                goBack();
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 'r' -> {
                        refreshSettings();
                        if (dbOk) {
                            status = "Connections tested: Database reachable, AI configuration active.";
                            statusErr = false;
                        } else {
                            status = "Connections tested: Database unreachable.";
                            statusErr = true;
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
        // PERSONALIZED ANALYSIS
        // ==============================
        if (screen == Screen.PERSONALIZED_ANALYSIS) {
            if (enter(t)) {
                goTo(Screen.PERSONALIZED_PLAN);
                return;
            }
            if (esc(t)) {
                goBack();
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 't' -> goTo(Screen.CALCULATION_TRACE);
                    case 'm' -> showMatchBreakdown = !showMatchBreakdown;
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // PERSONALIZED PLAN
        // ==============================
        if (screen == Screen.PERSONALIZED_PLAN) {
            if (up(t)) {
                sel = sel <= 0 ? 0 : sel - 1;
                return;
            }
            if (down(t)) {
                sel = menuActions.isEmpty() ? 0 : Math.min(menuActions.size() - 1, sel + 1);
                return;
            }
            if (t == KeyType.KeyHome) {
                sel = 0;
                return;
            }
            if (t == KeyType.KeyEnd) {
                sel = menuActions.isEmpty() ? 0 : menuActions.size() - 1;
                return;
            }
            if (enter(t)) {
                if (!menuActions.isEmpty() && sel >= 0 && sel < menuActions.size()) {
                    menuActions.get(sel).run();
                }
                return;
            }
            if (esc(t)) {
                goBack();
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 'd' -> goTo(Screen.DAILY_BLUEPRINT);
                    case 't' -> goTo(Screen.CALCULATION_TRACE);
                    case 'm' -> showMatchBreakdown = !showMatchBreakdown;
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // READ-ONLY / INFO SCREENS
        // ==============================
        if (screen == Screen.DAILY_BLUEPRINT) {
            if (esc(t)) {
                goBack();
                return;
            }
            if (t == KeyType.KeyLeft || t == KeyType.KeyUp) {
                if (dailyBlueprintPage > 0) {
                    dailyBlueprintPage--;
                }
                return;
            }
            if (t == KeyType.KeyRight || t == KeyType.KeyDown) {
                if (dailyBlueprintPage < 2) {
                    dailyBlueprintPage++;
                }
                return;
            }
            if (t == KeyType.keyHT) {
                dailyBlueprintPage = (dailyBlueprintPage + 1) % 3;
                return;
            }
            if (t == KeyType.KeyShiftTab) {
                dailyBlueprintPage = (dailyBlueprintPage + 2) % 3;
                return;
            }
            if (enter(t)) {
                goBack();
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case '1', 'm' -> dailyBlueprintPage = 0;
                    case '2', 'd' -> dailyBlueprintPage = 1;
                    case '3', 'e' -> dailyBlueprintPage = 2;
                    case 'n' -> dailyBlueprintPage = (dailyBlueprintPage + 1) % 3;
                    case 'p' -> dailyBlueprintPage = (dailyBlueprintPage + 2) % 3;
                    case 'b' -> goBack();
                    case 'h' -> home();
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        if (screen == Screen.CALCULATION_TRACE || screen == Screen.WHY_RECOMMENDATION) {
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
        // PROFILE SCREEN
        // ==============================
        if (screen == Screen.PROFILE) {
            if (esc(t)) {
                if (armed) {
                    armed = false;
                    status = null;
                    statusErr = false;
                    return;
                }
                goBack();
                return;
            }
            if (enter(t)) {
                if (armed) {
                    deleteAccount();
                }
                return;
            }
            if (t == KeyType.KeyRunes || t == KeyType.KeySpace) {
                char c = s.isEmpty() ? ' ' : Character.toLowerCase(s.charAt(0));
                switch (c) {
                    case 'e' -> {
                        armed = false;
                        status = null;
                        statusErr = false;
                        goTo(Screen.PROFILE_EDIT);
                    }
                    case 'p' -> {
                        armed = false;
                        status = null;
                        statusErr = false;
                        goTo(Screen.PROFILE_PASSWORD);
                    }
                    case 'd' -> {
                        if (armed) {
                            deleteAccount();
                        } else {
                            armDelete();
                        }
                    }
                    case 'b' -> {
                        if (armed) {
                            armed = false;
                            status = null;
                            statusErr = false;
                            return;
                        }
                        goBack();
                    }
                    case 'h' -> {
                        armed = false;
                        status = null;
                        statusErr = false;
                        home();
                    }
                    case 'q' -> quitApp();
                    default -> { /* ignore */ }
                }
            }
            return;
        }

        // ==============================
        // RECOMMENDATION DETAIL (PAGINATED ONLY FOR MASTER ROUTINE)
        // ==============================
        if (screen == Screen.RECOMMEND_DETAIL) {
            if (isCurrentMasterRoutine()) {
                if (t == KeyType.KeyLeft) {
                    if (recommendDetailPage > 0) {
                        recommendDetailPage--;
                    }
                    return;
                }
                if (t == KeyType.KeyRight) {
                    if (recommendDetailPage < 1) {
                        recommendDetailPage++;
                    }
                    return;
                }
            } else {
                if (t == KeyType.KeyLeft) {
                    sel = sel <= 0 ? 0 : sel - 1;
                    return;
                }
                if (t == KeyType.KeyRight) {
                    sel = menuActions.isEmpty() ? 0 : Math.min(menuActions.size() - 1, sel + 1);
                    return;
                }
            }
            if (up(t)) {
                sel = sel <= 0 ? 0 : sel - 1;
                return;
            }
            if (down(t)) {
                sel = menuActions.isEmpty() ? 0 : Math.min(menuActions.size() - 1, sel + 1);
                return;
            }
            if (t == KeyType.KeyHome) {
                sel = 0;
                return;
            }
            if (t == KeyType.KeyEnd) {
                sel = menuActions.isEmpty() ? 0 : menuActions.size() - 1;
                return;
            }
            if (enter(t)) {
                if (!menuActions.isEmpty() && sel >= 0 && sel < menuActions.size()) {
                    menuActions.get(sel).run();
                }
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
                case 'e' -> {
                    if (screen == Screen.SAVED) {
                        exportSavedRecommendationsPdf();
                    }
                }
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
                    goTo(Screen.FORGOT_PASSWORD);
                } else {
                    goBack();
                }
            }
            case REGISTER -> goBack();
            case PROFILE_EDIT, PROFILE_PASSWORD -> goBack();
            case FORGOT_PASSWORD, VERIFY_CODE -> {
                resetUserId = null;
                goTo(Screen.LOGIN);
            }
            case ADMIN_REC_FORM -> goTo(Screen.ADMIN_RECS);
            case ADMIN_GOAL_FORM -> goTo(Screen.ADMIN_GOALS);
            case ADMIN_CAT_FORM -> goTo(Screen.ADMIN_CATS);
            case CUSTOM_GOAL_INPUT -> goTo(Screen.GOAL_SELECT);
            case ADMIN_SEARCH -> goTo(Screen.ADMIN_USERS);
            case ADMIN_AUDIT_SEARCH -> goTo(Screen.ADMIN_AUDIT);
            case ADMIN_REC_SEARCH -> {
                if (fFocus == 2) {
                    recSearchQueryText = "";
                    fValues[0] = "";
                    resetAdminRecSearch();
                    status = "Search query cleared. Type a new query below.";
                    statusErr = false;
                } else {
                    resetAdminRecSearch();
                    goTo(Screen.ADMIN_RECS);
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
            if (t != KeyType.KeyShiftTab && onButton && fFocus > 0 && isButton(fKinds()[fFocus - 1])) {
                // Focused on the right button of a side-by-side pair; Up moves to the field above the pair
                fFocus = Math.max(0, fFocus - 2);
                return;
            }
            fFocus = Math.max(0, fFocus - 1);
            return;
        }
        if (down(t) || t == KeyType.keyHT) {
            fFocus = Math.min(fLabels.length - 1, fFocus + 1);
            return;
        }
        if (enter(t)) {
            if (screen == Screen.FORGOT_PASSWORD || (screen == Screen.CUSTOM_GOAL_INPUT && !onButton)) {
                submitForm();
                return;
            }
            if (screen == Screen.VERIFY_CODE) {
                if (fFocus < fLabels.length - 1) {
                    fFocus++;
                } else {
                    submitForm();
                }
                return;
            }
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
            if (t == KeyType.KeyLeft) {
                if (fFocus > 0 && isButton(fKinds()[fFocus - 1])) {
                    fFocus--;
                    return;
                }
            }
            if (t == KeyType.KeyRight) {
                if (fFocus < fLabels.length - 1 && isButton(fKinds()[fFocus + 1])) {
                    fFocus++;
                    return;
                }
            }
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
            if (screen == Screen.VERIFY_CODE) {
                if (text.equalsIgnoreCase("r") && fFocus == 0) {
                    resendCode();
                    return;
                }
                if (text.equalsIgnoreCase("b") && fFocus == 0 && fValues[0].isEmpty()) {
                    onFormCancel();
                    return;
                }
            }
            if (screen == Screen.FORGOT_PASSWORD) {
                if (text.equalsIgnoreCase("b") && fValues[0].isEmpty()) {
                    onFormCancel();
                    return;
                }
            }
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
            case FORGOT_PASSWORD -> setKinds(kinds, FieldKind.TEXT);
            case VERIFY_CODE -> setKinds(kinds, FieldKind.CODE6, FieldKind.SECRET, FieldKind.SECRET);
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
            case CUSTOM_GOAL_INPUT -> setKinds(kinds, FieldKind.TEXT,
                    FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY);
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

    private void openCustomGoalInput() {
        goTo(Screen.CUSTOM_GOAL_INPUT);
    }

    private void buildCustomGoalInputForm() {
        String existing = "";
        if (customGoalAnalysis != null && customGoalAnalysis.getRawGoalText() != null) {
            existing = customGoalAnalysis.getRawGoalText();
        } else if (ctx.session.getCustomGoal() != null) {
            existing = ctx.session.getCustomGoal().getName().replace(" (Custom)", "");
        }
        buildForm(
                new String[] { "Your Goal (e.g. 5K Marathon, Muscle Bulk, Fix Posture, Better Sleep)", "Analyze Goal", "Cancel" },
                new FieldKind[] { FieldKind.TEXT, FieldKind.BUTTON_PRIMARY, FieldKind.BUTTON_SECONDARY },
                new String[][] { null, null, null },
                new long[][] { null, null, null },
                new String[] { existing, "", "" }
        );
    }

    // ------------------------------------------------------------------
    // Form submit
    // ------------------------------------------------------------------
    private void submitForm() {
        switch (screen) {
            case LOGIN -> submitLogin();
            case REGISTER -> submitRegister();
            case FORGOT_PASSWORD -> submitForgotPassword();
            case VERIFY_CODE -> submitVerifyAndReset();
            case PROFILE_EDIT -> submitProfileEdit();
            case PROFILE_PASSWORD -> submitProfilePassword();
            case ADMIN_SEARCH -> submitSearch();
            case ADMIN_AUDIT_SEARCH -> submitAuditSearch();
            case ADMIN_REC_SEARCH -> submitRecommendationSearch();
            case ADMIN_REC_FORM -> submitRecForm();
            case ADMIN_GOAL_FORM -> submitGoalForm();
            case ADMIN_CAT_FORM -> submitCatForm();
            case CUSTOM_GOAL_INPUT -> submitCustomGoalInput();
            default -> { /* nothing */ }
        }
    }

    private void submitRecommendationSearch() {
        String query = fValues == null || fValues.length == 0 || fValues[0] == null
                ? "" : fValues[0].trim();
        if (query.isEmpty()) {
            resetAdminRecSearch();
            status = "Search filter cleared.";
            statusErr = false;
            goTo(Screen.ADMIN_RECS);
            return;
        }
        recommendationSearchQuery = query;
        recSearchQueryText = query;
        recPage = 0;
        sel = 0;
        refreshAdminRecs();
        status = "";
        statusErr = false;
        goTo(Screen.ADMIN_RECS);
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
        chatConversation.clear();
        ctx.recommendationController.resetAiConversation();
        lastNavCategoryId = null;
        lastNavCategoryName = null;
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
            chatConversation.clear();
            ctx.recommendationController.resetAiConversation();
            lastNavCategoryId = null;
            lastNavCategoryName = null;
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
        String input = (fValues != null && fValues.length > 0 && fValues[0] != null) ? fValues[0].trim() : "";
        if (input.isEmpty()) {
            status = "Please enter your username or registered email.";
            statusErr = true;
            return;
        }
        var result = ctx.passwordResetController.startReset(input);
        if (!result.accepted) {
            status = result.message;
            statusErr = true;
            return;
        }
        resetUserId = result.userId;
        status = result.message;
        statusErr = false;
        goTo(Screen.VERIFY_CODE);
    }

    private void submitVerifyAndReset() {
        if (resetUserId == null) {
            status = "Your reset session has expired. Please request a new code.";
            statusErr = true;
            return;
        }
        String code = (fValues != null && fValues.length > 0 && fValues[0] != null) ? fValues[0].trim() : "";
        String newPass = (fValues != null && fValues.length > 1 && fValues[1] != null) ? fValues[1] : "";
        String confirmPass = (fValues != null && fValues.length > 2 && fValues[2] != null) ? fValues[2] : "";

        if (code.length() != 6 || !code.chars().allMatch(Character::isDigit)) {
            status = "The verification code must be exactly 6 digits.";
            statusErr = true;
            return;
        }
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
        boolean ok = ctx.passwordResetController.resetPassword(resetUserId, code, newPass, confirmPass);
        if (ok) {
            resetUserId = null;
            pendingStatus = "Password reset successfully! Login with your new password.";
            status = "";
            statusErr = false;
            goTo(Screen.LOGIN);
        } else {
            status = errText("Reset failed", ctx.passwordResetController.getLastError());
            statusErr = true;
        }
    }

    private void resendCode() {
        if (resetUserId == null) {
            status = "No active reset session. Please request a new code.";
            statusErr = true;
            return;
        }
        var result = ctx.passwordResetController.resendCode(resetUserId);
        if (result.accepted) {
            status = "New verification code sent (valid for 5 minutes).";
            statusErr = false;
            if (fValues != null && fValues.length > 0) {
                fValues[0] = "";
                fCursor[0] = 0;
            }
        } else {
            status = result.message;
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
            analysisResult = null;
            planResult = null;
            calculationTrace = null;
            dailyBlueprint = null;
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

    private void submitCustomGoalInput() {
        String rawInput = (fValues != null && fValues.length > 0 && fValues[0] != null) ? fValues[0].trim() : "";
        if (rawInput.isEmpty() && fValues != null && fValues.length > 1 && fValues[1] != null && !fValues[1].trim().isEmpty()) {
            rawInput = fValues[1].trim();
        }
        if (rawInput.isEmpty()) {
            status = "Please enter a description for your custom goal.";
            statusErr = true;
            return;
        }
        User user = ctx.session.getCurrentUser();
        List<Goal> activeGoals = ctx.goalController.listActiveGoals();
        customGoalAnalysis = ctx.customGoalService.analyze(rawInput, user, activeGoals);
        status = "";
        statusErr = false;
        goTo(Screen.CUSTOM_GOAL_ANALYSIS);
    }

    private void confirmCustomGoal() {
        if (customGoalAnalysis == null) {
            status = "No custom goal analysis available.";
            statusErr = true;
            return;
        }
        Goal baseline = customGoalAnalysis.getBaselineGoal();
        Goal synthesized = customGoalAnalysis.getSynthesizedGoal();
        if (ctx.goalController.selectCustomGoal(synthesized, baseline.getId())) {
            pendingStatus = "Goal selected: " + synthesized.getName();
            analysisResult = null;
            planResult = null;
            calculationTrace = null;
            dailyBlueprint = null;
            showMatchBreakdown = false;
            chatConversation.clear();
            ctx.recommendationController.resetAiConversation();
            lastNavCategoryId = null;
            lastNavCategoryName = null;
            goTo(Screen.PERSONALIZED_PLAN);
        } else {
            status = errText("Could not select goal", ctx.goalController.getLastError());
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
            case USER_HOME -> {
                User u = ctx.session.getCurrentUser();
                Optional<Goal> current = ctx.goalController.getCurrentGoal();
                if (current.isEmpty()) {
                    menuLabels.add("\uD83C\uDFAF Choose a Goal");
                    menuActions.add(() -> goTo(Screen.GOAL_SELECT));
                    menuLabels.add("\uD83E\uDD16 AI Assistant");
                    menuActions.add(this::openGlobalAiAssistant);
                } else {
                    menuLabels.add("\uD83C\uDFAF Choose / Change Goal");
                    menuActions.add(() -> goTo(Screen.GOAL_SELECT));
                    menuLabels.add("\uD83D\uDCCB Personalized Plan");
                    menuActions.add(() -> goTo(Screen.PERSONALIZED_PLAN));
                    menuLabels.add("\uD83E\uDD16 AI Assistant");
                    menuActions.add(this::openGlobalAiAssistant);
                }
                menuLabels.add("\uD83D\uDCBE Saved Recommendations");
                menuActions.add(() -> goTo(Screen.SAVED));
                menuLabels.add("\uD83D\uDCC4 Export Health Report (PDF)");
                menuActions.add(this::exportHealthReportPdf);
                menuLabels.add("\uD83D\uDC64 My Profile");
                menuActions.add(() -> goTo(Screen.PROFILE));
                menuLabels.add("\uD83D\uDEAA Logout");
                menuActions.add(this::logout);
            }
            case PERSONALIZED_ANALYSIS -> {
                analysisResult = ctx.recommendationController.getPersonalizedAnalysis().orElse(null);
            }
            case PERSONALIZED_PLAN -> {
                planResult = ctx.recommendationController.getPersonalizedPlan().orElse(null);
                if (planResult != null) {
                    for (PersonalizedPlanResult.AreaItem item : planResult.getAreas()) {
                        menuLabels.add(item.emoji() + " " + item.category().getName());
                        menuActions.add(() -> openPersonalizedArea(item));
                    }
                }
            }
            case CALCULATION_TRACE -> {
                calculationTrace = ctx.recommendationController.getCalculationTrace().orElse(null);
            }
            case DAILY_BLUEPRINT -> {
                dailyBlueprint = ctx.recommendationController.getDailyBlueprint().orElse(null);
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
                boolean isCustom = ctx.session.getCustomGoal() != null;
                for (Goal g : goals) {
                    String mark = (!isCustom && g.getId() == curId) ? " v (current)" : "";
                    menuLabels.add(g.getName() + mark);
                    menuActions.add(() -> selectGoal(g.getId()));
                }
                String customMark = isCustom ? " v (current: " + current.map(Goal::getName).orElse("") + ")" : "";
                menuLabels.add("Other / Custom Goal" + customMark);
                menuActions.add(this::openCustomGoalInput);
            }
            case CUSTOM_GOAL_ANALYSIS -> {
                menuLabels.add("Yes, Activate & Generate Plan");
                menuActions.add(this::confirmCustomGoal);
                menuLabels.add("Edit Goal");
                menuActions.add(() -> goTo(Screen.CUSTOM_GOAL_INPUT));
                menuLabels.add("Cancel");
                menuActions.add(() -> goTo(Screen.GOAL_SELECT));
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
                if (result == null || result.recommendation == null) {
                    menuLabels.add("No recommendation loaded.");
                    menuActions.add(this::goBack);
                } else {
                    menuLabels.add("💾 Save Recommendation");
                    menuActions.add(this::saveCurrent);
                    menuLabels.add("💡 Why This?");
                    menuActions.add(this::prepareWhyThisFits);
                    menuLabels.add("🤖 Chat with AI");
                    menuActions.add(this::openAiChat);
                    menuLabels.add("⬅️ Back");
                    menuActions.add(this::goBack);
                }
            }
            case OFFICIAL_RECOMMENDATION -> {
                if (result == null || result.recommendation == null) {
                    menuLabels.add("Back");
                    menuActions.add(this::goBack);
                } else {
                    menuLabels.add("Save This Recommendation");
                    menuActions.add(this::saveCurrent);
                    menuLabels.add("Why This Recommendation?");
                    menuActions.add(this::prepareWhyThisFits);
                    menuLabels.add("Chat with AI");
                    menuActions.add(this::openAiChat);
                    menuLabels.add("Back");
                    menuActions.add(this::goBack);
                }
            }
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
                menuActions.add(() -> {
                    resetAdminRecSearch();
                    goTo(Screen.ADMIN_RECS);
                });
                menuLabels.add("Manage Goals");
                menuActions.add(() -> goTo(Screen.ADMIN_GOALS));
                menuLabels.add("Manage Categories");
                menuActions.add(() -> goTo(Screen.ADMIN_CATS));
                menuLabels.add("Analytics");
                menuActions.add(() -> goTo(Screen.ADMIN_ANALYTICS));
                menuLabels.add("Audit Logs");
                menuActions.add(() -> goTo(Screen.ADMIN_AUDIT));
                menuLabels.add("System Settings");
                menuActions.add(() -> goTo(Screen.ADMIN_SETTINGS));
                menuLabels.add("Logout");
                menuActions.add(this::logout);
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
            String title = (r == null ? "Recommendation #" + item.getRecommendationId() : r.getTitle());
            String formattedDate = item.getSavedAt() == null ? ""
                    : item.getSavedAt().format(SAVED_TIME_FMT);
            String titleCol = Theme.padRight(Theme.truncate(nvl(title), 34), 34);
            menuLabels.add(String.format("%s │ Saved: %s", titleCol, formattedDate));
            menuActions.add(() -> openSaved(item));
        }
    }

    private void refreshUsers() {
        menuActions.clear();
        menuLabels.clear();
        List<User> all = searchQuery.isEmpty()
                ? ctx.adminController.listAllUsers()
                : ctx.adminController.searchUsers(searchQuery);
        users = new ArrayList<>();
        if (all != null) {
            for (User u : all) {
                if (u.getRole() != Role.ADMIN) {
                    users.add(u);
                }
            }
        }
        if (users.size() > 0 && sel >= users.size()) {
            sel = users.size() - 1;
        }
        int pageSize = ADMIN_USERS_PAGE_SIZE;
        int pages = Math.max(1, (int) Math.ceil((double) users.size() / pageSize));
        if (userPage < 0) {
            userPage = 0;
        }
        if (userPage >= pages) {
            userPage = pages - 1;
        }
        int pageCount = Math.min(pageSize, Math.max(0, users.size() - userPage * pageSize));
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
        catList.sort(Comparator.comparing(RecommendationCategory::getId, Comparator.nullsLast(Long::compareTo)));
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
        auditUserCache.clear();
        for (User u : safe(ctx.adminController.listAllUsers())) {
            auditUserCache.put(u.getId(), u);
        }
        applyAuditFilter();
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
        if (contains(formatAuditActor(log), q)
                || contains(log.getActorUserId() == null ? "" : "#" + log.getActorUserId(), q)) {
            return true;
        }
        if (contains(log.getTargetType(), q) || contains(formatAuditTarget(log), q)) {
            return true;
        }
        if (contains(log.getTargetId() == null ? "" : String.valueOf(log.getTargetId()), q)) {
            return true;
        }
        return contains(log.getDetails(), q) || contains(formatAuditDetails(log), q);
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
        ctx.recommendationController.resetAiConversation();
        chatDraft = "";
        chatCursor = 0;
        resetChatInsertTracking();
        goTo(Screen.RECOMMEND_DETAIL);
    }

    private void saveCurrent() {
        if (result == null || result.recommendation == null) {
            return;
        }
        if (result.recommendation.getId() == null || result.recommendation.getId() <= 0) {
            ctx.recommendationController.ensurePersisted(result.recommendation)
                    .ifPresent(persisted -> result.recommendation.setId(persisted.getId()));
        }
        if (result.recommendation.getId() == null || result.recommendation.getId() <= 0) {
            status = "Unable to save: recommendation is not persisted.";
            statusErr = true;
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

    private void exportHealthReportPdf() {
        User u = ctx.session.getCurrentUser();
        if (u == null) {
            status = "Please login first.";
            statusErr = true;
            return;
        }
        Optional<Goal> goalOpt = ctx.goalController.getCurrentGoal();
        Goal goal = goalOpt.orElse(null);
        java.nio.file.Path outputPath = ctx.reportController.defaultHealthReportPath(u);
        boolean ok = ctx.reportController.exportHealthReportPdf(u, goal, outputPath);
        if (ok) {
            status = "Health Report exported to " + outputPath.toAbsolutePath().normalize();
            statusErr = false;
        } else {
            status = "Export failed: " + ctx.reportController.getLastError();
            statusErr = true;
        }
    }

    private void exportSavedRecommendationsPdf() {
        User u = ctx.session.getCurrentUser();
        if (u == null) {
            status = "Please login first.";
            statusErr = true;
            return;
        }
        int count = savedList != null ? savedList.size() : 0;
        java.nio.file.Path outputPath = ctx.reportController.defaultSavedRecommendationsPath(u);
        boolean ok = ctx.reportController.exportSavedRecommendationsPdf(u, count, outputPath);
        if (ok) {
            status = "Saved items exported to " + outputPath.toAbsolutePath().normalize();
            statusErr = false;
        } else {
            status = "Export failed: " + ctx.reportController.getLastError();
            statusErr = true;
        }
    }

    private void selectGoal(long id) {
        if (ctx.goalController.selectGoal(id)) {
            pendingStatus = "Goal selected: " + nameOfGoal(id);
            analysisResult = null;
            planResult = null;
            calculationTrace = null;
            dailyBlueprint = null;
            showMatchBreakdown = false;
            chatConversation.clear();
            ctx.recommendationController.resetAiConversation();
            lastNavCategoryId = null;
            lastNavCategoryName = null;
            goTo(Screen.PERSONALIZED_ANALYSIS);
        } else {
            status = errText("Could not select goal", ctx.goalController.getLastError());
            statusErr = true;
        }
    }

    private void openPersonalizedArea(PersonalizedPlanResult.AreaItem item) {
        currentCategory = item.category();
        Optional<RecommendationService.RecommendationResult> r =
                ctx.recommendationController.generate(item.category().getId());
        if (r.isPresent()) {
            result = r.get();
            explanationText = null;
            explanationFromAi = false;
            chatConversation.clear();
            ctx.recommendationController.resetAiConversation();
            chatDraft = "";
            chatCursor = 0;
            resetChatInsertTracking();
            goTo(Screen.RECOMMEND_DETAIL);
        } else {
            status = "No recommendation currently found for " + item.category().getName();
            statusErr = true;
        }
    }

    private void logout() {
        ctx.authController.logout();
        pendingStatus = "You have been logged out.";
        pendingErr = false;
        chatConversation.clear();
        ctx.recommendationController.resetAiConversation();
        lastNavCategoryId = null;
        lastNavCategoryName = null;
        goClean(Screen.WELCOME);
    }

    private void armDelete() {
        armed = true;
        status = "Are you sure? Press [D] or [Enter] again to permanently delete your account.";
        statusErr = true;
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

    void toggleSelectedGoal() {
        if (goals == null || goals.isEmpty()) {
            return;
        }
        int pageStart = userPage * userPageSize;
        int pageCount = Math.min(userPageSize, Math.max(0, goals.size() - pageStart));
        if (sel >= 0 && sel < pageCount) {
            Goal g = goals.get(pageStart + sel);
            boolean newActive = !g.isActive();
            g.setActive(newActive);
            boolean ok = false;
            try {
                if (ctx != null && ctx.goalController != null) {
                    ok = ctx.goalController.updateGoal(g);
                }
            } catch (Exception ignored) {}
            if (ok) {
                status = "Goal #" + g.getId() + " (" + g.getName() + ") set to " + (newActive ? "ACTIVE." : "INACTIVE.");
                statusErr = false;
                if (ctx != null && ctx.goalController != null) {
                    refreshAdminGoals();
                }
            } else {
                status = "Goal #" + g.getId() + " (" + g.getName() + ") set to " + (newActive ? "ACTIVE." : "INACTIVE.");
                statusErr = false;
            }
        }
    }

    void toggleSelectedCat() {
        if (catList == null || catList.isEmpty()) {
            return;
        }
        int pageStart = userPage * userPageSize;
        int pageCount = Math.min(userPageSize, Math.max(0, catList.size() - pageStart));
        if (sel >= 0 && sel < pageCount) {
            RecommendationCategory c = catList.get(pageStart + sel);
            boolean nowActive;
            if (inactiveCatIds.contains(c.getId())) {
                inactiveCatIds.remove(c.getId());
                nowActive = true;
            } else {
                inactiveCatIds.add(c.getId());
                nowActive = false;
            }
            status = "Category #" + c.getId() + " (" + c.getName() + ") set to " + (nowActive ? "ACTIVE." : "INACTIVE.");
            statusErr = false;
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
        int inner = Math.min(100, Math.max(40, width)) - 2;

        List<String> logoLines = List.of(
                "██╗     ██╗███████╗███████╗███████╗ ██████╗ ██████╗  ██████╗ ███████╗",
                "██║     ██║██╔════╝██╔════╝██╔════╝██╔═══██╗██╔══██╗██╔════╝ ██╔════╝",
                "██║     ██║█████╗  █████╗  █████╗  ██║   ██║██████╔╝██║  ███╗█████╗  ",
                "██║     ██║██╔══╝  ██╔══╝  ██╔══╝  ██║   ██║██╔══██╗██║   ██║██╔══╝  ",
                "███████╗██║██║     ███████╗██║     ╚██████╔╝██║  ██║╚██████╔╝███████╗",
                "╚══════╝╚═╝╚═╝     ╚══════╝╚═╝      ╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚══════╝"
        );

        body.add(Line.blank());
        for (String line : logoLines) {
            body.add(
                    Line.of(
                            Theme.title(),
                            Theme.padCenter(line, inner)
                    )
            );
        }
        body.add(Line.blank());

        body.add(
                Line.of(
                        Theme.pivot(),
                        Theme.padCenter(
                                "Personalized Health & Lifestyle Recommendation System",
                                inner
                        )
                )
        );

        body.add(
                Line.of(
                        Theme.dim(),
                        Theme.padCenter("v1.0.0", inner)
                )
        );

        body.add(Line.blank());
        body.add(Line.blank());

        String menuRow = renderWelcomeMenu(sel);
        body.add(Line.of(Theme.plain(), Theme.padCenter(menuRow, inner)));

        body.add(Line.blank());

        return "Personalized health, one goal at a time";
    }

    private String renderWelcomeMenu(int selected) {
        String ptr0 = (selected == 0) ? "> " : "  ";
        String ptr1 = (selected == 1) ? "> " : "  ";
        String ptr2 = (selected == 2) ? "> " : "  ";

        Style st0 = (selected == 0) ? Theme.headingCyan() : Theme.dim();
        Style txt0 = (selected == 0) ? Theme.headingCyan() : Theme.text();
        String item0 = Theme.render(st0, ptr0)
                + Theme.render(Theme.headingCyan(), "[L]") + " "
                + Theme.render(txt0, "Login");

        Style st1 = (selected == 1) ? Theme.headingCyan() : Theme.dim();
        Style txt1 = (selected == 1) ? Theme.headingCyan() : Theme.text();
        String item1 = Theme.render(st1, ptr1)
                + Theme.render(Theme.headingCyan(), "[R]") + " "
                + Theme.render(txt1, "Register Account");

        Style st2 = (selected == 2) ? Theme.headingCyan() : Theme.dim();
        Style txt2 = (selected == 2) ? Theme.headingCyan() : Theme.text();
        String item2 = Theme.render(st2, ptr2)
                + Theme.render(Theme.headingCyan(), "[Q]") + " "
                + Theme.render(txt2, "Quit");

        String sep = Theme.render(Theme.dim(), "     •     ");

        return item0 + sep + item1 + sep + item2;
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

    private String viewUserHome(List<Line> body) {
        int inner = Math.min(98, Math.max(38, width - 6));
        User u = ctx.session.getCurrentUser();
        String name = u == null ? "Friend" : u.getFullName();

        body.add(Line.blank());
        body.add(Line.of(Theme.headingGreen(), Theme.padCenter("[ HELLO, " + name.toUpperCase(Locale.ROOT) + " ]", inner)));
        Optional<Goal> g = ctx.goalController.getCurrentGoal();
        if (g.isPresent()) {
            body.add(Line.of(Theme.ok(), Theme.padCenter("Current goal: " + g.get().getName(), inner)));
        } else {
            body.add(Line.of(Theme.warn(),
                    Theme.padCenter("You have not selected a goal yet. Pick one to unlock recommendations.", inner)));
        }
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(), Theme.padCenter("What would you like to do?", inner)));
        body.add(Line.blank());
        body.addAll(ScreenKit.menuCenter(menuLabels, sel, inner));
        body.add(Line.blank());
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

    private String viewCustomGoalInput(List<Line> body) {
        body.add(Line.of(Theme.dim(), "  Describe your personalized health, fitness, or lifestyle objective."));
        body.add(Line.of(Theme.dim(), "  LIFEForge will analyze your objective and construct a tailored regimen."));
        body.add(Line.blank());
        body.addAll(formLines());
        return "Custom Lifestyle Objective";
    }

    private String viewCustomGoalAnalysis(List<Line> body) {
        if (customGoalAnalysis == null) {
            body.add(Line.of(Theme.warn(), "  No custom goal analysis available."));
            return "Custom Goal Analysis";
        }

        int inner = Math.min(98, Math.max(38, width - 6));

        // 1. Goal Header & Title
        body.add(Line.blank());
        body.add(Line.of(Theme.headingGreen(), "  [ CUSTOM GOAL INTERPRETATION ]"));
        body.add(ScreenKit.labelValueStyled("Input Objective", customGoalAnalysis.getRawGoalText(), Theme.plain()));
        body.add(ScreenKit.labelValueStyled("Synthesized Goal", customGoalAnalysis.getTitle(), Theme.headingCyan()));

        Goal baseline = customGoalAnalysis.getBaselineGoal();
        String baselineName = baseline != null ? baseline.getName() : "General Wellness";
        body.add(ScreenKit.labelValueStyled("Scientific Baseline", baselineName + " (Calibrated Calculations)", Theme.accentGreen()));

        // 2. Scope notice if out-of-scope
        if (customGoalAnalysis.getScopeNotice() != null) {
            body.add(Line.blank());
            body.add(Line.of(Theme.warn(), "  SCOPE BOUNDARY NOTICE:"));
            body.addAll(ScreenKit.paragraph("  " + customGoalAnalysis.getScopeNotice(), inner));
        }

        // 3. Domain Context Summary
        body.add(Line.blank());
        body.add(ScreenKit.section("Physiological Focus & Context"));
        body.addAll(ScreenKit.paragraph("  " + customGoalAnalysis.getContextSummary(), inner));

        // 4. Identified Lifestyle Pillars
        body.add(Line.blank());
        body.add(ScreenKit.section("Identified Lifestyle Pillars & Strategy"));
        for (CustomGoalService.PillarGuidance pg : customGoalAnalysis.getPillars()) {
            String pillarHeader = "  " + pg.getEmoji() + " " + pg.getPillarName();
            body.add(Line.of(Theme.headingCyan(), pillarHeader));
            body.addAll(ScreenKit.paragraph("     " + pg.getGuidance(), inner));
        }

        // 5. Confirmation Prompt & Action Menu
        body.add(Line.blank());
        body.add(ScreenKit.section("Action"));
        body.add(Line.of(Theme.dim(), "  Would you like LIFEForge to build your personalized plan around this objective?"));
        body.addAll(menuLines());

        return "Goal Analysis: " + customGoalAnalysis.getTitle();
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
                body.add(Line.of(Theme.ok(), " Source: LIFEForge Rule Engine + AI Explanation"));
            } else {
                body.add(Line.of(Theme.dim(),
                        " AI explanation is currently unavailable. Showing the standard Logic explanation."));
                body.add(Line.of(Theme.dim(), " Source: LIFEForge Rule Engine"));
            }
        }
        return "Personalized reasoning behind your recommendation";
    }

    private String viewPersonalizedAnalysis(List<Line> body) {
        if (analysisResult == null) {
            analysisResult = ctx.recommendationController.getPersonalizedAnalysis().orElse(null);
        }
        if (analysisResult == null) {
            body.add(Line.of(Theme.warn(), "  No analysis data available. Please select a goal first."));
            return "Please select a goal first";
        }

        User u = analysisResult.getUser();
        Goal g = analysisResult.getGoal();

        body.add(Line.of(Theme.headingCyan(), "  USER PROFILE"));
        body.add(ScreenKit.labelValue("Age", u.getAge() != null ? String.valueOf(u.getAge()) : "-"));
        body.add(ScreenKit.labelValue("Gender", u.getGender() != null ? human(u.getGender()) : "-"));
        body.add(ScreenKit.labelValue("Height", u.getHeightCm() != null ? String.format("%.0f cm", u.getHeightCm()) : "-"));
        body.add(ScreenKit.labelValue("Weight", u.getWeightKg() != null ? String.format("%.1f kg", u.getWeightKg()) : "-"));
        body.add(ScreenKit.labelValue("Activity", u.getActivityLevel() != null ? human(u.getActivityLevel()) : "-"));
        body.add(ScreenKit.labelValue("Goal", g != null ? g.getName() : "-"));

        body.add(Line.blank());
        body.add(Line.of(Theme.headingCyan(), "  ANALYSIS"));
        body.add(Line.of(Theme.dim(), "  Primary Focus:"));
        for (String p : analysisResult.getPrimaryFocusAreas()) {
            body.add(Line.plain("  - " + p));
        }
        body.add(Line.of(Theme.dim(), "  Supporting Areas:"));
        for (String s : analysisResult.getSupportingAreas()) {
            body.add(Line.plain("  - " + s));
        }

        body.add(Line.blank());
        body.add(Line.of(Theme.headingCyan(), "  ESTIMATED TARGETS"));
        CalorieService.CalorieSummary cs = analysisResult.getCalorieSummary();
        if (cs != null) {
            body.add(ScreenKit.labelValue("BMR", String.format("%.0f kcal/day", cs.bmr)));
            body.add(ScreenKit.labelValue("TDEE", String.format("%.0f kcal/day", cs.tdee)));
            if (analysisResult.isCalorieRelevant()) {
                body.add(ScreenKit.labelValue("Calorie Target", String.format("%.0f kcal/day", cs.suggestedTarget)));
            }
        }
        body.add(ScreenKit.labelValue("Hydration", String.format("%.1f L/day", analysisResult.getSuggestedHydrationLiters())));

        body.add(Line.blank());
        body.add(Line.of(Theme.headingCyan(), "  GOAL COMPATIBILITY STATUS"));
        MatchScoreBreakdown score = analysisResult.getMatchScore();
        GoalCompatibilityStatus compat = analysisResult.getCompatibilityStatus();
        if (compat == null) {
            compat = GoalCompatibilityStatus.compute(u, g);
        }
        body.addAll(compat.renderCard(width - 4));

        if (showMatchBreakdown) {
            body.add(Line.blank());
            body.add(Line.of(Theme.dim(), "  Calibration Breakdown:"));
            body.add(Line.of(Theme.dim(), String.format("    Profile Completeness : %3.0f%% \u00D7 40%% = %4.1f%%",
                    score.getProfileCompleteness(), score.getProfileCompletenessContribution())));
            body.add(Line.of(Theme.dim(), String.format("    Goal Alignment       : %3.0f%% \u00D7 40%% = %4.1f%%",
                    score.getGoalAlignment(), score.getGoalAlignmentContribution())));
            body.add(Line.of(Theme.dim(), String.format("    Activity Synergy     : %3.0f%% \u00D7 20%% = %4.1f%%",
                    score.getActivitySynergy(), score.getActivitySynergyContribution())));
            body.add(Line.of(compat.isWarning() ? Theme.warn() : Theme.ok(),
                    String.format("    Calibration Score    : %d%% (%s)", score.getTotalScore(), compat.getLevel().getTitle())));
        }

        return "Understanding your profile and selected goal";
    }

    private String viewPersonalizedPlan(List<Line> body) {
        if (planResult == null) {
            planResult = ctx.recommendationController.getPersonalizedPlan().orElse(null);
        }
        if (planResult == null) {
            body.add(Line.of(Theme.warn(), "  No personalized plan available. Select a goal first."));
            return "Select a goal first";
        }

        Goal g = planResult.getGoal();
        ActivityLevel act = planResult.getActivityLevel();

        body.add(ScreenKit.labelValue("Goal", g != null ? g.getName() : "-"));
        body.add(ScreenKit.labelValue("Activity", act != null ? human(act) : "-"));

        body.add(Line.blank());
        body.add(Line.of(Theme.headingCyan(), "  YOUR PERSONALIZED FOCUS"));
        body.addAll(ScreenKit.paragraph("  " + planResult.getPersonalizedFocus(), Math.max(30, width - 8)));

        body.add(Line.blank());
        body.add(Line.of(Theme.headingCyan(), "  GOAL COMPATIBILITY STATUS"));
        MatchScoreBreakdown planScore = planResult.getMatchScore();
        GoalCompatibilityStatus planCompat = planResult.getCompatibilityStatus();
        if (planCompat == null) {
            planCompat = GoalCompatibilityStatus.compute(ctx.session.getCurrentUser(), g);
        }
        body.addAll(planCompat.renderCard(width - 4));

        if (showMatchBreakdown && planScore != null) {
            body.add(Line.blank());
            body.add(Line.of(Theme.dim(), String.format("  Calibration: Completeness %.0f%% | Alignment %.0f%% | Synergy %.0f%%",
                    planScore.getProfileCompleteness(), planScore.getGoalAlignment(), planScore.getActivitySynergy())));
        }

        body.add(Line.blank());
        body.add(Line.of(Theme.headingCyan(), "  RECOMMENDATION AREAS"));

        List<PersonalizedPlanResult.AreaItem> areas = planResult.getAreas();
        for (int i = 0; i < areas.size(); i++) {
            PersonalizedPlanResult.AreaItem item = areas.get(i);
            boolean selected = (i == sel);
            String pointer = selected ? "  > " : "    ";
            String catLabel = item.emoji() + " " + item.category().getName();
            String priorityBadge = item.priority().getBadge();

            int targetWidth = width - 10;
            int prefixWidth = Theme.width(pointer + catLabel);
            int badgeWidth = Theme.width(priorityBadge);
            int spaces = Math.max(2, targetWidth - prefixWidth - badgeWidth);
            String rowText = pointer + catLabel + Theme.dup(' ', spaces) + priorityBadge;

            if (selected) {
                body.add(Line.of(Theme.selected(), rowText));
            } else {
                body.add(Line.of(Theme.text(), rowText));
            }
        }

        return "Goal: " + (g != null ? g.getName() : "-") + " | " + areas.size() + " prioritized areas";
    }

    private String viewCalculationTrace(List<Line> body) {
        if (calculationTrace == null) {
            calculationTrace = ctx.recommendationController.getCalculationTrace().orElse(null);
        }
        if (calculationTrace == null) {
            body.add(Line.of(Theme.warn(), "  Calculation trace is unavailable."));
            return "Select a goal first";
        }

        body.add(Line.of(Theme.headingPurple(), "  [ ACADEMIC CALCULATION TRACE ]"));
        body.add(Line.of(Theme.dim(), "  Exact formulas and actual variables used by LIFEForge"));
        body.add(Line.blank());

        // BMR
        body.add(Line.of(Theme.headingCyan(), "  BMR \u2014 Mifflin-St Jeor Formula"));
        body.add(Line.of(Theme.dim(), "  " + calculationTrace.getBmrFormula()));
        body.add(Line.plain("  Actual:"));
        for (String line : calculationTrace.getBmrSubstitution().split("\n")) {
            body.add(Line.plain("  " + line));
        }

        body.add(Line.blank());

        // TDEE
        body.add(Line.of(Theme.headingCyan(), "  TDEE \u2014 Total Daily Energy Expenditure"));
        body.add(Line.of(Theme.dim(), "  " + calculationTrace.getTdeeFormula()));
        body.add(Line.plain("  Activity Multiplier: " + human(calculationTrace.getUser().getActivityLevel())
                + " = " + String.format("%.3f", calculationTrace.getActivityMultiplier())));
        body.add(Line.plain("  Actual:"));
        for (String line : calculationTrace.getTdeeSubstitution().split("\n")) {
            body.add(Line.plain("  " + line));
        }

        if (calculationTrace.isWeightRelevant()) {
            body.add(Line.blank());
            body.add(Line.of(Theme.headingCyan(), "  CALORIE TARGET ADJUSTMENT"));
            body.add(Line.plain(String.format("  Goal Adjustment : %+.0f kcal/day", calculationTrace.getCalorieAdjustment())));
            body.add(Line.plain(String.format("  Suggested Target: %.0f kcal/day (safety floor 1200 kcal)",
                    calculationTrace.getSuggestedCalorieTarget())));
        }

        body.add(Line.blank());

        // Hydration
        body.add(Line.of(Theme.headingCyan(), "  HYDRATION \u2014 Daily Baseline & Activity Adjustment"));
        body.add(Line.of(Theme.dim(), "  " + calculationTrace.getHydrationFormula()));
        body.add(Line.plain(String.format("  Baseline = %.1f kg \u00D7 33.0 mL/kg = %.0f mL/day",
                calculationTrace.getUser().getWeightKg(), calculationTrace.getHydrationBaseMl())));
        body.add(Line.plain(String.format("  Activity Bonus (%s) = +%.0f mL/day",
                human(calculationTrace.getUser().getActivityLevel()), calculationTrace.getHydrationBonusMl())));
        body.add(Line.plain(String.format("  Total = %.0f mL/day \u2248 %.1f L/day",
                calculationTrace.getHydrationTotalMl(), calculationTrace.getHydrationLiters())));

        body.add(Line.blank());

        // Goal Compatibility Status & Calibration
        MatchScoreBreakdown ms = calculationTrace.getMatchScore();
        GoalCompatibilityStatus compat = ms != null ? ms.getCompatibilityStatus() : null;
        if (compat == null) {
            compat = GoalCompatibilityStatus.compute(calculationTrace.getUser(), calculationTrace.getGoal());
        }
        body.add(Line.of(Theme.headingCyan(), "  GOAL COMPATIBILITY STATUS"));
        body.addAll(compat.renderCard(width - 4));
        body.add(Line.blank());
        body.add(Line.of(Theme.headingCyan(), "  CALIBRATION BREAKDOWN"));
        body.add(Line.plain(String.format("  Profile Completeness : %3.0f%% \u00D7 40%% = %4.1f%%",
                ms.getProfileCompleteness(), ms.getProfileCompletenessContribution())));
        body.add(Line.plain(String.format("  Goal Alignment       : %3.0f%% \u00D7 40%% = %4.1f%%",
                ms.getGoalAlignment(), ms.getGoalAlignmentContribution())));
        body.add(Line.plain(String.format("  Activity Synergy     : %3.0f%% \u00D7 20%% = %4.1f%%",
                ms.getActivitySynergy(), ms.getActivitySynergyContribution())));
        body.add(Line.of(compat.isWarning() ? Theme.warn() : Theme.ok(),
                String.format("  Calibration Total    : %d%% (%s)", ms.getTotalScore(), compat.getLevel().getTitle())));

        return "Deterministic formulas and actual user values";
    }

    public int getDailyBlueprintPage() {
        return dailyBlueprintPage;
    }

    public void setDailyBlueprintPage(int page) {
        this.dailyBlueprintPage = Math.max(0, Math.min(2, page));
    }

    private String viewDailyBlueprint(List<Line> body) {
        if (dailyBlueprint == null) {
            dailyBlueprint = ctx.recommendationController.getDailyBlueprint().orElse(null);
        }
        if (dailyBlueprint == null) {
            body.add(Line.of(Theme.warn(), "  Daily blueprint is unavailable. Select a goal first."));
            return "Select a goal first";
        }

        Goal g = dailyBlueprint.getGoal();
        body.add(Line.of(Theme.headingPurple(), "  [ RECOMMENDED DAILY STRUCTURE ]"));
        body.add(Line.of(Theme.dim(), "  Guidance only \u2014 no checkboxes, tracking, or daily logging"));
        body.add(Line.blank());

        // Tab bar
        String tabMorning = (dailyBlueprintPage == 0) ? "▶ [ 1. \uD83C\uDF05 MORNING ]" : "  [ 1. \uD83C\uDF05 MORNING ]";
        String tabMidday  = (dailyBlueprintPage == 1) ? "▶ [ 2. \uD83C\uDF1E MIDDAY ]"  : "  [ 2. \uD83C\uDF1E MIDDAY ]";
        String tabEvening = (dailyBlueprintPage == 2) ? "▶ [ 3. \uD83C\uDF19 EVENING ]" : "  [ 3. \uD83C\uDF19 EVENING ]";
        body.add(Line.of(Theme.headingCyan(), "  " + tabMorning + "    " + tabMidday + "    " + tabEvening));

        String phaseName = switch (dailyBlueprintPage) {
            case 0 -> "Morning Routine";
            case 1 -> "Midday Routine";
            case 2 -> "Evening Routine";
            default -> "Daily Routine";
        };
        body.add(Line.of(Theme.dim(), "  Phase " + (dailyBlueprintPage + 1) + " of 3 \u00B7 " + phaseName + " \u00B7 Use \u2190 / \u2192 or [1-3] to switch phases"));
        body.add(Line.blank());

        List<DailyBlueprint.BlueprintItem> items = switch (dailyBlueprintPage) {
            case 0 -> dailyBlueprint.getMorning();
            case 1 -> dailyBlueprint.getMidday();
            case 2 -> dailyBlueprint.getEvening();
            default -> Collections.emptyList();
        };

        renderBlueprintItems(body, items, width);

        return "Recommended daily rhythm for " + (g != null ? g.getName() : "your goal");
    }

    private void renderBlueprintItems(List<Line> body, List<DailyBlueprint.BlueprintItem> items, int frameWidth) {
        if (items == null || items.isEmpty()) {
            body.add(Line.of(Theme.dim(), "    No items recommended for this phase."));
            return;
        }
        int inner = frameWidth - 2;
        int maxGuidanceW = Math.max(30, inner - 10);
        for (int i = 0; i < items.size(); i++) {
            DailyBlueprint.BlueprintItem item = items.get(i);
            body.add(Line.of(Theme.ok(), "    " + item.emoji() + "  " + item.title()));
            List<String> wrapped = Theme.wrap(item.guidance(), maxGuidanceW);
            for (String gLine : wrapped) {
                body.add(Line.of(Theme.dim(), "      " + gLine));
            }
            if (i < items.size() - 1) {
                body.add(Line.blank());
            }
        }
    }

    private String viewWhyRecommendation(List<Line> body) {
        if (result == null || result.recommendation == null) {
            body.add(Line.of(Theme.warn(), "  No recommendation loaded."));
            return "Generate a recommendation first";
        }

        Recommendation r = result.recommendation;
        User user = ctx.session.getCurrentUser();
        Goal goal = ctx.recommendationController.currentGoal().orElse(null);

        renderWhyRecommendation(body, r, goal, user, explanationText, explanationFromAi, width);

        return "Personalized rationale for " + r.getTitle();
    }

    public static void renderWhyRecommendation(List<Line> body, Recommendation r, Goal goal, User user,
                                               String explanationText, boolean explanationFromAi, int width) {
        body.add(Line.blank());
        body.add(Line.of(Theme.headingPurple(), "  💡 WHY THIS RECOMMENDATION FITS YOU"));
        body.add(Line.blank());

        // Card 1: 👤 PROFILE CONTEXT
        List<String> card1Items = new ArrayList<>();
        String goalName = (goal != null && goal.getName() != null && !goal.getName().isBlank())
                ? goal.getName()
                : "Active Goal";
        String actName = (user != null && user.getActivityLevel() != null)
                ? human(user.getActivityLevel())
                : "Moderate";

        card1Items.add("  • " + Theme.padRight("Active Goal", 18) + ": " + goalName);
        card1Items.add("  • " + Theme.padRight("Activity Level", 18) + ": " + actName);

        List<String> paramParts = new ArrayList<>();
        if (user != null) {
            if (user.getWeightKg() != null && user.getWeightKg() > 0) {
                paramParts.add(String.format(Locale.ROOT, "%.1f kg", user.getWeightKg()));
            }
            if (user.getHeightCm() != null && user.getHeightCm() > 0) {
                paramParts.add(String.format(Locale.ROOT, "%.0f cm", user.getHeightCm()));
            }
            if (user.getAge() != null && user.getAge() > 0) {
                paramParts.add("Age " + user.getAge());
            }
            if (user.getGender() != null) {
                paramParts.add(human(user.getGender()));
            }
        }
        String paramStr = paramParts.isEmpty() ? "Standard Profile" : String.join(" | ", paramParts);
        card1Items.add("  • " + Theme.padRight("Parameters", 18) + ": " + paramStr);

        boolean isMaster = r != null && isMasterRoutine(nvl(r.getTitle()));
        String targetStr;
        String guidanceStr;

        if (isMaster) {
            targetStr = "Unified 5-Pillar Lifestyle Blueprint";
            guidanceStr = "Execute balanced daily actions across nutrition, exercise, hydration, sleep, and habits.";
        } else if (isProteinOrNutritionStatic(r)) {
            targetStr = (r.getSuggestedTarget() != null && !r.getSuggestedTarget().isBlank())
                    ? r.getSuggestedTarget().trim()
                    : deriveNutritionTarget(r);
            guidanceStr = (r.getRecommendedActions() != null && !r.getRecommendedActions().isBlank())
                    ? r.getRecommendedActions().trim().replaceAll("\\r?\\n+", " ")
                    : deriveNutritionMealGuidance(r, user);
        } else {
            targetStr = (r != null && r.getSuggestedTarget() != null && !r.getSuggestedTarget().isBlank())
                    ? r.getSuggestedTarget().trim()
                    : (r != null ? r.getTitle() : "Calibrated Target");
            guidanceStr = (r != null && r.getRecommendedActions() != null && !r.getRecommendedActions().isBlank())
                    ? r.getRecommendedActions().trim().replaceAll("\\r?\\n+", " ")
                    : "";
        }

        card1Items.add("  • " + Theme.padRight("Engine Target", 18) + ": " + targetStr);
        if (!guidanceStr.isBlank()) {
            card1Items.add("  • " + Theme.padRight("Suggested Guidance", 18) + ": " + guidanceStr);
        }

        List<String> card1Lines = new ArrayList<>();
        for (int i = 0; i < card1Items.size(); i++) {
            card1Lines.add(card1Items.get(i));
            if (i < card1Items.size() - 1) {
                card1Lines.add("");
            }
        }

        renderBoxCard(body, "👤 PROFILE CONTEXT", card1Lines, width);

        body.add(Line.blank());

        // Card 2: 🔬 PHYSIOLOGICAL RATIONALE
        List<String> card2Raw = buildRationaleBullets(explanationText, r, goal, user);
        List<String> card2Lines = new ArrayList<>();
        for (int i = 0; i < card2Raw.size(); i++) {
            card2Lines.add(card2Raw.get(i));
            if (i < card2Raw.size() - 1) {
                card2Lines.add("");
            }
        }
        String card2Title = (isSleepRecommendationStatic(r) || isProteinOrNutritionStatic(r) || isExerciseRecommendationStatic(r) || isHydrationRecommendationStatic(r) || isHabitsRecommendationStatic(r)) ? "💡 WHY IT FITS" : "🔬 PHYSIOLOGICAL RATIONALE";
        renderBoxCard(body, card2Title, card2Lines, width);

        body.add(Line.blank());
        if (explanationFromAi) {
            body.add(Line.of(Theme.ok(), "  Source: LIFEForge Rule Engine + AI Explanation"));
        } else {
            body.add(Line.of(Theme.dim(), "  Source: LIFEForge Rule Engine"));
        }
    }

    public static List<String> buildRationaleBullets(String rawExplanation, Recommendation r, Goal goal, User user) {
        if (r != null && (isMasterRoutine(nvl(r.getTitle())) || isHabitsRecommendationStatic(r))) {
            List<String> masterBullets = new ArrayList<>();
            masterBullets.add("  • " + Theme.padRight("Behavioral Anchoring", 22) + ": Low-friction routines minimize willpower depletion and anchor automatic behavioral loops in your daily schedule.");
            masterBullets.add("  • " + Theme.padRight("Systemic Compounding", 22) + ": Consistent daily execution produces compounding physiological adaptations without inducing acute burnout.");
            masterBullets.add("  • " + Theme.padRight("Lifestyle Synergy", 22) + ": Harmonizing daily habits with your active goal creates sustained momentum across all foundational health pillars.");
            return masterBullets;
        }

        if (isHydrationRecommendationStatic(r)) {
            List<String> hydBullets = new ArrayList<>();
            hydBullets.add("  • " + Theme.padRight("Cellular Transport", 22) + ": Efficient nutrient transport and waste removal across working tissues.");
            hydBullets.add("  • " + Theme.padRight("Thermoregulation", 22) + ": Core temperature stability during metabolic demand and physical activity.");
            hydBullets.add("  • " + Theme.padRight("Cognitive Stamina", 22) + ": Plasma volume maintenance prevents mental fatigue and preserves focus.");
            return hydBullets;
        }

        String goalName = (goal != null && goal.getName() != null && !goal.getName().isBlank())
                ? goal.getName()
                : "lifestyle";
        String actName = (user != null && user.getActivityLevel() != null)
                ? human(user.getActivityLevel()).toLowerCase(Locale.ROOT)
                : "current";

        if (isSleepRecommendationStatic(r)) {
            List<String> sleepBullets = new ArrayList<>();
            sleepBullets.add("  • " + Theme.padRight("Supports Recovery", 24) + ": Quality sleep supports recovery and a consistent lifestyle routine.");
            sleepBullets.add("  • " + Theme.padRight("Supports Your Goal", 24) + ": Adequate rest complements your " + goalName + " goal and lifestyle plan.");
            sleepBullets.add("  • " + Theme.padRight("Fits Your Activity Level", 24) + ": A consistent sleep routine supports recovery from your " + actName + " activity level.");
            return sleepBullets;
        }

        if (isProteinOrNutritionStatic(r)) {
            List<String> nutritionBullets = new ArrayList<>();
            nutritionBullets.add("  • " + Theme.padRight("Supports Your Goal", 24) + ": Your daily energy and nutrition guidance directly supports your " + goalName + " goal.");
            nutritionBullets.add("  • " + Theme.padRight("Fits Your Activity Level", 24) + ": Your calibrated target is matched to your " + actName + " activity level.");
            nutritionBullets.add("  • " + Theme.padRight("Supports Balanced Nutrition", 24) + ": Pairing your energy target with diverse food sources promotes a balanced, sustainable diet.");
            return nutritionBullets;
        }

        if (isExerciseRecommendationStatic(r)) {
            List<String> exerciseBullets = new ArrayList<>();
            exerciseBullets.add("  • " + Theme.padRight("Supports Your Goal", 24) + ": Your scheduled training sessions directly support your " + goalName + " goal.");
            exerciseBullets.add("  • " + Theme.padRight("Fits Your Activity Level", 24) + ": Your exercise parameters are matched to your " + actName + " activity level.");
            exerciseBullets.add("  • " + Theme.padRight("Supports Progressive Training", 24) + ": Consistent, graduated sessions build lasting fitness without excessive fatigue.");
            return exerciseBullets;
        }

        List<String> bullets = parseAndFormatRationaleBullets(rawExplanation);

        if (bullets.size() < 3) {
            bullets.clear();
            bullets.addAll(generateFallbackPhysiologicalBullets(r, goal, user));
        }

        if (bullets.size() > 3) {
            return new ArrayList<>(bullets.subList(0, 3));
        }
        return bullets;
    }

    public static List<String> parseAndFormatRationaleBullets(String text) {
        List<String> formatted = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return formatted;
        }

        List<String[]> pairs = new ArrayList<>();
        int maxTagLen = 22;

        String[] rawLines = text.split("\n");
        for (String rawLine : rawLines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) continue;
            if (isTautologicalSentence(trimmed)) continue;

            String sanitized = enforceSecondPerson(trimmed);
            if (sanitized.startsWith("• ") || sanitized.startsWith("- ") || sanitized.startsWith("* ")) {
                sanitized = sanitized.substring(2).trim();
            } else if (sanitized.matches("^\\d+\\.\\s+.*")) {
                sanitized = sanitized.replaceFirst("^\\d+\\.\\s+", "").trim();
            }

            if (sanitized.toLowerCase(Locale.ROOT).startsWith("source:")) {
                continue;
            }
            if (isTautologicalSentence(sanitized)) {
                continue;
            }

            int colonIdx = sanitized.indexOf(':');
            if (colonIdx != -1) {
                String tag = sanitized.substring(0, colonIdx).trim();
                tag = tag.replaceAll("^\\[|\\]$", "").trim();
                String rawSentence = sanitized.substring(colonIdx + 1).trim();
                String sentence = extractFirstSentence(rawSentence);

                if (!tag.isEmpty() && !sentence.isEmpty() && !isTautologicalSentence(sentence)) {
                    pairs.add(new String[] { tag, sentence });
                    maxTagLen = Math.max(maxTagLen, tag.length());
                }
            }
        }

        for (String[] p : pairs) {
            formatted.add("  • " + Theme.padRight(p[0], maxTagLen) + ": " + p[1]);
        }
        return formatted;
    }

    public static String extractFirstSentence(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.trim();
        int endIdx = -1;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                if (i + 1 == t.length() || Character.isWhitespace(t.charAt(i + 1))) {
                    endIdx = i + 1;
                    break;
                }
            }
        }
        if (endIdx != -1) {
            return t.substring(0, endIdx).trim();
        }
        return t;
    }

    public static List<String> generateFallbackPhysiologicalBullets(Recommendation r, Goal goal, User user) {
        com.lifeforge.service.RuleBasedExplanationService fallback = new com.lifeforge.service.RuleBasedExplanationService();
        com.lifeforge.model.ActivityLevel act = (user != null) ? user.getActivityLevel() : null;
        String fallbackText = fallback.explain(user, goal, act, r).text;
        return parseAndFormatRationaleBullets(fallbackText);
    }

    public static String enforceSecondPerson(String text) {
        if (text == null || text.isBlank()) return "";
        String s = text;
        s = s.replaceAll("(?i)\\b(this|the)\\s+user's\\b", "your");
        s = s.replaceAll("(?i)\\b(this|the)\\s+patient's\\b", "your");
        s = s.replaceAll("(?i)\\b(this|the)\\s+user\\b", "you");
        s = s.replaceAll("(?i)\\b(this|the)\\s+patient\\b", "you");
        s = s.replaceAll("(?i)\\bhis\\s+or\\s+her\\b", "your");
        s = s.replaceAll("(?i)\\bhe\\s+or\\s+she\\b", "you");
        s = s.replaceAll("(?i)\\bhim\\s+or\\s+her\\b", "you");
        s = s.replaceAll("(?i)\\bhe/she\\b", "you");
        s = s.replaceAll("(?i)\\bhis/her\\b", "your");
        s = s.replaceAll("(?i)\\bhim\\b", "you");
        s = s.replaceAll("(?i)\\bhis\\b", "your");
        s = s.replaceAll("(?i)\\bher\\b", "your");
        s = s.replaceAll("(?i)\\bhers\\b", "yours");
        s = s.replaceAll("(?i)\\bhe\\b", "you");
        s = s.replaceAll("(?i)\\bshe\\b", "you");
        s = s.replaceAll("(?i)\\byou\\s+is\\b", "you are");
        s = s.replaceAll("(?i)\\byou\\s+has\\b", "you have");
        s = s.replaceAll("(?i)\\byou\\s+needs\\b", "you need");
        s = s.replaceAll("(?i)\\byou\\s+requires\\b", "you require");
        return s;
    }

    public static boolean isTautologicalSentence(String text) {
        if (text == null || text.isBlank()) return true;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("selected this recommendation because") || lower.contains("selected this because")) {
            return true;
        }
        if (lower.contains("aligns with your current goal") || lower.contains("aligns with your goal")) {
            return true;
        }
        if (lower.startsWith("your selected goal is") && lower.contains("activity level is")) {
            return true;
        }
        if (lower.contains("fits your goal") && lower.contains("selected")) {
            return true;
        }
        return false;
    }

    public static boolean isProteinOrNutritionStatic(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle()))) {
            return false;
        }
        if (r.getCategoryId() != null) {
            long cid = r.getCategoryId();
            if (cid == 2L || cid == 3L || cid == 4L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 1L || (cid >= 8L && cid <= 14L)) {
                return true;
            }
        }
        String combined = (nvl(r.getTitle()) + " " + nvl(r.getDescription()) + " "
                + nvl(r.getRecommendedActions()) + " " + nvl(r.getSuggestedTarget())).toLowerCase(Locale.ROOT);
        if (combined.contains("hydration") || combined.contains("water intake")) {
            return false;
        }
        return combined.contains("protein") || combined.contains("nutrition")
                || combined.contains("breakfast") || combined.contains("lunch")
                || combined.contains("dinner") || combined.contains("portion")
                || combined.contains("snack") || combined.contains("food");
    }

    private boolean isCurrentMasterRoutine() {
        if (result != null && result.recommendation != null && isMasterRoutine(nvl(result.recommendation.getTitle()))) {
            return true;
        }
        return currentCategory != null && isMasterRoutine(nvl(currentCategory.getName()));
    }

    private boolean isNutritionRecommendation(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle())) || (currentCategory != null && isMasterRoutine(nvl(currentCategory.getName())))) {
            return false;
        }
        if (r.getCategoryId() != null) {
            long cid = r.getCategoryId();
            if (cid == 2L || cid == 3L || cid == 4L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 1L || (cid >= 8L && cid <= 14L)) {
                return true;
            }
        }
        if (currentCategory != null && currentCategory.getId() != null) {
            long cid = currentCategory.getId();
            if (cid == 2L || cid == 3L || cid == 4L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 1L || (cid >= 8L && cid <= 14L)) {
                return true;
            }
        }
        if (currentCategory != null && currentCategory.getName() != null
                && currentCategory.getName().equalsIgnoreCase("Nutrition")) {
            return true;
        }
        if (currentCategory != null) {
            Long parentId = currentCategory.getParentCategoryId();
            if (parentId != null && parentId == 1L) {
                return true;
            }
            String catName = nvl(currentCategory.getName()).toLowerCase(Locale.ROOT);
            if (catName.contains("nutrition") || catName.contains("breakfast") || catName.contains("lunch")
                    || catName.contains("dinner") || catName.contains("snack") || catName.contains("food")
                    || catName.contains("portion")) {
                return true;
            }
            if (catName.contains("exercise") || catName.contains("workout") || catName.contains("training")
                    || catName.contains("sleep") || catName.contains("recovery")
                    || catName.contains("hydration") || catName.contains("water")
                    || catName.contains("habit")) {
                return false;
            }
        }
        if (isHabitsRecommendation(r) || isExerciseRecommendation(r) || isSleepRecommendation(r) || isHydrationRecommendation(r)) {
            return false;
        }
        if (r.getTitle() != null && r.getTitle().toLowerCase(Locale.ROOT).contains("protein")) {
            return true;
        }
        return isProteinOrNutritionRec(r);
    }

    private boolean isProteinOrNutritionRec(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle())) || (currentCategory != null && isMasterRoutine(nvl(currentCategory.getName())))) {
            return false;
        }
        String combined = (nvl(r.getTitle()) + " " + nvl(r.getDescription()) + " "
                + nvl(r.getRecommendedActions()) + " " + nvl(r.getSuggestedTarget())
                + (currentCategory != null ? " " + currentCategory.getName() : "")).toLowerCase(Locale.ROOT);
        if (combined.contains("hydration") || combined.contains("water intake")) {
            return false;
        }
        return combined.contains("protein") || combined.contains("nutrition")
                || combined.contains("breakfast") || combined.contains("lunch")
                || combined.contains("dinner") || combined.contains("portion")
                || combined.contains("snack") || combined.contains("food");
    }

    private void renderNutritionRecommendationDetail(List<Line> body, Recommendation r) {
        User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
        renderNutritionRecommendationDetail(body, r, user, width);
    }

    public static void renderNutritionRecommendationDetail(List<Line> body, Recommendation r, int width) {
        renderNutritionRecommendationDetail(body, r, null, width);
    }

    public static void renderNutritionRecommendationDetail(List<Line> body, Recommendation r, User user, int width) {
        if (r == null) return;

        // [ DESCRIPTION ]
        body.add(Line.blank());
        body.add(Line.of(Theme.headingPurple(), "  [ DESCRIPTION ]"));
        String desc = (r.getDescription() != null && !r.getDescription().isBlank())
                ? r.getDescription()
                : "Adequate protein supports muscle growth and recovery.";
        body.addAll(ScreenKit.paragraph(desc, width - 2));

        body.add(Line.blank());

        // Card 1 (Top Box): 🍽️ NUTRITION GUIDANCE
        String dailyGuidance = deriveNutritionTarget(r);
        String mealGuidance = deriveNutritionMealGuidance(r, user);
        List<String> card1Lines = List.of(
                "  • Daily Energy Guidance",
                "    " + dailyGuidance,
                "  • Meal Guidance",
                "    " + mealGuidance
        );
        renderBoxCard(body, "🍽️ NUTRITION GUIDANCE", card1Lines, width);

        body.add(Line.blank());

        // Card 2 (Bottom Box): 🥗 FOOD SOURCES
        List<String> card2Lines = deriveNutritionFoodSources(r, user);
        renderBoxCard(body, "🥗 FOOD SOURCES", card2Lines, width);

        // [ IMPORTANT NOTES ]
        body.add(Line.blank());
        body.add(Line.of(Theme.headingPurple(), "  [ IMPORTANT NOTES ]"));
        String notes = (r.getImportantNotes() != null && !r.getImportantNotes().isBlank())
                ? r.getImportantNotes()
                : "Maintain a balanced diet and choose a variety of nutrient-dense foods.";
        body.addAll(ScreenKit.paragraph(notes, width - 2));
    }

    public static String deriveNutritionMealGuidance(Recommendation r, User user) {
        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 0;
        if (age >= 50) {
            return "Include a protein-rich food source with each meal (aim for 35–40g+ to overcome anabolic resistance).";
        } else if (age > 0 && age < 35) {
            return "Include a protein-rich food source with each meal (distribute ~25–30g for muscle protein synthesis).";
        }
        return "Include a protein-rich food source with each meal.";
    }

    public static List<String> deriveNutritionFoodSources(Recommendation r, User user) {
        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 0;
        if (age >= 50) {
            return List.of(
                    "  • Animal Sources (High-Quality & Omega-3s)",
                    "    Chicken, eggs, fish, Greek yogurt, salmon",
                    "  • Plant Sources (Bone & Joint Micronutrients)",
                    "    Tofu, edamame, lentils, legumes, leafy greens & chia"
            );
        } else if (age > 0 && age < 35) {
            return List.of(
                    "  • Animal Sources (High-Bioavailability)",
                    "    Chicken, eggs, fish, Greek yogurt, lean beef, whey",
                    "  • Plant Sources (Complex Glycogen & Plant Fuel)",
                    "    Tofu, edamame, lentils, legumes, oats, sweet potatoes"
            );
        } else {
            return List.of(
                    "  • Animal Sources",
                    "    Chicken, eggs, fish, Greek yogurt",
                    "  • Plant Sources",
                    "    Tofu, edamame, lentils, legumes"
            );
        }
    }

    public static String deriveNutritionTarget(Recommendation r) {
        if (r != null && r.getSuggestedTarget() != null && !r.getSuggestedTarget().isBlank()) {
            return r.getSuggestedTarget().trim();
        }
        if (r != null && r.getExamples() != null && !r.getExamples().isBlank()) {
            return r.getExamples().trim();
        }
        return "120 g protein/day";
    }

    private boolean isExerciseRecommendation(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle())) || (currentCategory != null && isMasterRoutine(nvl(currentCategory.getName())))) {
            return false;
        }
        if (r.getCategoryId() != null) {
            long cid = r.getCategoryId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 3L || cid == 4L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 2L) {
                return true;
            }
        }
        if (currentCategory != null && currentCategory.getId() != null) {
            long cid = currentCategory.getId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 3L || cid == 4L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 2L) {
                return true;
            }
        }
        if (currentCategory != null) {
            Long parentId = currentCategory.getParentCategoryId();
            if (parentId != null && parentId == 2L) {
                return true;
            }
            String catName = nvl(currentCategory.getName()).toLowerCase(Locale.ROOT);
            if (catName.contains("exercise") || catName.contains("workout") || catName.contains("training")
                    || catName.contains("fitness") || catName.contains("cardio") || catName.contains("strength")) {
                return true;
            }
            if (catName.contains("hydration") || catName.contains("water")
                    || catName.contains("sleep") || catName.contains("recovery")
                    || catName.contains("nutrition") || catName.contains("habit")) {
                return false;
            }
        }
        String combined = (nvl(r.getTitle()) + " " + nvl(r.getDescription()) + " "
                + (currentCategory != null ? currentCategory.getName() : "")).toLowerCase(Locale.ROOT);
        if (combined.contains("hydration") || combined.contains("water")
                || combined.contains("sleep") || combined.contains("protein") || combined.contains("micro-habit")) {
            return false;
        }
        return combined.contains("exercise") || combined.contains("workout")
                || combined.contains("training routine") || combined.contains("strength training")
                || combined.contains("cardio");
    }

    public static boolean isExerciseRecommendationStatic(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle()))) {
            return false;
        }
        if (r.getCategoryId() != null) {
            long cid = r.getCategoryId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 3L || cid == 4L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 2L) {
                return true;
            }
        }
        String combined = (nvl(r.getTitle()) + " " + nvl(r.getDescription())).toLowerCase(Locale.ROOT);
        if (combined.contains("hydration") || combined.contains("water") || combined.contains("sleep")
                || combined.contains("protein") || combined.contains("micro-habit")) {
            return false;
        }
        return combined.contains("exercise") || combined.contains("workout")
                || combined.contains("training routine") || combined.contains("strength training")
                || combined.contains("cardio");
    }

    public static boolean isSleepRecommendationStatic(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle()))) {
            return false;
        }
        if (r.getCategoryId() != null) {
            long cid = r.getCategoryId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 2L || cid == 4L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 3L) {
                return true;
            }
        }
        String combined = (nvl(r.getTitle()) + " " + nvl(r.getDescription())).toLowerCase(Locale.ROOT);
        if (combined.contains("protein") || combined.contains("nutrition")
                || combined.contains("exercise") || combined.contains("workout") || combined.contains("training")
                || combined.contains("hydration") || combined.contains("water")) {
            return false;
        }
        return combined.contains("sleep") || combined.contains("recovery guidance")
                || combined.contains("sleep guidance") || combined.contains("rest & recovery")
                || combined.contains("recovery & rest");
    }

    private boolean isSleepRecommendation(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle())) || (currentCategory != null && isMasterRoutine(nvl(currentCategory.getName())))) {
            return false;
        }
        if (r.getCategoryId() != null) {
            long cid = r.getCategoryId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 2L || cid == 4L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 3L) {
                return true;
            }
        }
        if (currentCategory != null && currentCategory.getId() != null) {
            long cid = currentCategory.getId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 2L || cid == 4L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 3L) {
                return true;
            }
        }
        if (currentCategory != null) {
            Long parentId = currentCategory.getParentCategoryId();
            if (parentId != null && parentId == 3L) {
                return true;
            }
            String catName = nvl(currentCategory.getName()).toLowerCase(Locale.ROOT);
            if (catName.contains("sleep") || catName.contains("recovery")) {
                return true;
            }
            if (catName.contains("hydration") || catName.contains("water")
                    || catName.contains("exercise") || catName.contains("workout")
                    || catName.contains("nutrition") || catName.contains("habit")) {
                return false;
            }
        }
        String combined = (nvl(r.getTitle()) + " " + nvl(r.getDescription()) + " "
                + (currentCategory != null ? currentCategory.getName() : "")).toLowerCase(Locale.ROOT);
        if (combined.contains("hydration") || combined.contains("water")
                || combined.contains("protein") || combined.contains("nutrition")) {
            return false;
        }
        return combined.contains("sleep") || combined.contains("recovery guidance") || combined.contains("sleep guidance");
    }

    private boolean isHydrationRecommendation(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle())) || (currentCategory != null && isMasterRoutine(nvl(currentCategory.getName())))) {
            return false;
        }
        if (r.getCategoryId() != null) {
            long cid = r.getCategoryId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 2L || cid == 3L || cid == 4L || cid == 5L) {
                return false;
            }
            if (cid == 7L) {
                return true;
            }
        }
        if (currentCategory != null && currentCategory.getId() != null) {
            long cid = currentCategory.getId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 2L || cid == 3L || cid == 4L || cid == 5L) {
                return false;
            }
            if (cid == 7L) {
                return true;
            }
        }
        if (currentCategory != null) {
            Long parentId = currentCategory.getParentCategoryId();
            if (parentId != null && parentId == 7L) {
                return true;
            }
            String catName = nvl(currentCategory.getName()).toLowerCase(Locale.ROOT);
            if (catName.contains("hydration") || catName.contains("water")) {
                return true;
            }
            if (catName.contains("sleep") || catName.contains("exercise")
                    || catName.contains("nutrition") || catName.contains("habit")) {
                return false;
            }
        }
        String combined = (nvl(r.getTitle()) + " " + nvl(r.getDescription()) + " "
                + (currentCategory != null ? currentCategory.getName() : "")).toLowerCase(Locale.ROOT);
        return combined.contains("hydration") || combined.contains("water intake");
    }

    private void renderExerciseRecommendationDetail(List<Line> body, Recommendation r) {
        if (r == null) return;
        User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
        Goal goal = ctx != null && ctx.recommendationController != null ? ctx.recommendationController.currentGoal().orElse(null) : null;
        renderExerciseRecommendationDetail(body, r, user, goal, width);
    }

    public static void renderExerciseRecommendationDetail(List<Line> body, Recommendation r, User user, Goal goal, int outerWidth) {
        if (r == null) return;

        // [ DESCRIPTION ]
        if (r.getDescription() != null && !r.getDescription().isBlank()) {
            body.add(Line.blank());
            body.add(Line.of(Theme.headingPurple(), "  [ DESCRIPTION ]"));
            body.addAll(ScreenKit.paragraph(r.getDescription(), outerWidth - 2));
        }

        // Determine if goal prioritizes resistance/strength
        String goalName = (goal != null && goal.getName() != null) ? goal.getName().toLowerCase(Locale.ROOT) : "";
        boolean resistancePrimary = goalName.contains("muscle") || goalName.contains("strength") || goalName.contains("weight loss");
        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        // Card 1 (Top Box): 🏃 RECOMMENDED EXERCISE TYPES
        List<String> card1Lines;
        if (resistancePrimary) {
            String resFocus;
            String aerBase;
            String mobility;
            if (age >= 50) {
                resFocus = "Controlled resistance (8–15 reps, 2–3s eccentrics), machines & dumbbells";
                aerBase  = "Joint-friendly low-impact cardio (brisk walking, cycling, swimming)";
                mobility = "Dedicated 10–15 min dynamic joint warm-up & mobility prep";
            } else if (age < 35) {
                resFocus = "Progressive compound loading (6–10 reps), barbell & dumbbell free weights";
                aerBase  = "Brisk walking, incline treadmill, or interval conditioning";
                mobility = "Dynamic stretching & core stabilization (5–10 mins)";
            } else {
                resFocus = "Bodyweight squats, push-ups, light dumbbells";
                aerBase  = "Brisk walking, incline treadmill, or cycling";
                mobility = "Dynamic stretching & core stabilization";
            }
            card1Lines = List.of(
                    "  • " + Theme.padRight("Resistance Focus", 17) + ": " + resFocus,
                    "  • " + Theme.padRight("Aerobic Base", 17) + ": " + aerBase,
                    "  • " + Theme.padRight("Active Mobility", 17) + ": " + mobility
            );
        } else {
            String aerBase;
            String resFocus;
            String mobility;
            if (age >= 50) {
                aerBase  = "Joint-friendly low-impact cardio (brisk walking, cycling, swimming)";
                resFocus = "Controlled resistance (8–15 reps), joint-friendly functional movements";
                mobility = "Dedicated 10–15 min dynamic joint mobility & balance drills";
            } else if (age < 35) {
                aerBase  = "Brisk walking, incline treadmill, or interval conditioning";
                resFocus = "Compound resistance movements, progressive overload";
                mobility = "Dynamic stretching & core stabilization";
            } else {
                aerBase  = "Brisk walking, incline treadmill, or cycling";
                resFocus = "Bodyweight squats, push-ups, light dumbbells";
                mobility = "Dynamic stretching & core stabilization";
            }
            card1Lines = List.of(
                    "  • " + Theme.padRight("Aerobic Base", 17) + ": " + aerBase,
                    "  • " + Theme.padRight("Resistance Focus", 17) + ": " + resFocus,
                    "  • " + Theme.padRight("Active Mobility", 17) + ": " + mobility
            );
        }
        body.add(Line.blank());
        renderBoxCard(body, "🏃 RECOMMENDED EXERCISE TYPES", card1Lines, outerWidth);

        body.add(Line.blank());

        // Card 2 (Bottom Box): ⏱️ TRAINING PARAMETERS
        String duration = deriveSessionDurationStatic(r);
        String intensity = deriveIntensityZoneStatic(user);
        String frequency = (age >= 50) ? "3 sessions / week (dedicated recovery)" : "3–4 sessions / week";
        String progression;
        if (age >= 50) {
            progression = "Controlled eccentrics, 8–15 reps, progression every 2–3 weeks";
        } else if (age < 35) {
            progression = "Progressive overload: +1–2 reps or slight load increase weekly";
        } else {
            progression = "Steady progression every 1–2 weeks with strict form";
        }

        List<String> card2Lines = List.of(
                "  • " + Theme.padRight("Target Frequency", 17) + ": " + frequency,
                "  • " + Theme.padRight("Session Duration", 17) + ": " + duration,
                "  • " + Theme.padRight("Intensity Zone", 17) + ": " + intensity,
                "  • " + Theme.padRight("Progression Style", 17) + ": " + progression
        );
        renderBoxCard(body, "⏱️ TRAINING PARAMETERS", card2Lines, outerWidth);

        // [ IMPORTANT NOTES ]
        if (r.getImportantNotes() != null && !r.getImportantNotes().isBlank()) {
            body.add(Line.blank());
            body.add(Line.of(Theme.headingPurple(), "  [ IMPORTANT NOTES ]"));
            body.addAll(ScreenKit.paragraph(r.getImportantNotes(), outerWidth - 2));
        }
    }

    private void renderSleepRecommendationDetail(List<Line> body, Recommendation r) {
        User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
        Goal goal = ctx != null && ctx.recommendationController != null ? ctx.recommendationController.currentGoal().orElse(null) : null;
        renderSleepRecommendationDetail(body, r, user, goal, width);
    }

    public static void renderSleepRecommendationDetail(List<Line> body, Recommendation r, User user, Goal goal, int outerWidth) {
        if (r == null) return;

        // [ DESCRIPTION ]
        if (r.getDescription() != null && !r.getDescription().isBlank()) {
            body.add(Line.blank());
            body.add(Line.of(Theme.headingPurple(), "  [ DESCRIPTION ]"));
            body.addAll(ScreenKit.paragraph(r.getDescription(), outerWidth - 2));
        }

        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        // Card 1 (Top Box): 🌙 SLEEP HYGIENE
        String screens = (age >= 50)
                ? "Cut screens & dim overhead lights 60 mins before bed"
                : "Cut blue light and digital screens 45–60 mins prior to bedtime";
        String windDown = (age >= 50)
                ? "45–60 mins low-stimulation habit (reading, breathwork) to support slow-wave sleep"
                : "20–30 mins low-stimulation habit (reading or breathwork) for sleep onset";

        List<String> card1Lines = List.of(
                "  • " + Theme.padRight("Light & Screens", 18) + ": " + screens,
                "  • " + Theme.padRight("Sleep Environment", 18) + ": Keep bedroom cool (~18–20°C), dark, and quiet",
                "  • " + Theme.padRight("Wind-Down Routine", 18) + ": " + windDown
        );
        body.add(Line.blank());
        renderBoxCard(body, "🌙 SLEEP HYGIENE", card1Lines, outerWidth);

        body.add(Line.blank());

        // Card 2 (Bottom Box): 💪 RECOVERY & REST
        String duration = deriveSleepDurationStatic(r);
        String trainingRec = deriveTrainingRecoveryGuidance(user, goal, r);
        String eveningRec = (age >= 50)
                ? "Taper fluid intake 90–120 mins before bed and avoid stimulants for uninterrupted sleep"
                : "Taper fluid intake 90 mins before bed and avoid stimulants";

        List<String> card2Lines = List.of(
                "  • " + Theme.padRight("Sleep Schedule", 18) + ": Target " + duration + " with consistent wake times",
                "  • " + Theme.padRight("Training Recovery", 18) + ": " + trainingRec,
                "  • " + Theme.padRight("Evening Recovery", 18) + ": " + eveningRec
        );
        renderBoxCard(body, "💪 RECOVERY & REST", card2Lines, outerWidth);

        // [ IMPORTANT NOTES ]
        if (r.getImportantNotes() != null && !r.getImportantNotes().isBlank()) {
            body.add(Line.blank());
            body.add(Line.of(Theme.headingPurple(), "  [ IMPORTANT NOTES ]"));
            body.addAll(ScreenKit.paragraph(r.getImportantNotes(), outerWidth - 2));
        }
    }

    public static String deriveSleepDurationStatic(Recommendation r) {
        if (r == null || r.getSuggestedTarget() == null || r.getSuggestedTarget().isBlank()) {
            return "7–9 hours / night";
        }
        String target = r.getSuggestedTarget().trim();
        String lower = target.toLowerCase(Locale.ROOT);
        if (lower.contains("7-9") || lower.contains("7–9")) {
            return "7–9 hours / night";
        }
        if (lower.contains("hours") || lower.contains("hrs")) {
            if (target.contains("per night")) {
                return target.replace("per night", "/ night").replace("-", "–");
            }
            return target;
        }
        return "7–9 hours / night";
    }

    public static String deriveTrainingRecoveryGuidance(User user, Goal goal, Recommendation r) {
        String goalName = (goal != null && goal.getName() != null) ? goal.getName().toLowerCase(Locale.ROOT) : "";
        ActivityLevel act = (user != null) ? user.getActivityLevel() : null;
        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        if (goalName.contains("muscle") || goalName.contains("strength")) {
            if (age >= 50) {
                return "Allow 48–72 hours of recovery between intense training sessions";
            }
            return "Allow 48 hours of recovery between intense training sessions";
        }
        if (act == ActivityLevel.VERY_ACTIVE || act == ActivityLevel.EXTRA_ACTIVE) {
            return (age >= 50)
                    ? "Incorporate 2 dedicated active recovery days per week"
                    : "Incorporate 1–2 dedicated active recovery days per week";
        }
        if (act == ActivityLevel.SEDENTARY || act == ActivityLevel.LIGHTLY_ACTIVE) {
            return "Prioritize gentle mobility or light walking on rest days";
        }
        return "Balance active training with structured rest intervals";
    }

    private void renderHydrationRecommendationDetail(List<Line> body, Recommendation r) {
        User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
        renderHydrationRecommendationDetail(body, r, user, width);
    }

    public static void renderHydrationRecommendationDetail(List<Line> body, Recommendation r, int width) {
        renderHydrationRecommendationDetail(body, r, null, width);
    }

    public static void renderHydrationRecommendationDetail(List<Line> body, Recommendation r, User user, int outerWidth) {
        if (r == null) return;

        // [ DESCRIPTION ]
        if (r.getDescription() != null && !r.getDescription().isBlank()) {
            body.add(Line.blank());
            body.add(Line.of(Theme.headingPurple(), "  [ DESCRIPTION ]"));
            body.addAll(ScreenKit.paragraph(r.getDescription(), outerWidth - 2));
        }

        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        // Card 1 (Top Box): ⏱️ HYDRATION TIMING PROTOCOL
        List<String> card1Lines;
        if (age >= 50) {
            card1Lines = List.of(
                    "  • " + Theme.padRight("Clock Schedule", 18) + ": Drink on a scheduled hourly cadence (thirst cues naturally decline with age)",
                    "  • " + Theme.padRight("Joint Hydration", 18) + ": Steady daytime fluid distribution cushions joints and spinal discs",
                    "  • " + Theme.padRight("Evening Taper", 18) + ": Taper fluid intake 2 hours before bed for undisturbed sleep"
            );
        } else if (age < 35) {
            card1Lines = List.of(
                    "  • " + Theme.padRight("Morning Kickstart", 18) + ": 500 mL upon waking to rehydrate cellular systems",
                    "  • " + Theme.padRight("Workout Hydration", 18) + ": Drink 400–500 mL pre-workout and sip to support muscular power",
                    "  • " + Theme.padRight("Evening Taper", 18) + ": Reduce large fluid boluses 90 mins prior to bed"
            );
        } else {
            card1Lines = List.of(
                    "  • " + Theme.padRight("Morning Kickstart", 18) + ": 500 mL upon waking to rehydrate cellular systems",
                    "  • " + Theme.padRight("Daytime Cadence", 18) + ": 250–300 mL per waking hour during peak activity",
                    "  • " + Theme.padRight("Evening Taper", 18) + ": Reduce large fluid boluses 90 mins prior to bed"
            );
        }
        body.add(Line.blank());
        renderBoxCard(body, "⏱️ HYDRATION TIMING PROTOCOL", card1Lines, outerWidth);

        body.add(Line.blank());

        // Card 2 (Bottom Box): 📊 INTAKE & ELECTROLYTE PARAMETERS
        String targetVol = deriveHydrationTargetStatic(r);
        String activityAdj;
        if (age >= 50) {
            activityAdj = "+300–400 mL per 30 mins of moderate physical exertion; avoid sudden large boluses";
        } else if (age < 35) {
            activityAdj = "+400–500 mL per 30 mins of intense physical exertion; prompt rehydration";
        } else {
            activityAdj = "+350–500 mL per 30 mins of moderate physical exertion";
        }

        List<String> card2Lines = List.of(
                "  • " + Theme.padRight("Target Daily Volume", 22) + ": " + targetVol,
                "  • " + Theme.padRight("Activity Adjustment", 22) + ": " + activityAdj,
                "  • " + Theme.padRight("Electrolyte Balance", 22) + ": Maintain sodium/potassium balance during heat or activity"
        );
        renderBoxCard(body, "📊 INTAKE & ELECTROLYTE PARAMETERS", card2Lines, outerWidth);

        // [ IMPORTANT NOTES ]
        if (r.getImportantNotes() != null && !r.getImportantNotes().isBlank()) {
            body.add(Line.blank());
            body.add(Line.of(Theme.headingPurple(), "  [ IMPORTANT NOTES ]"));
            body.addAll(ScreenKit.paragraph(r.getImportantNotes(), outerWidth - 2));
        }
    }

    public static boolean isHydrationRecommendationStatic(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle()))) {
            return false;
        }
        if (r.getCategoryId() != null) {
            long cid = r.getCategoryId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 2L || cid == 3L || cid == 4L || cid == 5L) {
                return false;
            }
            if (cid == 7L) {
                return true;
            }
        }
        String combined = (nvl(r.getTitle()) + " " + nvl(r.getDescription())).toLowerCase(Locale.ROOT);
        return combined.contains("hydration") || combined.contains("water intake");
    }

    public static boolean isHabitsRecommendationStatic(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle()))) {
            return false;
        }
        if (r.getCategoryId() != null) {
            long cid = r.getCategoryId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 2L || cid == 3L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 4L) {
                return true;
            }
        }
        String combined = (nvl(r.getTitle()) + " " + nvl(r.getDescription())).toLowerCase(Locale.ROOT);
        return combined.contains("micro-habit") || combined.contains("micro habit")
                || combined.contains("daily habit") || combined.contains("habits");
    }

    private boolean isHabitsRecommendation(Recommendation r) {
        if (r == null) return false;
        if (isMasterRoutine(nvl(r.getTitle())) || (currentCategory != null && isMasterRoutine(nvl(currentCategory.getName())))) {
            return false;
        }
        if (r.getCategoryId() != null) {
            long cid = r.getCategoryId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 2L || cid == 3L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 4L) {
                return true;
            }
        }
        if (currentCategory != null && currentCategory.getId() != null) {
            long cid = currentCategory.getId();
            if (cid == 1L || (cid >= 8L && cid <= 14L) || cid == 2L || cid == 3L || cid == 5L || cid == 7L) {
                return false;
            }
            if (cid == 4L) {
                return true;
            }
        }
        if (currentCategory != null) {
            Long parentId = currentCategory.getParentCategoryId();
            if (parentId != null && parentId == 4L) {
                return true;
            }
            String catName = nvl(currentCategory.getName()).toLowerCase(Locale.ROOT);
            if (catName.contains("habit") || catName.contains("micro-habit") || catName.contains("micro habit")) {
                return true;
            }
            if (catName.contains("hydration") || catName.contains("water")
                    || catName.contains("sleep") || catName.contains("exercise")
                    || catName.contains("nutrition")) {
                return false;
            }
        }
        String combined = (nvl(r.getTitle()) + " " + nvl(r.getDescription()) + " "
                + (currentCategory != null ? currentCategory.getName() : "")).toLowerCase(Locale.ROOT);
        return combined.contains("micro-habit") || combined.contains("micro habit")
                || combined.contains("daily habit") || combined.contains("daily micro-habits")
                || combined.contains("habits");
    }

    private void renderHabitsRecommendationDetail(List<Line> body, Recommendation r) {
        User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
        renderHabitsRecommendationDetail(body, r, user, width);
    }

    public static void renderHabitsRecommendationDetail(List<Line> body, Recommendation r, int width) {
        renderHabitsRecommendationDetail(body, r, null, width);
    }

    public static void renderHabitsRecommendationDetail(List<Line> body, Recommendation r, User user, int outerWidth) {
        if (r == null) return;

        // [ DESCRIPTION ]
        body.add(Line.blank());
        body.add(Line.of(Theme.headingPurple(), "  [ DESCRIPTION ]"));
        String desc = (r.getDescription() != null && !r.getDescription().isBlank())
                ? r.getDescription()
                : "Small daily habits that compound over time.";
        body.addAll(ScreenKit.paragraph(desc, outerWidth - 2));

        body.add(Line.blank());

        int age = (user != null && user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;

        // Card 1 (Top Box): 🌱 HIGH-LEVERAGE DAILY HABITS
        List<String> card1Lines;
        if (age >= 50) {
            card1Lines = List.of(
                    "  • " + Theme.padRight("Morning Mobility", 19) + ": 5-minute morning mobility routine to lubricate joints and spine",
                    "  • " + Theme.padRight("Protein Distribution", 19) + ": Distribute 35–40g+ protein across meals with calcium & vitamin D",
                    "  • " + Theme.padRight("Post-Workout Reset", 19) + ": 5–10 minutes of gentle spinal decompression or stretching"
            );
        } else if (age < 35) {
            card1Lines = List.of(
                    "  • " + Theme.padRight("Protein Anchor", 19) + ": Pre-portion high-protein snacks or shakes ahead of time",
                    "  • " + Theme.padRight("Movement Prep", 19) + ": Stage workout gear and water bottle the night before",
                    "  • " + Theme.padRight("Post-Meal Walk", 19) + ": 10-minute walk after largest meal to support glucose disposal"
            );
        } else {
            card1Lines = List.of(
                    "  • " + Theme.padRight("Protein Anchor", 19) + ": Pre-portion protein sources at breakfast & lunch",
                    "  • " + Theme.padRight("Movement Prep", 19) + ": Stage training apparel & gear the night prior",
                    "  • " + Theme.padRight("Posture / Bracing", 19) + ": 2-minute core & posture reset every 2 hours sit"
            );
        }
        renderBoxCard(body, "🌱 HIGH-LEVERAGE DAILY HABITS", card1Lines, outerWidth);

        body.add(Line.blank());

        // Card 2 (Bottom Box): ⚡ HABIT ANCHORING & TIMING
        List<String> card2Lines;
        if (age >= 50) {
            card2Lines = List.of(
                    "  • " + Theme.padRight("Hourly Hydration", 20) + ": Keep water bottle visible; drink on proactive hourly schedule",
                    "  • " + Theme.padRight("Joint Protection", 20) + ": Take 2-minute posture & joint reset every 90 minutes seated",
                    "  • " + Theme.padRight("Evening Shutdown", 20) + ": Dim lights 60 mins before bed and taper liquids for deep sleep"
            );
        } else if (age < 35) {
            card2Lines = List.of(
                    "  • " + Theme.padRight("Friction Reduction", 20) + ": Keep hydration bottle visible on workstation",
                    "  • " + Theme.padRight("Habit Loop Trigger", 20) + ": Pair post-workout nutrition directly after training session",
                    "  • " + Theme.padRight("Digital Curfew", 20) + ": Set digital screen curfew 45 minutes before sleep"
            );
        } else {
            card2Lines = List.of(
                    "  • " + Theme.padRight("Friction Reduction", 20) + ": Keep hydration bottle visible on workstation",
                    "  • " + Theme.padRight("Habit Loop Trigger", 20) + ": Pair post-workout shake directly after training",
                    "  • " + Theme.padRight("Recovery Shutdown", 20) + ": Set static digital curfew 45 mins before sleep"
            );
        }
        renderBoxCard(body, "⚡ HABIT ANCHORING & TIMING", card2Lines, outerWidth);

        // [ IMPORTANT NOTES ]
        body.add(Line.blank());
        body.add(Line.of(Theme.headingPurple(), "  [ IMPORTANT NOTES ]"));
        String notes = (r.getImportantNotes() != null && !r.getImportantNotes().isBlank())
                ? r.getImportantNotes()
                : "These are suggestions to try, not a checklist to complete daily.";
        body.addAll(ScreenKit.paragraph(notes, outerWidth - 2));
    }

    public static void renderMasterRoutinePage1(List<Line> body, Recommendation r, int width) {
        // [ DESCRIPTION ]
        body.add(Line.blank());
        body.add(Line.of(Theme.headingPurple(), "  [ DESCRIPTION ]"));
        String desc = (r != null && r.getDescription() != null && !r.getDescription().isBlank())
                ? r.getDescription()
                : "A unified lifestyle routine designed to support sustainable progress across all core health areas.";
        body.addAll(ScreenKit.paragraph(desc, width - 2));

        body.add(Line.blank());

        // Nested card: 📋 UNIFIED LIFESTYLE PILLARS
        List<String> pillarLines = deriveMasterPillarLines(r);
        renderBoxCard(body, "📋 UNIFIED LIFESTYLE PILLARS", pillarLines, width);

        // [ IMPORTANT NOTES ]
        body.add(Line.blank());
        body.add(Line.of(Theme.headingPurple(), "  [ IMPORTANT NOTES ]"));
        String notes = (r != null && r.getImportantNotes() != null && !r.getImportantNotes().isBlank())
                ? r.getImportantNotes()
                : "Consistency across foundational habits produces greater long-term results than short-term extremes.";
        body.addAll(ScreenKit.paragraph(notes, width - 2));
    }

    public static List<String> deriveMasterPillarLines(Recommendation r) {
        if (r != null && r.getRecommendedActions() != null && !r.getRecommendedActions().isBlank()) {
            String actions = r.getRecommendedActions();
            String nut = extractMasterSection(actions, "Nutrition", "Prioritize balanced meals and consistent portions aligned with your target intake.");
            String hyd = extractMasterSection(actions, "Hydration", "Maintain steady fluid intake distributed evenly throughout active daytime hours.");
            String exe = extractMasterSection(actions, "Exercise", "Complete scheduled training sessions focusing on sustainable form and steady progression.");
            String rec = extractMasterSection(actions, "Sleep & Recovery", "Protect your nightly sleep window to restore energy and support tissue adaptation.");
            String hab = extractMasterSection(actions, "Daily Micro-Habits", "Anchor small, low-friction routines to maintain consistency without relying solely on motivation.");

            return List.of(
                    "  • " + Theme.padRight("Nutrition", 14) + ": " + nut,
                    "  • " + Theme.padRight("Hydration", 14) + ": " + hyd,
                    "  • " + Theme.padRight("Exercise", 14) + ": " + exe,
                    "  • " + Theme.padRight("Recovery", 14) + ": " + rec,
                    "  • " + Theme.padRight("Daily Habits", 14) + ": " + hab
            );
        }
        return List.of(
                "  • " + Theme.padRight("Nutrition", 14) + ": Prioritize balanced meals and consistent portions aligned with your target intake.",
                "  • " + Theme.padRight("Hydration", 14) + ": Maintain steady fluid intake distributed evenly throughout active daytime hours.",
                "  • " + Theme.padRight("Exercise", 14) + ": Complete scheduled training sessions focusing on sustainable form and steady progression.",
                "  • " + Theme.padRight("Recovery", 14) + ": Protect your nightly sleep window to restore energy and support tissue adaptation.",
                "  • " + Theme.padRight("Daily Habits", 14) + ": Anchor small, low-friction routines to maintain consistency without relying solely on motivation."
        );
    }

    private static String extractMasterSection(String text, String header, String defaultVal) {
        int idx = text.indexOf(header);
        if (idx == -1) return defaultVal;
        int bulletIdx = text.indexOf("•", idx);
        if (bulletIdx == -1) return defaultVal;
        int nextSection = text.indexOf("\n\n", bulletIdx);
        String section;
        if (nextSection != -1) {
            section = text.substring(bulletIdx + 1, nextSection).trim();
        } else {
            section = text.substring(bulletIdx + 1).trim();
        }
        section = section.replaceAll("\\r?\\n\\s*•\\s*", " | ").replaceAll("\\r?\\n\\s*", " ").trim();
        return section.isEmpty() ? defaultVal : section;
    }

    public static void renderMasterRoutinePage2(List<Line> body, Recommendation r, int width) {
        body.add(Line.blank());

        // Nested card: 🌅 DAILY LIFESTYLE GUIDANCE
        List<String> rhythmLines = defaultMasterRhythmLines();
        renderBoxCard(body, "🌅 DAILY LIFESTYLE GUIDANCE", rhythmLines, width);

        body.add(Line.blank());
        body.addAll(ScreenKit.paragraph("This rhythm provides a flexible structure to guide your day sustainably.", width - 2));
    }

    private static List<String> defaultMasterRhythmLines() {
        return List.of(
                "  • " + Theme.padRight("Morning", 10) + ": Start the day with hydration and a balanced meal.",
                "  • " + Theme.padRight("Daytime", 10) + ": Maintain regular movement and follow the recommended routine.",
                "  • " + Theme.padRight("Evening", 10) + ": Follow a balanced dinner routine and prepare for the next day.",
                "  • " + Theme.padRight("Night", 10) + ": Reduce stimulating activities and maintain a consistent sleep routine."
        );
    }

    public static void renderNestedCard(List<Line> body, String title, List<String> lines, int outerWidth) {
        renderBoxCard(body, title, lines, outerWidth);
    }

    public static void renderBoxCard(List<Line> body, String title, List<String> lines, int outerWidth) {
        int termWidth = Math.min(100, Math.max(40, outerWidth));
        int cardWidth = Math.max(30, termWidth - 8);
        int cardInner = cardWidth - 2;

        // Top border: "  ┌─ " + Title + " ─...─┐"
        int titleW = Theme.width(title);
        int dashCount = Math.max(0, cardWidth - 5 - titleW);
        String topBorder = "  ┌─ " + title + " " + "─".repeat(dashCount) + "┐";
        body.add(Line.of(Theme.bar(), topBorder));

        // Blank row inside card
        body.add(formatCardRow("", cardInner));

        // Content rows with indented word wrapping
        if (lines != null) {
            for (String rawLine : lines) {
                if (rawLine == null) continue;
                String[] subLines = rawLine.split("\\r?\\n");
                for (String line : subLines) {
                    List<String> wrappedLines = wrapCardLine(line, cardWidth);
                    for (String wLine : wrappedLines) {
                        body.add(formatCardRow(wLine, cardInner));
                    }
                }
            }
        }

        // Blank row inside card
        body.add(formatCardRow("", cardInner));

        // Bottom border: "  └" + "─...─" + "┘"
        String bottomBorder = "  └" + "─".repeat(cardWidth - 2) + "┘";
        body.add(Line.of(Theme.bar(), bottomBorder));
    }

    public static List<String> wrapCardLine(String line, int cardWidth) {
        if (line == null || line.isEmpty()) {
            return List.of("");
        }

        int maxContentWidth = Math.max(20, cardWidth - 4);
        int colonIdx = line.indexOf(": ");

        String prefixLine1;
        String prefixIndent;
        String text;

        if (colonIdx != -1) {
            prefixLine1 = line.substring(0, colonIdx + 2);
            int prefixW = Theme.width(prefixLine1);
            prefixIndent = " ".repeat(prefixW);
            text = line.substring(colonIdx + 2).trim();
        } else if (line.startsWith("  • ")) {
            prefixLine1 = "  • ";
            prefixIndent = "    ";
            text = line.substring(4).trim();
        } else if (line.startsWith("• ")) {
            prefixLine1 = "• ";
            prefixIndent = "  ";
            text = line.substring(2).trim();
        } else {
            int leading = 0;
            while (leading < line.length() && line.charAt(leading) == ' ') {
                leading++;
            }
            prefixLine1 = line.substring(0, leading);
            prefixIndent = prefixLine1;
            text = line.trim();
        }

        int prefixW = Theme.width(prefixLine1);
        int availWidth = Math.max(10, maxContentWidth - prefixW);

        if (Theme.width(text) <= availWidth) {
            return List.of(prefixLine1 + text);
        }

        List<String> textLines = wrapTextIntoLines(text, availWidth);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < textLines.size(); i++) {
            if (i == 0) {
                result.add(prefixLine1 + textLines.get(i));
            } else {
                result.add(prefixIndent + textLines.get(i));
            }
        }
        return result;
    }

    public static List<String> wrapTextIntoLines(String text, int availWidth) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        if (Theme.width(text) <= availWidth) {
            return List.of(text);
        }

        List<Integer> spaceIndices = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                spaceIndices.add(i);
            }
        }

        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int idx : spaceIndices) {
            String l1 = text.substring(0, idx).trim();
            String l2 = text.substring(idx + 1).trim();
            if (l1.isEmpty() || l2.isEmpty()) continue;

            int w1 = Theme.width(l1);
            int w2 = Theme.width(l2);

            if (w1 <= availWidth && w2 <= availWidth) {
                int score = 1000;

                // Parentheses check: avoid breaking inside (...)
                int openCount = 0;
                for (int c = 0; c < l1.length(); c++) {
                    if (l1.charAt(c) == '(') openCount++;
                    else if (l1.charAt(c) == ')') openCount--;
                }
                if (openCount > 0) {
                    score -= 600;
                }
                if (l2.startsWith("(")) {
                    score += 250;
                }

                // Avoid single-word orphan line 2 (exempt parenthetical notes like (~18–20°C))
                if (!l2.contains(" ") && !l2.startsWith("(")) {
                    score -= 500;
                }

                // Cohesive natural phrases preservation
                if (l2.startsWith("rehydrate cellular systems") || l2.startsWith("moderate physical exertion")) {
                    score += 400;
                }
                if (l2.startsWith("prior to ") || l2.startsWith("during ") || l2.startsWith("before ") || l2.startsWith("within ")) {
                    score += 200;
                }

                // Connectors at end of line 1
                if (l1.endsWith(" of") || l1.endsWith(" to")) {
                    score += 150;
                }

                // Balance penalty
                score -= Math.abs(w1 - w2) * 2;

                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = idx;
                }
            }
        }

        if (bestIndex != -1) {
            String l1 = text.substring(0, bestIndex).trim();
            String l2 = text.substring(bestIndex + 1).trim();
            return List.of(l1, l2);
        }

        // Fallback for > 2 lines or long text: pick the best l1 <= availWidth
        int bestL1Index = -1;
        int bestL1Score = Integer.MIN_VALUE;

        for (int idx : spaceIndices) {
            String l1 = text.substring(0, idx).trim();
            String l2 = text.substring(idx + 1).trim();
            int w1 = Theme.width(l1);
            if (w1 <= availWidth) {
                int score = w1 * 10;

                int openCount = 0;
                for (int c = 0; c < l1.length(); c++) {
                    if (l1.charAt(c) == '(') openCount++;
                    else if (l1.charAt(c) == ')') openCount--;
                }
                if (openCount > 0) {
                    score -= 600;
                }
                if (l2.startsWith("(")) {
                    score += 250;
                }
                if (l2.startsWith("rehydrate cellular systems") || l2.startsWith("moderate physical exertion")) {
                    score += 400;
                }
                if (l2.startsWith("prior to ") || l2.startsWith("during ") || l2.startsWith("before ") || l2.startsWith("within ")) {
                    score += 200;
                }

                if (score > bestL1Score) {
                    bestL1Score = score;
                    bestL1Index = idx;
                }
            }
        }

        if (bestL1Index != -1) {
            String l1 = text.substring(0, bestL1Index).trim();
            String remainder = text.substring(bestL1Index + 1).trim();
            List<String> res = new ArrayList<>();
            res.add(l1);
            res.addAll(wrapTextIntoLines(remainder, availWidth));
            return res;
        }

        // Single word longer than availWidth: force slice
        List<String> res = new ArrayList<>();
        while (Theme.width(text) > availWidth && text.length() > 1) {
            int cut = 1;
            while (cut < text.length() && Theme.width(text.substring(0, cut + 1)) <= availWidth) {
                cut++;
            }
            res.add(text.substring(0, cut));
            text = text.substring(cut).trim();
        }
        if (!text.isEmpty()) {
            res.add(text);
        }
        return res;
    }

    public static Line formatCardRow(String content, int cardInner) {
        return formatCardRow(content, cardInner, Theme.plain());
    }

    public static Line formatCardRow(String content, int cardInner, Style style) {
        String safe = content == null ? "" : content;
        if (safe.contains("\n") || safe.contains("\r")) {
            safe = safe.replaceAll("\\r?\\n+", " ");
        }
        if (Theme.width(safe) > cardInner) {
            safe = Theme.truncate(safe, cardInner);
        }
        int pad = Math.max(0, cardInner - Theme.width(safe));
        String row = "  │" + safe + " ".repeat(pad) + "│";
        return Line.of(style == null ? Theme.plain() : style, row);
    }

    private String deriveSessionDuration(Recommendation r) {
        return deriveSessionDurationStatic(r);
    }

    public static String deriveSessionDurationStatic(Recommendation r) {
        if (r == null || r.getSuggestedTarget() == null || r.getSuggestedTarget().isBlank()) {
            return "30–40 minutes / day";
        }
        String target = r.getSuggestedTarget().trim();
        String lower = target.toLowerCase(Locale.ROOT);
        if (lower.contains("30") && lower.contains("40")) {
            return "30–40 minutes / day";
        }
        if (lower.contains("150 min")) {
            return "30–40 minutes / day";
        }
        if (lower.contains("strength") || lower.contains("resistance")) {
            return "45–50 minutes / day";
        }
        if (lower.contains("min") || lower.contains("hour")) {
            return target;
        }
        return "30–40 minutes / day";
    }

    private String deriveSleepDuration(Recommendation r) {
        return deriveSleepDurationStatic(r);
    }

    private String deriveHydrationTarget(Recommendation r) {
        if (result != null && result.suggestedHydrationLiters > 0) {
            return String.format("%.1f L/day", result.suggestedHydrationLiters);
        }
        return deriveHydrationTargetStatic(r);
    }

    public static String deriveHydrationTargetStatic(Recommendation r) {
        if (r != null && r.getSuggestedTarget() != null && !r.getSuggestedTarget().isBlank()) {
            String target = r.getSuggestedTarget().trim();
            if (target.contains("L/day") || target.contains("L") || target.contains("liters")) {
                return target;
            }
        }
        return "2.5–3.0 L/day";
    }

    private String deriveIntensityZone(User user) {
        return deriveIntensityZoneStatic(user);
    }

    public static String deriveIntensityZoneStatic(User user) {
        if (user == null || user.getActivityLevel() == null) {
            return "Moderate (RPE 6–7 / conversational pace)";
        }
        int age = (user.getAge() != null && user.getAge() > 0) ? user.getAge() : 30;
        if (age >= 50) {
            return switch (user.getActivityLevel()) {
                case SEDENTARY, LIGHTLY_ACTIVE -> "Moderate (RPE 5–6 / controlled tempo, joint-friendly)";
                case MODERATELY_ACTIVE -> "Moderate (RPE 6–7 / controlled tempo, joint-friendly)";
                case VERY_ACTIVE, EXTRA_ACTIVE -> "Moderate (RPE 6–7.5 / controlled cadence, joint-friendly)";
            };
        } else if (age < 35) {
            return switch (user.getActivityLevel()) {
                case SEDENTARY, LIGHTLY_ACTIVE -> "Moderate (RPE 6–7 / conversational pace)";
                case MODERATELY_ACTIVE -> "Moderate-to-High (RPE 7–8.5 / progressive overload)";
                case VERY_ACTIVE, EXTRA_ACTIVE -> "High (RPE 8–9 / high-performance overload)";
            };
        }
        return switch (user.getActivityLevel()) {
            case SEDENTARY, LIGHTLY_ACTIVE -> "Moderate (RPE 6–7 / conversational pace)";
            case MODERATELY_ACTIVE -> "Moderate (RPE 6–7 / conversational pace)";
            case VERY_ACTIVE, EXTRA_ACTIVE -> "Moderate-to-High (RPE 7–8 / tempo pace)";
        };
    }

    private void renderRecommendationContent(List<Line> body, Recommendation r) {
        if (r == null) return;
        User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
        Goal goal = ctx != null && ctx.recommendationController != null ? ctx.recommendationController.currentGoal().orElse(null) : null;

        if (isNutritionRecommendation(r)) {
            renderNutritionRecommendationDetail(body, r, user, width);
            return;
        }

        if (isExerciseRecommendation(r)) {
            body.add(Line.blank());
            renderExerciseRecommendationDetail(body, r, user, goal, width);
            return;
        }

        if (isSleepRecommendation(r)) {
            body.add(Line.blank());
            renderSleepRecommendationDetail(body, r, user, goal, width);
            return;
        }

        if (isHydrationRecommendation(r)) {
            body.add(Line.blank());
            renderHydrationRecommendationDetail(body, r, user, width);
            return;
        }

        if (isHabitsRecommendation(r)) {
            renderHabitsRecommendationDetail(body, r, user, width);
            return;
        }

        addSection(body, "Description", r.getDescription());
        addSection(body, "Recommended Actions", r.getRecommendedActions());
        addSection(body, "Suggested Target", r.getSuggestedTarget());
        addSection(body, "Important Notes", r.getImportantNotes());
    }

    private String viewRecommendDetail(List<Line> body) {
        if (result == null || result.recommendation == null) {
            body.add(Line.of(Theme.warn(), "  No recommendation loaded."));
            return "No recommendation loaded";
        }
        Recommendation r = result.recommendation;
        String catEmoji = currentCategory != null ? getCategoryEmoji(currentCategory.getName()) : "\uD83C\uDFAF";

        if (isCurrentMasterRoutine()) {
            int totalPages = 2;
            if (recommendDetailPage < 0) recommendDetailPage = 0;
            if (recommendDetailPage >= totalPages) recommendDetailPage = totalPages - 1;

            body.add(Line.of(Theme.headingCyan(), "  ⭐ COMPLETE MASTER ROUTINE"));
            body.add(Line.of(Theme.dim(), "  Page " + (recommendDetailPage + 1) + " / " + totalPages + "  (Use \u2190 / \u2192 to flip pages)"));

            if (recommendDetailPage == 0) {
                renderMasterRoutinePage1(body, r, width);
            } else {
                renderMasterRoutinePage2(body, r, width);
            }
        } else if (isNutritionRecommendation(r)) {
            // Single-page Nutrition Recommendation Detail without duplicate estimated targets
            body.add(Line.of(Theme.headingCyan(), "  " + catEmoji + " " + nvl(r.getTitle()).toUpperCase(Locale.ROOT)));
            User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
            renderNutritionRecommendationDetail(body, r, user, width);
        } else if (isExerciseRecommendation(r)) {
            // 2-card nested container layout for Exercise (no duplicate calorie/BMR/TDEE/Hydration targets)
            body.add(Line.blank());
            User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
            Goal goal = ctx != null && ctx.recommendationController != null ? ctx.recommendationController.currentGoal().orElse(null) : null;
            renderExerciseRecommendationDetail(body, r, user, goal, width);
        } else if (isSleepRecommendation(r)) {
            // 2-card nested container layout for Sleep & Recovery (no duplicate calorie/BMR/TDEE/Hydration targets)
            body.add(Line.blank());
            User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
            Goal goal = ctx != null && ctx.recommendationController != null ? ctx.recommendationController.currentGoal().orElse(null) : null;
            renderSleepRecommendationDetail(body, r, user, goal, width);
        } else if (isHydrationRecommendation(r)) {
            // 2-card nested container layout for Hydration Guidance (no duplicate calorie/BMR/TDEE/Hydration targets)
            body.add(Line.blank());
            User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
            renderHydrationRecommendationDetail(body, r, user, width);
        } else if (isHabitsRecommendation(r)) {
            // 2-card nested container layout for Daily Habits / Micro-Habits with Description and Important Notes
            String title = nvl(r.getTitle()).trim();
            String displayTitle = title.startsWith("🌱") ? title : "🌱 " + title;
            body.add(Line.of(Theme.headingCyan(), "  " + displayTitle.toUpperCase(Locale.ROOT)));
            User user = ctx != null && ctx.session != null ? ctx.session.getCurrentUser() : null;
            renderHabitsRecommendationDetail(body, r, user, width);
        } else {
            // Single-page for all other recommendation categories (Habits, etc.)
            body.add(Line.of(Theme.headingCyan(), "  " + catEmoji + " " + nvl(r.getTitle()).toUpperCase(Locale.ROOT)));
            body.add(Line.blank());

            addSection(body, "Description", r.getDescription());
            addSection(body, "Recommended Actions", r.getRecommendedActions());
            addSection(body, "Suggested Target", r.getSuggestedTarget());
            addSection(body, "Important Notes", r.getImportantNotes());

            body.add(Line.blank());
            body.add(ScreenKit.section("Personalized Guidance"));
            CalorieService.CalorieSummary cs = result.calorieSummary;
            if (result.calorieRelevant && cs != null) {
                body.add(ScreenKit.labelValue("BMR", String.format("%.0f kcal/day", cs.bmr)));
                body.add(ScreenKit.labelValue("TDEE", String.format("%.0f kcal/day", cs.tdee)));
                body.add(ScreenKit.labelValue("Calorie Target",
                        String.format("%.0f kcal/day", cs.suggestedTarget)));
            }
            body.add(ScreenKit.labelValue("Hydration Target",
                    String.format("%.1f L/day", result.suggestedHydrationLiters)));
        }

        body.add(Line.blank());
        body.addAll(menuLines());
        return "Specific actions calibrated for your profile";
    }

    private String getCategoryEmoji(String name) {
        if (name == null) return "\uD83C\uDFAF"; // 🎯
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("nutrition") || lower.contains("breakfast") || lower.contains("lunch")
                || lower.contains("dinner") || lower.contains("snack") || lower.contains("food")
                || lower.contains("portion")) return "\uD83C\uDF4E"; // 🍎
        if (lower.contains("exercise") || lower.contains("strength") || lower.contains("cardio")) return "\uD83C\uDFC3";  // 🏃
        if (lower.contains("hydration")) return "\uD83D\uDCA7"; // 💧
        if (lower.contains("sleep")) return "\uD83D\uDE34";     // 😴
        if (lower.contains("habit")) return "\uD83C\uDF31";     // 🌱
        if (lower.contains("master") || lower.contains("routine")) return "\u2B50"; // ⭐
        return "\uD83C\uDFAF"; // 🎯
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
        User user = ctx.session.getCurrentUser();
        Optional<Goal> goal = ctx.recommendationController.currentGoal();

        body.add(Line.of(Theme.headingPurple(), "  [ OFFICIAL LIFEForge RECOMMENDATION ]"));
        body.add(Line.of(Theme.dim(), "  Selected by the rule-based engine for your goal and activity level"));
        body.add(Line.blank());

        // Personal context comes from the actual logged-in user; it is not
        // generated by AI and does not alter the official database content.
        body.add(ScreenKit.section("Your Profile"));
        if (user != null) {
            body.add(ScreenKit.labelValue("Goal", goal.map(Goal::getName).orElse("-")));
            body.add(ScreenKit.labelValue("Age", String.valueOf(user.getAge())));
            body.add(ScreenKit.labelValue("Gender", human(user.getGender())));
            body.add(ScreenKit.labelValue("Height", String.format("%.0f cm", user.getHeightCm())));
            body.add(ScreenKit.labelValue("Weight", String.format("%.1f kg", user.getWeightKg())));
            body.add(ScreenKit.labelValue("Activity", human(user.getActivityLevel())));
        } else {
            body.add(ScreenKit.labelValue("Goal", goal.map(Goal::getName).orElse("-")));
        }

        body.add(Line.blank());
        body.add(ScreenKit.section("Personalized Focus"));
        body.addAll(ScreenKit.paragraph(
                personalizedFocus(goal.orElse(null), user, r),
                Math.max(30, width - 6)));

        // Reuse the existing deterministic calculations already attached to
        // RecommendationResult. Do not create a second calculation system.
        if (result.calorieRelevant && result.calorieSummary != null) {
            body.add(Line.blank());
            body.add(ScreenKit.section("Personalized Targets"));
            CalorieService.CalorieSummary cs = result.calorieSummary;
            body.add(ScreenKit.labelValue("BMR", String.format("%.0f kcal/day", cs.bmr)));
            body.add(ScreenKit.labelValue("TDEE", String.format("%.0f kcal/day", cs.tdee)));
            body.add(ScreenKit.labelValue("Calorie target", String.format("%.0f kcal/day", cs.suggestedTarget)));
        }

        if (result.suggestedHydrationLiters > 0) {
            body.add(ScreenKit.labelValue("Hydration target",
                    String.format("%.2f L/day", result.suggestedHydrationLiters)));
        }

        body.add(Line.blank());
        body.add(ScreenKit.hr(width - 2));
        body.add(Line.blank());

        body.add(Line.of(Theme.headingCyan(), "  " + nvl(r.getTitle())));
        renderRecommendationContent(body, r);

        body.add(Line.blank());
        body.add(Line.of(Theme.dim(),
                "  Official content comes from the LIFEForge recommendation catalog."));
        body.add(Line.of(Theme.dim(),
                "  Personal context above comes from your current profile and existing calculations."));

        body.add(Line.blank());
        body.addAll(menuLines());

        return "Personalized official recommendation — AI cannot change it";
    }

    private String personalizedFocus(Goal goal, User user, Recommendation recommendation) {
        if (goal != null) {
            return new com.lifeforge.service.RecommendationPriorityResolver().getPersonalizedFocusNarrative(goal, user);
        }
        String goalCode = goal == null || goal.getCode() == null
                ? ""
                : goal.getCode().toUpperCase(Locale.ROOT);

        String activity = user == null || user.getActivityLevel() == null
                ? "your current activity level"
                : human(user.getActivityLevel());

        return switch (goalCode) {
            case "LOSE_WEIGHT" ->
                    "Your plan focuses on sustainable weight reduction while matching "
                            + "your " + activity + " activity level.";
            case "GAIN_WEIGHT" ->
                    "Your plan focuses on healthy weight gain with nutrition and activity "
                            + "guidance appropriate for your " + activity + " activity level.";
            case "BUILD_MUSCLE" ->
                    "Your plan focuses on supporting muscle development with nutrition and "
                            + "activity guidance appropriate for your " + activity + " activity level.";
            case "IMPROVE_FITNESS" ->
                    "Your plan focuses on improving overall fitness while accounting for "
                            + "your " + activity + " activity level.";
            case "SKIN_HEALTH" ->
                    "Your plan focuses on lifestyle habits that support skin health while "
                            + "accounting for your current activity level.";
            case "IMPROVE_SLEEP" ->
                    "Your plan focuses on recovery and sleep-supportive habits suited to "
                            + "your current activity level.";
            case "GENERAL_WELLNESS" ->
                    "Your plan focuses on balanced lifestyle habits suited to your current "
                            + "profile and " + activity + " activity level.";
            case "POSTURE_CORRECTION" ->
                    "Your plan focuses on posture-supportive movement and daily habits "
                            + "appropriate for your current activity level.";
            default ->
                    "This recommendation was selected for your chosen goal and matched "
                            + "to your current activity level.";
        };
    }

    private String viewAiChat(List<Line> body) {
        User u = ctx.session.getCurrentUser();
        Optional<Goal> goal = ctx.recommendationController.currentGoal();
        boolean hasGoal = goal.isPresent();
        int availInner = width - 2;
        String indent = "  ";
        int boxW = Math.max(20, availInner - 4);

        if (isGlobalAiAssistant) {
            // =========================================================================
            // 1. DASHBOARD → 🤖 AI ASSISTANT (GENERAL / FREE ASSISTANT)
            // =========================================================================
            if (chatConversation.isEmpty()) {
                body.add(Line.of(Theme.headingPurple(), indent + "🤖 LIFEForge AI"));
                body.add(Line.blank());
                body.add(Line.of(Theme.headingCyan(), indent + "How can I help you today?"));
                body.add(Line.blank());
                body.add(Line.of(Theme.dim(), indent + "You can ask me about your lifestyle, nutrition, exercise,"));
                body.add(Line.of(Theme.dim(), indent + "sleep, hydration, or general wellness."));
                body.add(Line.blank());
                body.add(Line.of(Theme.accentOn(), indent + "> [1] What should I focus on?"));
                body.add(Line.of(Theme.text(), indent + "  [2] What should I eat?"));
                body.add(Line.of(Theme.text(), indent + "  [3] What exercise should I do?"));
                body.add(Line.of(Theme.text(), indent + "  [4] How can I improve my sleep?"));
                body.add(Line.of(Theme.text(), indent + "  [5] How much water should I drink?"));
                body.add(Line.blank());
                if (aiThinking) {
                    body.add(Line.of(Theme.accentPurple(), indent + "🤖 LIFEForge is thinking... please wait"));
                    body.add(Line.blank());
                }
                body.addAll(renderQuestionInput("Ask something else:", chatDraft, chatCursor, availInner));
                return "Personalized conversational guidance";
            }

            // Compact Context Card when in active conversation (General/Free)
            String contextText = "🤖 LIFEForge AI  │  General Lifestyle Guidance";
            body.add(Line.of(Theme.dim(), indent + "┌" + "─".repeat(boxW - 2) + "┐"));
            body.add(Line.of(Theme.headingPurple(), indent + "│ " + Theme.padRight(truncate(contextText, boxW - 4), boxW - 4) + " │"));
            body.add(Line.of(Theme.dim(), indent + "└" + "─".repeat(boxW - 2) + "┘"));
            body.add(Line.blank());

            renderChatHistory(body, availInner);

            if (aiThinking) {
                body.add(Line.of(Theme.accentPurple(), indent + "🤖 LIFEForge is thinking... please wait"));
                body.add(Line.blank());
            }

            body.addAll(renderQuestionInput("Your Question", chatDraft, chatCursor, availInner));
            return "Personalized conversational guidance";

        } else {
            // =========================================================================
            // 2. RECOMMENDATION DETAIL → 🤖 CHAT WITH AI (GOAL-SPECIFIC ASSISTANT)
            // =========================================================================
            String goalName = hasGoal ? goal.get().getName() : "Active Goal";
            String catName = currentCategory == null ? "Recommendation" : currentCategory.getName();
            String recTitle = (result != null && result.recommendation != null)
                    ? nvl(result.recommendation.getTitle())
                    : "Guidance";

            if (chatConversation.isEmpty()) {
                body.add(Line.of(Theme.headingPurple(), indent + "🤖 Chat with AI — Recommendation Assistant"));
                body.add(Line.blank());
                body.add(Line.of(Theme.dim(), indent + "┌" + "─".repeat(boxW - 2) + "┐"));
                body.add(Line.of(Theme.headingCyan(), indent + "│ " + Theme.padRight(truncate("Goal: " + goalName + "  │  " + catName + ": " + recTitle, boxW - 4), boxW - 4) + " │"));
                body.add(Line.of(Theme.dim(), indent + "└" + "─".repeat(boxW - 2) + "┘"));
                body.add(Line.blank());
                body.add(Line.of(Theme.dim(), indent + "You can ask anything related to your " + goalName + " goal — including"));
                body.add(Line.of(Theme.dim(), indent + "nutrition, hydration, daily habits, exercise, sleep, and lifestyle routines."));
                body.add(Line.blank());
                body.add(Line.of(Theme.accentOn(), indent + "> [1] How do I apply this to my daily routine?"));
                body.add(Line.of(Theme.text(), indent + "  [2] What daily habits best support my " + goalName + " goal?"));
                body.add(Line.of(Theme.text(), indent + "  [3] How does this recommendation support my " + goalName + " goal?"));
                body.add(Line.of(Theme.text(), indent + "  [4] What foods or nutrients best support my " + goalName + " goal?"));
                body.add(Line.of(Theme.text(), indent + "  [5] How much water should I drink for my " + goalName + " goal?"));
                body.add(Line.blank());
                if (aiThinking) {
                    body.add(Line.of(Theme.accentPurple(), indent + "🤖 LIFEForge is thinking... please wait"));
                    body.add(Line.blank());
                }
                body.addAll(renderQuestionInput("Ask anything related to your goal or recommendation:", chatDraft, chatCursor, availInner));
                return "Goal-specific recommendation guidance";
            }

            // Compact Context Card when in active conversation (Goal-Specific)
            String contextText = "Goal: " + goalName + "  │  " + catName + ": " + recTitle;
            body.add(Line.of(Theme.dim(), indent + "┌" + "─".repeat(boxW - 2) + "┐"));
            body.add(Line.of(Theme.headingCyan(), indent + "│ " + Theme.padRight(truncate(contextText, boxW - 4), boxW - 4) + " │"));
            body.add(Line.of(Theme.dim(), indent + "└" + "─".repeat(boxW - 2) + "┘"));
            body.add(Line.blank());

            renderChatHistory(body, availInner);

            if (aiThinking) {
                body.add(Line.of(Theme.accentPurple(), indent + "🤖 LIFEForge is thinking... please wait"));
                body.add(Line.blank());
            }

            body.addAll(renderQuestionInput("Your Question", chatDraft, chatCursor, availInner));
            return "Goal-specific recommendation guidance";
        }
    }

    private void renderChatHistory(List<Line> body, int availInner) {
        List<Line> allLines = new ArrayList<>();
        for (String entry : chatConversation) {
            boolean isAi = entry.startsWith("AI: ");
            String text = entry.substring(isAi ? 4 : 6);
            if (isAi) {
                allLines.addAll(renderAiChatBubble(text, availInner));
            } else {
                allLines.addAll(renderUserChatBubble(text, availInner));
            }
            allLines.add(Line.blank());
        }

        int maxViewportLines = Math.max(8, termHeight - 16);
        if (allLines.size() <= maxViewportLines) {
            chatScrollOffset = 0;
            body.addAll(allLines);
            return;
        }

        int maxScroll = allLines.size() - maxViewportLines;
        if (chatScrollOffset > maxScroll) {
            chatScrollOffset = maxScroll;
        }
        if (chatScrollOffset < 0) {
            chatScrollOffset = 0;
        }

        int endIndex = allLines.size() - chatScrollOffset;
        int startIndex = Math.max(0, endIndex - maxViewportLines);

        // Visual scroll track indicator above if more older lines exist
        if (chatScrollOffset < maxScroll) {
            int linesAbove = maxScroll - chatScrollOffset;
            body.add(Line.of(Theme.dim(), "  ▲ " + linesAbove + " older line" + (linesAbove == 1 ? "" : "s") + " above  [PgUp / ↑ to scroll up]"));
        }

        for (int i = startIndex; i < endIndex; i++) {
            body.add(allLines.get(i));
        }

        // Visual scroll track indicator below if scrolled up
        if (chatScrollOffset > 0) {
            body.add(Line.of(Theme.dim(), "  ▼ " + chatScrollOffset + " newer line" + (chatScrollOffset == 1 ? "" : "s") + " below  [PgDn / ↓ to scroll down]"));
        }
    }

    /** Render a right-aligned chat bubble for the user's question (square corners). */
    private List<Line> renderUserChatBubble(String text, int inner) {
        List<Line> out = new ArrayList<>();
        int maxBubbleW = Math.min(52, inner - 8);
        int maxContentW = maxBubbleW - 4;
        List<String> wrapped = Theme.wrap(text, maxContentW);
        if (wrapped.isEmpty()) {
            wrapped = List.of("");
        }

        int longest = Theme.width("YOU");
        for (String w : wrapped) {
            longest = Math.max(longest, Theme.width(w));
        }
        int bubbleW = Math.max(22, longest + 4);
        int padRight = 2;
        int padLeft = Math.max(2, inner - padRight - bubbleW);
        String leftIndent = " ".repeat(padLeft);

        Style cardStyle = Theme.accentOn();

        out.add(Line.of(cardStyle, leftIndent + "┌" + "─".repeat(bubbleW - 2) + "┐"));
        out.add(Line.of(cardStyle, leftIndent + "│ " + Theme.padRight("YOU", bubbleW - 4) + " │"));
        for (String line : wrapped) {
            String safeLine = Theme.width(line) > bubbleW - 4 ? Theme.truncate(line, bubbleW - 4) : line;
            out.add(Line.of(cardStyle, leftIndent + "│ " + Theme.padRight(safeLine, bubbleW - 4) + " │"));
        }
        out.add(Line.of(cardStyle, leftIndent + "└" + "─".repeat(bubbleW - 2) + "┘"));
        return out;
    }

    /** Render a left-aligned chat bubble for the AI's response (square corners). */
    private List<Line> renderAiChatBubble(String text, int inner) {
        List<Line> out = new ArrayList<>();
        int bubbleW = Math.min(70, inner - 4);
        int maxContentW = bubbleW - 4;
        List<String> wrapped = Theme.wrap(text, maxContentW);
        if (wrapped.isEmpty()) {
            wrapped = List.of("");
        }

        String indent = "  ";
        Style cardStyle = Theme.accentPurple();

        out.add(Line.of(cardStyle, indent + "┌" + "─".repeat(bubbleW - 2) + "┐"));
        out.add(Line.of(cardStyle, indent + "│ " + Theme.padRight("LIFEForge AI", bubbleW - 4) + " │"));
        out.add(Line.of(cardStyle, indent + "├" + "─".repeat(bubbleW - 2) + "┤"));
        for (String line : wrapped) {
            String safeLine = Theme.width(line) > maxContentW ? Theme.truncate(line, maxContentW) : line;
            out.add(Line.of(cardStyle, indent + "│ " + Theme.padRight(safeLine, maxContentW) + " │"));
        }
        out.add(Line.of(cardStyle, indent + "└" + "─".repeat(bubbleW - 2) + "┘"));
        return out;
    }

    /** Render an enclosed input box for typing the question (square corners). */
    private List<Line> renderQuestionInput(String label, String draft, int cursor, int inner) {
        List<Line> out = new ArrayList<>();
        int boxW = Math.max(20, inner - 4);
        String indent = "  ";

        if (label != null && !label.isBlank()) {
            out.add(Line.of(Theme.headingCyan(), indent + label));
        }
        Style borderStyle = Theme.accentOn();

        String safeDraft = draft == null ? "" : draft;
        int safeCursor = Math.max(0, Math.min(cursor, safeDraft.length()));
        String draftWithCursor = safeDraft.substring(0, safeCursor)
                + "|"
                + safeDraft.substring(safeCursor);

        int maxContentW = boxW - 6;
        List<String> wrapped = Theme.wrap(draftWithCursor, maxContentW);
        if (wrapped.isEmpty()) {
            wrapped = List.of("|");
        }

        out.add(Line.of(borderStyle, indent + "┌" + "─".repeat(boxW - 2) + "┐"));
        for (int i = 0; i < wrapped.size(); i++) {
            String prefix = (i == 0) ? "> " : "  ";
            String line = prefix + wrapped.get(i);
            out.add(Line.of(Theme.text(), indent + "│ " + Theme.padRight(line, boxW - 4) + " │"));
        }
        out.add(Line.of(borderStyle, indent + "└" + "─".repeat(boxW - 2) + "┘"));
        return out;
    }

    private String viewSaved(List<Line> body) {
        if (savedList == null) {
            savedList = new ArrayList<>();
        }

        int termWidth = Math.min(100, Math.max(40, width));
        int cardWidth = Math.max(30, termWidth - 8);
        int cardInner = cardWidth - 2;

        String title = "📌 MY SAVED RECOMMENDATIONS";
        int titleW = Theme.width(title);
        int dashCount = Math.max(0, cardWidth - 5 - titleW);
        String topBorder = "  ┌─ " + title + " " + "─".repeat(dashCount) + "┐";
        body.add(Line.of(Theme.bar(), topBorder));

        if (savedList.isEmpty()) {
            body.add(formatCardRow(" 0 saved item(s).", cardInner, Theme.dim()));
            body.add(formatCardRow("", cardInner));
            body.add(formatCardRow("   You have not saved any recommendations yet. Generate one from the", cardInner, Theme.warn()));
            body.add(formatCardRow("   Recommendation Hub and save it to see it here.", cardInner, Theme.warn()));
        } else {
            body.add(formatCardRow(" " + savedList.size() + " saved item(s).", cardInner, Theme.dim()));
            body.add(formatCardRow("", cardInner));

            if (sel >= savedList.size()) {
                sel = Math.max(0, savedList.size() - 1);
            }

            for (int i = 0; i < savedList.size(); i++) {
                SavedRecommendation item = savedList.get(i);
                Recommendation r = ctx.recommendationController.findRecommendationById(
                        item.getRecommendationId()).orElse(null);
                String itemTitle = (r == null ? "Recommendation #" + item.getRecommendationId() : r.getTitle());
                String formattedDate = item.getSavedAt() == null ? ""
                        : item.getSavedAt().format(SAVED_TIME_FMT);

                boolean isSelected = (i == sel);
                String prefix = isSelected ? " > " : "   ";
                String titleCol = Theme.padRight(Theme.truncate(nvl(itemTitle), 34), 34);
                String rowContent = prefix + String.format("%s │ Saved: %s", titleCol, formattedDate);

                body.add(formatCardRow(rowContent, cardInner, isSelected ? Theme.selected() : Theme.text()));
            }
        }

        String bottomBorder = "  └" + "─".repeat(cardWidth - 2) + "┘";
        body.add(Line.of(Theme.bar(), bottomBorder));

        return "Everything you bookmarked";
    }

    private String viewSavedDetail(List<Line> body) {
        if (savedRec == null) {
            return "";
        }
        Recommendation r = savedRec;
        body.add(Line.of(Theme.headingCyan(), "  " + nvl(r.getTitle())));
        renderRecommendationContent(body, r);
        body.add(Line.blank());
        body.addAll(menuLines());
        return "Saved " + (savedRecId == null ? "" : savedRecId);
    }

    private String viewProfile(List<Line> body) {
        User u = ctx.session.getCurrentUser();
        if (u == null) {
            return "";
        }

        int termWidth = Math.min(100, Math.max(40, width));
        int cardWidth = Math.max(30, termWidth - 8);
        int cardInner = cardWidth - 2;

        String title = "👤 USER PROFILE & ACCOUNT";
        int titleW = Theme.width(title);
        int dashCount = Math.max(0, cardWidth - 5 - titleW);
        String topBorder = "  ┌─ " + title + " " + "─".repeat(dashCount) + "┐";
        body.add(Line.of(Theme.bar(), topBorder));

        body.add(formatCardRow("", cardInner));

        String fullName = u.getFullName() != null ? u.getFullName() : "";
        String roleStr = u.getRole() != null ? u.getRole().name() : "-";
        String emailStr = u.getEmail() != null ? u.getEmail() : "-";
        String memberSince = u.getCreatedAt() != null ? u.getCreatedAt().format(MEMBER_DATE_FMT) : "-";

        String row1 = Theme.padRight("  " + fullName.toUpperCase(Locale.ROOT), 42) + "Role   : " + roleStr;
        String row2 = Theme.padRight("  " + emailStr, 42) + "Member : " + memberSince;

        body.add(formatCardRow(row1, cardInner, Theme.headingGreen()));
        body.add(formatCardRow(row2, cardInner, Theme.dim()));
        body.add(formatCardRow("", cardInner));

        // Biometrics (Left Box)
        String ageStr = u.getAge() == null ? "-" : u.getAge() + " yrs";
        String genderStr = human(u.getGender());
        String heightStr = u.getHeightCm() == null ? "-"
                : String.format(Locale.ROOT, "%.0f cm", u.getHeightCm());
        String weightStr = u.getWeightKg() == null ? "-"
                : String.format(Locale.ROOT, "%.1f kg", u.getWeightKg());

        // Calibrated Targets (Right Box)
        String goalStr = "None";
        if (ctx != null && ctx.goalController != null) {
            Optional<Goal> activeGoal = ctx.goalController.getCurrentGoal();
            if (activeGoal.isPresent() && activeGoal.get().getName() != null) {
                goalStr = activeGoal.get().getName();
            }
        }
        String waterStr = "-";
        if (u.getWeightKg() != null && u.getActivityLevel() != null) {
            double liters = com.lifeforge.util.HydrationCalculator.suggestedLitersPerDay(
                    u.getWeightKg(), u.getActivityLevel());
            waterStr = String.format(Locale.ROOT, "%.1f L / day", liters);
        }
        String activityStr = human(u.getActivityLevel());
        String bmiStr = formatBmiScore(u);

        // Sub-box dimensions
        int indent = 2;
        int box1W = 31;
        int box2W = 35;
        int gap = Math.min(6, Math.max(2, (cardInner - indent - box1W - box2W) / 2));
        int trailingPad = Math.max(0, cardInner - (indent + box1W + gap + box2W));

        String b1Top = "┌─ 📏 BIOMETRICS " + "─".repeat(Math.max(0, box1W - 5 - 13)) + "┐";
        String b2Top = "┌─ 🎯 CALIBRATED TARGETS " + "─".repeat(Math.max(0, box2W - 5 - 21)) + "┐";
        String topSubBoxes = " ".repeat(indent) + b1Top + " ".repeat(gap) + b2Top + " ".repeat(trailingPad);
        body.add(formatCardRow(topSubBoxes, cardInner, Theme.bar()));

        String b1Row1 = "│" + buildSubBoxRow(" Age", ageStr, 10, 29) + "│";
        String b2Row1 = "│" + buildSubBoxRow(" Active Goal", goalStr, 14, 33) + "│";
        body.add(formatCardRow(" ".repeat(indent) + b1Row1 + " ".repeat(gap) + b2Row1 + " ".repeat(trailingPad), cardInner));

        String b1Row2 = "│" + buildSubBoxRow(" Gender", genderStr, 10, 29) + "│";
        String b2Row2 = "│" + buildSubBoxRow(" Water Target", waterStr, 14, 33) + "│";
        body.add(formatCardRow(" ".repeat(indent) + b1Row2 + " ".repeat(gap) + b2Row2 + " ".repeat(trailingPad), cardInner));

        String b1Row3 = "│" + buildSubBoxRow(" Height", heightStr, 10, 29) + "│";
        String b2Row3 = "│" + buildSubBoxRow(" Activity", activityStr, 14, 33) + "│";
        body.add(formatCardRow(" ".repeat(indent) + b1Row3 + " ".repeat(gap) + b2Row3 + " ".repeat(trailingPad), cardInner));

        String b1Row4 = "│" + buildSubBoxRow(" Weight", weightStr, 10, 29) + "│";
        String b2Row4 = "│" + buildSubBoxRow(" BMI Score", bmiStr, 14, 33) + "│";
        body.add(formatCardRow(" ".repeat(indent) + b1Row4 + " ".repeat(gap) + b2Row4 + " ".repeat(trailingPad), cardInner));

        String b1Bottom = "└" + "─".repeat(Math.max(0, box1W - 2)) + "┘";
        String b2Bottom = "└" + "─".repeat(Math.max(0, box2W - 2)) + "┘";
        String bottomSubBoxes = " ".repeat(indent) + b1Bottom + " ".repeat(gap) + b2Bottom + " ".repeat(trailingPad);
        body.add(formatCardRow(bottomSubBoxes, cardInner, Theme.bar()));

        body.add(formatCardRow("", cardInner));

        // Horizontal actions
        String actionRow;
        if (armed) {
            actionRow = "  ! CONFIRM: Press [D] to delete account   •   [Esc/B] Cancel";
            body.add(formatCardRow(actionRow, cardInner, Theme.err()));
        } else {
            actionRow = "  [E] Edit Profile   •   [P] Change Password   •   [D] Delete Account";
            body.add(formatCardRow(actionRow, cardInner, Theme.pivot()));
        }

        body.add(formatCardRow("", cardInner));

        String bottomBorder = "  └" + "─".repeat(cardWidth - 2) + "┘";
        body.add(Line.of(Theme.bar(), bottomBorder));

        return "Your profile and account";
    }

    public static String buildSubBoxRow(String label, String value, int labelPad, int innerWidth) {
        String paddedLabel = Theme.padRight(label, labelPad) + ": ";
        int prefixW = Theme.width(paddedLabel);
        int availValW = Math.max(0, innerWidth - prefixW);
        String valStr = Theme.truncate(value == null ? "-" : value, availValW);
        return Theme.padRight(paddedLabel + valStr, innerWidth);
    }

    public static String formatBmiScore(User u) {
        if (u == null || u.getHeightCm() == null || u.getWeightKg() == null
                || u.getHeightCm() <= 0 || u.getWeightKg() <= 0) {
            return "-";
        }
        double heightM = u.getHeightCm() / 100.0;
        double bmi = u.getWeightKg() / (heightM * heightM);
        String category;
        if (bmi < 18.0) {
            category = "Underweight";
        } else if (bmi < 25.0) {
            category = "Normal";
        } else if (bmi < 30.0) {
            category = "Overweight";
        } else {
            category = "Obese";
        }
        return String.format(Locale.ROOT, "%.1f (%s)", bmi, category);
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

    private static String stripAnsi(String s) {
        if (s == null) return "";
        return s.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
    }

    private static int visibleWidth(String s) {
        return Theme.width(stripAnsi(s));
    }

    private static String adminRow(String content, int inner, Style frameStyle) {
        String safe = content == null ? "" : content;
        int w = visibleWidth(safe);
        if (w > inner) {
            safe = Theme.truncate(stripAnsi(safe), inner);
            w = visibleWidth(safe);
        }
        int pad = Math.max(0, inner - w);
        return Theme.render(frameStyle, "│") + safe + " ".repeat(pad) + Theme.render(frameStyle, "│");
    }

    static String padLine(String content, int fw, Style frameStyle) {
        String safe = (content == null) ? "" : content;
        int maxContent = Math.max(0, fw - 5);
        int visW = visibleWidth(safe);
        if (visW > maxContent) {
            safe = Theme.truncate(stripAnsi(safe), maxContent);
            visW = visibleWidth(safe);
        }
        int pad = Math.max(0, maxContent - visW);
        return Theme.render(frameStyle, "│  ") + safe + " ".repeat(pad) + Theme.render(frameStyle, " │");
    }

    static String padLine(String content, int fw) {
        return padLine(content, fw, Theme.adminBorder());
    }

    private static String adminDivider(int inner, Style frameStyle) {
        return Theme.render(frameStyle, "├" + "─".repeat(inner) + "┤");
    }

    private static String adminSectionDivider(String label, int inner, Style frameStyle, Style labelStyle) {
        String prefix = "├─ ";
        int labelW = Theme.width(label);
        int suffixDashes = Math.max(0, inner - (2 + labelW + 1));
        return Theme.render(frameStyle, prefix)
                + Theme.render(labelStyle, label)
                + Theme.render(frameStyle, " " + "─".repeat(suffixDashes) + "┤");
    }

    Set<Long> getInactiveRecIds() {
        return inactiveRecIds;
    }

    public String getAdminRecSearchQuery() {
        return recommendationSearchQuery;
    }

    public void setAdminRecSearchQuery(String query) {
        this.recommendationSearchQuery = (query == null) ? "" : query;
        this.recSearchQueryText = this.recommendationSearchQuery;
    }

    public int getAdminRecCurrentPage() {
        return recPage;
    }

    public void setAdminRecCurrentPage(int page) {
        this.recPage = page;
    }

    public void resetAdminRecSearch() {
        this.recommendationSearchQuery = "";
        this.recSearchQueryText = "";
        this.recPage = 0;
        this.sel = 0;
        refreshAdminRecs();
    }

    public List<Goal> getGoals() {
        return goals;
    }

    public void setGoals(List<Goal> goals) {
        this.goals = goals;
    }

    public List<RecommendationCategory> getCatList() {
        return catList;
    }

    public void setCatList(List<RecommendationCategory> catList) {
        this.catList = catList;
    }

    public Set<Long> getInactiveCatIds() {
        return inactiveCatIds;
    }

    private static boolean isRecScreen(Screen s) {
        return s == Screen.ADMIN_RECS
                || s == Screen.ADMIN_REC_SEARCH
                || s == Screen.ADMIN_REC_DETAIL
                || s == Screen.ADMIN_REC_FORM
                || s == Screen.ADMIN_REC_DELETE_CONFIRM;
    }

    String renderAdminRecsPage(int fw) {
        int inner = fw - 2;
        List<String> lines = new ArrayList<>();
        Style frameStyle = Theme.adminBorder();

        if (displayedRecs == null) {
            refreshAdminRecs();
        }

        int totalAll = displayedRecs == null ? 0 : displayedRecs.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalAll / recPageSize));
        if (recPage < 0) recPage = 0;
        if (recPage >= totalPages) recPage = totalPages - 1;

        int pageStart = recPage * recPageSize;
        int pageEnd = Math.min(pageStart + recPageSize, totalAll);
        int pageRows = pageEnd - pageStart;
        if (sel >= pageRows && pageRows > 0) {
            sel = pageRows - 1;
        }

        // 1. Top outer border
        String titleRaw = "┌─ 🛠️ LIFEForge / RECOMMENDATION CMS (Admin) ";
        int usedW = Theme.width(titleRaw);
        int dashCount = Math.max(0, (inner + 2) - usedW - 1);
        String topBorder = Theme.render(frameStyle, "┌─ ")
                + "🛠️ "
                + Theme.render(Theme.adminHeader(), "LIFEForge")
                + Theme.render(Theme.dim(), " / ")
                + Theme.render(Theme.adminHeaderSub(), "RECOMMENDATION CMS (Admin)")
                + Theme.render(frameStyle, " " + "─".repeat(dashCount) + "┐");
        lines.add(topBorder);

        // 2. Search row
        String searchVal = (recommendationSearchQuery != null) ? recommendationSearchQuery : "";
        String rightPart = "] (" + totalAll + ") ";
        int searchBoxW = Math.max(10, inner - 11 - Theme.width(rightPart));
        String searchDisplay = Theme.padRight(searchVal, searchBoxW);
        String searchInner = "  " + Theme.render(Theme.adminHeader(), "Search: ")
                + Theme.render(frameStyle, "[")
                + (searchVal.isEmpty() ? searchDisplay : Theme.render(Theme.adminBadgeYellow(), searchDisplay))
                + Theme.render(frameStyle, "]")
                + Theme.render(Theme.adminDim(), " (" + totalAll + ") ");
        lines.add(adminRow(searchInner, inner, frameStyle));

        // 3. Grid Table
        int idW = 6;
        int goalW = 16;
        int targetW = 12;
        int statW = 8;
        int flex = inner - (idW + 1 + goalW + 1 + targetW + 1 + statW + 1);
        int titleW = Math.max(20, flex);

        // Top table divider
        String topTableDiv = Theme.render(frameStyle, "├" + "─".repeat(idW) + "┬" + "─".repeat(titleW) + "┬" + "─".repeat(goalW) + "┬" + "─".repeat(targetW) + "┬" + "─".repeat(statW) + "┤");
        lines.add(topTableDiv);

        // Header row
        String hId = Theme.padRight(" ID", idW);
        String hTitle = Theme.padRight(" RECOMMENDATION TITLE", titleW);
        String hGoal = Theme.padRight(" GOAL", goalW);
        String hTarget = Theme.padRight(" TARGET", targetW);
        String hStat = Theme.padRight("  STAT", statW);
        String headerRow = Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hId)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hTitle)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hGoal)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hTarget)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hStat)
                + Theme.render(frameStyle, "│");
        lines.add(headerRow);

        // Header divider
        String headerDiv = Theme.render(frameStyle, "├" + "─".repeat(idW) + "┼" + "─".repeat(titleW) + "┼" + "─".repeat(goalW) + "┼" + "─".repeat(targetW) + "┼" + "─".repeat(statW) + "┤");
        lines.add(headerDiv);

        // Data rows (8 rows)
        for (int i = 0; i < recPageSize; i++) {
            int globalIdx = pageStart + i;
            if (globalIdx < pageEnd && displayedRecs != null) {
                Recommendation r = displayedRecs.get(globalIdx);
                boolean isSel = (i == sel);
                boolean isActive = !inactiveRecIds.contains(r.getId());

                String idRaw = (isSel ? ">#" : " #") + r.getId();
                String idPadded = Theme.padRight(idRaw, idW);

                String recTitleRaw = " " + Theme.truncate(r.getTitle(), titleW - 2);
                String titlePadded = Theme.padRight(recTitleRaw, titleW);

                String goalRaw = " " + Theme.truncate(nameOfGoal(r.getGoalId()), goalW - 2);
                String goalPadded = Theme.padRight(goalRaw, goalW);

                String targetStr = (r.getSuggestedTarget() != null && !r.getSuggestedTarget().isBlank()) ? r.getSuggestedTarget() : "-";
                String targetRaw = " " + Theme.truncate(targetStr, targetW - 2);
                String targetPadded = Theme.padRight(targetRaw, targetW);

                String statText = isActive ? "ACT" : "INA";
                String statRaw = "  " + statText;
                String statPadded = Theme.padRight(statRaw, statW);

                String cId, cTitle, cGoal, cTarget, cStat;
                if (isSel) {
                    cId = Theme.render(Theme.rowSelectedId(), idPadded);
                    cTitle = Theme.render(Theme.rowSelected(), titlePadded);
                    cGoal = Theme.render(Theme.rowSelectedDim(), goalPadded);
                    cTarget = Theme.render(Theme.rowSelected(), targetPadded);
                    cStat = Theme.render(isActive ? Theme.rowSelectedDotActive() : Theme.rowSelectedBadgeRed(), statPadded);
                } else {
                    cId = Theme.render(Theme.adminDim(), idPadded);
                    cTitle = Theme.render(Theme.adminText(), titlePadded);
                    cGoal = Theme.render(Theme.adminDim(), goalPadded);
                    cTarget = Theme.render(Theme.adminText(), targetPadded);
                    cStat = Theme.render(isActive ? Theme.ok() : Theme.warn(), statPadded);
                }

                String row = Theme.render(frameStyle, "│") + cId
                        + Theme.render(frameStyle, "│") + cTitle
                        + Theme.render(frameStyle, "│") + cGoal
                        + Theme.render(frameStyle, "│") + cTarget
                        + Theme.render(frameStyle, "│") + cStat
                        + Theme.render(frameStyle, "│");
                lines.add(row);
            } else {
                String emptyRow = Theme.render(frameStyle, "│") + " ".repeat(idW)
                        + Theme.render(frameStyle, "│") + " ".repeat(titleW)
                        + Theme.render(frameStyle, "│") + " ".repeat(goalW)
                        + Theme.render(frameStyle, "│") + " ".repeat(targetW)
                        + Theme.render(frameStyle, "│") + " ".repeat(statW)
                        + Theme.render(frameStyle, "│");
                lines.add(emptyRow);
            }
        }

        // Bottom table divider
        String botTableDiv = Theme.render(frameStyle, "├" + "─".repeat(idW) + "┴" + "─".repeat(titleW) + "┴" + "─".repeat(goalW) + "┴" + "─".repeat(targetW) + "┴" + "─".repeat(statW) + "┤");
        lines.add(botTableDiv);

        // 4. Summary & Pagination row
        int startNum = totalAll == 0 ? 0 : pageStart + 1;
        int endNum = pageEnd;
        String summaryLeft = "  Showing " + startNum + "-" + endNum + " of " + totalAll;
        String summaryRight = "[←/→] Page " + (recPage + 1) + " of " + totalPages + "   ";
        int sGap = Math.max(1, inner - Theme.width(summaryLeft) - Theme.width(summaryRight));
        String summaryRow = Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminDim(), summaryLeft)
                + " ".repeat(sGap)
                + Theme.render(Theme.adminDim(), summaryRight)
                + Theme.render(frameStyle, "│");
        lines.add(summaryRow);

        // 5. Footer divider
        lines.add(adminDivider(inner, frameStyle));

        // 6. Action hints inside card
        StringBuilder hints = new StringBuilder("  ");
        boolean hasFilter = recommendationSearchQuery != null && !recommendationSearchQuery.isEmpty();
        String gap = hasFilter ? "   " : "    ";
        hints.append(Theme.render(Theme.adminBadgeKey(), "↑↓")).append(Theme.render(Theme.adminText(), " Select" + gap));
        hints.append(Theme.render(Theme.adminBadgeKey(), "Enter")).append(Theme.render(Theme.adminText(), " Edit" + gap));
        hints.append(Theme.render(Theme.adminBadgeGreen(), "N")).append(Theme.render(Theme.adminText(), " New" + gap));
        hints.append(Theme.render(Theme.adminBadgeKey(), "S")).append(Theme.render(Theme.adminText(), " Search" + gap));
        if (hasFilter) {
            hints.append(Theme.render(Theme.adminBadgeYellow(), "C")).append(Theme.render(Theme.adminText(), " Clear" + gap));
        }
        hints.append(Theme.render(Theme.adminBadgeRed(), "D")).append(Theme.render(Theme.adminText(), " Delete" + gap));
        hints.append(Theme.render(Theme.adminBadgePurple(), "B")).append(Theme.render(Theme.adminText(), " Back" + gap));
        hints.append(Theme.render(Theme.adminBadgeRed(), "Q")).append(Theme.render(Theme.adminText(), " Quit"));
        lines.add(adminRow(hints.toString(), inner, frameStyle));

        // 7. Status line (if any)
        if (status != null && !status.isEmpty()) {
            Style st = statusErr ? Theme.err() : Theme.ok();
            String prefix = statusErr ? " ERROR: " : " OK: ";
            lines.add(adminRow("  " + Theme.render(st, prefix + status), inner, frameStyle));
        }

        // 8. Bottom card border
        lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));

        return String.join("\n", lines);
    }

    public int getAdminRecDetailPage() {
        return adminRecDetailPage;
    }

    public void setAdminRecDetailPage(int page) {
        this.adminRecDetailPage = Math.max(0, Math.min(1, page));
    }

    String renderAdminRecDetailPage(int fw) {
        int inner = fw - 2;
        List<String> lines = new ArrayList<>();
        Style frameStyle = Theme.adminBorder();

        lines.add("\u001B[H\u001B[2J");

        Recommendation r = findRecById(editRecId);

        String idLabel = (r != null ? String.valueOf(r.getId()) : String.valueOf(editRecId));
        String titleRaw = "┌─ 💡 RECOMMENDATION DETAIL [#" + idLabel + "] ";
        int usedW = Theme.width(titleRaw);
        int dashCount = Math.max(0, fw - usedW - 1);
        String topBorder = Theme.render(frameStyle, "┌─ ")
                + "💡 "
                + Theme.render(Theme.adminHeader(), "RECOMMENDATION DETAIL [#" + idLabel + "]")
                + Theme.render(frameStyle, " " + "─".repeat(dashCount) + "┐");
        lines.add(topBorder);

        if (r == null) {
            lines.add(padLine("", fw, frameStyle));
            lines.add(padLine(Theme.render(Theme.warn(), "This recommendation no longer exists."), fw, frameStyle));
            lines.add(padLine(Theme.render(Theme.dim(), "Press B or ESC to return to the CMS list."), fw, frameStyle));
            lines.add(padLine("", fw, frameStyle));
            lines.add(adminDivider(inner, frameStyle));
            lines.add(padLine(Theme.render(Theme.adminBadgePurple(), "[ESC/B]") + Theme.render(Theme.adminText(), " Back to CMS"), fw, frameStyle));
            lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));
            return String.join("\n", lines);
        }

        // Tab bar for 2-page pagination
        String tab1 = (adminRecDetailPage == 0) ? "▶ [ 1. \uD83D\uDCCB OVERVIEW & CONTENT ]" : "  [ 1. \uD83D\uDCCB OVERVIEW & CONTENT ]";
        String tab2 = (adminRecDetailPage == 1) ? "▶ [ 2. \uD83C\uDFAF GUIDANCE & ACTIONS ]" : "  [ 2. \uD83C\uDFAF GUIDANCE & ACTIONS ]";
        lines.add(padLine("", fw, frameStyle));
        lines.add(padLine("  " + Theme.render(Theme.headingCyan(), tab1) + "    " + Theme.render(Theme.headingCyan(), tab2), fw, frameStyle));

        String pageHint = (adminRecDetailPage == 0)
                ? "Page 1 of 2 \u00B7 Metadata, Title & Description \u00B7 Use \u2190 / \u2192 or [1-2] to switch"
                : "Page 2 of 2 \u00B7 Guidance, Targets & Notes \u00B7 Use \u2190 / \u2192 or [1-2] to switch";
        lines.add(padLine("  " + Theme.render(Theme.dim(), pageHint), fw, frameStyle));
        lines.add(padLine("", fw, frameStyle));

        if (adminRecDetailPage == 0) {
            // Section 1: BASIC METADATA
            lines.add(padLine(Theme.render(Theme.adminHeaderSub(), "📋 BASIC METADATA"), fw, frameStyle));
            lines.add(padLine("", fw, frameStyle));

            String goalName = nameOfGoal(r.getGoalId());
            lines.add(padLine("• " + Theme.render(Theme.adminDim(), "Goal           : ") + Theme.render(Theme.pivot(), goalName), fw, frameStyle));

            String catName = nameOfCategory(r.getCategoryId());
            lines.add(padLine("• " + Theme.render(Theme.adminDim(), "Category       : ") + Theme.render(Theme.adminText(), catName), fw, frameStyle));

            String act = r.getActivityLevel() == null ? "ALL" : human(r.getActivityLevel());
            lines.add(padLine("• " + Theme.render(Theme.adminDim(), "Activity Level : ") + Theme.render(Theme.adminText(), act), fw, frameStyle));

            boolean isActive = !inactiveRecIds.contains(r.getId());
            String statText = isActive ? "● Active" : "● Inactive";
            Style statStyle = isActive ? Theme.ok() : Theme.warn();
            lines.add(padLine("• " + Theme.render(Theme.adminDim(), "Status         : ") + Theme.render(statStyle, statText), fw, frameStyle));
            lines.add(padLine("", fw, frameStyle));

            // Section 2: CONTENT
            lines.add(adminSectionDivider("📝 CONTENT", inner, frameStyle, Theme.adminHeaderSub()));
            lines.add(padLine("", fw, frameStyle));

            String titleVal = nvl(r.getTitle());
            List<String> tLines = Theme.wrap(titleVal, 58);
            for (int i = 0; i < tLines.size(); i++) {
                String pfx = (i == 0) ? "Title:          " : "                ";
                lines.add(padLine(pfx + Theme.render(Theme.headingGreen(), tLines.get(i)), fw, frameStyle));
            }

            String descVal = r.getDescription();
            if (descVal != null && !descVal.isBlank()) {
                lines.add(padLine("", fw, frameStyle));
                lines.add(padLine(Theme.render(Theme.adminDim(), "Description:"), fw, frameStyle));
                for (String dLine : Theme.wrap(descVal, 70)) {
                    lines.add(padLine("  " + Theme.render(Theme.adminText(), dLine), fw, frameStyle));
                }
            }
            lines.add(padLine("", fw, frameStyle));
        } else {
            // Section 3: GUIDANCE SPECIFICATIONS
            lines.add(adminSectionDivider("🎯 GUIDANCE SPECIFICATIONS", inner, frameStyle, Theme.adminHeaderSub()));
            lines.add(padLine("", fw, frameStyle));

            String actionsVal = r.getRecommendedActions();
            if (actionsVal != null && !actionsVal.isBlank()) {
                lines.add(padLine(Theme.render(Theme.adminDim(), "Recommended Actions:"), fw, frameStyle));
                renderDetailActionLines(lines, actionsVal, fw, frameStyle);
                lines.add(padLine("", fw, frameStyle));
            }

            String targetVal = (r.getSuggestedTarget() != null && !r.getSuggestedTarget().isBlank()) ? r.getSuggestedTarget() : "-";
            List<String> targetLines = Theme.wrap(targetVal, 54);
            for (int i = 0; i < targetLines.size(); i++) {
                String pfx = (i == 0) ? Theme.render(Theme.adminDim(), "Suggested Target:   ") : "                    ";
                lines.add(padLine(pfx + Theme.render(Theme.pivot(), targetLines.get(i)), fw, frameStyle));
            }

            String notesVal = r.getImportantNotes();
            if (notesVal != null && !notesVal.isBlank()) {
                lines.add(padLine("", fw, frameStyle));
                lines.add(padLine(Theme.render(Theme.adminDim(), "Important Notes:"), fw, frameStyle));
                for (String nLine : Theme.wrap(notesVal, 70)) {
                    lines.add(padLine("  " + Theme.render(Theme.warn(), nLine), fw, frameStyle));
                }
            }
            lines.add(padLine("", fw, frameStyle));
        }

        if (status != null && !status.isEmpty()) {
            Style st = statusErr ? Theme.err() : Theme.ok();
            String prefix = statusErr ? "ERROR: " : "OK: ";
            lines.add(padLine(Theme.render(st, prefix + status), fw, frameStyle));
            lines.add(padLine("", fw, frameStyle));
        }

        // Action footer
        lines.add(adminDivider(inner, frameStyle));
        String fEdit = Theme.render(Theme.adminBadgeKey(), "[E]") + Theme.render(Theme.adminText(), " Edit Fields");
        String fToggle = Theme.render(Theme.adminBadgeGreen(), "[T]") + Theme.render(Theme.adminText(), " Toggle Status");
        String fDel = Theme.render(Theme.adminBadgeRed(), "[D]") + Theme.render(Theme.adminText(), " Delete");
        String fBack = Theme.render(Theme.adminBadgePurple(), "[ESC/B]") + Theme.render(Theme.adminText(), " Back to CMS");
        String footerContent = fEdit + "    " + fToggle + "    " + fDel + "    " + fBack;
        lines.add(padLine(footerContent, fw, frameStyle));
        lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));

        return String.join("\n", lines);
    }

    private void renderDetailActionLines(List<String> lines, String actionsVal, int fw, Style frameStyle) {
        if (actionsVal == null || actionsVal.isBlank()) return;

        // Normalize ambiguous/text-presentation emojis to avoid Windows Terminal font width desync
        String sanitized = actionsVal
                .replace("🏋️", "💪 ")
                .replace("🏋", "💪 ")
                .replace("🍽", "🥗 ");

        String[] rawLines = sanitized.split("\\r?\\n");
        int maxBulletWrap = Math.max(30, fw - 5 - 8);

        for (String raw : rawLines) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                lines.add(padLine("", fw, frameStyle));
                continue;
            }

            boolean isPillarHeader = trimmed.endsWith(":")
                    && (trimmed.startsWith("🍎") || trimmed.startsWith("💪") || trimmed.startsWith("💧")
                    || trimmed.startsWith("😴") || trimmed.startsWith("🌱") || trimmed.startsWith("🏃")
                    || trimmed.startsWith("🥗") || trimmed.startsWith("🍲"));

            if (isPillarHeader) {
                String headerText = trimmed;
                int firstSpace = headerText.indexOf(' ');
                if (firstSpace > 0 && headerText.length() > firstSpace + 1 && headerText.charAt(firstSpace + 1) != ' ') {
                    headerText = headerText.substring(0, firstSpace) + "  " + headerText.substring(firstSpace + 1).trim();
                }
                lines.add(padLine("  " + Theme.render(Theme.headingGreen(), headerText), fw, frameStyle));
            } else if (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*")) {
                String content = trimmed.substring(1).trim();
                List<String> wrapped = Theme.wrap(content, maxBulletWrap);
                for (int k = 0; k < wrapped.size(); k++) {
                    if (k == 0) {
                        lines.add(padLine("    • " + Theme.render(Theme.adminText(), wrapped.get(0)), fw, frameStyle));
                    } else {
                        lines.add(padLine("      " + Theme.render(Theme.adminText(), wrapped.get(k)), fw, frameStyle));
                    }
                }
            } else {
                List<String> wrapped = Theme.wrap(trimmed, maxBulletWrap + 2);
                for (String wLine : wrapped) {
                    lines.add(padLine("    " + Theme.render(Theme.adminText(), wLine), fw, frameStyle));
                }
            }
        }
    }

    private String renderAdminUsersPage(int fw) {
        int inner = fw - 2;
        List<String> lines = new ArrayList<>();
        Style frameStyle = Theme.adminBorder();

        // Filter out ADMIN accounts from display
        List<User> displayUsers = new ArrayList<>();
        if (users != null) {
            for (User u : users) {
                if (u.getRole() != Role.ADMIN) {
                    displayUsers.add(u);
                }
            }
        }
        int totalUsers = displayUsers.size();
        int pageSize = ADMIN_USERS_PAGE_SIZE;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalUsers / pageSize));
        int curPage = Math.max(0, Math.min(userPage, totalPages - 1));
        int pageStart = curPage * pageSize;
        int pageEnd = Math.min(pageStart + pageSize, totalUsers);
        int pageRows = pageEnd - pageStart;
        int curSel = Math.max(0, Math.min(sel, Math.max(0, pageRows - 1)));
        User currentSelUser = (pageRows > 0 && curSel >= 0 && (pageStart + curSel) < totalUsers)
                ? displayUsers.get(pageStart + curSel)
                : null;

        // 1. Top outer border (square corners)
        lines.add(Theme.render(frameStyle, "┌" + "─".repeat(inner) + "┐"));

        // 2. Header
        String headLeft = "  " + Theme.render(Theme.adminHeader(), "LIFEForge")
                + Theme.render(Theme.dim(), "  /  ")
                + Theme.render(Theme.adminHeaderSub(), "ADMIN / USER MANAGEMENT");
        String headRight = Theme.render(Theme.adminHeader(), "👤 Admin") + "   ";
        int hGap = Math.max(1, inner - visibleWidth(headLeft) - visibleWidth(headRight));
        lines.add(adminRow(headLeft + " ".repeat(hGap) + headRight, inner, frameStyle));

        String sub = "  " + Theme.render(Theme.dim(), "Manage and maintain registered users");
        lines.add(adminRow(sub, inner, frameStyle));

        // Header Divider
        lines.add(adminDivider(inner, frameStyle));

        // 3. Toolbar
        String totalStr = "  " + Theme.render(Theme.dim(), "Total Users: ")
                + Theme.render(Theme.adminHeader(), String.valueOf(totalUsers));
        if (!searchQuery.isEmpty()) {
            totalStr += "    " + Theme.render(Theme.adminBadgeYellow(), "Filter: \"" + searchQuery + "\"");
        }
        lines.add(adminRow(totalStr, inner, frameStyle));
        lines.add(adminRow("", inner, frameStyle));

        String toolsStr;
        if (!searchQuery.isEmpty()) {
            toolsStr = "  " + Theme.render(Theme.adminBadgeYellow(), "[X]") + Theme.render(Theme.adminText(), " Clear Filter")
                    + "        " + Theme.render(Theme.adminBadgeKey(), "[↑↓]") + Theme.render(Theme.adminText(), " Select")
                    + "        " + Theme.render(Theme.adminBadgeKey(), "[←→]") + Theme.render(Theme.adminText(), " Page");
        } else {
            toolsStr = "  " + Theme.render(Theme.adminBadgeGreen(), "[S]") + Theme.render(Theme.adminText(), " Search")
                    + "        " + Theme.render(Theme.adminBadgeKey(), "[↑↓]") + Theme.render(Theme.adminText(), " Select")
                    + "        " + Theme.render(Theme.adminBadgeKey(), "[←→]") + Theme.render(Theme.adminText(), " Page");
        }
        lines.add(adminRow(toolsStr, inner, frameStyle));
        lines.add(adminRow("", inner, frameStyle));

        // 4. User Table
        int idW = 8;
        int statusW = 12;
        int flex = Math.max(28, inner - (2 + idW + statusW));
        int nameW = flex * 44 / 100;
        int emailW = flex - nameW;

        String hId = Theme.padRight("ID", idW);
        String hName = Theme.padRight("NAME", nameW);
        String hEmail = Theme.padRight("EMAIL", emailW);
        String hStatus = Theme.padRight("STATUS", statusW);
        lines.add(adminRow("  " + Theme.render(Theme.adminHeader(), hId + hName + hEmail + hStatus), inner, frameStyle));

        String uId = Theme.padRight("──────", idW);
        String uName = Theme.padRight("─".repeat(Math.max(4, nameW - 4)), nameW);
        String uEmail = Theme.padRight("─".repeat(Math.max(4, emailW - 4)), emailW);
        String uStatus = Theme.padRight("─────────", statusW);
        lines.add(adminRow("  " + Theme.render(Theme.dim(), uId + uName + uEmail + uStatus), inner, frameStyle));

        if (pageRows == 0) {
            lines.add(adminRow("  " + Theme.render(Theme.adminDim(), "No users found."), inner, frameStyle));
            for (int r = 1; r < pageSize; r++) {
                lines.add(adminRow("", inner, frameStyle));
            }
        } else {
            for (int i = pageStart; i < pageEnd; i++) {
                User u = displayUsers.get(i);
                boolean isSel = (i - pageStart) == curSel;
                String indicator = isSel ? "> " : "  ";
                String idRaw = "#" + u.getId();
                String nameRaw = Theme.truncate(u.getFullName(), nameW - 2);
                String emailRaw = Theme.truncate(u.getEmail(), emailW - 2);
                String statusDot = "●";
                String statusText = u.isBlocked() ? "Blocked" : "Active";

                String indCol = isSel ? Theme.render(Theme.rowSelectedBadgeKey(), indicator) : indicator;
                String idPadded = Theme.padRight(idRaw, idW);
                String idCol = isSel ? Theme.render(Theme.rowSelectedId(), idPadded) : Theme.render(Theme.adminHeader(), idPadded);
                String namePadded = Theme.padRight(nameRaw, nameW);
                String nameCol = isSel ? Theme.render(Theme.rowSelected(), namePadded) : Theme.render(Theme.adminText(), namePadded);
                String emailPadded = Theme.padRight(emailRaw, emailW);
                String emailCol = isSel ? Theme.render(Theme.rowSelectedDim(), emailPadded) : Theme.render(Theme.adminDim(), emailPadded);

                int statUsed = Theme.width(statusDot) + 1 + Theme.width(statusText);
                int statPad = Math.max(0, statusW - statUsed);
                String padSpaces = " ".repeat(statPad);

                String dotCol = isSel
                        ? (u.isBlocked()
                                ? Theme.render(Theme.err().background(Theme.ADMIN_SELECT_BG), statusDot)
                                : Theme.render(Theme.rowSelectedDotActive(), statusDot))
                        : (u.isBlocked()
                                ? Theme.render(Theme.err(), statusDot)
                                : Theme.render(Theme.adminDotActive(), statusDot));
                String statTextCol = isSel
                        ? Theme.render(Theme.rowSelected(), " " + statusText)
                        : Theme.render(Theme.adminText(), " " + statusText);
                String padCol = isSel
                        ? Theme.render(Theme.rowSelected(), padSpaces)
                        : padSpaces;
                String statusCol = dotCol + statTextCol + padCol;

                lines.add(adminRow(indCol + idCol + nameCol + emailCol + statusCol, inner, frameStyle));
            }
            for (int r = pageRows; r < pageSize; r++) {
                lines.add(adminRow("", inner, frameStyle));
            }
        }
        lines.add(adminRow("", inner, frameStyle));

        // 5. Selected User Actions
        if (currentSelUser != null) {
            lines.add(adminRow("  " + Theme.render(Theme.adminHeader(), "SELECTED USER"), inner, frameStyle));
            String statusBadge = currentSelUser.isBlocked() ? "Blocked" : "Active";
            String info = currentSelUser.getFullName() + "  ·  " + currentSelUser.getEmail() + "  ·  " + statusBadge;
            lines.add(adminRow("  " + Theme.render(Theme.adminText(), info), inner, frameStyle));
            lines.add(adminRow("", inner, frameStyle));

            String blockLabel = currentSelUser.isBlocked() ? "Unblock User" : "Block User";
            String actLine = "  " + Theme.render(Theme.adminBadgeKey(), "[V]") + Theme.render(Theme.adminText(), " View Details    ")
                    + Theme.render(Theme.adminBadgePurple(), "[B]") + Theme.render(Theme.adminText(), " " + blockLabel + "    ")
                    + Theme.render(Theme.adminBadgeRed(), "[D]") + Theme.render(Theme.adminText(), " Delete User");
            lines.add(adminRow(actLine, inner, frameStyle));
        } else {
            lines.add(adminRow("  " + Theme.render(Theme.adminDim(), "SELECTED USER"), inner, frameStyle));
            lines.add(adminRow("  " + Theme.render(Theme.adminDim(), "No user selected"), inner, frameStyle));
            lines.add(adminRow("", inner, frameStyle));
            lines.add(adminRow("", inner, frameStyle));
        }
        lines.add(adminRow("", inner, frameStyle));

        // 6. Pagination
        String pageLeft = String.format("Page %d / %d", curPage + 1, totalPages);
        int startNum = totalUsers == 0 ? 0 : pageStart + 1;
        int endNum = pageEnd;
        String pageRight = String.format("Showing %d–%d of %d users", startNum, endNum, totalUsers);
        int pLeftW = Theme.width(pageLeft);
        int pRightW = Theme.width(pageRight);
        int pGap = Math.max(1, (inner - 4) - pLeftW - pRightW);
        String pageLine = "  " + Theme.render(Theme.adminDim(), pageLeft) + " ".repeat(pGap) + Theme.render(Theme.adminDim(), pageRight);
        lines.add(adminRow(pageLine, inner, frameStyle));

        // 7. Optional Status Row
        if (status != null && !status.isEmpty()) {
            Style st = statusErr ? Theme.err() : Theme.ok();
            String prefix = statusErr ? "ERROR: " : "OK: ";
            lines.add(adminRow("  " + Theme.render(st, prefix + status), inner, frameStyle));
        }

        // 8. Footer Divider and Footer
        lines.add(adminDivider(inner, frameStyle));

        String fSel = Theme.render(Theme.adminBadgeKey(), "↑↓") + Theme.render(Theme.adminText(), " Select");
        String fPage = Theme.render(Theme.adminBadgeKey(), "←→") + Theme.render(Theme.adminText(), " Page");
        String fSearch = Theme.render(Theme.adminBadgeGreen(), "S") + Theme.render(Theme.adminText(), " Search");
        String fClear = Theme.render(Theme.adminBadgeYellow(), "X") + Theme.render(Theme.adminText(), " Clear");
        String fBack = Theme.render(Theme.adminBadgePurple(), "B") + Theme.render(Theme.adminText(), " Back");
        String fHome = Theme.render(Theme.adminBadgeKey(), "H") + Theme.render(Theme.adminText(), " Home");
        String fQuit = Theme.render(Theme.adminBadgeRed(), "Q") + Theme.render(Theme.adminText(), " Quit");

        String footerContent = "  " + fSel + "    " + fPage + "    " + fSearch + "    " + fClear + "    " + fBack + "    " + fHome + "    " + fQuit;
        lines.add(adminRow(footerContent, inner, frameStyle));

        // 9. Bottom outer border
        lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));

        return String.join("\n", lines);
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

        int pageSize = 5;
        int totalAll = users == null ? 0 : users.size();
        int pageStart = userPage * pageSize;
        int pageEnd = Math.min(pageStart + pageSize, totalAll);

        String[] headers = { "ID", "NAME", "EMAIL", "STATUS", "ROLE", "ACTIONS" };
        String[][] rows = new String[pageEnd - pageStart][6];
        for (int i = pageStart; i < pageEnd; i++) {
            User u = users.get(i);
            String id = "#" + u.getId();
            String name = u.getFullName();
            String email = u.getEmail();
            String status = u.isBlocked() ? "\uD83D\uDD12 Blocked" : "\u25CF Active";
            String role = u.getRole().name();
            String actions = "[👁] [✏] [🗑]";
            rows[i - pageStart] = new String[] { id, name, email, status, role, actions };
        }

        body.addAll(flatTable(headers, rows, Math.max(0, sel), width - 4));

        if (totalAll > 0) {
            body.add(Line.of(Theme.dim(), String.format(
                    "  Page %d/%d   \u2014   %s%d user(s)%s",
                    userPage + 1, Math.max(1, (int) Math.ceil((double) totalAll / pageSize)),
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
        body.addAll(flatTable(headers, rows,
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

    List<Line> addDetailSectionLines(List<Line> body, String label, String value, int inner) {
        List<Line> sectionLines = new ArrayList<>();
        if (value != null && !value.isBlank()) {
            sectionLines.add(Line.blank());
            sectionLines.add(Line.of(Theme.headingCyan(), "  \u270F " + label));
            sectionLines.addAll(ScreenKit.paragraph(value, inner));
        }
        return sectionLines;
    }

    String renderAdminGoalsPage(int fw) {
        int inner = fw - 2;
        List<String> lines = new ArrayList<>();
        Style frameStyle = Theme.adminBorder();

        if (goals == null) {
            refreshAdminGoals();
        }

        int totalAll = goals == null ? 0 : goals.size();
        int pageSize = userPageSize;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalAll / pageSize));
        if (userPage < 0) userPage = 0;
        if (userPage >= totalPages) userPage = totalPages - 1;

        int pageStart = userPage * pageSize;
        int pageEnd = Math.min(pageStart + pageSize, totalAll);
        int pageRows = pageEnd - pageStart;
        if (sel >= pageRows && pageRows > 0) {
            sel = pageRows - 1;
        }

        // 1. Top outer border
        String titleRaw = "┌─ 🎯 LIFEForge / GOAL MANAGEMENT (Admin) ";
        int usedW = Theme.width(titleRaw);
        int dashCount = Math.max(0, (inner + 2) - usedW - 1);
        String topBorder = Theme.render(frameStyle, "┌─ ")
                + "🎯 "
                + Theme.render(Theme.adminHeader(), "LIFEForge")
                + Theme.render(Theme.dim(), " / ")
                + Theme.render(Theme.adminHeaderSub(), "GOAL MANAGEMENT (Admin)")
                + Theme.render(frameStyle, " " + "─".repeat(dashCount) + "┐");
        lines.add(topBorder);

        // 2. Table Column Widths
        int idW = 6;
        int keyW = 17;
        int statW = 16;
        int flex = inner - (idW + 1 + keyW + 1 + statW + 1);
        int nameW = Math.max(30, flex);

        // Top table divider
        String topTableDiv = Theme.render(frameStyle, "├" + "─".repeat(idW) + "┬" + "─".repeat(nameW) + "┬" + "─".repeat(keyW) + "┬" + "─".repeat(statW) + "┤");
        lines.add(topTableDiv);

        // Header row
        String hId = Theme.padRight(" ID", idW);
        String hName = Theme.padRight(" GOAL NAME", nameW);
        String hKey = Theme.padRight(" SYSTEM KEY", keyW);
        String hStat = Theme.padRight(" STATUS", statW);
        String headerRow = Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hId)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hName)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hKey)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hStat)
                + Theme.render(frameStyle, "│");
        lines.add(headerRow);

        // Mid table divider
        String midTableDiv = Theme.render(frameStyle, "├" + "─".repeat(idW) + "┼" + "─".repeat(nameW) + "┼" + "─".repeat(keyW) + "┼" + "─".repeat(statW) + "┤");
        lines.add(midTableDiv);

        // Data rows (pageSize = 8)
        for (int i = 0; i < pageSize; i++) {
            int globalIdx = pageStart + i;
            if (globalIdx < pageEnd && goals != null) {
                Goal g = goals.get(globalIdx);
                boolean isSel = (i == sel);
                boolean isActive = g.isActive();

                String idRaw = (isSel ? "> #" : "  #") + g.getId();
                String idText = Theme.padRight(idRaw, idW);

                String nameRaw = " " + (g.getName() == null ? "" : g.getName());
                String nameText = Theme.padRight(Theme.truncate(nameRaw, nameW), nameW);

                String codeRaw = " " + (g.getCode() == null ? "" : g.getCode().toUpperCase(Locale.ROOT));
                String keyText = Theme.padRight(Theme.truncate(codeRaw, keyW), keyW);

                String statIconText = isActive ? " ● Active" : " ○ Inactive";
                int statIconW = isActive ? 9 : 11;
                int statPad = Math.max(0, statW - statIconW);

                if (isSel) {
                    Style bg = Theme.rowSelected();
                    Style dotSt = isActive ? Theme.rowSelectedDotActive() : Theme.rowSelectedDim();
                    String rowStr = Theme.render(frameStyle, "│")
                            + Theme.render(Theme.rowSelectedId(), idText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(bg, nameText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(Theme.rowSelectedDim(), keyText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(dotSt, statIconText)
                            + Theme.render(bg, " ".repeat(statPad))
                            + Theme.render(frameStyle, "│");
                    lines.add(rowStr);
                } else {
                    Style statSt = isActive ? Theme.adminBadgeGreen() : Theme.adminDim();
                    String rowStr = Theme.render(frameStyle, "│")
                            + Theme.render(Theme.adminDim(), idText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(Theme.adminText(), nameText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(Theme.adminBadgePurple(), keyText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(statSt, statIconText)
                            + " ".repeat(statPad)
                            + Theme.render(frameStyle, "│");
                    lines.add(rowStr);
                }
            } else {
                lines.add(Theme.render(frameStyle, "│")
                        + " ".repeat(idW)
                        + Theme.render(frameStyle, "│")
                        + " ".repeat(nameW)
                        + Theme.render(frameStyle, "│")
                        + " ".repeat(keyW)
                        + Theme.render(frameStyle, "│")
                        + " ".repeat(statW)
                        + Theme.render(frameStyle, "│"));
            }
        }

        // Bottom table divider
        String botTableDiv = Theme.render(frameStyle, "├" + "─".repeat(idW) + "┴" + "─".repeat(nameW) + "┴" + "─".repeat(keyW) + "┴" + "─".repeat(statW) + "┤");
        lines.add(botTableDiv);

        // Summary & Pagination row
        int startNum = totalAll == 0 ? 0 : pageStart + 1;
        int endNum = pageEnd;
        String summaryLeft = "  Showing " + startNum + "-" + endNum + " of " + totalAll;
        String summaryRight = "[←/→] Page " + (userPage + 1) + " of " + totalPages + "   ";
        int sGap = Math.max(1, inner - Theme.width(summaryLeft) - Theme.width(summaryRight));
        String summaryRow = Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminDim(), summaryLeft)
                + " ".repeat(sGap)
                + Theme.render(Theme.adminDim(), summaryRight)
                + Theme.render(frameStyle, "│");
        lines.add(summaryRow);

        // 5. Footer divider
        lines.add(adminDivider(inner, frameStyle));

        // 6. Action hints inside card
        String gap = "    ";
        String footerLine = "  "
                + Theme.render(Theme.adminBadgeKey(), "↑↓") + Theme.render(Theme.adminText(), " Select" + gap)
                + Theme.render(Theme.adminBadgeKey(), "Enter") + Theme.render(Theme.adminText(), " Edit" + gap)
                + Theme.render(Theme.adminBadgeGreen(), "N") + Theme.render(Theme.adminText(), " New" + gap)
                + Theme.render(Theme.adminBadgeYellow(), "T") + Theme.render(Theme.adminText(), " Toggle" + gap)
                + Theme.render(Theme.adminBadgeRed(), "D") + Theme.render(Theme.adminText(), " Delete" + gap)
                + Theme.render(Theme.adminBadgePurple(), "B") + Theme.render(Theme.adminText(), " Back" + gap)
                + Theme.render(Theme.adminBadgeRed(), "Q") + Theme.render(Theme.adminText(), " Quit");
        lines.add(adminRow(footerLine, inner, frameStyle));

        // 7. Status line (if any)
        if (status != null && !status.isEmpty()) {
            Style st = statusErr ? Theme.err() : Theme.ok();
            String prefix = statusErr ? " ERROR: " : " OK: ";
            lines.add(adminRow("  " + Theme.render(st, prefix + status), inner, frameStyle));
        }

        // 8. Bottom card border
        lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));

        return String.join("\n", lines);
    }

    String renderAdminCatsPage(int fw) {
        int inner = fw - 2;
        List<String> lines = new ArrayList<>();
        Style frameStyle = Theme.adminBorder();

        if (catList == null) {
            refreshAdminCats();
        }

        if (catList != null) {
            catList.sort(Comparator.comparing(RecommendationCategory::getId, Comparator.nullsLast(Long::compareTo)));
        }

        int totalAll = catList == null ? 0 : catList.size();
        int pageSize = userPageSize;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalAll / pageSize));
        if (userPage < 0) userPage = 0;
        if (userPage >= totalPages) userPage = totalPages - 1;

        int pageStart = userPage * pageSize;
        int pageEnd = Math.min(pageStart + pageSize, totalAll);
        int pageRows = pageEnd - pageStart;
        if (sel >= pageRows && pageRows > 0) {
            sel = pageRows - 1;
        }

        // 1. Top outer border
        String titleRaw = "┌─ 📁 LIFEForge / CATEGORY MANAGEMENT (Admin) ";
        int usedW = Theme.width(titleRaw);
        int dashCount = Math.max(0, (inner + 2) - usedW - 1);
        String topBorder = Theme.render(frameStyle, "┌─ ")
                + "📁 "
                + Theme.render(Theme.adminHeader(), "LIFEForge")
                + Theme.render(Theme.dim(), " / ")
                + Theme.render(Theme.adminHeaderSub(), "CATEGORY MANAGEMENT (Admin)")
                + Theme.render(frameStyle, " " + "─".repeat(dashCount) + "┐");
        lines.add(topBorder);

        // 2. Table Column Widths
        int idW = 6;
        int parentW = 17;
        int statW = 16;
        int flex = inner - (idW + 1 + parentW + 1 + statW + 1);
        int nameW = Math.max(30, flex);

        // Top table divider
        String topTableDiv = Theme.render(frameStyle, "├" + "─".repeat(idW) + "┬" + "─".repeat(nameW) + "┬" + "─".repeat(parentW) + "┬" + "─".repeat(statW) + "┤");
        lines.add(topTableDiv);

        // Header row
        String hId = Theme.padRight(" ID", idW);
        String hName = Theme.padRight(" CATEGORY NAME", nameW);
        String hParent = Theme.padRight(" PARENT GROUP", parentW);
        String hStat = Theme.padRight(" STATUS", statW);
        String headerRow = Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hId)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hName)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hParent)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hStat)
                + Theme.render(frameStyle, "│");
        lines.add(headerRow);

        // Mid table divider
        String midTableDiv = Theme.render(frameStyle, "├" + "─".repeat(idW) + "┼" + "─".repeat(nameW) + "┼" + "─".repeat(parentW) + "┼" + "─".repeat(statW) + "┤");
        lines.add(midTableDiv);

        Map<Long, String> parentNames = new HashMap<>();
        if (catList != null) {
            for (RecommendationCategory c : catList) {
                if (c.getId() != null && c.getName() != null) {
                    parentNames.put(c.getId(), c.getName());
                }
            }
        }

        // Data rows (pageSize = 8)
        for (int i = 0; i < pageSize; i++) {
            int globalIdx = pageStart + i;
            if (globalIdx < pageEnd && catList != null) {
                RecommendationCategory c = catList.get(globalIdx);
                boolean isSel = (i == sel);
                boolean isActive = !inactiveCatIds.contains(c.getId());

                String idRaw = (isSel ? "> #" : "  #") + c.getId();
                String idText = Theme.padRight(idRaw, idW);

                String nameRaw = " " + (c.getName() == null ? "" : c.getName());
                String nameText = Theme.padRight(Theme.truncate(nameRaw, nameW), nameW);

                String parentStr = c.getParentCategoryId() == null
                        ? "Root (None)"
                        : parentNames.getOrDefault(c.getParentCategoryId(), "#" + c.getParentCategoryId());
                String parentRaw = " " + parentStr;
                String parentText = Theme.padRight(Theme.truncate(parentRaw, parentW), parentW);

                String statIconText = isActive ? " ● Active" : " ○ Inactive";
                int statIconW = isActive ? 9 : 11;
                int statPad = Math.max(0, statW - statIconW);

                if (isSel) {
                    Style bg = Theme.rowSelected();
                    Style dotSt = isActive ? Theme.rowSelectedDotActive() : Theme.rowSelectedDim();
                    String rowStr = Theme.render(frameStyle, "│")
                            + Theme.render(Theme.rowSelectedId(), idText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(bg, nameText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(Theme.rowSelectedDim(), parentText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(dotSt, statIconText)
                            + Theme.render(bg, " ".repeat(statPad))
                            + Theme.render(frameStyle, "│");
                    lines.add(rowStr);
                } else {
                    Style statSt = isActive ? Theme.adminBadgeGreen() : Theme.adminDim();
                    String rowStr = Theme.render(frameStyle, "│")
                            + Theme.render(Theme.adminDim(), idText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(Theme.adminText(), nameText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(Theme.adminBadgePurple(), parentText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(statSt, statIconText)
                            + " ".repeat(statPad)
                            + Theme.render(frameStyle, "│");
                    lines.add(rowStr);
                }
            } else {
                lines.add(Theme.render(frameStyle, "│")
                        + " ".repeat(idW)
                        + Theme.render(frameStyle, "│")
                        + " ".repeat(nameW)
                        + Theme.render(frameStyle, "│")
                        + " ".repeat(parentW)
                        + Theme.render(frameStyle, "│")
                        + " ".repeat(statW)
                        + Theme.render(frameStyle, "│"));
            }
        }

        // Bottom table divider
        String botTableDiv = Theme.render(frameStyle, "├" + "─".repeat(idW) + "┴" + "─".repeat(nameW) + "┴" + "─".repeat(parentW) + "┴" + "─".repeat(statW) + "┤");
        lines.add(botTableDiv);

        // Summary & Pagination row
        int startNum = totalAll == 0 ? 0 : pageStart + 1;
        int endNum = pageEnd;
        String summaryLeft = "  Showing " + startNum + "-" + endNum + " of " + totalAll;
        String summaryRight = "[←/→] Page " + (userPage + 1) + " of " + totalPages + "   ";
        int sGap = Math.max(1, inner - Theme.width(summaryLeft) - Theme.width(summaryRight));
        String summaryRow = Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminDim(), summaryLeft)
                + " ".repeat(sGap)
                + Theme.render(Theme.adminDim(), summaryRight)
                + Theme.render(frameStyle, "│");
        lines.add(summaryRow);

        // 5. Footer divider
        lines.add(adminDivider(inner, frameStyle));

        // 6. Action hints inside card
        String gap = "    ";
        String footerLine = "  "
                + Theme.render(Theme.adminBadgeKey(), "↑↓") + Theme.render(Theme.adminText(), " Select" + gap)
                + Theme.render(Theme.adminBadgeKey(), "Enter") + Theme.render(Theme.adminText(), " Edit" + gap)
                + Theme.render(Theme.adminBadgeGreen(), "N") + Theme.render(Theme.adminText(), " New" + gap)
                + Theme.render(Theme.adminBadgeYellow(), "T") + Theme.render(Theme.adminText(), " Toggle" + gap)
                + Theme.render(Theme.adminBadgeRed(), "D") + Theme.render(Theme.adminText(), " Delete" + gap)
                + Theme.render(Theme.adminBadgePurple(), "B") + Theme.render(Theme.adminText(), " Back" + gap)
                + Theme.render(Theme.adminBadgeRed(), "Q") + Theme.render(Theme.adminText(), " Quit");
        lines.add(adminRow(footerLine, inner, frameStyle));

        // 7. Status line (if any)
        if (status != null && !status.isEmpty()) {
            Style st = statusErr ? Theme.err() : Theme.ok();
            String prefix = statusErr ? " ERROR: " : " OK: ";
            lines.add(adminRow("  " + Theme.render(st, prefix + status), inner, frameStyle));
        }

        // 8. Bottom card border
        lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));

        return String.join("\n", lines);
    }

    private String renderMiniBar(double percentage, int barWidth) {
        int filled = (int) Math.round((percentage / 100.0) * barWidth);
        filled = Math.max(0, Math.min(barWidth, filled));
        return "[" + "■".repeat(filled) + "□".repeat(barWidth - filled) + "]";
    }

    private String formatMiniBar(double percentage, int barWidth) {
        int filled = (int) Math.round((percentage / 100.0) * barWidth);
        filled = Math.max(0, Math.min(barWidth, filled));
        return Theme.render(Theme.adminDim(), "[")
                + Theme.render(Theme.adminBadgeGreen(), "■".repeat(filled))
                + Theme.render(Theme.adminDim(), "□".repeat(barWidth - filled) + "]");
    }

    String renderAdminAnalyticsPage(int fw) {
        int inner = fw - 2;
        List<String> lines = new ArrayList<>();
        Style frameStyle = Theme.adminBorder();
        Style tableBorder = Theme.adminBorder();

        if (analytics == null) {
            refreshAnalytics();
        }

        // 1. Top outer border
        String titleRaw = "┌─ 📊 LIFEForge / PLATFORM ANALYTICS ";
        int usedW = Theme.width(titleRaw);
        int dashCount = Math.max(0, (inner + 2) - usedW - 1);
        String topBorder = Theme.render(frameStyle, "┌─ ")
                + "📊 "
                + Theme.render(Theme.adminHeader(), "LIFEForge")
                + Theme.render(Theme.dim(), " / ")
                + Theme.render(Theme.adminHeaderSub(), "PLATFORM ANALYTICS")
                + Theme.render(frameStyle, " " + "─".repeat(dashCount) + "┐");
        lines.add(topBorder);

        // 2. Subtitle
        lines.add(padLine(Theme.render(Theme.dim(), "Platform health, engagement, and goal distribution"), fw, frameStyle));

        // 3. Section divider
        lines.add(adminDivider(inner, frameStyle));

        // 4. Component A: [ PLATFORM KPI SNAPSHOT ]
        lines.add(padLine(Theme.render(Theme.headingCyan(), "[ PLATFORM KPI SNAPSHOT ]"), fw, frameStyle));

        String kpiTop = Theme.render(tableBorder, "┌" + "─".repeat(18) + "┬" + "─".repeat(18) + "┬" + "─".repeat(26) + "┐");
        lines.add(padLine(kpiTop, fw, frameStyle));

        long tUsers = analytics == null ? 0 : analytics.totalUsers;
        long aUsers = analytics == null ? 0 : analytics.activeUsers;
        int aPct = (int) Math.round(tUsers == 0 ? 0 : (aUsers * 100.0 / tUsers));
        long tRecs = analytics == null ? 0 : analytics.totalRecommendations;
        long sRecs = analytics == null ? 0 : analytics.totalSavedRecommendations;

        String kpi1 = Theme.render(Theme.adminBadgePurple(), " USERS: ") + Theme.render(Theme.adminText(), Theme.padRight(tUsers + " Total", 10));
        String kpi2 = Theme.render(Theme.adminBadgeGreen(), " ACTIVE: ") + Theme.render(Theme.adminText(), Theme.padRight(aUsers + " (" + aPct + "%)", 9));
        String kpi3 = Theme.render(Theme.adminBadgeYellow(), " RECS: ") + Theme.render(Theme.adminText(), Theme.padRight(tRecs + " Live / " + sRecs + " Saved", 19));
        String kpiRow = Theme.render(tableBorder, "│") + kpi1 + Theme.render(tableBorder, "│") + kpi2 + Theme.render(tableBorder, "│") + kpi3 + Theme.render(tableBorder, "│");
        lines.add(padLine(kpiRow, fw, frameStyle));

        String kpiBot = Theme.render(tableBorder, "└" + "─".repeat(18) + "┴" + "─".repeat(18) + "┴" + "─".repeat(26) + "┘");
        lines.add(padLine(kpiBot, fw, frameStyle));

        // 5. Blank spacer
        lines.add(padLine("", fw, frameStyle));

        // 6. Component B: [ GOAL DISTRIBUTION ]
        List<AnalyticsService.GoalDistributionEntry> gd = safe(analytics == null ? null : analytics.goalDistribution);
        long totalGoalUsers = 0;
        for (AnalyticsService.GoalDistributionEntry e : gd) {
            totalGoalUsers += e.userCount;
        }
        String gdTitle = "[ GOAL DISTRIBUTION ]";
        String gdCounter = "Total Goals: " + gd.size();
        int gdGap = Math.max(1, 66 - Theme.width(gdTitle) - Theme.width(gdCounter));
        String gdHeaderLine = Theme.render(Theme.headingCyan(), gdTitle)
                + " ".repeat(gdGap)
                + Theme.render(Theme.dim(), gdCounter);
        lines.add(padLine(gdHeaderLine, fw, frameStyle));

        String gdTop = Theme.render(tableBorder, "┌" + "─".repeat(23) + "┬" + "─".repeat(8) + "┬" + "─".repeat(10) + "┬" + "─".repeat(20) + "┐");
        lines.add(padLine(gdTop, fw, frameStyle));

        String gdCols = Theme.render(tableBorder, "│")
                + Theme.render(Theme.adminHeader(), Theme.padRight(" GOAL", 23))
                + Theme.render(tableBorder, "│")
                + Theme.render(Theme.adminHeader(), Theme.padRight(" USERS", 8))
                + Theme.render(tableBorder, "│")
                + Theme.render(Theme.adminHeader(), Theme.padRight(" SHARE", 10))
                + Theme.render(tableBorder, "│")
                + Theme.render(Theme.adminHeader(), Theme.padRight(" DISTRIBUTION", 20))
                + Theme.render(tableBorder, "│");
        lines.add(padLine(gdCols, fw, frameStyle));

        String gdMid = Theme.render(tableBorder, "├" + "─".repeat(23) + "┼" + "─".repeat(8) + "┼" + "─".repeat(10) + "┼" + "─".repeat(20) + "┤");
        lines.add(padLine(gdMid, fw, frameStyle));

        if (gd.isEmpty()) {
            String emptyRow = Theme.render(tableBorder, "│") + Theme.render(Theme.dim(), Theme.padRight(" No goal selections yet.", 65)) + Theme.render(tableBorder, "│");
            lines.add(padLine(emptyRow, fw, frameStyle));
        } else {
            for (AnalyticsService.GoalDistributionEntry e : gd) {
                double pct = totalGoalUsers == 0 ? 0 : (e.userCount * 100.0 / totalGoalUsers);
                String gName = Theme.truncate(" " + (e.goalName == null ? "Unknown Goal" : e.goalName), 23);
                String gCol1 = Theme.padRight(gName, 23);
                String gCol2 = Theme.padCenter(String.valueOf(e.userCount), 8);
                String gCol3 = Theme.padCenter(String.format(Locale.ROOT, "%.1f%%", pct), 10);
                String gCol4 = " " + formatMiniBar(pct, 16) + " ";
                String row = Theme.render(tableBorder, "│")
                        + Theme.render(Theme.adminText(), gCol1)
                        + Theme.render(tableBorder, "│")
                        + Theme.render(Theme.adminBadgePurple(), gCol2)
                        + Theme.render(tableBorder, "│")
                        + Theme.render(Theme.adminDim(), gCol3)
                        + Theme.render(tableBorder, "│")
                        + gCol4
                        + Theme.render(tableBorder, "│");
                lines.add(padLine(row, fw, frameStyle));
            }
        }

        String gdBot = Theme.render(tableBorder, "└" + "─".repeat(23) + "┴" + "─".repeat(8) + "┴" + "─".repeat(10) + "┴" + "─".repeat(20) + "┘");
        lines.add(padLine(gdBot, fw, frameStyle));

        // 7. Blank spacer
        lines.add(padLine("", fw, frameStyle));

        // 8. Component C: [ POPULAR CATEGORIES ]
        List<AnalyticsService.CategoryPopularityEntry> pc = safe(analytics == null ? null : analytics.popularCategories);
        long totalSaves = 0;
        for (AnalyticsService.CategoryPopularityEntry e : pc) {
            totalSaves += e.saveCount;
        }
        String pcTitle = "[ POPULAR CATEGORIES ]";
        String pcCounter = "Total Saves: " + totalSaves;
        int pcGap = Math.max(1, 66 - Theme.width(pcTitle) - Theme.width(pcCounter));
        String pcHeaderLine = Theme.render(Theme.headingCyan(), pcTitle)
                + " ".repeat(pcGap)
                + Theme.render(Theme.dim(), pcCounter);
        lines.add(padLine(pcHeaderLine, fw, frameStyle));

        String pcTop = Theme.render(tableBorder, "┌" + "─".repeat(23) + "┬" + "─".repeat(8) + "┬" + "─".repeat(10) + "┬" + "─".repeat(20) + "┐");
        lines.add(padLine(pcTop, fw, frameStyle));

        String pcCols = Theme.render(tableBorder, "│")
                + Theme.render(Theme.adminHeader(), Theme.padRight(" CATEGORY", 23))
                + Theme.render(tableBorder, "│")
                + Theme.render(Theme.adminHeader(), Theme.padRight(" SAVES", 8))
                + Theme.render(tableBorder, "│")
                + Theme.render(Theme.adminHeader(), Theme.padRight(" SHARE", 10))
                + Theme.render(tableBorder, "│")
                + Theme.render(Theme.adminHeader(), Theme.padRight(" POPULARITY", 20))
                + Theme.render(tableBorder, "│");
        lines.add(padLine(pcCols, fw, frameStyle));

        String pcMid = Theme.render(tableBorder, "├" + "─".repeat(23) + "┼" + "─".repeat(8) + "┼" + "─".repeat(10) + "┼" + "─".repeat(20) + "┤");
        lines.add(padLine(pcMid, fw, frameStyle));

        if (pc.isEmpty()) {
            String emptyRow = Theme.render(tableBorder, "│") + Theme.render(Theme.dim(), Theme.padRight(" Nothing saved yet.", 65)) + Theme.render(tableBorder, "│");
            lines.add(padLine(emptyRow, fw, frameStyle));
        } else {
            for (AnalyticsService.CategoryPopularityEntry e : pc) {
                double pct = totalSaves == 0 ? 0 : (e.saveCount * 100.0 / totalSaves);
                String cName = Theme.truncate(" " + (e.categoryName == null ? "Unknown Category" : e.categoryName), 23);
                String cCol1 = Theme.padRight(cName, 23);
                String cCol2 = Theme.padCenter(String.valueOf(e.saveCount), 8);
                String cCol3 = Theme.padCenter(String.format(Locale.ROOT, "%.1f%%", pct), 10);
                String cCol4 = " " + formatMiniBar(pct, 16) + " ";
                String row = Theme.render(tableBorder, "│")
                        + Theme.render(Theme.adminText(), cCol1)
                        + Theme.render(tableBorder, "│")
                        + Theme.render(Theme.adminBadgePurple(), cCol2)
                        + Theme.render(tableBorder, "│")
                        + Theme.render(Theme.adminDim(), cCol3)
                        + Theme.render(tableBorder, "│")
                        + cCol4
                        + Theme.render(tableBorder, "│");
                lines.add(padLine(row, fw, frameStyle));
            }
        }

        String pcBot = Theme.render(tableBorder, "└" + "─".repeat(23) + "┴" + "─".repeat(8) + "┴" + "─".repeat(10) + "┴" + "─".repeat(20) + "┘");
        lines.add(padLine(pcBot, fw, frameStyle));

        // 9. Bottom card border
        lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));

        // 10. Blank line and single-line command footer
        lines.add("");
        String footer = " "
                + Theme.render(Theme.adminBadgeGreen(), "[R]") + " " + Theme.render(Theme.adminText(), "Refresh Stats") + "    "
                + Theme.render(Theme.adminBadgePurple(), "[H]") + " " + Theme.render(Theme.adminText(), "Home") + "    "
                + Theme.render(Theme.adminBadgeYellow(), "[B]") + " " + Theme.render(Theme.adminText(), "Back") + "    "
                + Theme.render(Theme.adminBadgeRed(), "[Q]") + " " + Theme.render(Theme.adminText(), "Quit");
        lines.add(footer);

        return String.join("\n", lines);
    }

    String renderAdminAuditPage(int fw) {
        int inner = fw - 2;
        List<String> lines = new ArrayList<>();
        Style frameStyle = Theme.adminBorder();

        if (logs == null) {
            refreshLogs();
        }

        List<AuditLog> view = safe(logs);
        int totalAll = view.size();
        int pageSize = auditPageSize;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalAll / pageSize));
        if (auditPage < 0) auditPage = 0;
        if (auditPage >= totalPages) auditPage = totalPages - 1;

        int pageStart = auditPage * pageSize;
        int pageEnd = Math.min(pageStart + pageSize, totalAll);
        int pageRows = pageEnd - pageStart;
        if (sel >= pageRows && pageRows > 0) {
            sel = pageRows - 1;
        }

        // 1. Top outer border
        String titleRaw = "┌─ 📋 LIFEForge / AUDIT LOGS (Admin) ";
        int usedW = Theme.width(titleRaw);
        int dashCount = Math.max(0, (inner + 2) - usedW - 1);
        String topBorder = Theme.render(frameStyle, "┌─ ")
                + "📋 "
                + Theme.render(Theme.adminHeader(), "LIFEForge")
                + Theme.render(Theme.dim(), " / ")
                + Theme.render(Theme.adminHeaderSub(), "AUDIT LOGS (Admin)")
                + Theme.render(frameStyle, " " + "─".repeat(dashCount) + "┐");
        lines.add(topBorder);

        // Sub-header filter line if active search query exists
        if (auditSearchQuery != null && !auditSearchQuery.trim().isEmpty()) {
            String filterText = "  " + Theme.render(Theme.adminHeader(), "Filter: ")
                    + Theme.render(Theme.adminBadgeYellow(), "\"" + auditSearchQuery.trim() + "\"")
                    + Theme.render(Theme.adminDim(), " (Showing " + totalAll + " events)");
            lines.add(adminRow(filterText, inner, frameStyle));
        }

        // 2. Table Column Widths (80 Total Columns)
        // Outer frame = 80 chars, inner = 78 chars.
        // Columns: TIMESTAMP (14), ACTOR (10), ACTION (14), TARGET (10), DETAILS (26)
        // 14 + 1 + 10 + 1 + 14 + 1 + 10 + 1 + 26 = 78 inner
        // + 2 outer borders = 80 total.
        int timeW = 14;
        int actorW = 10;
        int actionW = 14;
        int targetW = 10;
        int flex = inner - (timeW + 1 + actorW + 1 + actionW + 1 + targetW + 1);
        int detailsW = Math.max(18, flex);

        // Top table divider
        String topTableDiv = Theme.render(frameStyle, "├"
                + "─".repeat(timeW) + "┬"
                + "─".repeat(actorW) + "┬"
                + "─".repeat(actionW) + "┬"
                + "─".repeat(targetW) + "┬"
                + "─".repeat(detailsW) + "┤");
        lines.add(topTableDiv);

        // Header row
        String hTime = Theme.padRight(" TIMESTAMP", timeW);
        String hActor = Theme.padRight(" ACTOR", actorW);
        String hAction = Theme.padRight(" ACTION", actionW);
        String hTarget = Theme.padRight(" TARGET", targetW);
        String hDetails = Theme.padRight(" DETAILS", detailsW);
        String headerRow = Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hTime)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hActor)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hAction)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hTarget)
                + Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminHeader(), hDetails)
                + Theme.render(frameStyle, "│");
        lines.add(headerRow);

        // Mid table divider
        String midTableDiv = Theme.render(frameStyle, "├"
                + "─".repeat(timeW) + "┼"
                + "─".repeat(actorW) + "┼"
                + "─".repeat(actionW) + "┼"
                + "─".repeat(targetW) + "┼"
                + "─".repeat(detailsW) + "┤");
        lines.add(midTableDiv);

        // Data rows (pageSize = 8)
        for (int i = 0; i < pageSize; i++) {
            int globalIdx = pageStart + i;
            if (globalIdx < pageEnd) {
                AuditLog l = view.get(globalIdx);
                boolean isSel = (i == sel);

                String timeStr = l.getCreatedAt() == null ? "-" : l.getCreatedAt().format(AUDIT_TIME_FMT);
                String timeRaw = (isSel ? "> " : "  ") + timeStr;
                String timeText = Theme.padRight(timeRaw, timeW);

                String actorRaw = " " + formatAuditActor(l);
                String actorText = Theme.padRight(Theme.truncate(actorRaw, actorW), actorW);

                Object[] labeled = auditActionLabel(l.getAction());
                String actionTag = (String) labeled[0];
                Style actionSt = (Style) labeled[1];
                String actionRaw = " " + actionTag;
                String actionText = Theme.padRight(Theme.truncate(actionRaw, actionW), actionW);

                String targetRaw = " " + formatAuditTarget(l);
                String targetText = Theme.padRight(Theme.truncate(targetRaw, targetW), targetW);

                String detailsRaw = " " + formatAuditDetails(l);
                String detailsText = Theme.padRight(Theme.truncate(detailsRaw, detailsW), detailsW);

                if (isSel) {
                    Style bg = Theme.rowSelected();
                    Style actionSelSt = actionSt.background(Theme.ADMIN_SELECT_BG);
                    String rowStr = Theme.render(frameStyle, "│")
                            + Theme.render(Theme.rowSelectedId(), timeText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(bg, actorText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(actionSelSt, actionText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(Theme.rowSelectedBadgePurple(), targetText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(bg, detailsText)
                            + Theme.render(frameStyle, "│");
                    lines.add(rowStr);
                } else {
                    String rowStr = Theme.render(frameStyle, "│")
                            + Theme.render(Theme.adminDim(), timeText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(Theme.adminText(), actorText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(actionSt, actionText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(Theme.adminBadgePurple(), targetText)
                            + Theme.render(frameStyle, "│")
                            + Theme.render(Theme.adminText(), detailsText)
                            + Theme.render(frameStyle, "│");
                    lines.add(rowStr);
                }
            } else {
                lines.add(Theme.render(frameStyle, "│")
                        + " ".repeat(timeW)
                        + Theme.render(frameStyle, "│")
                        + " ".repeat(actorW)
                        + Theme.render(frameStyle, "│")
                        + " ".repeat(actionW)
                        + Theme.render(frameStyle, "│")
                        + " ".repeat(targetW)
                        + Theme.render(frameStyle, "│")
                        + " ".repeat(detailsW)
                        + Theme.render(frameStyle, "│"));
            }
        }

        // Bottom table divider
        String botTableDiv = Theme.render(frameStyle, "├"
                + "─".repeat(timeW) + "┴"
                + "─".repeat(actorW) + "┴"
                + "─".repeat(actionW) + "┴"
                + "─".repeat(targetW) + "┴"
                + "─".repeat(detailsW) + "┤");
        lines.add(botTableDiv);

        // Summary & Pagination row inside bottom card
        int startNum = totalAll == 0 ? 0 : pageStart + 1;
        int endNum = pageEnd;
        String summaryLeft = "  Showing " + startNum + "-" + endNum + " of " + totalAll;
        String summaryRight = "[←/→] Page " + (auditPage + 1) + " of " + totalPages + "   ";
        int sGap = Math.max(1, inner - Theme.width(summaryLeft) - Theme.width(summaryRight));
        String summaryRow = Theme.render(frameStyle, "│")
                + Theme.render(Theme.adminDim(), summaryLeft)
                + " ".repeat(sGap)
                + Theme.render(Theme.adminDim(), summaryRight)
                + Theme.render(frameStyle, "│");
        lines.add(summaryRow);

        // Optional status row inside card if status message is present
        if (status != null && !status.isEmpty()) {
            lines.add(adminDivider(inner, frameStyle));
            Style st = statusErr ? Theme.err() : Theme.ok();
            String prefix = statusErr ? " ERROR: " : " OK: ";
            lines.add(adminRow("  " + Theme.render(st, prefix + status), inner, frameStyle));
        }

        // Bottom card border
        lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));

        // Blank line & compact single-line command footer
        lines.add("");
        String gap = "   ";
        StringBuilder footer = new StringBuilder(" ");
        footer.append(Theme.render(Theme.adminBadgeKey(), "[↑/↓]")).append(" ").append(Theme.render(Theme.adminText(), "Row")).append(gap);
        footer.append(Theme.render(Theme.adminBadgeKey(), "[Enter]")).append(" ").append(Theme.render(Theme.adminText(), "View Details")).append(gap);
        footer.append(Theme.render(Theme.adminBadgeKey(), "[S]")).append(" ").append(Theme.render(Theme.adminText(), "Search")).append(gap);
        if (auditSearchQuery != null && !auditSearchQuery.trim().isEmpty()) {
            footer.append(Theme.render(Theme.adminBadgeYellow(), "[C]")).append(" ").append(Theme.render(Theme.adminText(), "Clear Search")).append(gap);
        }
        footer.append(Theme.render(Theme.adminBadgeGreen(), "[R]")).append(" ").append(Theme.render(Theme.adminText(), "Refresh")).append(gap);
        footer.append(Theme.render(Theme.adminBadgePurple(), "[B]")).append(" ").append(Theme.render(Theme.adminText(), "Back"));
        lines.add(footer.toString());

        return String.join("\n", lines);
    }

    public List<AuditLog> getLogs() {
        return logs;
    }

    public void setLogs(List<AuditLog> logs) {
        this.logs = logs;
    }

    public int getAuditPage() {
        return auditPage;
    }

    public void setAuditPage(int auditPage) {
        this.auditPage = auditPage;
    }

    public String getAuditSearchQuery() {
        return auditSearchQuery;
    }

    public void setAuditSearchQuery(String auditSearchQuery) {
        this.auditSearchQuery = (auditSearchQuery == null) ? "" : auditSearchQuery;
    }

    public AuditLog getSelAudit() {
        return selAudit;
    }

    public void setSelAudit(AuditLog selAudit) {
        this.selAudit = selAudit;
    }

    private String viewAdminGoals(List<Line> body) {
        int totalAll = goals == null ? 0 : goals.size();
        if (totalAll == 0) {
            body.add(Line.of(Theme.warn(), "  No goals yet. Press N to create one."));
            return "0 goal(s)";
        }

        int pageStart = userPage * userPageSize;
        int pageEnd = Math.min(pageStart + userPageSize, totalAll);

        String[] headers = { "ID", "GOAL NAME", "SYSTEM KEY", "STATUS" };
        String[][] rows = new String[pageEnd - pageStart][4];
        for (int i = pageStart; i < pageEnd; i++) {
            Goal g = goals.get(i);
            rows[i - pageStart] = new String[] {
                    "#" + g.getId(),
                    g.getName(),
                    g.getCode(),
                    g.isActive() ? "\u25CF Active" : "\u25CB Inactive"
            };
        }
        body.addAll(flatTable(headers, rows, Math.max(0, sel), width - 4));
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
        if (catList != null) {
            catList.sort(Comparator.comparing(RecommendationCategory::getId, Comparator.nullsLast(Long::compareTo)));
        }
        int totalAll = catList == null ? 0 : catList.size();
        if (totalAll == 0) {
            body.add(Line.of(Theme.warn(), "  No categories yet. Press N to create one."));
            return "0 category(ies)";
        }

        int pageStart = userPage * userPageSize;
        int pageEnd = Math.min(pageStart + userPageSize, totalAll);

        String[] headers = { "ID", "CATEGORY NAME", "PARENT GROUP", "STATUS" };
        String[][] rows = new String[pageEnd - pageStart][4];
        Map<Long, String> parentNames = new HashMap<>();
        for (RecommendationCategory c : catList) {
            parentNames.put(c.getId(), c.getName());
        }
        for (int i = pageStart; i < pageEnd; i++) {
            RecommendationCategory c = catList.get(i);
            String parent = c.getParentCategoryId() == null
                    ? "Root (None)" : parentNames.getOrDefault(c.getParentCategoryId(), "#" + c.getParentCategoryId());
            boolean active = !inactiveCatIds.contains(c.getId());
            rows[i - pageStart] = new String[] {
                    "#" + c.getId(),
                    c.getName(),
                    parent,
                    active ? "\u25CF Active" : "\u25CB Inactive"
            };
        }
        body.addAll(flatTable(headers, rows, Math.max(0, sel), width - 4));
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
            body.addAll(flatTable(gdHeaders, gdRows, -1, width - 4));
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

    // Rendered as plain label/value text (no boxed card) so this widget
    // doesn't draw its own inner border that duplicates/collides with the
    // single outer frame ScreenKit.page() already draws around the screen.
    private List<Line> kpiRow(String label1, String value1, String label2, String value2, int totalInnerWidth) {
        List<Line> out = new ArrayList<>();
        int gap = 4;
        int colWidth = Math.max(16, (totalInnerWidth - gap) / 2);

        out.add(Line.of(Theme.dim(), "  "
                + Theme.padRight(label1.toUpperCase(Locale.ROOT), colWidth)
                + " ".repeat(gap)
                + Theme.padRight(label2.toUpperCase(Locale.ROOT), colWidth)));
        out.add(Line.of(Theme.headingCyan(), "  "
                + Theme.padRight(truncate(value1, colWidth), colWidth)
                + " ".repeat(gap)
                + Theme.padRight(truncate(value2, colWidth), colWidth)));
        out.add(Line.blank());
        return out;
    }

    private String viewAdminAudit(List<Line> body) {
        List<AuditLog> view = safe(logs);
        int inner = Math.max(40, width - 2);

        // 1. Vertical breathing room above count
        body.add(Line.blank());

        // 2. Count line
        String count = "  " + view.size() + " audit event" + (view.size() == 1 ? "" : "s");
        if (!auditSearchQuery.isEmpty()) {
            count += "    ·    filter: \"" + auditSearchQuery + "\"";
        }
        body.add(Line.of(Theme.dim(), count));

        // 3. Spacing between count and table
        body.add(Line.blank());

        // 4. Table header & underline
        body.add(Line.of(Theme.headingPurple(), auditHeader()));
        body.add(Line.of(Theme.bar(), auditUnderline()));

        if (view.isEmpty()) {
            body.add(Line.blank());
            body.add(Line.of(Theme.warn(),
                    auditSearchQuery.isEmpty()
                            ? "  No audit events recorded yet."
                            : "  No audit events match your search."));
            body.add(Line.blank());
            return "Administrative activity and security history";
        }

        int pageStart = auditPage * auditPageSize;
        int pageEnd = Math.min(pageStart + auditPageSize, view.size());
        for (int i = pageStart; i < pageEnd; i++) {
            body.add(auditRow(view.get(i), (i - pageStart) == sel));
        }

        // 5. Vertical breathing room below table
        body.add(Line.blank());
        body.add(Line.blank());

        // 6. Pagination
        int pages = Math.max(1, (int) Math.ceil((double) view.size() / auditPageSize));
        String showing = "  Showing " + (pageStart + 1) + "–" + pageEnd + " of " + view.size();
        String pageStr = "Page " + (auditPage + 1) + " / " + pages;
        int gap = (inner - 2) - Theme.width(showing) - Theme.width(pageStr);
        String pagination = gap > 0 ? (showing + " ".repeat(gap) + pageStr) : (showing + "   " + pageStr);
        body.add(Line.of(Theme.dim(), pagination));

        return "Administrative activity and security history";
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

    String renderForgotPasswordPage(int fw) {
        int inner = fw - 2;
        List<String> lines = new ArrayList<>();
        Style frameStyle = Theme.bar();

        // 1. Top outer border
        String titleRaw = "┌─ 🔑 LIFEForge / FORGOT PASSWORD ";
        int usedW = Theme.width(titleRaw);
        int dashCount = Math.max(0, (inner + 2) - usedW - 1);
        String topBorder = Theme.render(frameStyle, "┌─ ")
                + "🔑 "
                + Theme.render(Theme.headingCyan(), "LIFEForge")
                + Theme.render(Theme.dim(), " / ")
                + Theme.render(Theme.headingCyan(), "FORGOT PASSWORD")
                + Theme.render(frameStyle, " " + "─".repeat(dashCount) + "┐");
        lines.add(topBorder);

        // 2. Breathing room + Subtitle
        lines.add(padLine("", fw, frameStyle));
        lines.add(padLine(Theme.render(Theme.dim(), "Enter your registered email address or username to receive a 6-digit code."), fw, frameStyle));
        lines.add(padLine("", fw, frameStyle));

        // 3. Section divider
        lines.add(adminDivider(inner, frameStyle));

        // 4. Section header
        lines.add(padLine("", fw, frameStyle));
        lines.add(padLine(Theme.render(Theme.headingCyan(), "[ ACCOUNT VERIFICATION ]"), fw, frameStyle));
        lines.add(padLine("", fw, frameStyle));

        // 5. Form Input: Username or Email
        String label = "Username or Email   : ";
        String val = (fValues != null && fValues.length > 0 && fValues[0] != null) ? fValues[0] : "";
        int boxInnerW = 42;
        String display = val;
        if (fFocus == 0) {
            display = display + "|";
        }
        if (Theme.width(display) > boxInnerW) {
            display = Theme.truncate(display, boxInnerW);
        }
        int pad = Math.max(0, boxInnerW - Theme.width(display));
        String boxContent = " " + display + " ".repeat(pad) + " ";
        Style lblStyle = (fFocus == 0) ? Theme.headingCyan() : Theme.dim();
        Style boxBorderStyle = (fFocus == 0) ? Theme.accentOn() : Theme.dim();
        Style txtStyle = (fFocus == 0) ? Theme.text() : Theme.dim();

        String inputRow = Theme.render(lblStyle, label)
                + Theme.render(boxBorderStyle, "[")
                + Theme.render(txtStyle, boxContent)
                + Theme.render(boxBorderStyle, "]");
        lines.add(padLine(inputRow, fw, frameStyle));

        lines.add(padLine("", fw, frameStyle));

        // 6. Status message line (if any)
        if (status != null && !status.isEmpty()) {
            Style st = statusErr ? Theme.err() : Theme.ok();
            String prefix = statusErr ? "⚠ " : "✓ ";
            lines.add(padLine(Theme.render(st, prefix + status), fw, frameStyle));
        } else {
            lines.add(padLine("", fw, frameStyle));
        }

        // 7. Divider before footer
        lines.add(adminDivider(inner, frameStyle));

        // 8. Single-line footer inside frame
        String footerContent = Theme.render(Theme.adminBadgeGreen(), "[Enter]") + " " + Theme.render(Theme.text(), "Send Code")
                + "         "
                + Theme.render(Theme.adminBadgePurple(), "[ESC/B]") + " " + Theme.render(Theme.text(), "Back to Login");
        lines.add(padLine(footerContent, fw, frameStyle));

        // 9. Bottom card border
        lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));

        return String.join("\n", lines);
    }

    String renderVerifyResetPage(int fw) {
        int inner = fw - 2;
        List<String> lines = new ArrayList<>();
        Style frameStyle = Theme.bar();

        // 1. Top outer border
        String titleRaw = "┌─ 🔑 LIFEForge / RESET PASSWORD ";
        int usedW = Theme.width(titleRaw);
        int dashCount = Math.max(0, (inner + 2) - usedW - 1);
        String topBorder = Theme.render(frameStyle, "┌─ ")
                + "🔑 "
                + Theme.render(Theme.headingCyan(), "LIFEForge")
                + Theme.render(Theme.dim(), " / ")
                + Theme.render(Theme.headingCyan(), "RESET PASSWORD")
                + Theme.render(frameStyle, " " + "─".repeat(dashCount) + "┐");
        lines.add(topBorder);

        // 2. Subtitle (fixed text without truncation)
        lines.add(adminRow("  " + Theme.render(Theme.dim(), "Enter the 6-digit verification code along with your new password."), inner, frameStyle));

        // 3. Section divider
        lines.add(adminDivider(inner, frameStyle));

        // 4. Section header
        lines.add(adminRow("  " + Theme.render(Theme.headingCyan(), "[ VERIFY & RESET CREDENTIALS ]"), inner, frameStyle));
        lines.add(adminRow("", inner, frameStyle));

        // 5. Demo Code line (Simulation / Console)
        String demoCode = (resetUserId != null) ? ctx.passwordResetController.getActiveCode(resetUserId) : null;
        if (demoCode == null && resetUserId != null) {
            demoCode = ctx.passwordResetController.devLastCode(resetUserId);
        }
        String codeDisplay = (demoCode != null) ? demoCode : "849201";
        String demoLine = "  " + Theme.render(Theme.dim(), "Demo Code Sent      : ")
                + Theme.render(Theme.adminBadgeYellow(), codeDisplay)
                + "  " + Theme.render(Theme.adminDim(), "(Simulation / Console)");
        lines.add(adminRow(demoLine, inner, frameStyle));
        lines.add(adminRow("", inner, frameStyle));

        // 6. Form inputs (3 fields)
        String[] labels = {
            "Verification Code   : ",
            "New Password        : ",
            "Confirm Password    : "
        };
        int boxInnerW = 42;

        for (int i = 0; i < 3; i++) {
            boolean foc = (fFocus == i);
            String val = (fValues != null && fValues.length > i && fValues[i] != null) ? fValues[i] : "";
            String display = (i > 0) ? "*".repeat(val.length()) : val;
            if (foc) {
                display = display + "|";
            }
            if (Theme.width(display) > boxInnerW) {
                display = Theme.truncate(display, boxInnerW);
            }
            int pad = Math.max(0, boxInnerW - Theme.width(display));
            String boxContent = " " + display + " ".repeat(pad) + " ";
            Style lblStyle = foc ? Theme.headingCyan() : Theme.dim();
            Style boxBorderStyle = foc ? Theme.accentOn() : Theme.dim();
            Style txtStyle = foc ? Theme.text() : Theme.dim();

            String inputRow = "  " + Theme.render(lblStyle, labels[i])
                    + Theme.render(boxBorderStyle, "[")
                    + Theme.render(txtStyle, boxContent)
                    + Theme.render(boxBorderStyle, "]");
            lines.add(adminRow(inputRow, inner, frameStyle));
        }

        lines.add(adminRow("", inner, frameStyle));

        // 7. Status message line or Expiry hint
        if (status != null && !status.isEmpty()) {
            Style st = statusErr ? Theme.err() : Theme.ok();
            String prefix = statusErr ? "⚠ " : "✓ ";
            lines.add(adminRow("  " + Theme.render(st, prefix + status), inner, frameStyle));
        } else {
            lines.add(adminRow("  " + Theme.render(Theme.dim(), "Code expires in 5 minutes. Use [R] to request a new code."), inner, frameStyle));
        }

        // 8. Divider before footer
        lines.add(adminDivider(inner, frameStyle));

        // 9. Single-line footer inside frame
        String footerContent = "  "
                + Theme.render(Theme.adminBadgeGreen(), "[Enter]") + " " + Theme.render(Theme.text(), "Submit Reset")
                + "    "
                + Theme.render(Theme.adminBadgeYellow(), "[R]") + " " + Theme.render(Theme.text(), "Resend Code")
                + "    "
                + Theme.render(Theme.adminBadgePurple(), "[ESC/B]") + " " + Theme.render(Theme.text(), "Back to Login");
        lines.add(adminRow(footerContent, inner, frameStyle));

        // 10. Bottom card border
        lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));

        return String.join("\n", lines);
    }

    String renderAdminSettingsPage(int fw) {
        int inner = fw - 2;
        List<String> lines = new ArrayList<>();
        Style frameStyle = Theme.adminBorder();
        Style cardBorder = Theme.adminCardBorder();

        lines.add("\u001B[H\u001B[2J");

        // 1. Top outer border (80 columns total)
        String titleRaw = "┌─ ⚙️  LIFEForge / SYSTEM SETTINGS ";
        int usedW = Theme.width(titleRaw);
        int dashCount = Math.max(0, (inner + 2) - usedW - 1);
        String topBorder = Theme.render(frameStyle, "┌─ ")
                + "⚙️  "
                + Theme.render(Theme.adminHeader(), "LIFEForge")
                + Theme.render(Theme.dim(), " / ")
                + Theme.render(Theme.adminHeaderSub(), "SYSTEM SETTINGS")
                + Theme.render(frameStyle, " " + "─".repeat(dashCount) + "┐");
        lines.add(topBorder);

        // Subtitle & Divider
        lines.add(adminRow("  " + Theme.render(Theme.adminDim(), "Runtime environment, AI configuration, and platform constraints"), inner, frameStyle));
        lines.add(adminDivider(inner, frameStyle));

        // -------------------------------------------------------------
        // Sub-Card 1: [ SYSTEM CORE ] (Total width: 74 chars)
        // Col 1: 25, Col 2: 15, Col 3: 30 (25 + 1 + 15 + 1 + 30 = 72 inner)
        // -------------------------------------------------------------
        lines.add(adminRow("  " + Theme.render(Theme.headingCyan(), "[ SYSTEM CORE ]"), inner, frameStyle));

        String sc1Top = Theme.render(cardBorder, "┌" + "─".repeat(25) + "┬" + "─".repeat(15) + "┬" + "─".repeat(30) + "┐");
        lines.add(adminRow("  " + sc1Top + "  ", inner, frameStyle));

        // Row 1
        String sc1Col1 = Theme.render(Theme.adminDim(), " Name    : ") + Theme.render(Theme.adminText(), AppConfig.APP_NAME) + " ".repeat(Math.max(0, 25 - visibleWidth(" Name    : " + AppConfig.APP_NAME)));
        String sc1Col2 = Theme.render(Theme.adminDim(), " Ver: ") + Theme.render(Theme.adminText(), AppConfig.APP_VERSION) + " ".repeat(Math.max(0, 15 - visibleWidth(" Ver: " + AppConfig.APP_VERSION)));
        String dbBadge = dbOk ? Theme.render(Theme.adminBadgeGreen(), "● Connected") : Theme.render(Theme.adminBadgeRed(), "○ Disconnected");
        String dbText = dbOk ? "● Connected" : "○ Disconnected";
        String sc1Col3 = Theme.render(Theme.adminDim(), " DB Status: ") + dbBadge + " ".repeat(Math.max(0, 30 - visibleWidth(" DB Status: " + dbText)));
        String sc1Row1 = Theme.render(cardBorder, "│") + sc1Col1 + Theme.render(cardBorder, "│") + sc1Col2 + Theme.render(cardBorder, "│") + sc1Col3 + Theme.render(cardBorder, "│");
        lines.add(adminRow("  " + sc1Row1 + "  ", inner, frameStyle));

        // Mid divider
        String sc1Mid = Theme.render(cardBorder, "├" + "─".repeat(25) + "┴" + "─".repeat(15) + "┴" + "─".repeat(30) + "┤");
        lines.add(adminRow("  " + sc1Mid + "  ", inner, frameStyle));

        // Row 2: Tagline
        String sc1Tagline = Theme.render(Theme.adminDim(), " Tagline : ") + Theme.render(Theme.adminText(), AppConfig.APP_TAGLINE) + " ".repeat(Math.max(0, 72 - visibleWidth(" Tagline : " + AppConfig.APP_TAGLINE)));
        String sc1Row2 = Theme.render(cardBorder, "│") + sc1Tagline + Theme.render(cardBorder, "│");
        lines.add(adminRow("  " + sc1Row2 + "  ", inner, frameStyle));

        // Bottom
        String sc1Bot = Theme.render(cardBorder, "└" + "─".repeat(72) + "┘");
        lines.add(adminRow("  " + sc1Bot + "  ", inner, frameStyle));

        lines.add(adminRow("", inner, frameStyle));

        // -------------------------------------------------------------
        // Sub-Card 2: [ AI ASSISTANT / OLLAMA ] (Total width: 74 chars)
        // Col 1: 26, Col 2: 45 (26 + 1 + 45 = 72 inner)
        // -------------------------------------------------------------
        lines.add(adminRow("  " + Theme.render(Theme.headingCyan(), "[ AI ASSISTANT / OLLAMA ]"), inner, frameStyle));

        String sc2Top = Theme.render(cardBorder, "┌" + "─".repeat(26) + "┬" + "─".repeat(45) + "┐");
        lines.add(adminRow("  " + sc2Top + "  ", inner, frameStyle));

        boolean aiOn = AppConfig.isAiEnabled();
        String aiBadge = aiOn ? Theme.render(Theme.adminBadgeGreen(), "● Enabled") : Theme.render(Theme.adminBadgeYellow(), "○ Disabled");
        String aiText = aiOn ? "● Enabled" : "○ Disabled";
        String sc2Col1 = Theme.render(Theme.adminDim(), " Status   : ") + aiBadge + " ".repeat(Math.max(0, 26 - visibleWidth(" Status   : " + aiText)));
        String sc2Col2 = Theme.render(Theme.adminDim(), " Model    : ") + Theme.render(Theme.adminText(), AppConfig.getOllamaModel()) + " ".repeat(Math.max(0, 45 - visibleWidth(" Model    : " + AppConfig.getOllamaModel())));
        String sc2Row1 = Theme.render(cardBorder, "│") + sc2Col1 + Theme.render(cardBorder, "│") + sc2Col2 + Theme.render(cardBorder, "│");
        lines.add(adminRow("  " + sc2Row1 + "  ", inner, frameStyle));

        String sc2Row2Col1 = Theme.render(Theme.adminDim(), " Provider : ") + Theme.render(Theme.adminText(), "Local") + " ".repeat(Math.max(0, 26 - visibleWidth(" Provider : Local")));
        String sc2Row2Col2 = Theme.render(Theme.adminDim(), " Endpoint : ") + Theme.render(Theme.adminText(), AppConfig.getOllamaBaseUrl()) + " ".repeat(Math.max(0, 45 - visibleWidth(" Endpoint : " + AppConfig.getOllamaBaseUrl())));
        String sc2Row2 = Theme.render(cardBorder, "│") + sc2Row2Col1 + Theme.render(cardBorder, "│") + sc2Row2Col2 + Theme.render(cardBorder, "│");
        lines.add(adminRow("  " + sc2Row2 + "  ", inner, frameStyle));

        // Mid divider
        String sc2Mid = Theme.render(cardBorder, "├" + "─".repeat(26) + "┴" + "─".repeat(45) + "┤");
        lines.add(adminRow("  " + sc2Mid + "  ", inner, frameStyle));

        // Row 3: Fallback note
        String fallbackStr = "Automatic fallback to rule-based engine if offline";
        String sc2Row3Content = Theme.render(Theme.adminDim(), " Fallback : ") + Theme.render(Theme.adminText(), fallbackStr) + " ".repeat(Math.max(0, 72 - visibleWidth(" Fallback : " + fallbackStr)));
        String sc2Row3 = Theme.render(cardBorder, "│") + sc2Row3Content + Theme.render(cardBorder, "│");
        lines.add(adminRow("  " + sc2Row3 + "  ", inner, frameStyle));

        // Bottom
        String sc2Bot = Theme.render(cardBorder, "└" + "─".repeat(72) + "┘");
        lines.add(adminRow("  " + sc2Bot + "  ", inner, frameStyle));

        lines.add(adminRow("", inner, frameStyle));

        // -------------------------------------------------------------
        // Sub-Card 3: [ VALIDATION CONSTRAINTS ] (Total width: 74 chars)
        // Col 1: 34, Col 2: 37 (34 + 1 + 37 = 72 inner)
        // -------------------------------------------------------------
        lines.add(adminRow("  " + Theme.render(Theme.headingCyan(), "[ VALIDATION CONSTRAINTS ]"), inner, frameStyle));

        String sc3Top = Theme.render(cardBorder, "┌" + "─".repeat(34) + "┬" + "─".repeat(37) + "┐");
        lines.add(adminRow("  " + sc3Top + "  ", inner, frameStyle));

        String pwdVal = AppConfig.MIN_PASSWORD_LENGTH + " ch";
        String sc3Row1Col1 = Theme.render(Theme.adminDim(), " Password Min Length : ") + Theme.render(Theme.adminText(), pwdVal) + " ".repeat(Math.max(0, 34 - visibleWidth(" Password Min Length : " + pwdVal)));
        String heightVal = String.format(Locale.ROOT, "%.1f - %.1f cm", AppConfig.MIN_HEIGHT_CM, AppConfig.MAX_HEIGHT_CM);
        String sc3Row1Col2 = Theme.render(Theme.adminDim(), " Height Range : ") + Theme.render(Theme.adminText(), heightVal) + " ".repeat(Math.max(0, 37 - visibleWidth(" Height Range : " + heightVal)));
        String sc3Row1 = Theme.render(cardBorder, "│") + sc3Row1Col1 + Theme.render(cardBorder, "│") + sc3Row1Col2 + Theme.render(cardBorder, "│");
        lines.add(adminRow("  " + sc3Row1 + "  ", inner, frameStyle));

        String ageVal = AppConfig.MIN_AGE + "-" + AppConfig.MAX_AGE;
        String sc3Row2Col1 = Theme.render(Theme.adminDim(), " User Age Range      : ") + Theme.render(Theme.adminText(), ageVal) + " ".repeat(Math.max(0, 34 - visibleWidth(" User Age Range      : " + ageVal)));
        String weightVal = String.format(Locale.ROOT, "%5.1f - %5.1f kg", AppConfig.MIN_WEIGHT_KG, AppConfig.MAX_WEIGHT_KG);
        String sc3Row2Col2 = Theme.render(Theme.adminDim(), " Weight Range : ") + Theme.render(Theme.adminText(), weightVal) + " ".repeat(Math.max(0, 37 - visibleWidth(" Weight Range : " + weightVal)));
        String sc3Row2 = Theme.render(cardBorder, "│") + sc3Row2Col1 + Theme.render(cardBorder, "│") + sc3Row2Col2 + Theme.render(cardBorder, "│");
        lines.add(adminRow("  " + sc3Row2 + "  ", inner, frameStyle));

        // Bottom
        String sc3Bot = Theme.render(cardBorder, "└" + "─".repeat(34) + "┴" + "─".repeat(37) + "┘");
        lines.add(adminRow("  " + sc3Bot + "  ", inner, frameStyle));

        lines.add(adminRow("", inner, frameStyle));

        // ENV Hint (clean, non-duplicated)
        lines.add(adminRow("  " + Theme.render(Theme.adminDim(), "ENV: Set LIFEFORGE_OLLAMA_URL / LIFEFORGE_OLLAMA_MODEL to override."), inner, frameStyle));

        // Status Feedback (if present)
        if (status != null && !status.trim().isEmpty()) {
            Style st = statusErr ? Theme.adminBadgeRed() : Theme.adminBadgeGreen();
            lines.add(adminRow("  " + Theme.render(st, status), inner, frameStyle));
        }

        // Divider before footer
        lines.add(adminDivider(inner, frameStyle));

        // Consolidated command footer inside outer frame
        String fRefresh = Theme.render(Theme.adminBadgeGreen(), "[R]") + " " + Theme.render(Theme.adminText(), "Test Connections");
        String fBack = Theme.render(Theme.adminBadgePurple(), "[B]") + " " + Theme.render(Theme.adminText(), "Back");
        String fHome = Theme.render(Theme.adminBadgeYellow(), "[H]") + " " + Theme.render(Theme.adminText(), "Home");
        String fQuit = Theme.render(Theme.adminBadgeRed(), "[Q]") + " " + Theme.render(Theme.adminText(), "Quit");
        String footerContent = "  " + fRefresh + "        " + fBack + "        " + fHome + "        " + fQuit;
        lines.add(adminRow(footerContent, inner, frameStyle));

        // Bottom outer border
        lines.add(Theme.render(frameStyle, "└" + "─".repeat(inner) + "┘"));

        return String.join("\n", lines);
    }

    private int auditDetailsWidth() {
        int inner = Math.max(40, width - 2);
        // Indent (2) + TIME (11) + gap (2) + ADMIN (8) + gap (2) + ACTION (17) + gap (2) + TARGET (10) + gap (2) = 56
        return Math.max(16, inner - 56);
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

    private String auditUnderline() {
        int detailsW = auditDetailsWidth();
        return "  "
                + Theme.padRight(Theme.dup('─', 10), AUDIT_TIME_W) + "  "
                + Theme.padRight(Theme.dup('─', 8), AUDIT_ADMIN_W) + "  "
                + Theme.padRight(Theme.dup('─', 17), AUDIT_ACTION_W) + "  "
                + Theme.padRight(Theme.dup('─', 10), AUDIT_TARGET_W) + "  "
                + Theme.padRight(Theme.dup('─', 16), detailsW);
    }

    private String formatAuditActor(AuditLog l) {
        if (l.getActorUserId() == null) {
            return "system";
        }
        User u = auditUserCache.get(l.getActorUserId());
        if (u != null) {
            if (u.getRole() == Role.ADMIN) {
                return "admin";
            }
            if (u.getUsername() != null && !u.getUsername().isBlank()) {
                return u.getUsername();
            }
        }
        return "admin";
    }

    private String formatAuditTarget(AuditLog l) {
        String type = nvl(l.getTargetType()).trim();
        Long id = l.getTargetId();
        if (type.isEmpty()) {
            return id != null ? "#" + id : "-";
        }
        String displayType = switch (type.toUpperCase(Locale.ROOT)) {
            case "PASSWORD_RESET", "RESET_REQUEST" -> "RESET";
            case "USER" -> "USER";
            case "ADMIN" -> "ADMIN";
            case "GOAL" -> "GOAL";
            case "CATEGORY" -> "CATEGORY";
            case "RECOMMENDATION", "REC" -> "REC";
            default -> type.replace('_', ' ');
        };
        if (id != null) {
            return displayType + " #" + id;
        }
        return displayType;
    }

    private String formatAuditDetails(AuditLog l) {
        String raw = nvl(l.getDetails()).replace('\n', ' ').trim();
        String rawLower = raw.toLowerCase(Locale.ROOT);
        String action = nvl(l.getAction()).toUpperCase(Locale.ROOT);

        if (action.contains("LOGIN") || rawLower.contains("logged in")) {
            return "User logged in";
        }
        if (rawLower.contains("verif")) {
            return "Verification started";
        }
        if (action.contains("PASSWORD_RESET_REQUEST_APPROVED") || (action.contains("APPROVED") && rawLower.contains("approved"))) {
            return "Request approved";
        }
        if (action.contains("PASSWORD_RESET_REQUEST_REJECTED") || (action.contains("REJECTED") && rawLower.contains("rejected"))) {
            return "Request rejected";
        }
        if (action.contains("PASSWORD_RESET_COMPLETED") || rawLower.contains("password reset completed") || rawLower.contains("password updated")) {
            return "Password updated";
        }
        if (action.contains("PASSWORD_RESET_REQUESTED") || action.contains("RESET") || rawLower.contains("reset requested")) {
            return "Reset requested";
        }
        if (action.contains("UNBLOCK") || rawLower.contains("unblock")) {
            return "User unblocked";
        }
        if (action.contains("BLOCK") || rawLower.contains("block")) {
            return "User blocked";
        }
        if (action.contains("DELETE") || rawLower.contains("delet")) {
            return "User deleted";
        }
        if (action.contains("ROLE")) {
            return "Role updated";
        }
        if (!raw.isEmpty() && raw.length() <= 22) {
            return raw;
        }
        return action.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private Line auditRow(AuditLog l, boolean selected) {
        int detailsW = auditDetailsWidth();
        String time = l.getCreatedAt() == null ? ""
                : l.getCreatedAt().format(AUDIT_TIME_FMT);
        String admin = formatAuditActor(l);
        Object[] labeled = auditActionLabel(l.getAction());
        String action = (String) labeled[0];
        String target = formatAuditTarget(l);
        String details = formatAuditDetails(l);

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
            return new Object[] { "-", Theme.adminDim() };
        }
        switch (action.toUpperCase(Locale.ROOT).trim()) {
            case "LOGIN":
                return new Object[] { "LOGIN", Theme.adminBadgeGreen() };
            case "PASSWORD_RESET", "PASSWORD_RESET_REQUESTED", "RESET_REQUEST", "RESET_REQ":
                return new Object[] { "RESET_REQ", Theme.adminBadgeYellow() };
            case "PASSWORD_RESET_REQUEST_APPROVED", "RESET_APPROVED":
                return new Object[] { "RESET_APPROVED", Theme.adminBadgeGreen() };
            case "PASSWORD_RESET_REQUEST_REJECTED", "RESET_REJECTED":
                return new Object[] { "RESET_REJECTED", Theme.adminBadgeRed() };
            case "PASSWORD_RESET_COMPLETED", "RESET_COMPLETED", "RESET_DONE":
                return new Object[] { "RESET_DONE", Theme.adminBadgeGreen() };
            case "USER_BLOCKED", "BLOCK_USER", "USER_LOCK":
                return new Object[] { "USER_LOCK", Theme.adminBadgeRed() };
            case "USER_UNBLOCKED", "UNBLOCK_USER", "USER_UNLOCK":
                return new Object[] { "USER_UNLOCK", Theme.adminBadgeGreen() };
            case "USER_DELETED", "DELETE_USER":
                return new Object[] { "USER_DEL", Theme.adminBadgeRed() };
            case "ROLE_CHANGED":
                return new Object[] { "ROLE_CHANGE", Theme.adminBadgePurple() };
            case "REC_CREATE", "RECOMMENDATION_CREATE":
                return new Object[] { "REC_CREATE", Theme.adminBadgeGreen() };
            case "REC_UPDATE", "RECOMMENDATION_UPDATE":
                return new Object[] { "REC_UPDATE", Theme.adminBadgeYellow() };
            case "REC_DELETE", "RECOMMENDATION_DELETE":
                return new Object[] { "REC_DEL", Theme.adminBadgeRed() };
            case "GOAL_CREATE":
                return new Object[] { "GOAL_CREATE", Theme.adminBadgeGreen() };
            case "GOAL_UPDATE":
                return new Object[] { "GOAL_UPDATE", Theme.adminBadgeYellow() };
            case "GOAL_DELETE":
                return new Object[] { "GOAL_DEL", Theme.adminBadgeRed() };
            case "CAT_CREATE":
                return new Object[] { "CAT_CREATE", Theme.adminBadgeGreen() };
            case "CAT_UPDATE":
                return new Object[] { "CAT_UPDATE", Theme.adminBadgeYellow() };
            case "CAT_DELETE":
                return new Object[] { "CAT_DEL", Theme.adminBadgeRed() };
            default:
                return new Object[] { action.toUpperCase(Locale.ROOT).replace(' ', '_'), Theme.adminDim() };
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
                "  LIFEFORGE_AI_ENABLED=false to force rule-based explanations only."));
        return "Configuration and environment";
    }

    // ------------------------------------------------------------------
    // Flat table renderer (no nested borders - fits inside outer frame)
    // ------------------------------------------------------------------
    private List<Line> flatTable(String[] headers, String[][] rows, int selected, int innerWidth) {
        List<Line> out = new ArrayList<>();
        if (headers == null || headers.length == 0) {
            return out;
        }
        int cols = headers.length;
        int[] widths = new int[cols];

        for (int i = 0; i < cols; i++) {
            widths[i] = Theme.width(headers[i] == null ? "" : headers[i]);
        }
        if (rows != null) {
            for (String[] row : rows) {
                for (int i = 0; i < cols; i++) {
                    String value = (row != null && i < row.length && row[i] != null)
                            ? row[i] : "";
                    widths[i] = Math.max(widths[i], Theme.width(value));
                }
            }
        }

        if (selected >= 0 && rows != null && rows.length > 0 && cols > 0) {
            widths[0] += 2;
        }

        int totalNeeded = 2;
        for (int i = 0; i < cols; i++) {
            totalNeeded += widths[i] + 3;
        }
        totalNeeded -= 1;

        int availWidth = Math.max(10, innerWidth - 2);
        if (totalNeeded > availWidth) {
            int excess = totalNeeded - availWidth;
            while (excess > 0) {
                int best = -1;
                int largest = 1;
                for (int i = 0; i < cols; i++) {
                    if (widths[i] > largest) {
                        largest = widths[i];
                        best = i;
                    }
                }
                if (best < 0) {
                    break;
                }
                widths[best]--;
                excess--;
            }
        }

        StringBuilder hdr = new StringBuilder("  ");
        for (int i = 0; i < cols; i++) {
            String h = Theme.truncate(headers[i] == null ? "" : headers[i], widths[i]);
            hdr.append(Theme.padCenter(h, widths[i]));
            hdr.append(i < cols - 1 ? "   " : "  ");
        }
        out.add(Line.of(Theme.headingPurple(), hdr.toString()));

        StringBuilder sep = new StringBuilder("  ");
        for (int i = 0; i < cols; i++) {
            sep.append(Theme.dup('\u2500', widths[i]));
            sep.append(i < cols - 1 ? "   " : "  ");
        }
        out.add(Line.of(Theme.dim(), sep.toString()));

        if (rows == null || rows.length == 0) {
            StringBuilder empty = new StringBuilder("  ");
            empty.append(Theme.padCenter("- no data -", Math.max(1, widths[0])));
            out.add(Line.of(Theme.dim(), empty.toString()));
        } else {
            for (int rIndex = 0; rIndex < rows.length; rIndex++) {
                String[] row = rows[rIndex];
                StringBuilder sb = new StringBuilder("  ");
                for (int c = 0; c < cols; c++) {
                    String cell = (row != null && c < row.length && row[c] != null)
                            ? row[c] : "";
                    if (rIndex == selected && c == 0) {
                        cell = "> " + cell;
                    }
                    cell = Theme.truncate(cell, widths[c]);
                    sb.append(Theme.padRight(cell, widths[c]));
                    sb.append(c < cols - 1 ? "   " : "  ");
                }
                out.add(Line.of(rIndex == selected ? Theme.selected() : Theme.text(), sb.toString()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Small renderers
    // ------------------------------------------------------------------
    private List<Line> menuLines() {
        if (screen == Screen.RECOMMEND_DETAIL) {
            return ScreenKit.menuHorizontal(menuLabels, sel, width - 2);
        }
        return ScreenKit.menu(menuLabels, sel, width - 2);
    }

    private List<Line> formLines() {
        List<Line> out = new ArrayList<>();
        FieldKind[] kinds = fKinds();
        int availInner = width - 2;
        String indent = "   ";
        int boxW = Math.max(20, availInner - 6);

        for (int i = 0; i < fLabels.length; i++) {
            boolean foc = i == fFocus;
            FieldKind kind = kinds[i];
            if (kind == FieldKind.BUTTON_PRIMARY || kind == FieldKind.BUTTON_SECONDARY) {
                // Check if this button and the next button should be rendered side-by-side (e.g. Login left, Back right)
                if (i + 1 < fLabels.length && (kinds[i + 1] == FieldKind.BUTTON_PRIMARY || kinds[i + 1] == FieldKind.BUTTON_SECONDARY)) {
                    String label1 = fLabels[i];
                    String label2 = fLabels[i + 1];
                    boolean isWide1 = label1.equalsIgnoreCase("Forgot Password") || label1.length() >= 20;
                    boolean isWide2 = label2.equalsIgnoreCase("Forgot Password") || label2.length() >= 20;
                    if (!isWide1 && !isWide2) {
                        out.add(Line.blank());
                        out.addAll(renderTwoButtonLines(label1, kinds[i] == FieldKind.BUTTON_PRIMARY, i == fFocus,
                                label2, kinds[i + 1] == FieldKind.BUTTON_PRIMARY, (i + 1) == fFocus, boxW, indent));
                        i++; // advance since both buttons are rendered together
                        continue;
                    }
                }
                out.add(Line.blank());
                out.addAll(renderButtonLines(fLabels[i], kind == FieldKind.BUTTON_PRIMARY, foc, boxW, indent));
                continue;
            }
            if (isChoice(kind)) {
                String value = fChoices[i][choice(fValues[i], 0)];
                out.add(Line.of(foc ? Theme.headingGreen() : Theme.dim(), indent + fLabels[i]));
                Style choiceBorderStyle = foc ? Theme.accentGreen() : Theme.dim();
                out.add(Line.of(choiceBorderStyle, indent + "╭" + "─".repeat(boxW - 2) + "╮"));
                String choiceDisplay = Theme.padCenter("◀  " + value + "  ▶", boxW - 4);
                out.add(Line.of(foc ? Theme.pivot() : Theme.dim(),
                        indent + "│ " + choiceDisplay + " │"));
                out.add(Line.of(choiceBorderStyle, indent + "╰" + "─".repeat(boxW - 2) + "╯"));
                continue;
            }
            String display = kind == FieldKind.SECRET ? mask(fValues[i]) : fValues[i];
            int maxText = Math.max(2, boxW - 4);
            if (foc) {
                if (display.length() > maxText) {
                    display = display.substring(display.length() - maxText);
                }
                display = display + (display.length() >= maxText ? "" : "|");
            }
            display = truncate(display, maxText);
            out.add(Line.of(foc ? Theme.headingCyan() : Theme.dim(), indent + fLabels[i]));
            Style borderStyle = foc ? Theme.accentOn() : Theme.dim();
            out.add(Line.of(borderStyle, indent + "╭" + "─".repeat(boxW - 2) + "╮"));
            out.add(Line.of(foc ? Theme.text() : Theme.dim(),
                    indent + "│ " + Theme.padRight(display, maxText) + " │"));
            out.add(Line.of(borderStyle, indent + "╰" + "─".repeat(boxW - 2) + "╯"));
        }
        return out;
    }

    /** Render two focusable buttons side-by-side in a row (e.g. Login on left, Back on right). */
    private List<Line> renderTwoButtonLines(String label1, boolean primary1, boolean foc1,
                                            String label2, boolean primary2, boolean foc2,
                                            int fullBoxW, String indent) {
        List<Line> out = new ArrayList<>();
        int gap = 4;
        int btnW1 = Math.max(12, (fullBoxW - gap) / 2);
        int btnW2 = Math.max(12, fullBoxW - gap - btnW1);

        String pointer1 = foc1 ? "> " : "";
        String pointer2 = foc2 ? "> " : "";
        String text1 = Theme.padCenter(pointer1 + label1, btnW1 - 2);
        String text2 = Theme.padCenter(pointer2 + label2, btnW2 - 2);

        String spacer = " ".repeat(gap);

        Style rowStyle;
        if (foc1) {
            rowStyle = primary1 ? Theme.accentOn() : Theme.accentPurple();
        } else if (foc2) {
            rowStyle = primary2 ? Theme.accentOn() : Theme.accentPurple();
        } else {
            rowStyle = Theme.dim();
        }

        String top = indent + "╭" + "─".repeat(btnW1 - 2) + "╮" + spacer + "╭" + "─".repeat(btnW2 - 2) + "╮";
        String mid = indent + "│" + text1 + "│" + spacer + "│" + text2 + "│";
        String bot = indent + "╰" + "─".repeat(btnW1 - 2) + "╯" + spacer + "╰" + "─".repeat(btnW2 - 2) + "╯";

        out.add(Line.of(rowStyle, top));
        out.add(Line.of(rowStyle, mid));
        out.add(Line.of(rowStyle, bot));
        return out;
    }

    /** Render a single focusable button as an enclosed box matching Picture 2. */
    private List<Line> renderButtonLines(String label, boolean primary, boolean focused, int fullBoxW, String indent) {
        List<Line> out = new ArrayList<>();
        Style btnStyle;
        if (focused) {
            btnStyle = primary ? Theme.accentOn() : Theme.accentPurple();
        } else {
            btnStyle = primary ? Theme.headingCyan() : Theme.dim();
        }

        int btnW;
        if (label.equalsIgnoreCase("Forgot Password") || label.length() >= 16) {
            btnW = fullBoxW;
        } else {
            btnW = Math.max(26, Math.min(fullBoxW, label.length() + 14));
        }

        String pointer = focused ? "> " : "";
        String text = Theme.padCenter(pointer + label, btnW - 2);

        out.add(Line.of(btnStyle, indent + "╭" + "─".repeat(btnW - 2) + "╮"));
        out.add(Line.of(btnStyle, indent + "│" + text + "│"));
        out.add(Line.of(btnStyle, indent + "╰" + "─".repeat(btnW - 2) + "╯"));
        return out;
    }

    /** Legacy single-line button fallback if needed. */
    private Line renderButton(String label, boolean primary, boolean focused) {
        Style box;
        if (focused) {
            box = primary ? Theme.accentOn() : Theme.accentPurple();
        } else {
            box = primary ? Theme.headingCyan() : Theme.headingPurple();
        }

        // Keep the selection pointer visible and inside the main frame.
        String pointer = focused ? "> " : "  ";
        return Line.of(box, pointer + "[ " + label + " ]");
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
            case WELCOME -> {
                out.add(new String[] { "←/→", "Select" });
                out.add(new String[] { "Enter", "Open" });
                out.add(new String[] { "L", "Login" });
                out.add(new String[] { "R", "Register" });
                out.add(new String[] { "Q", "Quit" });
            }
            case LOGIN, REGISTER, FORGOT_PASSWORD, VERIFY_CODE,
                 PROFILE_EDIT, PROFILE_PASSWORD, ADMIN_SEARCH, ADMIN_AUDIT_SEARCH,
                 ADMIN_REC_FORM, ADMIN_GOAL_FORM, ADMIN_CAT_FORM,
                 CUSTOM_GOAL_INPUT -> {
                out.add(new String[] { "Up/Down/Tab", "Move field" });
                out.add(new String[] { "Shift+Tab", "Previous field" });
                out.add(new String[] { "Left/Right", "Cursor / choice" });
                out.add(new String[] { "Enter", "Select / Submit" });
                out.add(new String[] { "Esc/Back", "Cancel" });
            }
            case PERSONALIZED_ANALYSIS -> {
                out.add(new String[] { "Enter", "Continue" });
                out.add(new String[] { "T", "Trace" });
                out.add(new String[] { "M", "Breakdown" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case PERSONALIZED_PLAN -> {
                out.add(new String[] { "↑↓", "Select" });
                out.add(new String[] { "Enter", "Open" });
                out.add(new String[] { "D", "Blueprint" });
                out.add(new String[] { "T", "Trace" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case DAILY_BLUEPRINT -> {
                out.add(new String[] { "←/→", "Flip Page" });
                out.add(new String[] { "1-3", "Phase" });
                out.add(new String[] { "Enter/B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case CALCULATION_TRACE, WHY_RECOMMENDATION -> {
                out.add(new String[] { "Enter/B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case CATEGORY, SUBCATEGORY, MASTER, SAVED_DETAIL -> {
                out.add(new String[] { "Up/Down", "Choose" });
                out.add(new String[] { "Enter", "Open / action" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case RECOMMEND_DETAIL -> {
                if (isCurrentMasterRoutine()) {
                    out.add(new String[] { "Up/Down", "Choose" });
                    out.add(new String[] { "Left/Right", "Page" });
                } else {
                    out.add(new String[] { "Left/Right", "Select" });
                }
                out.add(new String[] { "Enter", "Open / action" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case OFFICIAL_RECOMMENDATION -> {
                out.add(new String[] { "Up/Down", "Choose" });
                out.add(new String[] { "Enter", "Select" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case SAVED -> {
                out.add(new String[] { "↑/↓", "Navigate" });
                out.add(new String[] { "Enter", "Open & Manage" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case PROFILE -> {
                out.add(new String[] { "E", "Edit" });
                out.add(new String[] { "P", "Password" });
                out.add(new String[] { "D", "Delete" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case GOAL_SELECT, ADMIN_USER_ACTIONS, CUSTOM_GOAL_ANALYSIS -> {
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
                out.add(new String[] { "←/→", "Flip Page" });
                out.add(new String[] { "1-2", "Page" });
                out.add(new String[] { "Enter/E", "Edit" });
                out.add(new String[] { "T", "Toggle" });
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

            case ADMIN_GOALS -> {
                out.add(new String[] { "[↑/↓]", "Row" });
                out.add(new String[] { "[Enter]", "Edit" });
                out.add(new String[] { "[N]", "New" });
                out.add(new String[] { "[T]", "Toggle" });
                out.add(new String[] { "[D]", "Del" });
                out.add(new String[] { "[B]", "Back" });
            }
            case ADMIN_CATS -> {
                out.add(new String[] { "[↑/↓]", "Row" });
                out.add(new String[] { "[Enter]", "Edit" });
                out.add(new String[] { "[N]", "New" });
                out.add(new String[] { "[T]", "Toggle" });
                out.add(new String[] { "[D]", "Del" });
                out.add(new String[] { "[B]", "Back" });
            }
            case ADMIN_USERS -> {
                out.add(new String[] { "[U]", "Up/Down Select" });
                out.add(new String[] { "[L]", "Left/Right Page" });
                out.add(new String[] { "[E]", "Enter Details" });
                out.add(new String[] { "[S]", "Search" });
                out.add(new String[] { "[X]", "Clear Filter" });
                out.add(new String[] { "[B]", "Back" });
                out.add(new String[] { "[H]", "Home" });
                out.add(new String[] { "[Q]", "Quit" });
            }
            case ADMIN_ANALYTICS -> {
                out.add(new String[] { "R", "Refresh" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case ADMIN_AUDIT -> {
                out.add(new String[] { "↑/↓", "Row" });
                out.add(new String[] { "Enter", "View Details" });
                out.add(new String[] { "S", "Search" });
                out.add(new String[] { "R", "Refresh" });
                out.add(new String[] { "B", "Back" });
            }
            case ADMIN_AUDIT_DETAIL -> {
                out.add(new String[] { "B/Esc", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case AI_CHAT -> {
                out.add(new String[] { "1-5", "Quick Ask" });
                out.add(new String[] { "PgUp/Dn", "Scroll" });
                out.add(new String[] { "Enter", "Send" });
                out.add(new String[] { "Esc", "Back" });
            }
            case ADMIN_SETTINGS -> {
                out.add(new String[] { "R", "Test Connections" });
                out.add(new String[] { "B", "Back" });
                out.add(new String[] { "H", "Home" });
                out.add(new String[] { "Q", "Quit" });
            }
            case EXPLANATION -> {
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
            case LOGIN, REGISTER, FORGOT_PASSWORD, VERIFY_CODE,
                 PROFILE_EDIT, PROFILE_PASSWORD, ADMIN_SEARCH, ADMIN_AUDIT_SEARCH,
                 ADMIN_REC_FORM, ADMIN_GOAL_FORM, ADMIN_CAT_FORM,
                 CUSTOM_GOAL_INPUT -> true;
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
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("master")) return true;
        return lower.contains("routine") && !lower.contains("cardio") && !lower.contains("strength")
                && !lower.contains("exercise") && !lower.contains("workout") && !lower.contains("training")
                && !lower.contains("bedtime") && !lower.contains("sleep")
                && !lower.contains("hydration") && !lower.contains("water")
                && !lower.contains("nutrition") && !lower.contains("food")
                && !lower.contains("habit");
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