package com.lifeforge.view;

import com.lifeforge.AppContext;
import com.lifeforge.model.Goal;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminGoalsViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    private List<Goal> buildSampleGoals() {
        Goal g1 = new Goal(1L, "WEIGHT_LOSS", "Lose Weight", "Healthy weight loss guidance", true);
        Goal g2 = new Goal(2L, "GENERAL_WELLNESS", "General Wellness", "Daily wellness habits", true);
        Goal g3 = new Goal(3L, "MUSCLE_GAIN", "Build Muscle", "Resistance and hypertrophy training", false);
        Goal g4 = new Goal(4L, "STRESS_REDUCTION", "Manage Stress", "Mindfulness and rest balance", true);
        return new ArrayList<>(List.of(g1, g2, g3, g4));
    }

    private void injectGoals(LifeForge tui, List<Goal> goals) throws Exception {
        Field goalsField = LifeForge.class.getDeclaredField("goals");
        goalsField.setAccessible(true);
        goalsField.set(tui, new ArrayList<>(goals));
    }

    @Test
    public void testAdminGoalsTableAt80Columns() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectGoals(tui, buildSampleGoals());

        String rendered = tui.renderAdminGoalsPage(80);
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // 1. Top card border
        assertTrue(lines[0].startsWith("┌─"), "Top line must start with ┌─: " + lines[0]);
        assertTrue(lines[0].endsWith("┐"), "Top line must end with ┐: " + lines[0]);
        assertTrue(lines[0].contains("LIFEForge / GOAL MANAGEMENT (Admin)"), "Top header mismatch: " + lines[0]);

        // 2. Table top divider
        assertTrue(lines[1].startsWith("├──────┬"), "Top table divider start mismatch: " + lines[1]);
        assertTrue(lines[1].endsWith("┤"), "Top table divider end mismatch: " + lines[1]);
        assertTrue(lines[1].contains("┬─────────────────┬────────────────┤"), "Top divider columns mismatch: " + lines[1]);

        // 3. Header row
        assertTrue(lines[2].contains("ID") && lines[2].contains("GOAL NAME")
                && lines[2].contains("SYSTEM KEY") && lines[2].contains("STATUS"), "Header row mismatch: " + lines[2]);
        assertTrue(lines[2].startsWith("│"), "Header start: " + lines[2]);
        assertTrue(lines[2].endsWith("│"), "Header end: " + lines[2]);

        // 4. Mid table divider
        assertTrue(lines[3].startsWith("├──────┼"), "Mid table divider start: " + lines[3]);
        assertTrue(lines[3].endsWith("┤"), "Mid table divider end: " + lines[3]);

        // 5. Data rows
        // First row selected (> #1)
        assertTrue(lines[4].contains("> #1"), "Row 1 must be selected (> #1): " + lines[4]);
        assertTrue(lines[4].contains("Lose Weight"), "Row 1 name: " + lines[4]);
        assertTrue(lines[4].contains("WEIGHT_LOSS"), "Row 1 system key: " + lines[4]);
        assertTrue(lines[4].contains("● Active"), "Row 1 status: " + lines[4]);

        // Second row unselected (  #2)
        assertTrue(lines[5].contains("  #2"), "Row 2 must be unselected (  #2): " + lines[5]);
        assertTrue(lines[5].contains("General Wellness"), "Row 2 name: " + lines[5]);
        assertTrue(lines[5].contains("GENERAL_WELLNESS"), "Row 2 system key: " + lines[5]);
        assertTrue(lines[5].contains("● Active"), "Row 2 status: " + lines[5]);

        // Third row unselected (  #3) inactive
        assertTrue(lines[6].contains("  #3"), "Row 3 must be unselected (  #3): " + lines[6]);
        assertTrue(lines[6].contains("Build Muscle"), "Row 3 name: " + lines[6]);
        assertTrue(lines[6].contains("MUSCLE_GAIN"), "Row 3 system key: " + lines[6]);
        assertTrue(lines[6].contains("○ Inactive"), "Row 3 status: " + lines[6]);

        // Fourth row unselected (  #4)
        assertTrue(lines[7].contains("  #4"), "Row 4 must be unselected (  #4): " + lines[7]);
        assertTrue(lines[7].contains("Manage Stress"), "Row 4 name: " + lines[7]);
        assertTrue(lines[7].contains("STRESS_REDUCTION"), "Row 4 system key: " + lines[7]);
        assertTrue(lines[7].contains("● Active"), "Row 4 status: " + lines[7]);

        // 6. Table bottom closure divider
        assertTrue(lines[12].startsWith("├──────┴"), "Bottom table divider start: " + lines[12]);
        assertTrue(lines[12].endsWith("┤"), "Bottom table divider end: " + lines[12]);

        // 7. Pagination summary row
        assertTrue(lines[13].contains("Showing 1-4 of 4"), "Summary left: " + lines[13]);
        assertTrue(lines[13].contains("[←/→] Page 1 of 1"), "Summary right: " + lines[13]);

        // 8. Footer divider
        assertTrue(lines[14].startsWith("├"), "Footer divider: " + lines[14]);

        // 9. Enclosed single-line action footer inside card
        assertTrue(lines[15].contains("Select"), "Action footer: " + lines[15]);
        assertTrue(lines[15].contains("Edit"), "Action footer: " + lines[15]);
        assertTrue(lines[15].contains("New"), "Action footer: " + lines[15]);
        assertTrue(lines[15].contains("Toggle"), "Action footer: " + lines[15]);
        assertTrue(lines[15].contains("Delete"), "Action footer: " + lines[15]);
        assertTrue(lines[15].contains("Back"), "Action footer: " + lines[15]);
        assertTrue(lines[15].contains("Quit"), "Action footer: " + lines[15]);
        assertEquals(80, Theme.width(lines[15]), "Action footer line must have exact width 80: " + lines[15]);

        // 10. Bottom card border
        assertTrue(lines[16].startsWith("└"), "Card bottom start: " + lines[16]);
        assertTrue(lines[16].endsWith("┘"), "Card bottom end: " + lines[16]);

        // 11. Verify NO duplicate instruction line
        assertFalse(clean.contains("Enter = edit"), "Must not contain top duplicate instruction line");
        assertFalse(clean.contains("N = new"), "Must not contain top duplicate instruction line");
        assertFalse(clean.contains("↑/↓ = move"), "Must not contain top duplicate instruction line");

        // 12. Strict 80-column alignment across all card lines (lines 0 to 16)
        for (int i = 0; i <= 16; i++) {
            assertEquals(80, Theme.width(lines[i]), "Card line " + i + " width must be exactly 80: " + lines[i]);
        }
    }

    @Test
    public void testAdminGoalsTableAt74ColumnsExactDividers() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectGoals(tui, buildSampleGoals());

        String rendered = tui.renderAdminGoalsPage(74);
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // Verify exact divider strings from user specification
        assertEquals("├──────┬──────────────────────────────┬─────────────────┬────────────────┤", lines[1]);
        assertEquals("│ ID   │ GOAL NAME                    │ SYSTEM KEY      │ STATUS         │", lines[2]);
        assertEquals("├──────┼──────────────────────────────┼─────────────────┼────────────────┤", lines[3]);
        assertEquals("├──────┴──────────────────────────────┴─────────────────┴────────────────┤", lines[12]);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToggleGoalStatusKeyShortcut() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        List<Goal> goals = buildSampleGoals();
        injectGoals(tui, goals);

        Class<?> screenEnum = Class.forName("com.lifeforge.view.LifeForge$Screen");
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Object adminGoals = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_GOALS");
        screenField.set(tui, adminGoals);

        // Goal 1 is initially active
        assertTrue(tui.getGoals().get(0).isActive(), "Goal 1 initially active");

        // Press 't' -> toggles to inactive
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 't' })));
        assertFalse(tui.getGoals().get(0).isActive(), "Goal 1 must be toggled to INACTIVE");

        // Press 't' again -> toggles back to active
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 't' })));
        assertTrue(tui.getGoals().get(0).isActive(), "Goal 1 must be toggled back to ACTIVE");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNavigateUpAndDownAndEsc() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        List<Goal> goals = buildSampleGoals();
        injectGoals(tui, goals);

        Class<?> screenEnum = Class.forName("com.lifeforge.view.LifeForge$Screen");
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Object adminGoals = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_GOALS");
        screenField.set(tui, adminGoals);

        Field backField = LifeForge.class.getDeclaredField("back");
        backField.setAccessible(true);
        java.util.Deque<Object> back = (java.util.Deque<Object>) backField.get(tui);
        Object adminHome = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_HOME");
        back.push(adminHome);

        Field selField = LifeForge.class.getDeclaredField("sel");
        selField.setAccessible(true);
        assertEquals(0, selField.get(tui));

        // Press down arrow
        tui.update(new KeyPressMessage(new Key(KeyType.KeyDown)));
        assertEquals(1, selField.get(tui), "Down arrow must increment sel");

        // Press up arrow
        tui.update(new KeyPressMessage(new Key(KeyType.KeyUp)));
        assertEquals(0, selField.get(tui), "Up arrow must decrement sel");

        // Press ESC -> goes back
        tui.update(new KeyPressMessage(new Key(KeyType.keyESC)));
        assertNotEquals(adminGoals, screenField.get(tui), "ESC must navigate back from ADMIN_GOALS");
    }
}
