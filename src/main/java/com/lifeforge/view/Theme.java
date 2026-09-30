package com.lifeforge.view;

import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.RGBColor;
import com.williamcallahan.tui4j.compat.lipgloss.color.TerminalColor;

/**
 * Central dark-theme palette and style/string helpers for the LifeForge
 * terminal UI. Cyan, green and purple accents on a near-black background,
 * with bordered panels, highlighted selections and consistent spacing.
 */
public final class Theme {

    private Theme() {
    }

    // ------------------------------------------------------------------
    // Palette
    // ------------------------------------------------------------------
    public static final TerminalColor BG        = hex("#0a0e13");
    public static final TerminalColor PANEL     = hex("#10161d");
    public static final TerminalColor PANEL2    = hex("#151d26");
    public static final TerminalColor BORDER    = hex("#0284c7");
    public static final TerminalColor CYAN      = hex("#0284c7");
    public static final TerminalColor GREEN     = hex("#059669");
    public static final TerminalColor PURPLE    = hex("#7c3aed");
    public static final TerminalColor YELLOW    = hex("#d97706");
    public static final TerminalColor RED       = hex("#dc2626");
    public static final TerminalColor TEXT      = hex("#1e293b");
    public static final TerminalColor DIM       = hex("#475569");
    public static final TerminalColor HEADER    = hex("#0284c7");
    public static final TerminalColor SELECT_BG = hex("#0284c7");
    public static final TerminalColor SELECT_FG = hex("#0284c7");

    public static TerminalColor hex(String rgb) {
        return new RGBColor(rgb);
    }

    // ------------------------------------------------------------------
    // Named styles
    // ------------------------------------------------------------------
    public static Style plain()         { return Style.newStyle(); }
    public static Style dim()           { return Style.newStyle().foreground(DIM); }
    public static Style muted()         { return Style.newStyle().foreground(DIM); }
    public static Style text()          { return Style.newStyle().foreground(TEXT); }
    public static Style title()         { return Style.newStyle().foreground(HEADER).bold(true); }
    public static Style headingCyan()   { return Style.newStyle().foreground(CYAN).bold(true); }
    public static Style headingGreen()  { return Style.newStyle().foreground(GREEN).bold(true); }
    public static Style headingYellow() { return Style.newStyle().foreground(YELLOW).bold(true); }
    public static Style headingPurple() { return Style.newStyle().foreground(PURPLE).bold(true); }
    public static Style ok()            { return Style.newStyle().foreground(GREEN).bold(true); }
    public static Style warn()          { return Style.newStyle().foreground(YELLOW).bold(true); }
    public static Style err()           { return Style.newStyle().foreground(RED).bold(true); }
    public static Style pivot()         { return Style.newStyle().foreground(CYAN).bold(true); }
    public static Style keyTag()        { return Style.newStyle().foreground(PURPLE).bold(true); }
    public static Style selected()      { return Style.newStyle().foreground(CYAN).bold(true); }
    public static Style bar()           { return Style.newStyle().foreground(BORDER); }
    public static Style accentOn()      { return Style.newStyle().foreground(CYAN).bold(true); }
    public static Style accentGreen()   { return Style.newStyle().foreground(GREEN).bold(true); }
    public static final TerminalColor ADMIN_BORDER      = hex("#0284c7");
    public static final TerminalColor ADMIN_CARD_BORDER = hex("#0369a1");
    public static final TerminalColor ADMIN_CYAN        = hex("#0284c7");
    public static final TerminalColor ADMIN_LIGHT_BLUE  = hex("#0369a1");
    public static final TerminalColor ADMIN_GREEN       = hex("#059669");
    public static final TerminalColor ADMIN_YELLOW      = hex("#d97706");
    public static final TerminalColor ADMIN_PURPLE      = hex("#7c3aed");
    public static final TerminalColor ADMIN_RED         = hex("#dc2626");
    public static final TerminalColor ADMIN_MUTED       = hex("#475569");
    public static final TerminalColor ADMIN_TEXT        = hex("#1e293b");

