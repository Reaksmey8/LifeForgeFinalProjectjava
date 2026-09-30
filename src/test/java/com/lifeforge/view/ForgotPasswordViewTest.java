package com.lifeforge.view;

import com.lifeforge.AppContext;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class ForgotPasswordViewTest {

    @BeforeAll
    public static void setupTerminal() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    private void injectForm(LifeForge tui, String[] values, int focus) throws Exception {
        Field valuesField = LifeForge.class.getDeclaredField("fValues");
        valuesField.setAccessible(true);
        valuesField.set(tui, values);

        Field focusField = LifeForge.class.getDeclaredField("fFocus");
        focusField.setAccessible(true);
        focusField.set(tui, focus);
    }

    @Test
    public void testForgotPasswordPageAt80Columns() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectForm(tui, new String[] { "user@example.com" }, 0);

        String rendered = tui.renderForgotPasswordPage(80);
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            assertEquals(80, Theme.width(lines[i]),
                    "Line " + i + " width must be strictly 80 visible columns: '" + lines[i] + "'");
        }

        // 1. Top border
        assertTrue(lines[0].startsWith("┌─"), "Top border start: " + lines[0]);
        assertTrue(lines[0].endsWith("┐"), "Top border end: " + lines[0]);
        assertTrue(lines[0].contains("FORGOT PASSWORD"), "Title: " + lines[0]);

        // 2. Subtitle
        assertTrue(clean.contains("Enter your registered email address or username"));

        // 3. Section Header
        assertTrue(clean.contains("[ ACCOUNT VERIFICATION ]"));

        // 4. Aligned Colons and Input
        assertTrue(clean.contains("Username or Email   : [ user@example.com"));

        // 5. Footer inside card
        assertTrue(clean.contains("[Enter] Send Code"));
        assertTrue(clean.contains("[ESC/B] Back to Login"));

        // 6. Bottom border
        String lastLine = lines[lines.length - 1];
        assertTrue(lastLine.startsWith("└") && lastLine.endsWith("┘"), "Bottom border: " + lastLine);
    }

    @Test
    public void testVerifyResetPageAt80Columns() throws Exception {
        LifeForge tui = new LifeForge(AppContext.build());
        injectForm(tui, new String[] { "123456", "SecretPass123", "SecretPass123" }, 0);

        String rendered = tui.renderVerifyResetPage(80);
        assertNotNull(rendered);

        String clean = rendered.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            assertEquals(80, Theme.width(lines[i]),
                    "Line " + i + " width must be strictly 80 visible columns: '" + lines[i] + "'");
        }

        // 1. Top border
        assertTrue(lines[0].startsWith("┌─"), "Top border start: " + lines[0]);
        assertTrue(lines[0].endsWith("┐"), "Top border end: " + lines[0]);
        assertTrue(lines[0].contains("RESET PASSWORD"), "Title: " + lines[0]);

        // 2. Subtitle (non-truncated)
        assertTrue(clean.contains("Enter the 6-digit verification code along with your new password."));
        assertFalse(clean.contains("along with your n..."));

        // 3. Section Header
        assertTrue(clean.contains("[ VERIFY & RESET CREDENTIALS ]"));

        // 4. Demo Code Sent line
        assertTrue(clean.contains("Demo Code Sent      : "));
        assertTrue(clean.contains("(Simulation / Console)"));

        // 5. Aligned Colons for all 3 fields
        assertTrue(clean.contains("Verification Code   : [ 123456"));
        assertTrue(clean.contains("New Password        : [ *************"));
        assertTrue(clean.contains("Confirm Password    : [ *************"));

        // 6. Footer inside card
        assertTrue(clean.contains("[Enter] Submit Reset"));
        assertTrue(clean.contains("[R] Resend Code"));
        assertTrue(clean.contains("[ESC/B] Back to Login"));

        // 7. Bottom border
        String lastLine = lines[lines.length - 1];
        assertTrue(lastLine.startsWith("└") && lastLine.endsWith("┘"), "Bottom border: " + lastLine);
    }
}
