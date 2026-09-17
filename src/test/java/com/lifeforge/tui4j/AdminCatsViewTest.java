package com.lifeforge.tui4j;

import com.lifeforge.AppContext;
import com.lifeforge.model.RecommendationCategory;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminCatsViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    private List<RecommendationCategory> buildSampleCategories() {
        RecommendationCategory c1 = new RecommendationCategory(1L, "Nutrition", "Dietary guides", null, 1);
        RecommendationCategory c2 = new RecommendationCategory(2L, "Breakfast", "Morning meals", 1L, 2);
        RecommendationCategory c3 = new RecommendationCategory(3L, "Exercise", "Physical fitness", null, 3);
        RecommendationCategory c4 = new RecommendationCategory(4L, "Sleep", "Restorative habits", null, 4);
        return new ArrayList<>(List.of(c1, c2, c3, c4));
    }

    private void injectCategories(LifeForge tui, List<RecommendationCategory> cats) throws Exception {
        Field catListField = LifeForge.class.getDeclaredField("catList");
        catListField.setAccessible(true);
        catListField.set(tui, new ArrayList<>(cats));
    }

    @Test
    public void testAdminCatsTableAt80Columns() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectCategories(tui, buildSampleCategories());

        String rendered = tui.renderAdminCatsPage(80);
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // 1. Top card border
        assertTrue(lines[0].startsWith("┌─"), "Top line must start with ┌─: " + lines[0]);
        assertTrue(lines[0].endsWith("┐"), "Top line must end with ┐: " + lines[0]);
        assertTrue(lines[0].contains("LIFEForge / CATEGORY MANAGEMENT (Admin)"), "Top header mismatch: " + lines[0]);

        // 2. Table top divider
        assertTrue(lines[1].startsWith("├──────┬"), "Top table divider start mismatch: " + lines[1]);
        assertTrue(lines[1].endsWith("┤"), "Top table divider end mismatch: " + lines[1]);
        assertTrue(lines[1].contains("┬─────────────────┬────────────────┤"), "Top divider columns mismatch: " + lines[1]);

        // 3. Header row
        assertTrue(lines[2].contains("ID") && lines[2].contains("CATEGORY NAME")
                && lines[2].contains("PARENT GROUP") && lines[2].contains("STATUS"), "Header row mismatch: " + lines[2]);
        assertTrue(lines[2].startsWith("│"), "Header start: " + lines[2]);
        assertTrue(lines[2].endsWith("│"), "Header end: " + lines[2]);

        // 4. Mid table divider
        assertTrue(lines[3].startsWith("├──────┼"), "Mid table divider start: " + lines[3]);
        assertTrue(lines[3].endsWith("┤"), "Mid table divider end: " + lines[3]);

        // 5. Data rows
        // Row 1: Nutrition (Root (None)), selected (> #1)
        assertTrue(lines[4].contains("> #1"), "Row 1 must be selected (> #1): " + lines[4]);
        assertTrue(lines[4].contains("Nutrition"), "Row 1 name: " + lines[4]);
        assertTrue(lines[4].contains("Root (None)"), "Row 1 root parent: " + lines[4]);
        assertTrue(lines[4].contains("● Active"), "Row 1 status: " + lines[4]);

        // Row 2: Breakfast (Parent is Nutrition), unselected (  #2)
        assertTrue(lines[5].contains("  #2"), "Row 2 must be unselected (  #2): " + lines[5]);
        assertTrue(lines[5].contains("Breakfast"), "Row 2 name: " + lines[5]);
        assertTrue(lines[5].contains("Nutrition"), "Row 2 parent group: " + lines[5]);
        assertTrue(lines[5].contains("● Active"), "Row 2 status: " + lines[5]);

        // Mark category 3 inactive
        tui.getInactiveCatIds().add(3L);
        rendered = tui.renderAdminCatsPage(80);
        clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        lines = clean.split("\n", -1);

        // Row 3: Exercise, inactive (○ Inactive)
        assertTrue(lines[6].contains("  #3"), "Row 3 must be unselected (  #3): " + lines[6]);
        assertTrue(lines[6].contains("Exercise"), "Row 3 name: " + lines[6]);
        assertTrue(lines[6].contains("Root (None)"), "Row 3 parent: " + lines[6]);
        assertTrue(lines[6].contains("○ Inactive"), "Row 3 status: " + lines[6]);

        // Row 4: Sleep
        assertTrue(lines[7].contains("  #4"), "Row 4 must be unselected (  #4): " + lines[7]);
        assertTrue(lines[7].contains("Sleep"), "Row 4 name: " + lines[7]);
        assertTrue(lines[7].contains("Root (None)"), "Row 4 parent: " + lines[7]);
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
    public void testAdminCatsTableAt74ColumnsExactDividers() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectCategories(tui, buildSampleCategories());

        String rendered = tui.renderAdminCatsPage(74);
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // Verify exact divider strings from user specification
        assertEquals("├──────┬──────────────────────────────┬─────────────────┬────────────────┤", lines[1]);
        assertEquals("│ ID   │ CATEGORY NAME                │ PARENT GROUP    │ STATUS         │", lines[2]);
        assertEquals("├──────┼──────────────────────────────┼─────────────────┼────────────────┤", lines[3]);
        assertEquals("├──────┴──────────────────────────────┴─────────────────┴────────────────┤", lines[12]);
    }

    @Test
    public void testDeterministicSortByIdAsc() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        // Injected out of order: #7, #3, #1, #5
        RecommendationCategory c7 = new RecommendationCategory(7L, "Mindfulness", "Mental health", null, 1);
        RecommendationCategory c3 = new RecommendationCategory(3L, "Cardio", "Endurance", null, 2);
        RecommendationCategory c1 = new RecommendationCategory(1L, "Diet", "Food habits", null, 3);
        RecommendationCategory c5 = new RecommendationCategory(5L, "Recovery", "Rest days", null, 4);
        injectCategories(tui, List.of(c7, c3, c1, c5));

        String rendered = tui.renderAdminCatsPage(80);
        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // Must be sorted deterministically by ID ASC: #1, #3, #5, #7
        assertTrue(lines[4].contains("> #1"), "Row 1 must be #1: " + lines[4]);
        assertTrue(lines[4].contains("Diet"), "Row 1 must be Diet: " + lines[4]);

        assertTrue(lines[5].contains("  #3"), "Row 2 must be #3: " + lines[5]);
        assertTrue(lines[5].contains("Cardio"), "Row 2 must be Cardio: " + lines[5]);

        assertTrue(lines[6].contains("  #5"), "Row 3 must be #5: " + lines[6]);
        assertTrue(lines[6].contains("Recovery"), "Row 3 must be Recovery: " + lines[6]);

        assertTrue(lines[7].contains("  #7"), "Row 4 must be #7: " + lines[7]);
        assertTrue(lines[7].contains("Mindfulness"), "Row 4 must be Mindfulness: " + lines[7]);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToggleCategoryStatusKeyShortcut() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        List<RecommendationCategory> cats = buildSampleCategories();
        injectCategories(tui, cats);

        Class<?> screenEnum = Class.forName("com.lifeforge.tui4j.LifeForge$Screen");
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Object adminCats = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_CATS");
        screenField.set(tui, adminCats);

        // Category 1 is initially active
        assertFalse(tui.getInactiveCatIds().contains(1L), "Category 1 initially active");

        // Press 't' -> toggles to inactive
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 't' })));
        assertTrue(tui.getInactiveCatIds().contains(1L), "Category 1 must be toggled to INACTIVE");

        // Press 't' again -> toggles back to active
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 't' })));
        assertFalse(tui.getInactiveCatIds().contains(1L), "Category 1 must be toggled back to ACTIVE");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNavigateUpAndDownAndEsc() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        List<RecommendationCategory> cats = buildSampleCategories();
        injectCategories(tui, cats);

        Class<?> screenEnum = Class.forName("com.lifeforge.tui4j.LifeForge$Screen");
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Object adminCats = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_CATS");
        screenField.set(tui, adminCats);

        Field backField = LifeForge.class.getDeclaredField("back");
        backField.setAccessible(true);
        Deque<Object> back = (Deque<Object>) backField.get(tui);
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
        assertNotEquals(adminCats, screenField.get(tui), "ESC must navigate back from ADMIN_CATS");
    }
}
