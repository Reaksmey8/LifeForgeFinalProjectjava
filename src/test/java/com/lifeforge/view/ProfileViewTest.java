package com.lifeforge.view;

import com.lifeforge.AppContext;
import com.lifeforge.model.*;
import com.lifeforge.view.ScreenKit.Line;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProfileViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    private User createSampleUser() {
        User u = new User();
        u.setId(10L);
        u.setFullName("John Doe");
        u.setUsername("johndoe");
        u.setEmail("john.doe@example.com");
        u.setRole(Role.USER);
        u.setAge(25);
        u.setGender(Gender.MALE);
        u.setHeightCm(175.0);
        u.setWeightKg(70.0);
        u.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        u.setCreatedAt(LocalDateTime.of(2026, 9, 14, 1, 21, 3, 359598000));
        return u;
    }

    @Test
    public void testDateFormattingStripsNanoseconds() throws Exception {
        AppContext ctx = AppContext.build();
        User u = createSampleUser();
        ctx.session.setCurrentUser(u);
        LifeForge tui = new LifeForge(ctx);

        Field widthField = LifeForge.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.set(tui, 80);

        List<Line> body = new ArrayList<>();
        Method viewProfileMethod = LifeForge.class.getDeclaredMethod("viewProfile", List.class);
        viewProfileMethod.setAccessible(true);
        viewProfileMethod.invoke(tui, body);

        String fullText = body.stream().map(Line::text).reduce("", (a, b) -> a + "\n" + b);

        assertTrue(fullText.contains("Member : 2026-09-14"), "Must contain clean yyyy-MM-dd date");
        assertFalse(fullText.contains("359598"), "Must strip raw nanoseconds");
        assertFalse(fullText.contains("01:21:03"), "Must strip timestamp time component from memberSince");
    }

    @Test
    public void testBmiCalculationAndClassification() {
        User u = new User();

        // 50kg, 165cm -> 18.365 -> 18.4 (Normal)
        u.setHeightCm(165.0);
        u.setWeightKg(50.0);
        assertEquals("18.4 (Normal)", LifeForge.formatBmiScore(u));

        // 70kg, 175cm -> 22.857 -> 22.9 (Normal)
        u.setHeightCm(175.0);
        u.setWeightKg(70.0);
        assertEquals("22.9 (Normal)", LifeForge.formatBmiScore(u));

        // 40kg, 170cm -> 13.84 -> 13.8 (Underweight)
        u.setHeightCm(170.0);
        u.setWeightKg(40.0);
        assertEquals("13.8 (Underweight)", LifeForge.formatBmiScore(u));

        // 85kg, 175cm -> 27.755 -> 27.8 (Overweight)
        u.setHeightCm(175.0);
        u.setWeightKg(85.0);
        assertEquals("27.8 (Overweight)", LifeForge.formatBmiScore(u));

        // 100kg, 175cm -> 32.65 -> 32.7 (Obese)
        u.setHeightCm(175.0);
        u.setWeightKg(100.0);
        assertEquals("32.7 (Obese)", LifeForge.formatBmiScore(u));

        // Null / incomplete metrics
        u.setHeightCm(null);
        assertEquals("-", LifeForge.formatBmiScore(u));
    }

    @Test
    public void testDualCardMetricLayoutAndColonVerticalAlignment() throws Exception {
        AppContext ctx = AppContext.build();
        User u = createSampleUser();
        ctx.session.setCurrentUser(u);
        LifeForge tui = new LifeForge(ctx);

        Field widthField = LifeForge.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.set(tui, 80);

        List<Line> body = new ArrayList<>();
        Method viewProfileMethod = LifeForge.class.getDeclaredMethod("viewProfile", List.class);
        viewProfileMethod.setAccessible(true);
        viewProfileMethod.invoke(tui, body);

        // Sub-box titles
        String fullText = body.stream().map(Line::text).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(fullText.contains("BIOMETRICS"), "Must contain Left Box title");
        assertTrue(fullText.contains("CALIBRATED TARGETS"), "Must contain Right Box title");

        // Locate lines containing the 4 metric rows
        List<String> metricLines = new ArrayList<>();
        for (Line l : body) {
            String txt = l.text();
            if (txt.contains("Age") && txt.contains("Active Goal")) {
                metricLines.add(txt);
            } else if (txt.contains("Gender") && txt.contains("Water Target")) {
                metricLines.add(txt);
            } else if (txt.contains("Height") && txt.contains("Activity")) {
                metricLines.add(txt);
            } else if (txt.contains("Weight") && txt.contains("BMI Score")) {
                metricLines.add(txt);
            }
        }

        assertEquals(4, metricLines.size(), "Must find exactly 4 side-by-side metric rows");

        // Verify vertical colon alignment across all 4 rows
        int expectedColon1 = metricLines.get(0).indexOf(':');
        int expectedColon2 = metricLines.get(0).indexOf(':', expectedColon1 + 1);
        assertTrue(expectedColon1 > 0 && expectedColon2 > 0);

        for (int i = 0; i < metricLines.size(); i++) {
            String row = metricLines.get(i);
            int c1 = row.indexOf(':');
            int c2 = row.indexOf(':', c1 + 1);

            assertEquals(expectedColon1, c1, "Row " + i + " Left Box colon must strictly vertically align");
            assertEquals(expectedColon2, c2, "Row " + i + " Right Box colon must strictly vertically align");
        }
    }

    @Test
    public void testSingleLineHorizontalActions() throws Exception {
        AppContext ctx = AppContext.build();
        User u = createSampleUser();
        ctx.session.setCurrentUser(u);
        LifeForge tui = new LifeForge(ctx);

        Field widthField = LifeForge.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.set(tui, 80);

        List<Line> body = new ArrayList<>();
        Method viewProfileMethod = LifeForge.class.getDeclaredMethod("viewProfile", List.class);
        viewProfileMethod.setAccessible(true);
        viewProfileMethod.invoke(tui, body);

        String fullText = body.stream().map(Line::text).reduce("", (a, b) -> a + "\n" + b);

        // Verify single horizontal action row exists
        assertTrue(fullText.contains("[E] Edit Profile   \u2022   [P] Change Password   \u2022   [D] Delete Account"),
                "Actions must appear on a single horizontal row");

        // Verify vertical list and item numbers are REMOVED
        assertFalse(fullText.contains("> Edit Profile"), "Vertical menu pointer must be removed");
        assertFalse(fullText.contains("1. Edit Profile"), "Item numbers must not appear");
        assertFalse(fullText.contains("2. Change Password"), "Item numbers must not appear");
        assertFalse(fullText.contains("3. Delete My Account"), "Item numbers must not appear");

        // Now test armed state
        Field armedField = LifeForge.class.getDeclaredField("armed");
        armedField.setAccessible(true);
        armedField.set(tui, true);

        List<Line> armedBody = new ArrayList<>();
        viewProfileMethod.invoke(tui, armedBody);
        String armedText = armedBody.stream().map(Line::text).reduce("", (a, b) -> a + "\n" + b);

        assertTrue(armedText.contains("! CONFIRM: Press [D] to delete account   \u2022   [Esc/B] Cancel"),
                "Armed confirmation row must appear when armed");
    }

    @Test
    public void testOuterCardStraightBorders() throws Exception {
        AppContext ctx = AppContext.build();
        User u = createSampleUser();
        ctx.session.setCurrentUser(u);
        LifeForge tui = new LifeForge(ctx);

        Field widthField = LifeForge.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.set(tui, 80);

        List<Line> body = new ArrayList<>();
        Method viewProfileMethod = LifeForge.class.getDeclaredMethod("viewProfile", List.class);
        viewProfileMethod.setAccessible(true);
        viewProfileMethod.invoke(tui, body);

        int expectedWidth = 74; // 2 leading spaces + 72 outer card width

        for (int i = 0; i < body.size(); i++) {
            Line line = body.get(i);
            int visibleWidth = Theme.width(line.text());
            assertEquals(expectedWidth, visibleWidth,
                    "Line " + i + " width mismatch (" + line.text() + ")");
            assertTrue(line.text().startsWith("  \u250C") || line.text().startsWith("  \u2502") || line.text().startsWith("  \u2514"),
                    "Line " + i + " must start with border: " + line.text());
            assertTrue(line.text().endsWith("\u2510") || line.text().endsWith("\u2502") || line.text().endsWith("\u2518"),
                    "Line " + i + " must end with border: " + line.text());
        }
    }

    @Test
    public void testFooterNavigationForProfileScreen() throws Exception {
        AppContext ctx = AppContext.build();
        LifeForge tui = new LifeForge(ctx);

        // Set screen to PROFILE
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Class<?> screenEnum = Class.forName("com.lifeforge.view.LifeForge$Screen");
        Object profileScreen = Enum.valueOf((Class<Enum>) screenEnum, "PROFILE");
        screenField.set(tui, profileScreen);

        Method footerMethod = LifeForge.class.getDeclaredMethod("footer");
        footerMethod.setAccessible(true);
        List<String[]> footerKeys = (List<String[]>) footerMethod.invoke(tui);

        assertNotNull(footerKeys);
        assertEquals(6, footerKeys.size());
        assertArrayEquals(new String[] { "E", "Edit" }, footerKeys.get(0));
        assertArrayEquals(new String[] { "P", "Password" }, footerKeys.get(1));
        assertArrayEquals(new String[] { "D", "Delete" }, footerKeys.get(2));
        assertArrayEquals(new String[] { "B", "Back" }, footerKeys.get(3));
        assertArrayEquals(new String[] { "H", "Home" }, footerKeys.get(4));
        assertArrayEquals(new String[] { "Q", "Quit" }, footerKeys.get(5));

        String[] rendered = ScreenKit.keys(footerKeys.toArray(new String[0][]));
        String combined = String.join("    ", rendered);
        assertTrue(combined.contains("[E] Edit"));
        assertTrue(combined.contains("[P] Password"));
        assertTrue(combined.contains("[D] Delete"));
        assertTrue(combined.contains("[B] Back"));
        assertTrue(combined.contains("[H] Home"));
        assertTrue(combined.contains("[Q] Quit"));
    }

    @Test
    public void testProfileKeyboardShortcutsDispatch() throws Exception {
        AppContext ctx = AppContext.build();
        User u = createSampleUser();
        ctx.session.setCurrentUser(u);
        LifeForge tui = new LifeForge(ctx);

        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Class<?> screenEnum = Class.forName("com.lifeforge.view.LifeForge$Screen");
        Object profileScreen = Enum.valueOf((Class<Enum>) screenEnum, "PROFILE");
        Object profileEditScreen = Enum.valueOf((Class<Enum>) screenEnum, "PROFILE_EDIT");
        Object profilePasswordScreen = Enum.valueOf((Class<Enum>) screenEnum, "PROFILE_PASSWORD");

        Field armedField = LifeForge.class.getDeclaredField("armed");
        armedField.setAccessible(true);

        // Press 'E' -> should go to PROFILE_EDIT
        screenField.set(tui, profileScreen);
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'e' })));
        assertEquals(profileEditScreen, screenField.get(tui), "'e' must navigate to PROFILE_EDIT");

        // Press 'P' -> should go to PROFILE_PASSWORD
        screenField.set(tui, profileScreen);
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'p' })));
        assertEquals(profilePasswordScreen, screenField.get(tui), "'p' must navigate to PROFILE_PASSWORD");

        // Press 'D' when unarmed -> should arm deletion
        screenField.set(tui, profileScreen);
        armedField.set(tui, false);
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'd' })));
        assertTrue((Boolean) armedField.get(tui), "'d' must arm deletion confirmation");

        // Press 'Esc' when armed -> should disarm deletion
        tui.update(new KeyPressMessage(new Key(KeyType.keyESC)));
        assertFalse((Boolean) armedField.get(tui), "Esc must cancel deletion arming");
    }
}

