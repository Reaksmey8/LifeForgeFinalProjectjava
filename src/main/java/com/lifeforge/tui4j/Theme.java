package com.lifeforge.tui4j;

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
    public static final TerminalColor BORDER    = hex("#9c5cdc");
    public static final TerminalColor CYAN      = hex("#0284c7");
    public static final TerminalColor GREEN     = hex("#059669");
    public static final TerminalColor PURPLE    = hex("#7c3aed");
    public static final TerminalColor YELLOW    = hex("#d97706");
    public static final TerminalColor RED       = hex("#dc2626");
    public static final TerminalColor TEXT      = hex("#1e293b");
    public static final TerminalColor DIM       = hex("#64748b");
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
    public static Style text()          { return Style.newStyle(); }
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
    public static Style accentPurple()  { return Style.newStyle().foreground(PURPLE).bold(true); }

    // ------------------------------------------------------------------
    // Plain-string helpers (input must not contain ANSI escapes)
    // ------------------------------------------------------------------
    public static String render(Style style, String plain) {
        return style.render(TerminalSupport.sanitize(plain));
    }

    /** Visible width in terminal columns, accounting for common wide glyphs and emoji. */
    public static int width(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
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
        int i = 0;
        while (i < s.length() && width(sb.toString()) < keep) {
            int cp = s.codePointAt(i);
            if (width(sb.toString()) + displayWidth(cp) > keep) {
                break;
            }
            sb.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return sb + "...";
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

    /** Simple word wrap for plain text; returns lines broken at spaces. */
    public static java.util.List<String> wrap(String text, int w) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        for (String raw : text.split("\r?\n", -1)) {
            String line = raw.isEmpty() ? " " : raw;
            while (width(line) > w) {
                int cut = lastSpace(line, w);
                if (cut <= 0) {
                    cut = w;
                }
                while (cut < line.length() && line.charAt(cut) == ' ') {
                    cut++;
                }
                out.add(line.substring(0, cut));
                line = line.substring(cut);
            }
            out.add(line);
        }
        return out;
    }

    private static int lastSpace(String s, int w) {
        int index = -1;
        int col = 0;
        for (int i = 0; i < s.length(); i++) {
            int cp = s.codePointAt(i);
            int cw = col + displayWidth(cp);
            if (cw > w) {
                break;
            }
            col = cw;
            if (Character.isWhitespace(cp)) {
                index = i;
            }
            if (cp > 0xFFFF) {
                i++; // surrogates consumed via codePointAt
            }
        }
        return index + 1;
    }

    public static int displayWidth(int cp) {

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

        // Block elements are normally one terminal column wide.
        if (cp >= 0x2580 && cp <= 0x259F) {
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
                cp == 0x2699 || cp == 0x26A0 || cp == 0x270F || cp == 0x2795) {
            return 2;
        }

        // Everything else is one terminal column.
        return 1;
    }


}
