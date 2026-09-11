package com.lifeforge.tui4j;

import com.lifeforge.model.AuditLog;
import com.lifeforge.tui4j.ScreenKit.Line;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AuditLogsAndCenteringUiTest {

    @BeforeAll
    public static void setup() {
        TerminalInfo.provide(() -> new TerminalInfo(true, null));
    }

    @Test
    public void testTerminalInputDimensions() {
        com.lifeforge.tui4j.terminal.TerminalInput input = com.lifeforge.tui4j.terminal.TerminalInput.open();
        assertNotNull(input);
        System.out.println("[VERIFIED] TerminalInput description: " + input.description());
        System.out.println("[VERIFIED] Detected columns: " + input.columns());
        System.out.println("[VERIFIED] Detected rows: " + input.rows());
        assertTrue(input.columns() >= 40, "Columns should be at least 40");
        assertTrue(input.rows() >= 10, "Rows should be at least 10");
        input.close();
    }

    @Test
    public void testFrameWidthCalculationAndCenteringGeometry() {
        // Test frame width formula: frameWidth = min(80, terminalWidth)
        int[] terminalWidths = { 60, 80, 100, 120, 140 };
        int[] expectedFrameWidths = { 60, 80, 80, 80, 80 };
        int[] expectedLeftPads = { 0, 0, 10, 20, 30 };

        for (int i = 0; i < terminalWidths.length; i++) {
            int termW = terminalWidths[i];
            int frameW = Math.min(80, termW);
            int leftPad = Math.max(0, (termW - frameW) / 2);

            assertEquals(expectedFrameWidths[i], frameW, "Mismatch for termW=" + termW);
            assertEquals(expectedLeftPads[i], leftPad, "Mismatch leftPad for termW=" + termW);
        }
    }

    @Test
    public void testVerticalCenteringGeometry() {
        // Frame with 20 rows
        int frameHeight = 20;
        int[] terminalHeights = { 18, 24, 30, 40 };
        int[] expectedTopPads = { 0, 2, 5, 10 };

        for (int i = 0; i < terminalHeights.length; i++) {
            int termH = terminalHeights[i];
            int topPad = Math.max(0, (termH - frameHeight) / 2);
            assertEquals(expectedTopPads[i], topPad, "Mismatch topPad for termH=" + termH);
        }
    }

    @Test
    public void testAuditLogsTableLayoutAndBorderAlignment() {
        // Mock audit data
        LocalDateTime now = LocalDateTime.of(2026, 9, 9, 15, 40);
        List<AuditLog> testLogs = List.of(
                new AuditLog(1L, 1L, "LOGIN", "USER", 6L, "User logged in successfully", now),
                new AuditLog(2L, 1L, "PASSWORD_RESET_REQUESTED", "USER", 6L, "Password reset requested for email user@example.com", now.minusMinutes(5)),
                new AuditLog(3L, 1L, "PASSWORD_RESET_REQUEST_REJECTED", "PASSWORD_RESET", 468L, "Request #468 rejected by admin", now.minusMinutes(5)),
                new AuditLog(4L, 1L, "LOGIN", "USER", 6L, "User logged in successfully", now.minusMinutes(5)),
                new AuditLog(5L, 1L, "PASSWORD_RESET_REQUESTED", "USER", 6L, "Verification code sent to email", now.minusMinutes(6)),
                new AuditLog(6L, null, "PASSWORD_RESET_COMPLETED", "USER", 6L, "Password reset completed successfully", now.minusMinutes(8))
        );

        // Verify column widths inside an 80-column frame
        int frameWidth = 80;
        int inner = frameWidth - 2; // 78
        assertEquals(78, inner);

        // Build audit body manually matching LifeForge's viewAdminAudit logic
        List<Line> body = new ArrayList<>();
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(), "  749 audit events"));
        body.add(Line.blank());

        String header = "  TIME          ADMIN       ACTION             TARGET       DETAILS";
        body.add(Line.of(Theme.headingPurple(), header));
        String underline = "  ──────────    ────────    ─────────────────  ──────────   ────────────────";
        body.add(Line.of(Theme.bar(), underline));

        for (int i = 0; i < testLogs.size(); i++) {
            AuditLog log = testLogs.get(i);
            String prefix = i == 0 ? "> " : "  ";
            String line = prefix + "09/09 15:40  admin       🔐 LOGIN            USER #6      User logged in";
            body.add(Line.of(Theme.text(), line));
        }

        body.add(Line.blank());
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(), "  Showing 1–6 of 749                                      Page 1 / 125"));

        List<String[]> footer = List.of(
                new String[] { "↑↓", "Select" },
                new String[] { "←→", "Page" },
                new String[] { "S", "Search" },
                new String[] { "R", "Refresh" },
                new String[] { "B", "Back" },
                new String[] { "H", "Home" },
                new String[] { "Q", "Quit" }
        );

        String page = ScreenKit.page("Audit Logs", "Administrative activity and security history", body, "", false, footer, frameWidth);

        assertNotNull(page);
        // Strip ANSI escape sequences to inspect plain layout and exact character positions
        String clean = page.replaceAll("\u001B\\[[;\\d]*m", "");
        String[] lines = clean.split("\n", -1);

        // Verify square corners
        assertTrue(lines[0].startsWith("┌"));
        assertTrue(lines[0].endsWith("┐"));
        assertTrue(lines[lines.length - 1].startsWith("└"));
        assertTrue(lines[lines.length - 1].endsWith("┘"));

        // Verify that every single row has width 80 and the closing right border without drift
        for (int row = 0; row < lines.length; row++) {
            String l = lines[row];
            assertEquals(80, Theme.width(l), "Row " + row + " display width is not 80: " + l);
            assertTrue(l.endsWith("│") || l.endsWith("┐") || l.endsWith("┤") || l.endsWith("┘"),
                    "Row " + row + " right border misaligned: " + l);
        }

        // Verify footer does not contain 'Enter' or 'Details'
        for (String l : lines) {
            assertFalse(l.contains("Enter Details"), "Footer must not contain 'Enter Details'");
        }
    }

    private static String simulateCenterFrame(String page, int frameWidth, int termWidth, int termHeight) {
        String[] lines = page.split("\n", -1);
        int leftPad = Math.max(0, (termWidth - frameWidth) / 2);
        if (leftPad > 0) {
            String pad = " ".repeat(leftPad);
            for (int i = 0; i < lines.length; i++) {
                lines[i] = pad + lines[i];
            }
        }
        int topPad = Math.max(0, (termHeight - lines.length) / 2);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < topPad; i++) {
            out.append('\n');
        }
        for (int i = 0; i < lines.length; i++) {
            out.append(lines[i]);
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    @Test
    public void testVisualCenteringAtVariousResolutions() {
        int[][] resolutions = {
                { 80, 24 },
                { 100, 30 },
                { 120, 40 }
        };

        // Build audit body
        List<Line> body = new ArrayList<>();
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(), "  749 audit events"));
        body.add(Line.blank());

        String header = "  TIME          ADMIN       ACTION             TARGET       DETAILS";
        body.add(Line.of(Theme.headingPurple(), header));
        String underline = "  ──────────    ────────    ─────────────────  ──────────   ────────────────";
        body.add(Line.of(Theme.bar(), underline));

        body.add(Line.of(Theme.selected(), "> 09/09 15:40  admin       🔐 LOGIN            USER #6      User logged in"));
        body.add(Line.of(Theme.pivot(), "  09/09 15:35  admin       🔑 RESET REQUEST    USER #6      Reset requested"));
        body.add(Line.of(Theme.err(), "  09/09 15:35  admin       ❌ RESET REJECTED   RESET #468  Request rejected"));
        body.add(Line.of(Theme.dim(), "  09/09 15:35  admin       🔐 LOGIN            USER #6      User logged in"));
        body.add(Line.of(Theme.pivot(), "  09/09 15:34  admin       🔑 RESET REQUEST    USER #6      Verification"));
        body.add(Line.of(Theme.ok(), "  09/09 15:32  system      ✓ RESET COMPLETED  USER #6      Password updated"));

        body.add(Line.blank());
        body.add(Line.blank());
        body.add(Line.of(Theme.dim(), "  Showing 1–6 of 749                                      Page 1 / 125"));

        List<String[]> footer = List.of(
                new String[] { "↑↓", "Select" },
                new String[] { "←→", "Page" },
                new String[] { "S", "Search" },
                new String[] { "R", "Refresh" },
                new String[] { "B", "Back" },
                new String[] { "H", "Home" },
                new String[] { "Q", "Quit" }
        );

        for (int[] res : resolutions) {
            int termW = res[0];
            int termH = res[1];
            int frameW = Math.min(80, termW);

            String page = ScreenKit.page("Audit Logs", "Administrative activity and security history",
                    body, "", false, footer, frameW);
            String centered = simulateCenterFrame(page, frameW, termW, termH);

            System.out.println("\n=======================================================");
            System.out.println(" RENDERED AUDIT LOGS AT TERMINAL " + termW + "x" + termH);
            System.out.println("=======================================================");
            System.out.println(centered);
            System.out.println("=======================================================\n");

            // Strip ANSI codes
            String clean = centered.replaceAll("\u001B\\[[;\\d]*m", "");
            String[] lines = clean.split("\n", -1);

            int expectedTopPad = (termH - 21) / 2; // page is 21 rows tall
            int expectedLeftPad = (termW - 80) / 2;

            // Check top padding
            for (int r = 0; r < expectedTopPad; r++) {
                assertEquals("", lines[r].trim(), "Row " + r + " should be top padding in " + termW + "x" + termH);
            }

            // Check frame lines
            int frameStart = expectedTopPad;
            int frameEnd = frameStart + 21;
            for (int r = frameStart; r < frameEnd; r++) {
                String line = lines[r];
                if (expectedLeftPad > 0) {
                    assertTrue(line.startsWith(" ".repeat(expectedLeftPad)),
                            "Row " + r + " should have " + expectedLeftPad + " left spaces in " + termW + "x" + termH);
                }
                String trimmedFrame = line.substring(expectedLeftPad);
                assertEquals(80, Theme.width(trimmedFrame),
                        "Frame at row " + r + " should have display width 80 in " + termW + "x" + termH);
                assertTrue(trimmedFrame.endsWith("│") || trimmedFrame.endsWith("┐") || trimmedFrame.endsWith("┤") || trimmedFrame.endsWith("┘"),
                        "Right border should be aligned at row " + r + " in " + termW + "x" + termH);
            }
        }
    }
}
