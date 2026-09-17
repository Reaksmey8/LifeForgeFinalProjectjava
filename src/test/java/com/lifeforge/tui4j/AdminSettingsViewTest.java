package com.lifeforge.tui4j;

import com.lifeforge.AppContext;
import com.lifeforge.config.AppConfig;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminSettingsViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    @SuppressWarnings("unchecked")
    private void setScreen(LifeForge tui, String screenName) throws Exception {
        Class<?> screenEnum = Class.forName("com.lifeforge.tui4j.LifeForge$Screen");
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Object target = Enum.valueOf((Class<Enum>) screenEnum, screenName);
        screenField.set(tui, target);
    }

    private String getScreenName(LifeForge tui) throws Exception {
        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Object val = screenField.get(tui);
        return val == null ? "" : val.toString();
    }

    private String getStatus(LifeForge tui) throws Exception {
        Field statusField = LifeForge.class.getDeclaredField("status");
        statusField.setAccessible(true);
        return (String) statusField.get(tui);
    }

    @Test
    public void testAdminSettingsPageLayoutAt80ColumnsWithDbConnected() {
        LifeForge tui = new LifeForge(AppContext.build());
        tui.setDbOk(true);

        String rendered = tui.renderAdminSettingsPage(80);
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
        String[] lines = clean.split("\n", -1);

        // Strip initial ANSI clear screen if first line is empty
        int startIdx = 0;
        if (lines.length > 0 && lines[0].isEmpty()) {
            startIdx = 1;
        }

        // Verify every line is strictly 80 visible columns
        for (int i = startIdx; i < lines.length; i++) {
            assertEquals(80, Theme.width(lines[i]),
                    "Line " + i + " width must be strictly 80 visible columns: '" + lines[i] + "'");
        }

        // 1. Top card border
        String firstLine = lines[startIdx];
        assertTrue(firstLine.startsWith("┌─"), "Top border start: " + firstLine);
        assertTrue(firstLine.endsWith("┐"), "Top border end: " + firstLine);
        assertTrue(firstLine.contains("LIFEForge"), "Header brand: " + firstLine);
        assertTrue(firstLine.contains("SYSTEM SETTINGS"), "Header title: " + firstLine);

        // 2. Subtitle
        assertTrue(clean.contains("Runtime environment, AI configuration, and platform constraints"));

        // 3. Sub-Card 1: [ SYSTEM CORE ]
        assertTrue(clean.contains("[ SYSTEM CORE ]"), "Must contain SYSTEM CORE sub-card");
        assertTrue(clean.contains("Name    : " + AppConfig.APP_NAME));
        assertTrue(clean.contains("Ver: " + AppConfig.APP_VERSION));
        assertTrue(clean.contains("DB Status: ● Connected"));
        assertTrue(clean.contains("Tagline : " + AppConfig.APP_TAGLINE));

        // 4. Sub-Card 2: [ AI ASSISTANT / OLLAMA ]
        assertTrue(clean.contains("[ AI ASSISTANT / OLLAMA ]"), "Must contain AI ASSISTANT sub-card");
        assertTrue(clean.contains("Status   : ● Enabled"));
        assertTrue(clean.contains("Model    : " + AppConfig.getOllamaModel()));
        assertTrue(clean.contains("Provider : Local"));
        assertTrue(clean.contains("Endpoint : " + AppConfig.getOllamaBaseUrl()));
        assertTrue(clean.contains("Fallback : Automatic fallback to rule-based engine if offline"));
        // Ensure NO string truncation on fallback note
        assertFalse(clean.contains("unreachab"), "Fallback note must not cut off at 'unreachab'");

        // 5. Sub-Card 3: [ VALIDATION CONSTRAINTS ]
        assertTrue(clean.contains("[ VALIDATION CONSTRAINTS ]"), "Must contain VALIDATION CONSTRAINTS sub-card");
        assertTrue(clean.contains("Password Min Length : " + AppConfig.MIN_PASSWORD_LENGTH + " ch"));
        assertTrue(clean.contains("Height Range : 100.0 - 250.0 cm"));
        assertTrue(clean.contains("User Age Range      : 13-100"));
        assertTrue(clean.contains("Weight Range :  30.0 - 300.0 kg") || clean.contains("Weight Range : 30.0 - 300.0 kg"));

        // 6. Clean ENV hint without duplicates
        assertTrue(clean.contains("ENV: Set LIFEFORGE_OLLAMA_URL / LIFEFORGE_OLLAMA_MODEL to override."));
        int envOccurrences = clean.split("ENV: Set LIFEFORGE_OLLAMA_URL", -1).length - 1;
        assertEquals(1, envOccurrences, "ENV hint must not be duplicated");

        // 7. Footer controls
        assertTrue(clean.contains("[R] Test Connections"));
        assertTrue(clean.contains("[B] Back"));
        assertTrue(clean.contains("[H] Home"));
        assertTrue(clean.contains("[Q] Quit"));

        // 8. Bottom card border
        String lastLine = lines[lines.length - 1];
        assertTrue(lastLine.startsWith("└") && lastLine.endsWith("┘"), "Bottom border: " + lastLine);
    }

    @Test
    public void testAdminSettingsPageLayoutWithDbDisconnected() {
        LifeForge tui = new LifeForge(AppContext.build());
        tui.setDbOk(false);

        String rendered = tui.renderAdminSettingsPage(80);
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
        String[] lines = clean.split("\n", -1);

        int startIdx = 0;
        if (lines.length > 0 && lines[0].isEmpty()) {
            startIdx = 1;
        }

        for (int i = startIdx; i < lines.length; i++) {
            assertEquals(80, Theme.width(lines[i]),
                    "Line " + i + " width must be strictly 80 visible columns: '" + lines[i] + "'");
        }

        assertTrue(clean.contains("DB Status: ○ Disconnected"));
    }

    @Test
    public void testAdminSettingsKeyRTriggersRefresh() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        setScreen(tui, "ADMIN_SETTINGS");

        // Press 'r' to refresh connections
        KeyPressMessage rKey = new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'r' }));
        tui.update(rKey);

        String status = getStatus(tui);
        assertNotNull(status);
        assertTrue(status.contains("Connections tested"), "Status should indicate connections tested: " + status);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAdminSettingsKeyBNavigatesBack() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        setScreen(tui, "ADMIN_SETTINGS");

        Class<?> screenEnum = Class.forName("com.lifeforge.tui4j.LifeForge$Screen");
        Field backField = LifeForge.class.getDeclaredField("back");
        backField.setAccessible(true);
        java.util.Deque<Object> back = (java.util.Deque<Object>) backField.get(tui);
        Object adminHome = Enum.valueOf((Class<Enum>) screenEnum, "ADMIN_HOME");
        back.push(adminHome);

        // Press 'b' to go back
        KeyPressMessage bKey = new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'b' }));
        tui.update(bKey);

        assertEquals("ADMIN_HOME", getScreenName(tui));
    }

    @Test
    public void testAdminHomeMenuWidths() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        setScreen(tui, "ADMIN_HOME");
        Method refreshMethod = LifeForge.class.getDeclaredMethod("refresh");
        refreshMethod.setAccessible(true);
        refreshMethod.invoke(tui);

        Method menuLabelMethod = ScreenKit.class.getDeclaredMethod("menuLabel", String.class);
        menuLabelMethod.setAccessible(true);
        String settingsLabel = (String) menuLabelMethod.invoke(null, "System Settings");
        assertEquals("🔧 System Settings", settingsLabel, "System Settings icon must be standard 2-column wrench (🔧)");

        Field menuLabelsField = LifeForge.class.getDeclaredField("menuLabels");
        menuLabelsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) menuLabelsField.get(tui);
        for (String l : labels) {
            String ml = (String) menuLabelMethod.invoke(null, l);
            System.out.println("LABEL: '" + l + "' -> '" + ml + "' | WIDTH: " + Theme.width(ml) + " | RAW LEN: " + ml.length());
        }

        String page = tui.view();
        String[] lines = page.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
            if (line.isEmpty()) continue;
            int w = Theme.width(line);
            assertEquals(80, w, "Line " + i + " width must be 80: " + line);
        }
    }
}
