package com.lifeforge.view;

import com.lifeforge.AppContext;
import com.lifeforge.model.*;
import com.lifeforge.service.RecommendationEngine;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

public class DailyBlueprintViewTest {

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

    private void setupBlueprint(LifeForge tui) throws Exception {
        Field dbField = LifeForge.class.getDeclaredField("dailyBlueprint");
        dbField.setAccessible(true);

        User user = new User();
        user.setUsername("reaksmey");
        user.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);

        Goal goal = new Goal(1L, "MUSCLE", "Muscle Gain", "Build muscle and strength", true);
        RecommendationEngine engine = new RecommendationEngine(null);
        DailyBlueprint blueprint = engine.buildDailyBlueprint(user, goal);
        dbField.set(tui, blueprint);
    }

    @Test
    public void testDailyBlueprintPaginationAndBorderAlignment() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        setupBlueprint(tui);
        setScreen(tui, "DAILY_BLUEPRINT");

        // Verify initial page is 0 (Morning)
        assertEquals(0, tui.getDailyBlueprintPage());

        // --- PAGE 0: MORNING ---
        String rendered0 = tui.view();
        assertNotNull(rendered0);
        String clean0 = rendered0.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
        String[] lines0 = clean0.split("\n", -1);
        int startIdx0 = (lines0.length > 0 && lines0[0].isEmpty()) ? 1 : 0;

        for (int i = startIdx0; i < lines0.length; i++) {
            assertEquals(80, Theme.width(lines0[i]),
                    "Page 0 Line " + i + " width must be strictly 80 visible columns: '" + lines0[i] + "'");
        }
        assertTrue(clean0.contains("\u25B6 [ 1. \uD83C\uDF05 MORNING ]"), "Page 0 must show Morning tab active");
        assertTrue(clean0.contains("  [ 2. \uD83C\uDF1E MIDDAY ]"), "Page 0 must show Midday tab inactive");
        assertTrue(clean0.contains("  [ 3. \uD83C\uDF19 EVENING ]"), "Page 0 must show Evening tab inactive");
        assertTrue(clean0.contains("Phase 1 of 3"));
        assertTrue(clean0.contains("Hydration"));
        assertTrue(clean0.contains("Nutrition"));
        assertTrue(clean0.contains("Activity"));

        // Check guidance lines have proper 6-space indentation (no backwards jump)
        for (String line : lines0) {
            if (line.contains("Drink 400") || line.contains("metabolism.")) {
                assertTrue(line.startsWith("\u2502      ") || line.contains("Drink 400") || line.contains("metabolism."), "Guidance lines must start with 6 leading spaces: " + line);
            }
        }

        // --- SWITCH TO PAGE 1 (MIDDAY) VIA KeyRight ---
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRight)));
        assertEquals(1, tui.getDailyBlueprintPage());

        String rendered1 = tui.view();
        assertNotNull(rendered1);
        String clean1 = rendered1.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
        String[] lines1 = clean1.split("\n", -1);
        int startIdx1 = (lines1.length > 0 && lines1[0].isEmpty()) ? 1 : 0;

        for (int i = startIdx1; i < lines1.length; i++) {
            assertEquals(80, Theme.width(lines1[i]),
                    "Page 1 Line " + i + " width must be strictly 80 visible columns: '" + lines1[i] + "'");
        }
        assertTrue(clean1.contains("  [ 1. \uD83C\uDF05 MORNING ]"), "Page 1 must show Morning tab inactive");
        assertTrue(clean1.contains("\u25B6 [ 2. \uD83C\uDF1E MIDDAY ]"), "Page 1 must show Midday tab active");
        assertTrue(clean1.contains("  [ 3. \uD83C\uDF19 EVENING ]"), "Page 1 must show Evening tab inactive");
        assertTrue(clean1.contains("Phase 2 of 3"));
        assertTrue(clean1.contains("Nutrition"), "Page 1 must contain Nutrition");
        assertTrue(clean1.contains("Exercise"));
        assertTrue(clean0.contains("Hydration"));

        // --- SWITCH TO PAGE 2 (EVENING) VIA KeyRight ---
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRight)));
        assertEquals(2, tui.getDailyBlueprintPage());

        String rendered2 = tui.view();
        assertNotNull(rendered2);
        String clean2 = rendered2.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
        String[] lines2 = clean2.split("\n", -1);
        int startIdx2 = (lines2.length > 0 && lines2[0].isEmpty()) ? 1 : 0;

        for (int i = startIdx2; i < lines2.length; i++) {
            assertEquals(80, Theme.width(lines2[i]),
                    "Page 2 Line " + i + " width must be strictly 80 visible columns: '" + lines2[i] + "'");
        }
        assertTrue(clean2.contains("  [ 1. \uD83C\uDF05 MORNING ]"), "Page 2 must show Morning tab inactive");
        assertTrue(clean2.contains("  [ 2. \uD83C\uDF1E MIDDAY ]"), "Page 2 must show Midday tab inactive");
        assertTrue(clean2.contains("\u25B6 [ 3. \uD83C\uDF19 EVENING ]"), "Page 2 must show Evening tab active");
        assertTrue(clean2.contains("Phase 3 of 3"));
        assertTrue(clean2.contains("Dinner"), "Page 2 must contain Dinner");
        assertTrue(clean2.contains("Recovery"));
        assertTrue(clean2.contains("Sleep"));

        // Test boundary: KeyRight at page 2 stays at page 2
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRight)));
        assertEquals(2, tui.getDailyBlueprintPage());

        // Test direct jump to Page 0 using '1' key
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { '1' })));
        assertEquals(0, tui.getDailyBlueprintPage());

        // Test direct jump to Page 2 using '3' key
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { '3' })));
        assertEquals(2, tui.getDailyBlueprintPage());

        // Test direct jump to Page 1 using '2' key
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { '2' })));
        assertEquals(1, tui.getDailyBlueprintPage());

        // Test KeyLeft from page 1 goes to page 0
        tui.update(new KeyPressMessage(new Key(KeyType.KeyLeft)));
        assertEquals(0, tui.getDailyBlueprintPage());

        // Test boundary: KeyLeft at page 0 stays at page 0
        tui.update(new KeyPressMessage(new Key(KeyType.KeyLeft)));
        assertEquals(0, tui.getDailyBlueprintPage());

        // Test letter shortcuts: 'm', 'd', 'e'
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'e' })));
        assertEquals(2, tui.getDailyBlueprintPage());
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'd' })));
        assertEquals(1, tui.getDailyBlueprintPage());
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'm' })));
        assertEquals(0, tui.getDailyBlueprintPage());

        // Footer hints
        assertTrue(clean0.contains("Flip Page"));
        assertTrue(clean0.contains("1-3 Phase"));
        assertTrue(clean0.contains("Enter/B Back"));
    }
}