    public static Style accentPurple()  { return Style.newStyle().foreground(PURPLE).bold(true); }
    public static final TerminalColor ADMIN_SELECT_BG   = hex("#1e3a8a");
    public static Style rowSelected()           { return Style.newStyle().background(ADMIN_SELECT_BG).foreground(hex("#ffffff")).bold(true); }
    public static Style rowSelectedId()         { return Style.newStyle().background(ADMIN_SELECT_BG).foreground(hex("#7dd3fc")).bold(true); }
    public static Style rowSelectedDim()        { return Style.newStyle().background(ADMIN_SELECT_BG).foreground(hex("#bae6fd")); }
    public static Style rowSelectedDotActive()  { return Style.newStyle().background(ADMIN_SELECT_BG).foreground(hex("#4ade80")).bold(true); }
    public static Style rowSelectedBadgeKey()   { return Style.newStyle().background(ADMIN_SELECT_BG).foreground(hex("#7dd3fc")).bold(true); }
    public static Style rowSelectedBadgePurple(){ return Style.newStyle().background(ADMIN_SELECT_BG).foreground(hex("#d8b4fe")).bold(true); }
    public static Style rowSelectedBadgeRed()   { return Style.newStyle().background(ADMIN_SELECT_BG).foreground(hex("#fca5a5")).bold(true); }
    public static Style adminBorder()     { return Style.newStyle().foreground(ADMIN_BORDER); }
    public static Style adminCardBorder() { return Style.newStyle().foreground(ADMIN_CARD_BORDER); }
    public static Style adminHeader()     { return Style.newStyle().foreground(ADMIN_CYAN).bold(true); }
    public static Style adminHeaderSub()  { return Style.newStyle().foreground(ADMIN_LIGHT_BLUE).bold(true); }
    public static Style adminBadgeKey()   { return Style.newStyle().foreground(ADMIN_CYAN).bold(true); }
    public static Style adminBadgeGreen() { return Style.newStyle().foreground(ADMIN_GREEN).bold(true); }
    public static Style adminBadgeYellow(){ return Style.newStyle().foreground(ADMIN_YELLOW).bold(true); }
    public static Style adminBadgePurple(){ return Style.newStyle().foreground(ADMIN_PURPLE).bold(true); }
    public static Style adminBadgeRed()   { return Style.newStyle().foreground(ADMIN_RED).bold(true); }
    public static Style adminText()       { return Style.newStyle().foreground(ADMIN_TEXT); }
    public static Style adminDim()        { return Style.newStyle().foreground(ADMIN_MUTED); }
    public static Style adminDotActive()  { return Style.newStyle().foreground(ADMIN_GREEN).bold(true); }

    // ------------------------------------------------------------------
    // Plain-string helpers (input must not contain ANSI escapes)
    // ------------------------------------------------------------------
    public static String render(Style style, String plain) {
        return style.render(TerminalSupport.sanitize(plain));
    }

    public static String stripAnsi(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
    }

    /** Visible width in terminal columns, accounting for common wide glyphs and emoji. */
    public static int width(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        if (s.indexOf('\u001B') >= 0) {
            s = stripAnsi(s);
        }
        int columns = 0;
        for (int i = 0; i < s.length();) {
            int cp = s.codePointAt(i);
            columns += displayWidth(cp);
            i += Character.charCount(cp);
        }
        return columns;
    }

    public static String padRight(String s, int w) {
        int len = width(s);
        if (len >= w) {
            return s;
        }
        return s + dup(' ', w - len);
    }

    public static String padCenter(String s, int w) {
        int len = width(s);
        if (len >= w) {
            return s;
        }
        int left = (w - len) / 2;
        int right = w - len - left;
        return dup(' ', left) + s + dup(' ', right);
    }

    public static String padLeft(String s, int w) {
        int len = width(s);
        if (len >= w) {
            return s;
        }
        return dup(' ', w - len) + s;
    }

    public static String truncate(String s, int w) {
        if (s == null || w <= 0) {
            return "";
        }
        if (width(s) <= w) {
            return s;
        }
        if (w <= 3) {
            return dup('.', w);
        }
        int keep = w - 3;
        StringBuilder sb = new StringBuilder();
        java.text.BreakIterator charIter = java.text.BreakIterator.getCharacterInstance();
        charIter.setText(s);
        int start = charIter.first();
        for (int end = charIter.next(); end != java.text.BreakIterator.DONE; start = end, end = charIter.next()) {
            String cluster = s.substring(start, end);
            if (width(sb.toString()) + width(cluster) > keep) {
                break;
            }
            sb.append(cluster);
        }
        return sb.toString() + "...";
    }

