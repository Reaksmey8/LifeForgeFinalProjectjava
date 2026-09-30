package com.lifeforge.view;

import com.lifeforge.AppContext;
import com.lifeforge.model.Goal;
import com.lifeforge.model.User;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CustomGoalViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    @SuppressWarnings("unchecked")
    private void setScreen(LifeForge tui, String screenName) throws Exception {
        Class<?> screenEnum = Class.forName("com.lifeforge.view.LifeForge$Screen");
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Object target = Enum.valueOf((Class<Enum>) screenEnum, screenName);
        screenField.set(tui, target);
    }

    private Object getScreen(LifeForge tui) throws Exception {
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        return screenField.get(tui);
    }

    @Test
    @DisplayName("Goal Select screen includes 'Other / Custom Goal' menu option")
    public void testGoalSelectIncludesCustomGoalOption() throws Exception {
        AppContext ctx = AppContext.build();
        User testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("demo_user");
        ctx.session.setCurrentUser(testUser);

        LifeForge tui = new LifeForge(ctx);
        setScreen(tui, "GOAL_SELECT");

        Method refreshMethod = LifeForge.class.getDeclaredMethod("refresh");
        refreshMethod.setAccessible(true);
        refreshMethod.invoke(tui);

        Field menuLabelsField = LifeForge.class.getDeclaredField("menuLabels");
        menuLabelsField.setAccessible(true);
        List<String> labels = (List<String>) menuLabelsField.get(tui);

        assertNotNull(labels);
        boolean hasCustomGoalOption = labels.stream().anyMatch(l -> l.contains("Other / Custom Goal"));
        assertTrue(hasCustomGoalOption, "Goal Select menu must include 'Other / Custom Goal'");
    }

    @Test
    @DisplayName("Inputting custom goal and submitting transitions to Custom Goal Analysis view")
    public void testCustomGoalInputAndAnalysisView() throws Exception {
        AppContext ctx = AppContext.build();
        User testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("demo_user");
        testUser.setAge(25);
        testUser.setHeightCm(170.0);
        testUser.setWeightKg(65.0);
        testUser.setGender(com.lifeforge.model.Gender.MALE);
        testUser.setActivityLevel(com.lifeforge.model.ActivityLevel.MODERATELY_ACTIVE);
        ctx.session.setCurrentUser(testUser);

        LifeForge tui = new LifeForge(ctx);

        // 1. Go to CUSTOM_GOAL_INPUT
        Method goToMethod = LifeForge.class.getDeclaredMethod("goTo", Class.forName("com.lifeforge.view.LifeForge$Screen"));
        goToMethod.setAccessible(true);

        Class<?> screenEnum = Class.forName("com.lifeforge.view.LifeForge$Screen");
        Object inputScreen = Enum.valueOf((Class<Enum>) screenEnum, "CUSTOM_GOAL_INPUT");
        goToMethod.invoke(tui, inputScreen);

        // 2. Verify form fields
        Field fValuesField = LifeForge.class.getDeclaredField("fValues");
        fValuesField.setAccessible(true);
        String[] fValues = (String[]) fValuesField.get(tui);
        assertNotNull(fValues);
        assertTrue(fValues.length >= 2);

        // 3. Simulate user typing a custom goal
        fValues[0] = "Prepare for a 5k marathon and build stamina";

        // 4. Submit form
        Method submitMethod = LifeForge.class.getDeclaredMethod("submitCustomGoalInput");
        submitMethod.setAccessible(true);
        submitMethod.invoke(tui);

        // 5. Verify transition to CUSTOM_GOAL_ANALYSIS
        Object currentScreen = getScreen(tui);
        assertEquals("CUSTOM_GOAL_ANALYSIS", currentScreen.toString());

        // 6. Verify rendered view content
        String rendered = tui.view();
        assertNotNull(rendered);
        String clean = rendered.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");

        assertTrue(clean.contains("CUSTOM GOAL INTERPRETATION") || clean.contains("Custom Goal"));
        assertTrue(clean.contains("5k Marathon"));
        assertTrue(clean.contains("Improve Fitness"));
        assertTrue(clean.contains("Exercise"));
        assertTrue(clean.contains("Hydration"));
        assertTrue(clean.contains("Nutrition"));

        // 7. Confirm custom goal
        Method confirmMethod = LifeForge.class.getDeclaredMethod("confirmCustomGoal");
        confirmMethod.setAccessible(true);
        confirmMethod.invoke(tui);

        // 8. Verify active custom goal in session and transition to PERSONALIZED_PLAN
        assertNotNull(ctx.session.getCustomGoal());
        assertTrue(ctx.session.getCustomGoal().getName().contains("5k Marathon"));
        assertEquals("PERSONALIZED_PLAN", getScreen(tui).toString());
    }

    @Test
    @DisplayName("CUSTOM_GOAL_INPUT screen renders Analyze Goal and Cancel as buttons, not text boxes")
    public void testCustomGoalInputFormRendersButtons() throws Exception {
        AppContext ctx = AppContext.build();
        User testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("demo_user");
        ctx.session.setCurrentUser(testUser);

        LifeForge tui = new LifeForge(ctx);

        Method goToMethod = LifeForge.class.getDeclaredMethod("goTo", Class.forName("com.lifeforge.view.LifeForge$Screen"));
        goToMethod.setAccessible(true);
        Class<?> screenEnum = Class.forName("com.lifeforge.view.LifeForge$Screen");
        Object inputScreen = Enum.valueOf((Class<Enum>) screenEnum, "CUSTOM_GOAL_INPUT");
        goToMethod.invoke(tui, inputScreen);

        // Render view
        String rendered = tui.view();
        assertNotNull(rendered);
        String clean = rendered.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");

        // Verify title & instructions
        assertTrue(clean.contains("CUSTOM GOAL DEFINITION"));
        assertTrue(clean.contains("Your Goal"));

        // Verify buttons
        assertTrue(clean.contains("Analyze Goal"));
        assertTrue(clean.contains("Cancel"));

        // Verify fKinds are TEXT, BUTTON_PRIMARY, BUTTON_SECONDARY
        Method fKindsMethod = LifeForge.class.getDeclaredMethod("fKinds");
        fKindsMethod.setAccessible(true);
        Object[] kinds = (Object[]) fKindsMethod.invoke(tui);

        assertEquals("TEXT", kinds[0].toString());
        assertEquals("BUTTON_PRIMARY", kinds[1].toString());
        assertEquals("BUTTON_SECONDARY", kinds[2].toString());
    }
}

