package com.lifeforge.tui4j;

import com.lifeforge.AppContext;
import com.lifeforge.model.ActivityLevel;
import com.lifeforge.model.Goal;
import com.lifeforge.model.Recommendation;
import com.lifeforge.model.RecommendationCategory;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminRecsViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    private List<Recommendation> buildSampleRecs() {
        Recommendation r1 = new Recommendation();
        r1.setId(75L);
        r1.setGoalId(1L);
        r1.setCategoryId(10L);
        r1.setActivityLevel(ActivityLevel.SEDENTARY);
        r1.setTitle("Stay Properly Hydrated");
        r1.setDescription("Drink at least 2.5 liters of clean water daily to keep metabolism efficient.");
        r1.setRecommendedActions("Keep a water bottle on your desk and take sips regularly.");
        r1.setSuggestedTarget("1.9 L/d");
        r1.setImportantNotes("Avoid excessive sugary drinks and reduce caffeine intake.");

        Recommendation r2 = new Recommendation();
        r2.setId(74L);
        r2.setGoalId(1L);
        r2.setCategoryId(11L);
        r2.setActivityLevel(ActivityLevel.LIGHTLY_ACTIVE);
        r2.setTitle("Prioritize Quality Sleep");
        r2.setDescription("Maintain a strict sleep schedule of 7 to 9 hours nightly.");
        r2.setRecommendedActions("Turn off all screens 30 minutes before bedtime.");
        r2.setSuggestedTarget("7-9 hrs");
        r2.setImportantNotes("Keep room temperature cool and dark.");

        Recommendation r3 = new Recommendation();
        r3.setId(70L);
        r3.setGoalId(2L);
        r3.setCategoryId(12L);
        r3.setActivityLevel(null);
        r3.setTitle("Daily Posture Check");
        r3.setDescription("Perform quick posture checks during desk work sessions.");
        r3.setRecommendedActions("Roll shoulders back and align spine.");
        r3.setSuggestedTarget("Micro");
        r3.setImportantNotes("Use ergonomic chair support.");

        return List.of(r1, r2, r3);
    }

    private void injectTestData(LifeForge tui, List<Recommendation> recs) throws Exception {
        Field recsField = LifeForge.class.getDeclaredField("displayedRecs");
        recsField.setAccessible(true);
        recsField.set(tui, new ArrayList<>(recs));

        Field allRecsField = LifeForge.class.getDeclaredField("recomms");
        allRecsField.setAccessible(true);
        allRecsField.set(tui, new ArrayList<>(recs));

        Goal g1 = new Goal(1L, "LOSE_WEIGHT", "Lose Weight", "Lose body weight", true);
        Goal g2 = new Goal(2L, "GENERAL_WELLNESS", "General Well", "General wellness", true);
        Field goalsField = LifeForge.class.getDeclaredField("goals");
        goalsField.setAccessible(true);
        goalsField.set(tui, List.of(g1, g2));

        RecommendationCategory c1 = new RecommendationCategory(10L, "Hydration", "Hydration category", null, 1);
        RecommendationCategory c2 = new RecommendationCategory(11L, "Sleep", "Sleep category", null, 2);
        RecommendationCategory c3 = new RecommendationCategory(12L, "Posture", "Posture category", null, 3);
        Field catsField = LifeForge.class.getDeclaredField("catList");
        catsField.setAccessible(true);
        catsField.set(tui, List.of(c1, c2, c3));
    }

    @Test
    public void testAdminRecsTableAt80Columns() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectTestData(tui, buildSampleRecs());

        String rendered = tui.renderAdminRecsPage(80);
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // 1. Frame boundaries
        assertTrue(lines[0].startsWith("┌─"), "Top line must start with ┌─: " + lines[0]);
        assertTrue(lines[0].endsWith("┐"), "Top line must end with ┐: " + lines[0]);
        assertTrue(lines[0].contains("LIFEForge / RECOMMENDATION CMS (Admin)"), "Top header text mismatch: " + lines[0]);

        // 2. Search bar row
        assertTrue(lines[1].startsWith("│  Search: ["), "Search bar row mismatch: " + lines[1]);
        assertTrue(lines[1].contains("(3)"), "Search bar must show total count: " + lines[1]);
        assertTrue(lines[1].endsWith("│"), "Search bar row must end with │: " + lines[1]);

        // 3. Table grid dividers and headers
        assertEquals("├──────┬────────────────────────────────┬────────────────┬────────────┬────────┤", lines[2]);
        assertTrue(lines[3].contains("ID") && lines[3].contains("RECOMMENDATION TITLE") && lines[3].contains("GOAL") && lines[3].contains("TARGET") && lines[3].contains("STAT"));
        assertEquals("├──────┼────────────────────────────────┼────────────────┼────────────┼────────┤", lines[4]);

        // 4. Data rows
        assertTrue(lines[5].contains(">#75"), "First row must be selected (>#75): " + lines[5]);
        assertTrue(lines[5].contains("Stay Properly Hydrated"), "First row title: " + lines[5]);
        assertTrue(lines[5].contains("Lose Weight"), "First row goal: " + lines[5]);
        assertTrue(lines[5].contains("1.9 L/d"), "First row target: " + lines[5]);
        assertTrue(lines[5].contains("ACT"), "First row status must be ACT: " + lines[5]);

        assertTrue(lines[6].contains(" #74"), "Second row must be unselected ( #74): " + lines[6]);
        assertTrue(lines[6].contains("Prioritize Quality Sleep"), "Second row title: " + lines[6]);
        assertTrue(lines[6].contains("7-9 hrs"), "Second row target: " + lines[6]);

        assertTrue(lines[7].contains(" #70"), "Third row must be unselected ( #70): " + lines[7]);
        assertTrue(lines[7].contains("Daily Posture Check"), "Third row title: " + lines[7]);
        assertTrue(lines[7].contains("Micro"), "Third row target: " + lines[7]);

        // 5. Table closure divider
        assertEquals("├──────┴────────────────────────────────┴────────────────┴────────────┴────────┤", lines[13]);

        // 6. Pagination summary row
        assertTrue(lines[14].contains("Showing 1-3 of 3"), "Summary left: " + lines[14]);
        assertTrue(lines[14].contains("[←/→] Page 1 of 1"), "Summary right: " + lines[14]);

        // 7. Footer divider
        assertTrue(lines[15].startsWith("├"), "Footer divider: " + lines[15]);

        // 8. Action hints inside card
        assertTrue(lines[16].contains("Select"), "Action hints: " + lines[16]);
        assertTrue(lines[16].contains("Edit"), "Action hints: " + lines[16]);
        assertTrue(lines[16].contains("New"), "Action hints: " + lines[16]);
        assertTrue(lines[16].contains("Search"), "Action hints: " + lines[16]);
        assertTrue(lines[16].contains("Delete"), "Action hints: " + lines[16]);
        assertTrue(lines[16].contains("Back"), "Action hints: " + lines[16]);
        assertTrue(lines[16].contains("Quit"), "Action hints: " + lines[16]);

        // 9. Bottom card border
        assertTrue(lines[17].startsWith("└"), "Card bottom start: " + lines[17]);
        assertTrue(lines[17].endsWith("┘"), "Card bottom end: " + lines[17]);

        // 10. Strict 80-column alignment across all card lines (lines 0 to 17)
        for (int i = 0; i <= 17; i++) {
            assertEquals(80, Theme.width(lines[i]), "Line " + i + " width must be 80: " + lines[i]);
        }
    }

    @Test
    public void testAdminRecDetailSingleCardAt80Columns() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        List<Recommendation> recs = buildSampleRecs();
        injectTestData(tui, recs);

        Field editRecIdField = LifeForge.class.getDeclaredField("editRecId");
        editRecIdField.setAccessible(true);
        editRecIdField.set(tui, 75L);

        String rendered = tui.renderAdminRecDetailPage(80);
        assertNotNull(rendered);

        // Begins with clear screen code
        assertTrue(rendered.startsWith("\u001B[H\u001B[2J"), "Detail view must start with clear screen code");

        String clean = rendered.substring("\u001B[H\u001B[2J\n".length()).replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // 1. Single card top border
        assertTrue(lines[0].startsWith("┌─"), "Top border start: " + lines[0]);
        assertTrue(lines[0].endsWith("┐"), "Top border end: " + lines[0]);
        assertTrue(lines[0].contains("RECOMMENDATION DETAIL [#75]"), "Header title: " + lines[0]);

        // 2. Exact 3 sub-sections
        int basicMetaCount = 0;
        int contentCount = 0;
        int guidanceCount = 0;
        int topBorderCount = 0;
        int bottomBorderCount = 0;

        for (String l : lines) {
            if (l.contains("BASIC METADATA")) basicMetaCount++;
            if (l.contains("CONTENT")) contentCount++;
            if (l.contains("GUIDANCE SPECIFICATIONS")) guidanceCount++;
            if (l.startsWith("┌─")) topBorderCount++;
            if (l.startsWith("└")) bottomBorderCount++;
            assertFalse(l.startsWith("│├"), "Divider line must not have dangling left border tick: " + l);
        }

        assertEquals(1, topBorderCount, "Must have strictly ONE top border (no duplicate cards)");
        assertEquals(1, bottomBorderCount, "Must have strictly ONE bottom border");
        assertEquals(1, basicMetaCount, "Must have strictly ONE BASIC METADATA section");
        assertEquals(1, contentCount, "Must have strictly ONE CONTENT section");
        assertEquals(1, guidanceCount, "Must have strictly ONE GUIDANCE SPECIFICATIONS section");
        assertFalse(clean.contains("├─ 📋 BASIC METADATA"), "BASIC METADATA must not collide with top border as a horizontal divider");

        // 3. Section content verification & colon alignment
        assertTrue(clean.contains("• Goal           : Lose Weight"));
        assertTrue(clean.contains("• Category       : Hydration"));
        assertTrue(clean.contains("• Activity Level : Sedentary"));
        assertTrue(clean.contains("• Status         : ● Active"));

        int goalColon = -1, catColon = -1, actColon = -1, statColon = -1;
        for (String l : lines) {
            if (l.contains("• Goal")) goalColon = l.indexOf(':');
            if (l.contains("• Category")) catColon = l.indexOf(':');
            if (l.contains("• Activity Level")) actColon = l.indexOf(':');
            if (l.contains("• Status")) statColon = l.indexOf(':');
        }
        assertEquals(20, goalColon, "Goal colon alignment");
        assertEquals(20, catColon, "Category colon alignment");
        assertEquals(20, actColon, "Activity Level colon alignment");
        assertEquals(20, statColon, "Status colon alignment");

        assertTrue(clean.contains("Title:          Stay Properly Hydrated"));
        assertTrue(clean.contains("Description:"));
        assertTrue(clean.contains("Drink at least 2.5 liters of clean water daily"));
        assertTrue(clean.contains("Recommended Actions:"));
        assertTrue(clean.contains("Keep a water bottle on your desk"));
        assertTrue(clean.contains("Suggested Target:   1.9 L/d"));
        assertTrue(clean.contains("Important Notes:"));
        assertTrue(clean.contains("Avoid excessive sugary drinks"));

        // 4. Action footer
        assertTrue(clean.contains("[E] Edit Fields"));
        assertTrue(clean.contains("[T] Toggle Status"));
        assertTrue(clean.contains("[D] Delete"));
        assertTrue(clean.contains("[ESC/B] Back to CMS"));

        // 5. Strict 80-column alignment for all card rows
        for (int i = 0; i < lines.length; i++) {
            assertEquals(80, Theme.width(lines[i]), "Detail row " + i + " width must be 80: " + lines[i]);
            assertTrue(lines[i].endsWith("│") || lines[i].endsWith("┐") || lines[i].endsWith("┘") || lines[i].endsWith("┤"),
                    "Detail row " + i + " must end with border: " + lines[i]);
            assertTrue(lines[i].startsWith("┌─") || lines[i].startsWith("├") || lines[i].startsWith("│  ") || lines[i].startsWith("└"),
                    "Detail row " + i + " must start with valid border: " + lines[i]);
        }

        // 6. Test padLine directly
        String samplePad = LifeForge.padLine("Sample content", 80);
        String cleanPad = samplePad.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
        assertEquals(80, Theme.width(cleanPad));
        assertTrue(cleanPad.startsWith("│  Sample content"));
        assertTrue(cleanPad.endsWith(" │"));
    }

    @Test
    public void testStatusToggleActiveInactive() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        List<Recommendation> recs = buildSampleRecs();
        injectTestData(tui, recs);

        Field editRecIdField = LifeForge.class.getDeclaredField("editRecId");
        editRecIdField.setAccessible(true);
        editRecIdField.set(tui, 75L);

        // Initially active
        assertFalse(tui.getInactiveRecIds().contains(75L));
        String detail1 = tui.renderAdminRecDetailPage(80);
        assertTrue(detail1.contains("● Active"));
        String table1 = tui.renderAdminRecsPage(80);
        assertTrue(table1.contains("ACT"));

        // Toggle to inactive
        tui.getInactiveRecIds().add(75L);
        assertTrue(tui.getInactiveRecIds().contains(75L));
        String detail2 = tui.renderAdminRecDetailPage(80);
        assertTrue(detail2.contains("● Inactive"));
        String table2 = tui.renderAdminRecsPage(80);
        assertTrue(table2.contains("INA"));

        // Toggle back to active
        tui.getInactiveRecIds().remove(75L);
        assertFalse(tui.getInactiveRecIds().contains(75L));
        String detail3 = tui.renderAdminRecDetailPage(80);
        assertTrue(detail3.contains("● Active"));
        String table3 = tui.renderAdminRecsPage(80);
        assertTrue(table3.contains("ACT"));
    }

    @Test
    public void testAddDetailSectionLinesDoesNotDoubleBody() {
        LifeForge tui = new LifeForge(AppContext.build());
        List<ScreenKit.Line> body = new ArrayList<>();
        body.add(ScreenKit.Line.plain("Initial 1"));
        body.add(ScreenKit.Line.plain("Initial 2"));

        int initialSize = body.size();
        assertEquals(2, initialSize);

        // Calling addDetailSectionLines and adding to body should ONLY add the new lines, not double existing ones
        List<ScreenKit.Line> section1 = tui.addDetailSectionLines(body, "Description", "A brief description.", 78);
        body.addAll(section1);

        // section1 has 1 blank + 1 heading + 1 paragraph line = 3 lines
        assertEquals(3, section1.size());
        assertEquals(5, body.size());

        List<ScreenKit.Line> section2 = tui.addDetailSectionLines(body, "Notes", "Some important notes.", 78);
        body.addAll(section2);

        assertEquals(3, section2.size());
        assertEquals(8, body.size(), "Body size must be exactly 8 (no recursive doubling)");
    }

    @Test
    public void testAdminRecSearchActiveFilterFooterAndWidth() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectTestData(tui, buildSampleRecs());

        // Without search filter: no Clear
        String renderedWithoutFilter = tui.renderAdminRecsPage(80);
        String cleanWithoutFilter = renderedWithoutFilter.replaceAll("\u001B\\[[;\\d]*m", "");
        assertFalse(cleanWithoutFilter.contains("Clear"), "Without active filter, footer should not show Clear");

        // Set search filter
        tui.setAdminRecSearchQuery("Hydrated");
        String renderedWithFilter = tui.renderAdminRecsPage(80);
        String cleanWithFilter = renderedWithFilter.replaceAll("\u001B\\[[;\\d]*m", "");
        assertTrue(cleanWithFilter.contains("Clear"), "With active filter, footer MUST show Clear");

        // Verify width <= 80 on all lines including footer
        String[] lines = cleanWithFilter.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            assertTrue(Theme.width(lines[i]) <= 80, "Line " + i + " must not exceed 80 cols: " + lines[i]);
        }
    }

    @Test
    public void testResetAdminRecSearch() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectTestData(tui, buildSampleRecs());

        tui.setAdminRecSearchQuery("Hydrated");
        tui.setAdminRecCurrentPage(2);
        assertEquals("Hydrated", tui.getAdminRecSearchQuery());
        assertEquals(2, tui.getAdminRecCurrentPage());

        tui.resetAdminRecSearch();
        assertEquals("", tui.getAdminRecSearchQuery(), "Search query must be reset to empty string");
        assertEquals(0, tui.getAdminRecCurrentPage(), "Current page must be reset to 0");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSearchFilterResetOnEnteringAdminRecs() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectTestData(tui, buildSampleRecs());

        Class<?> screenEnum = Class.forName("com.lifeforge.tui4j.LifeForge$Screen");
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Object adminHome = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_HOME");
        Object adminRecs = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_RECS");

        // Navigate to ADMIN_HOME and set a lingering search query
        screenField.set(tui, adminHome);
        tui.setAdminRecSearchQuery("Sleep");
        tui.setAdminRecCurrentPage(1);

        // Call goTo(ADMIN_RECS) via reflection
        Method goToMethod = LifeForge.class.getDeclaredMethod("goTo", screenEnum);
        goToMethod.setAccessible(true);
        goToMethod.invoke(tui, adminRecs);

        // Verify search query and page are automatically reset
        assertEquals("", tui.getAdminRecSearchQuery(), "Search query must be reset when entering ADMIN_RECS from ADMIN_HOME");
        assertEquals(0, tui.getAdminRecCurrentPage(), "Page must be reset to 0 when entering ADMIN_RECS from ADMIN_HOME");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSearchFilterClearKeyShortcutsInAdminRecs() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectTestData(tui, buildSampleRecs());

        Class<?> screenEnum = Class.forName("com.lifeforge.tui4j.LifeForge$Screen");
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Object adminRecs = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_RECS");
        screenField.set(tui, adminRecs);

        // Test pressing 'c' clears search filter
        tui.setAdminRecSearchQuery("Posture");
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'c' })));
        assertEquals("", tui.getAdminRecSearchQuery(), "Pressing 'c' must clear search filter");

        // Test pressing 'x' clears search filter
        tui.setAdminRecSearchQuery("Posture");
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'x' })));
        assertEquals("", tui.getAdminRecSearchQuery(), "Pressing 'x' must clear search filter");

        // Test pressing ESC clears search filter (and stays on ADMIN_RECS)
        tui.setAdminRecSearchQuery("Posture");
        tui.update(new KeyPressMessage(new Key(KeyType.keyESC)));
        assertEquals("", tui.getAdminRecSearchQuery(), "Pressing ESC with active filter must clear filter");
        assertEquals(adminRecs, screenField.get(tui), "Pressing ESC with active filter should remain on ADMIN_RECS");
    }

    @Test
    public void testAdminHomeMenuRowWidthAndBorderAlignment() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());

        Field termWidthField = LifeForge.class.getDeclaredField("termWidth");
        termWidthField.setAccessible(true);
        termWidthField.set(tui, 80);

        com.lifeforge.model.User admin = new com.lifeforge.model.User();
        admin.setFullName("CHHOM CHANREAKSMEY");
        admin.setRole(com.lifeforge.model.Role.ADMIN);
        AppContext ctx = AppContext.build();
        ctx.session.setCurrentUser(admin);

        Field ctxField = LifeForge.class.getDeclaredField("ctx");
        ctxField.setAccessible(true);
        ctxField.set(tui, ctx);

        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        for (Object s : screenField.getType().getEnumConstants()) {
            if (s.toString().equals("ADMIN_HOME")) {
                Method goToMethod = LifeForge.class.getDeclaredMethod("goTo", screenField.getType());
                goToMethod.setAccessible(true);
                goToMethod.invoke(tui, s);
                break;
            }
        }

        String rendered = tui.view();
        String[] lines = rendered.split("\n", -1);

        for (String line : lines) {
            String clean = line.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
            if (clean.isBlank()) continue;
            assertEquals(80, Theme.width(clean), "Every non-empty row in ADMIN_HOME must have terminal width 80: " + clean);
            assertTrue(clean.startsWith("┌") || clean.startsWith("├") || clean.startsWith("│") || clean.startsWith("└"),
                    "Row must start with valid box-drawing border: " + clean);
            assertTrue(clean.endsWith("┐") || clean.endsWith("┤") || clean.endsWith("│") || clean.endsWith("┘"),
                    "Row must end with valid box-drawing border: " + clean);
        }
    }

    @Test
    public void testBoxDrawingCharactersWidthIsOne() {
        char[] boxChars = new char[] { '┌', '┐', '└', '┘', '├', '┤', '┬', '┴', '┼', '─', '│' };
        for (char c : boxChars) {
            assertEquals(1, Theme.displayWidth(c), "Character '" + c + "' displayWidth must be 1");
            assertEquals(1, Theme.width(String.valueOf(c)), "Character '" + c + "' Theme.width must be 1");
        }
    }

    @Test
    public void testResponsiveFramesAcrossDimensions() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectTestData(tui, buildSampleRecs());

        int[] widths = { 80, 100, 120 };
        for (int w : widths) {
            String rendered = tui.renderAdminRecsPage(w);
            assertNotNull(rendered);
            String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
            String[] lines = clean.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                assertEquals(w, Theme.width(lines[i]), "At width " + w + ", line " + i + " must have exact width " + w);
                assertTrue(lines[i].startsWith("┌") || lines[i].startsWith("├") || lines[i].startsWith("│") || lines[i].startsWith("└"),
                        "Line " + i + " must start with box character at width " + w);
                assertTrue(lines[i].endsWith("┐") || lines[i].endsWith("┤") || lines[i].endsWith("│") || lines[i].endsWith("┘"),
                        "Line " + i + " must end with box character at width " + w);
            }
        }
    }
}
