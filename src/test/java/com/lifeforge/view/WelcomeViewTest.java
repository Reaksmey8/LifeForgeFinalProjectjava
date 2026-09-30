package com.lifeforge.view;

import com.lifeforge.AppContext;
import com.lifeforge.view.ScreenKit.Line;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WelcomeViewTest {

    @BeforeAll
    public static void setup() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    @Test
    public void testMenuCenterAlignsInMiddle() {
        List<String> labels = List.of("Login", "Register Account", "Quit");
        List<Line> lines = ScreenKit.menuCenter(labels, 0, 80);

        assertEquals(3, lines.size());
        assertTrue(lines.get(0).text().contains("> Login"));
        assertTrue(lines.get(1).text().contains("  Register Account"));
        assertTrue(lines.get(2).text().contains("  Quit"));

        int indent1 = lines.get(0).text().indexOf('>');
        int indent2 = lines.get(1).text().indexOf("Register Account");
        assertTrue(indent1 > 20, "Menu should be centered away from left margin, was at " + indent1);
        assertEquals(indent1 + 2, indent2, "Text of options should align cleanly");
    }

    @Test
    public void testSingleContinuousFrameInPage() {
        List<Line> body = new ArrayList<>();
        body.add(Line.plain("Welcome Content"));

        List<String[]> footer = Collections.singletonList(new String[]{"Enter", "Select"});
        String rendered = ScreenKit.page("Welcome", "Personalized health", body, "", false, footer, 80);

        assertNotNull(rendered);
        assertTrue(rendered.contains("┌"));
        assertTrue(rendered.contains("┐"));
        assertTrue(rendered.contains("└"));
        assertTrue(rendered.contains("┘"));
        assertTrue(rendered.contains("│"));
        assertTrue(rendered.contains("├"));
        assertTrue(rendered.contains("┤"));

        assertFalse(rendered.contains("╔"));
        assertFalse(rendered.contains("╚"));
        assertFalse(rendered.contains("║"));
    }

    @Test
    public void testThemeSelectedHasNoBackgroundColor() {
        String rendered = Theme.render(Theme.selected(), "Login");
        assertFalse(rendered.contains("48;"), "Rendered text should not contain ANSI background code (48;...)");
        assertTrue(rendered.contains("Login"), "Rendered text should contain label");
    }

    @Test
    public void testRenderVisual() {
        List<ScreenKit.Line> body = new ArrayList<>();
        int inner = Math.min(100, Math.max(40, 80)) - 2;
        List<String> logoLines = List.of(
                "██╗     ██╗███████╗███████╗███████╗ ██████╗ ██████╗  ██████╗ ███████╗",
                "██║     ██║██╔════╝██╔════╝██╔════╝██╔═══██╗██╔══██╗██╔════╝ ██╔════╝",
                "██║     ██║█████╗  █████╗  █████╗  ██║   ██║██████╔╝██║  ███╗█████╗  ",
                "██║     ██║██╔══╝  ██╔══╝  ██╔══╝  ██║   ██║██╔══██╗██║   ██║██╔══╝  ",
                "███████╗██║██║     ███████╗██║     ╚██████╔╝██║  ██║╚██████╔╝███████╗",
                "╚══════╝╚═╝╚═╝     ╚══════╝╚═╝      ╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚══════╝"
        );

        body.add(ScreenKit.Line.blank());
        for (String line : logoLines) {
            body.add(ScreenKit.Line.of(Theme.title(), Theme.padCenter(line, inner)));
        }
        body.add(ScreenKit.Line.blank());
        body.add(ScreenKit.Line.of(Theme.pivot(), Theme.padCenter("Personalized Health & Lifestyle Recommendation System", inner)));
        body.add(ScreenKit.Line.of(Theme.dim(), Theme.padCenter("v1.0.0", inner)));
        body.add(ScreenKit.Line.blank());
        body.add(ScreenKit.Line.blank());

        String item0 = Theme.render(Theme.headingCyan(), "> ")
                + Theme.render(Theme.headingCyan(), "[L]") + " "
                + Theme.render(Theme.headingCyan(), "Login");
        String item1 = Theme.render(Theme.dim(), "  ")
                + Theme.render(Theme.headingCyan(), "[R]") + " "
                + Theme.render(Theme.text(), "Register Account");
        String item2 = Theme.render(Theme.dim(), "  ")
                + Theme.render(Theme.headingCyan(), "[Q]") + " "
                + Theme.render(Theme.text(), "Quit");
        String sep = Theme.render(Theme.dim(), "     •     ");
        String menuRow = item0 + sep + item1 + sep + item2;
        body.add(ScreenKit.Line.of(Theme.plain(), Theme.padCenter(menuRow, inner)));
        body.add(ScreenKit.Line.blank());

        List<String[]> footer = List.of(
                new String[] { "←/→", "Select" },
                new String[] { "Enter", "Open" },
                new String[] { "L", "Login" },
                new String[] { "R", "Register" },
                new String[] { "Q", "Quit" }
        );

        String page = ScreenKit.page("Welcome", "Personalized health, one goal at a time", body, "", false, footer, 80);
        System.out.println("\n=== RENDERED WELCOME SCREEN ===\n" + page + "\n===============================\n");

        assertNotNull(page);
        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");
        assertTrue(clean.contains("██╗     ██╗███████╗"), "Contains straight ANSI Shadow Unicode banner");
        assertTrue(clean.contains("[L] Login"), "Contains L Login");
        assertTrue(clean.contains("[R] Register Account"), "Contains R Register Account");
        assertTrue(clean.contains("[Q] Quit"), "Contains Q Quit");
        assertTrue(clean.contains("•"), "Contains bullet separator");
        assertFalse(clean.contains("> Register Account"), "Does not contain vertical menu");
    }

    @Test
    public void testWelcomeScreenViewAndKeyNavigation() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        String rendered = tui.view();
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        assertTrue(clean.contains("██╗     ██╗███████╗"), "Welcome view should contain straight ANSI Shadow Unicode banner");
        assertTrue(clean.contains("> [L] Login"), "Welcome view should highlight focused item 0");
        assertTrue(clean.contains("[R] Register Account"), "Welcome view should contain item 1");
        assertTrue(clean.contains("[Q] Quit"), "Welcome view should contain item 2");

        Field screenField = LifeForge.class.getDeclaredField("screen");
        screenField.setAccessible(true);
        Field selField = LifeForge.class.getDeclaredField("sel");
        selField.setAccessible(true);
        assertEquals("WELCOME", screenField.get(tui).toString());
        assertEquals(0, selField.get(tui));

        // Test Right arrow key -> moves selection to 1 (Register)
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRight)));
        assertEquals(1, selField.get(tui));
        String view1 = tui.view().replaceAll("\u001B\\[[;\\d]*m", "");
        assertTrue(view1.contains("> [R] Register Account"), "Item 1 should now be focused");

        // Test Enter on item 1 -> navigates to REGISTER
        tui.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        assertEquals("REGISTER", screenField.get(tui).toString());

        // Back to welcome
        tui.update(new KeyPressMessage(new Key(KeyType.keyESC)));
        assertEquals("WELCOME", screenField.get(tui).toString());

        // Test Right arrow key twice -> moves selection to 2 (Quit)
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRight)));
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRight)));
        assertEquals(2, selField.get(tui));
        String view2 = tui.view().replaceAll("\u001B\\[[;\\d]*m", "");
        assertTrue(view2.contains("> [Q] Quit"), "Item 2 should now be focused");

        // Test Left arrow key -> moves selection back to 1
        tui.update(new KeyPressMessage(new Key(KeyType.KeyLeft)));
        assertEquals(1, selField.get(tui));

        // Test Left arrow key again -> moves selection back to 0
        tui.update(new KeyPressMessage(new Key(KeyType.KeyLeft)));
        assertEquals(0, selField.get(tui));

        // Test Enter on item 0 -> navigates to LOGIN
        tui.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        assertEquals("LOGIN", screenField.get(tui).toString());

        // Back to welcome
        tui.update(new KeyPressMessage(new Key(KeyType.keyESC)));
        assertEquals("WELCOME", screenField.get(tui).toString());

        // Test direct L shortcut
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'l' })));
        assertEquals("LOGIN", screenField.get(tui).toString());

        // Back to welcome
        tui.update(new KeyPressMessage(new Key(KeyType.keyESC)));
        assertEquals("WELCOME", screenField.get(tui).toString());

        // Test direct R shortcut
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'R' })));
        assertEquals("REGISTER", screenField.get(tui).toString());

        // Back to welcome
        tui.update(new KeyPressMessage(new Key(KeyType.keyESC)));
        assertEquals("WELCOME", screenField.get(tui).toString());

        // Test direct Q shortcut
        Field quittingField = LifeForge.class.getDeclaredField("quitting");
        quittingField.setAccessible(true);
        assertFalse((boolean) quittingField.get(tui));
        tui.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[] { 'q' })));
    }

    @Test
    public void testLoginFormVisual() {
        int width = 80;
        int availInner = width - 2;
        String indent = "   ";
        int boxW = Math.max(20, availInner - 6);

        List<ScreenKit.Line> body = new ArrayList<>();
        body.add(ScreenKit.Line.of(Theme.dim(), "  Enter your email or username and password to continue."));
        body.add(ScreenKit.Line.blank());

        // Field 0: Email or Username (focused)
        body.add(ScreenKit.Line.of(Theme.headingCyan(), indent + "Email or Username"));
        body.add(ScreenKit.Line.of(Theme.accentOn(), indent + "╭" + "─".repeat(boxW - 2) + "╮"));
        body.add(ScreenKit.Line.of(Theme.text(), indent + "│ " + Theme.padRight("admin|", boxW - 4) + " │"));
        body.add(ScreenKit.Line.of(Theme.accentOn(), indent + "╰" + "─".repeat(boxW - 2) + "╯"));

        // Field 1: Password (unfocused)
        body.add(ScreenKit.Line.of(Theme.dim(), indent + "Password"));
        body.add(ScreenKit.Line.of(Theme.dim(), indent + "╭" + "─".repeat(boxW - 2) + "╮"));
        body.add(ScreenKit.Line.of(Theme.dim(), indent + "│ " + Theme.padRight("", boxW - 4) + " │"));
        body.add(ScreenKit.Line.of(Theme.dim(), indent + "╰" + "─".repeat(boxW - 2) + "╯"));

        // Button 1: Forgot Password (wide button)
        body.add(ScreenKit.Line.blank());
        body.add(ScreenKit.Line.of(Theme.dim(), indent + "╭" + "─".repeat(boxW - 2) + "╮"));
        body.add(ScreenKit.Line.of(Theme.dim(), indent + "│" + Theme.padCenter("Forgot Password", boxW - 2) + "│"));
        body.add(ScreenKit.Line.of(Theme.dim(), indent + "╰" + "─".repeat(boxW - 2) + "╯"));

        // Buttons: Login (left) and Back (right) side-by-side
        int gap = 4;
        int btnW1 = Math.max(12, (boxW - gap) / 2);
        int btnW2 = Math.max(12, boxW - gap - btnW1);
        String spacer = " ".repeat(gap);
        body.add(ScreenKit.Line.blank());
        body.add(ScreenKit.Line.of(Theme.headingCyan(), indent + "╭" + "─".repeat(btnW1 - 2) + "╮" + spacer + "╭" + "─".repeat(btnW2 - 2) + "╮"));
        body.add(ScreenKit.Line.of(Theme.headingCyan(), indent + "│" + Theme.padCenter("Login", btnW1 - 2) + "│" + spacer + "│" + Theme.padCenter("Back", btnW2 - 2) + "│"));
        body.add(ScreenKit.Line.of(Theme.headingCyan(), indent + "╰" + "─".repeat(btnW1 - 2) + "╯" + spacer + "╰" + "─".repeat(btnW2 - 2) + "╯"));

        List<String[]> footer = List.of(
                new String[] { "Up/Down/Tab", "Move field" },
                new String[] { "Enter", "Select / Submit" },
                new String[] { "Esc/Back", "Cancel" }
        );

        String page = ScreenKit.page("Login", "Login to continue", body, "", false, footer, width);
        System.out.println("\n=== RENDERED LOGIN SCREEN ===\n" + page + "\n=============================\n");

        assertNotNull(page);
        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // Verify every row has display width 80
        for (int r = 0; r < lines.length; r++) {
            assertEquals(80, Theme.width(lines[r]), "Row " + r + " display width must be 80: " + lines[r]);
            assertTrue(lines[r].endsWith("│") || lines[r].endsWith("┐") || lines[r].endsWith("┤") || lines[r].endsWith("┘"));
        }

        // Verify rounded corners exist
        assertTrue(clean.contains("╭"));
        assertTrue(clean.contains("╮"));
        assertTrue(clean.contains("╰"));
        assertTrue(clean.contains("╯"));

        // Verify labels and buttons
        assertTrue(clean.contains("Email or Username"));
        assertTrue(clean.contains("Password"));
        assertTrue(clean.contains("Forgot Password"));
        assertTrue(clean.contains("Login"));
        assertTrue(clean.contains("Back"));
    }

    @Test
    public void testAiChatVisualWithBubbles() {
        int width = 80;
        int availInner = width - 2;
        String indent = "   ";
        int boxW = Math.max(20, availInner - 6);

        List<ScreenKit.Line> body = new ArrayList<>();

        // 1. Context Card
        String contextText = "Goal: Lose Weight  │  Exercise: Daily Physical Activity";
        body.add(ScreenKit.Line.of(Theme.dim(), indent + "╭" + "─".repeat(boxW - 2) + "╮"));
        body.add(ScreenKit.Line.of(Theme.headingPurple(), indent + "│ " + Theme.padRight(Theme.truncate(contextText, boxW - 4), boxW - 4) + " │"));
        body.add(ScreenKit.Line.of(Theme.dim(), indent + "╰" + "─".repeat(boxW - 2) + "╯"));
        body.add(ScreenKit.Line.blank());

        // 2. User Question Bubble (Right aligned, matching Picture 2)
        String userQuestion = "How many steps should I walk daily?";
        int maxBubbleW = Math.min(52, availInner - 10);
        int maxContentW = maxBubbleW - 4;
        List<String> userWrapped = Theme.wrap(userQuestion, maxContentW);
        int bubbleW = Math.max(22, Theme.width(userQuestion) + 4);
        int padRight = 3;
        int padLeft = Math.max(2, availInner - padRight - bubbleW);
        String leftIndent = " ".repeat(padLeft);

        body.add(ScreenKit.Line.of(Theme.accentOn(), leftIndent + "╭" + "─".repeat(bubbleW - 2) + "╮"));
        body.add(ScreenKit.Line.of(Theme.headingCyan(), leftIndent + "│ " + Theme.padRight("YOU", bubbleW - 4) + " │"));
        for (String line : userWrapped) {
            body.add(ScreenKit.Line.of(Theme.text(), leftIndent + "│ " + Theme.padRight(line, bubbleW - 4) + " │"));
        }
        body.add(ScreenKit.Line.of(Theme.accentOn(), leftIndent + "╰" + "─".repeat(bubbleW - 2) + "╯"));
        body.add(ScreenKit.Line.blank());

        // 3. AI Response Bubble (Left aligned, matching Picture 2)
        String aiResponse = "Aim for 8,000 to 10,000 steps daily. Combine brisk walking with 2-3 resistance sessions per week.";
        int aiBubbleW = Math.min(68, availInner - 6);
        List<String> aiWrapped = Theme.wrap(aiResponse, aiBubbleW - 4);

        body.add(ScreenKit.Line.of(Theme.bar(), indent + "╭" + "─".repeat(aiBubbleW - 2) + "╮"));
        body.add(ScreenKit.Line.of(Theme.pivot(), indent + "│ " + Theme.padRight("LIFEForge AI", aiBubbleW - 4) + " │"));
        body.add(ScreenKit.Line.of(Theme.bar(), indent + "├" + "─".repeat(aiBubbleW - 2) + "┤"));
        for (String line : aiWrapped) {
            body.add(ScreenKit.Line.of(Theme.text(), indent + "│ " + Theme.padRight(line, aiBubbleW - 4) + " │"));
        }
        body.add(ScreenKit.Line.of(Theme.bar(), indent + "╰" + "─".repeat(aiBubbleW - 2) + "╯"));
        body.add(ScreenKit.Line.blank());

        // 4. Question Input Box
        body.add(ScreenKit.Line.of(Theme.headingCyan(), indent + "Your Question"));
        body.add(ScreenKit.Line.of(Theme.accentOn(), indent + "╭" + "─".repeat(boxW - 2) + "╮"));
        body.add(ScreenKit.Line.of(Theme.text(), indent + "│ " + Theme.padRight("> |", boxW - 4) + " │"));
        body.add(ScreenKit.Line.of(Theme.accentOn(), indent + "╰" + "─".repeat(boxW - 2) + "╯"));

        List<String[]> footer = List.of(
                new String[] { "Enter", "Send" },
                new String[] { "Ctrl+C", "Clear question" },
                new String[] { "Esc", "Back" }
        );

        String page = ScreenKit.page("AI Assistant", "Optional AI assistance — it cannot change the official recommendation", body, "", false, footer, width);
        System.out.println("\n=== RENDERED AI CHAT SCREEN WITH BUBBLES ===\n" + page + "\n============================================\n");

        assertNotNull(page);
        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        for (int r = 0; r < lines.length; r++) {
            assertEquals(80, Theme.width(lines[r]), "Row " + r + " display width must be 80: " + lines[r]);
            assertTrue(lines[r].endsWith("│") || lines[r].endsWith("┐") || lines[r].endsWith("┤") || lines[r].endsWith("┘"));
        }

        assertTrue(clean.contains("YOU"));
        assertTrue(clean.contains("LIFEForge AI"));
        assertTrue(clean.contains("Your Question"));
        assertTrue(clean.contains("╭"));
        assertTrue(clean.contains("╯"));
    }

    @Test
    public void testLiveOllamaChat() {
        com.lifeforge.service.AiExplanationService aiService = new com.lifeforge.service.AiExplanationService();
        com.lifeforge.model.User u = new com.lifeforge.model.User();
        u.setFullName("Test User");
        u.setEmail("test@example.com");
        u.setAge(20);
        u.setGender(com.lifeforge.model.Gender.MALE);
        u.setHeightCm(170.0);
        u.setWeightKg(50.0);
        u.setActivityLevel(com.lifeforge.model.ActivityLevel.MODERATELY_ACTIVE);

        com.lifeforge.model.Goal g = new com.lifeforge.model.Goal();
        g.setCode("LOSE_WEIGHT");
        g.setName("Lose Weight");

        com.lifeforge.model.RecommendationCategory cat = new com.lifeforge.model.RecommendationCategory();
        cat.setName("Nutrition");

        com.lifeforge.model.Recommendation rec = new com.lifeforge.model.Recommendation();
        rec.setTitle("Eat More Protein");
        rec.setDescription("Increase daily protein to support healthy body composition.");
        rec.setRecommendedActions("Add lean protein to every meal.");
        rec.setSuggestedTarget("1.6g per kg of bodyweight");
        rec.setImportantNotes("Keep hydration consistent.");

        com.lifeforge.service.AiChatResponse response = aiService.chat(u, g, cat, rec, java.util.List.of(), "I'm 50kg I want to lose 4kg in 3 week how to do?");
        assertNotNull(response);
        assertNotNull(response.text);
        assertTrue(response.text.length() > 0);
    }
}