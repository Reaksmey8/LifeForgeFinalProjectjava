package com.lifeforge.tui4j;

import com.lifeforge.AppContext;
import com.lifeforge.service.AnalyticsService;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminAnalyticsViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    private AnalyticsService.AnalyticsSummary buildSampleAnalytics() {
        List<AnalyticsService.GoalDistributionEntry> gd = List.of(
                new AnalyticsService.GoalDistributionEntry("Gain Weight", 2),
                new AnalyticsService.GoalDistributionEntry("Improve Fitness", 1),
                new AnalyticsService.GoalDistributionEntry("Posture Correction", 1)
        );

        List<AnalyticsService.CategoryPopularityEntry> pc = List.of(
                new AnalyticsService.CategoryPopularityEntry("Nutrition", 1),
                new AnalyticsService.CategoryPopularityEntry("Sleep & Recovery", 1),
                new AnalyticsService.CategoryPopularityEntry("Exercise", 0),
                new AnalyticsService.CategoryPopularityEntry("Daily Micro-Habits", 0),
                new AnalyticsService.CategoryPopularityEntry("Complete Master Rout.", 0)
        );

        return new AnalyticsService.AnalyticsSummary(5, 4, 56, 2, gd, pc);
    }

    private void injectAnalytics(LifeForge tui, AnalyticsService.AnalyticsSummary summary) throws Exception {
        Field analyticsField = LifeForge.class.getDeclaredField("analytics");
        analyticsField.setAccessible(true);
        analyticsField.set(tui, summary);
    }

    @Test
    public void testAdminAnalyticsAt80Columns() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectAnalytics(tui, buildSampleAnalytics());

        String rendered = tui.renderAdminAnalyticsPage(80);
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // 1. Top card border
        assertTrue(lines[0].startsWith("┌─"), "Top border start: " + lines[0]);
        assertTrue(lines[0].endsWith("┐"), "Top border end: " + lines[0]);
        assertTrue(lines[0].contains("PLATFORM ANALYTICS"), "Header title: " + lines[0]);

        // 2. Subtitle and divider
        assertTrue(lines[1].contains("Platform health, engagement, and goal distribution"), "Subtitle: " + lines[1]);
        assertTrue(lines[2].startsWith("├") && lines[2].endsWith("┤"), "Divider: " + lines[2]);

        // 3. Platform KPI Snapshot
        assertTrue(clean.contains("[ PLATFORM KPI SNAPSHOT ]"));
        assertTrue(clean.contains("USERS: 5 Total"), "Should show USERS: 5 Total");
        assertTrue(clean.contains("ACTIVE: 4 (80%)"), "Should show ACTIVE: 4 (80%)");
        assertTrue(clean.contains("RECS: 56 Live / 2 Saved"), "Should show RECS: 56 Live / 2 Saved");

        // 4. Goal Distribution
        assertTrue(clean.contains("[ GOAL DISTRIBUTION ]"));
        assertTrue(clean.contains("Total Goals: 3"));
        assertTrue(clean.contains("GOAL"));
        assertTrue(clean.contains("USERS"));
        assertTrue(clean.contains("SHARE"));
        assertTrue(clean.contains("DISTRIBUTION"));
        assertTrue(clean.contains("Gain Weight"));
        assertTrue(clean.contains("50.0%"));
        assertTrue(clean.contains("25.0%"));

        // 5. Popular Categories
        assertTrue(clean.contains("[ POPULAR CATEGORIES ]"));
        assertTrue(clean.contains("Total Saves: 2"));
        assertTrue(clean.contains("CATEGORY"));
        assertTrue(clean.contains("SAVES"));
        assertTrue(clean.contains("POPULARITY"));
        assertTrue(clean.contains("Nutrition"));
        assertTrue(clean.contains("Sleep & Recovery"));

        // 6. Bottom card border
        int bottomIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("└") && lines[i].endsWith("┘")) {
                bottomIdx = i;
                break;
            }
        }
        assertTrue(bottomIdx > 0, "Card must have a bottom border └...┘");

        // 7. Verify all lines within card (0 to bottomIdx) have exact width 80
        for (int i = 0; i <= bottomIdx; i++) {
            assertEquals(80, Theme.width(lines[i]), "Line " + i + " width must be 80: " + lines[i]);
            assertTrue(lines[i].startsWith("┌") || lines[i].startsWith("├") || lines[i].startsWith("│") || lines[i].startsWith("└"),
                    "Line " + i + " must start with border: " + lines[i]);
            assertTrue(lines[i].endsWith("┐") || lines[i].endsWith("┤") || lines[i].endsWith("│") || lines[i].endsWith("┘"),
                    "Line " + i + " must end with border: " + lines[i]);
        }

        // 8. Compact single-line footer
        assertTrue(clean.contains("[R] Refresh Stats"));
        assertTrue(clean.contains("[H] Home"));
        assertTrue(clean.contains("[B] Back"));
        assertTrue(clean.contains("[Q] Quit"));
    }

    @Test
    public void testMiniBarProgression() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        Method m = LifeForge.class.getDeclaredMethod("renderMiniBar", double.class, int.class);
        m.setAccessible(true);

        String bar0 = (String) m.invoke(tui, 0.0, 16);
        assertEquals("[□□□□□□□□□□□□□□□□]", bar0, "0% bar");

        String bar50 = (String) m.invoke(tui, 50.0, 16);
        assertEquals("[■■■■■■■■□□□□□□□□]", bar50, "50% bar");

        String bar100 = (String) m.invoke(tui, 100.0, 16);
        assertEquals("[■■■■■■■■■■■■■■■■]", bar100, "100% bar");
    }

    @Test
    public void testResponsiveAdminAnalytics() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectAnalytics(tui, buildSampleAnalytics());

        int[] widths = { 80, 100, 120 };
        for (int w : widths) {
            String rendered = tui.renderAdminAnalyticsPage(w);
            assertNotNull(rendered);
            String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
            String[] lines = clean.split("\n", -1);

            int bottomIdx = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].startsWith("└") && lines[i].endsWith("┘")) {
                    bottomIdx = i;
                    break;
                }
            }
            assertTrue(bottomIdx > 0, "Card bottom border must exist at width " + w);

            for (int i = 0; i <= bottomIdx; i++) {
                assertEquals(w, Theme.width(lines[i]), "At width " + w + ", line " + i + " width must be " + w);
            }
        }
    }

    @Test
    public void testAdminAnalyticsKpiWithAllActiveUsers() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        AnalyticsService.AnalyticsSummary summary = new AnalyticsService.AnalyticsSummary(
                3, 3, 56, 2, List.of(), List.of()
        );
        injectAnalytics(tui, summary);

        String rendered = tui.renderAdminAnalyticsPage(80);
        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");

        assertTrue(clean.contains("USERS: 3 Total"), "Should show USERS: 3 Total");
        assertTrue(clean.contains("ACTIVE: 3 (100%)"), "Should show ACTIVE: 3 (100%)");
    }
}