    public static String dup(char c, int n) {
        if (n <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    /** Simple word wrap for plain text; uses BreakIterator to support all languages (including Khmer/CJK). */
    public static java.util.List<String> wrap(String text, int w) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        int maxCols = Math.max(10, w);
        for (String raw : text.split("\r?\n", -1)) {
            if (raw.isEmpty()) {
                out.add("");
                continue;
            }
            if (width(raw) <= maxCols) {
                out.add(raw);
                continue;
            }

            java.text.BreakIterator boundary = java.text.BreakIterator.getLineInstance();
            boundary.setText(raw);

            java.util.List<String> segments = new java.util.ArrayList<>();
            int start = boundary.first();
            for (int end = boundary.next(); end != java.text.BreakIterator.DONE; start = end, end = boundary.next()) {
                segments.add(raw.substring(start, end));
            }

            StringBuilder currentLine = new StringBuilder();
            int currentWidth = 0;

            for (String seg : segments) {
                int segWidth = width(seg);

                if (currentWidth + segWidth <= maxCols || currentWidth == 0) {
                    currentLine.append(seg);
                    currentWidth += segWidth;
                } else {
                    out.add(trimTrailing(currentLine.toString()));
                    currentLine = new StringBuilder(seg);
                    currentWidth = segWidth;
                }

                // If a single unbroken segment exceeds maxCols, break by grapheme clusters (never split combining marks)
                while (currentWidth > maxCols) {
                    java.text.BreakIterator charBoundary = java.text.BreakIterator.getCharacterInstance();
                    charBoundary.setText(currentLine.toString());

                    StringBuilder chunk = new StringBuilder();
                    int chunkWidth = 0;
                    int cStart = charBoundary.first();
                    int lastGoodEnd = cStart;

                    for (int cEnd = charBoundary.next(); cEnd != java.text.BreakIterator.DONE; cStart = cEnd, cEnd = charBoundary.next()) {
                        String cluster = currentLine.substring(cStart, cEnd);
                        int clWidth = width(cluster);
                        if (chunkWidth + clWidth > maxCols && chunk.length() > 0) {
                            break;
                        }
                        chunk.append(cluster);
                        chunkWidth += clWidth;
                        lastGoodEnd = cEnd;
                    }

                    out.add(chunk.toString());
                    String remainder = currentLine.substring(lastGoodEnd);
                    currentLine = new StringBuilder(remainder);
                    currentWidth = width(remainder);
                }
            }

            if (currentLine.length() > 0) {
                out.add(trimTrailing(currentLine.toString()));
            }
        }
        return out;
    }

    private static String trimTrailing(String s) {
        int i = s.length();
        while (i > 0 && Character.isWhitespace(s.charAt(i - 1))) {
            i--;
        }
        return s.substring(0, i);
    }

    public static int displayWidth(char c) {
        return displayWidth((int) c);
    }

    public static int displayWidth(int cp) {
        // Zero-width combining characters and variation selectors
        if (cp >= 0xFE00 && cp <= 0xFE0F) {
            return 0;
        }

        // Zero-width formatting marks, zero-width space, joiners, soft hyphens
        if ((cp >= 0x200B && cp <= 0x200F) || cp == 0xFEFF || cp == 0x00AD) {
            return 0;
        }

        // General Unicode combining marks: non-spacing marks (Mn), enclosing marks (Me), and format (Cf)
        int type = Character.getType(cp);
        if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.FORMAT) {
            return 0;
        }

        // --------------------------------------------------------------
        // Box-drawing characters are ONE terminal column wide.
        // This is critical for the LifeForge borders:
        //
        // ┌ ─ ┐
        // │
        // ├ ─ ┤
        // └ ─ ┘
        // --------------------------------------------------------------
        if (cp >= 0x2500 && cp <= 0x257F) {
            return 1;
        }

        // Block elements and geometric shapes are one terminal column wide.
        if (cp >= 0x2580 && cp <= 0x25FF) {
            return 1;
        }

        // --------------------------------------------------------------
        // CJK characters are generally two terminal columns wide.
        // --------------------------------------------------------------
        if ((cp >= 0x1100 && cp <= 0x11FF) ||      // Hangul Jamo
                (cp >= 0x2E80 && cp <= 0x9FFF) ||      // CJK / radicals
                (cp >= 0xAC00 && cp <= 0xD7AF) ||      // Hangul syllables
                (cp >= 0xF900 && cp <= 0xFAFF) ||      // CJK compatibility
                (cp >= 0xFF01 && cp <= 0xFF60)) {      // Full-width forms
            return 2;
        }

        // --------------------------------------------------------------
        // Emoji / supplementary symbols used by LifeForge.
        // --------------------------------------------------------------
        if ((cp >= 0x1F000 && cp <= 0x1FAFF) ||
                cp == 0x274C || cp == 0x2705 || cp == 0x2B50 || cp == 0x2600 ||
                cp == 0x2699 || cp == 0x26A1 || cp == 0x270F || cp == 0x270D ||
                cp == 0x2795 || cp == 0x2611 || cp == 0x2610 || cp == 0x23F1 || cp == 0x2B05) {
            return 2;
        }

        // Everything else is one terminal column.
        return 1;
    }


}
