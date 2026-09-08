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
    public static final TerminalColor BORDER    = hex("#2a3744");
    public static final TerminalColor CYAN      = hex("#22d3ee");
    public static final TerminalColor GREEN     = hex("#34d399");
    public static final TerminalColor PURPLE    = hex("#a78bfa");
    public static final TerminalColor YELLOW    = hex("#fbbf24");
    public static final TerminalColor RED       = hex("#f87171");
    public static final TerminalColor TEXT      = hex("#b9c6d3");
    public static final TerminalColor DIM       = hex("#5d7084");
    public static final TerminalColor HEADER    = hex("#8fe9ff");
    public static final TerminalColor SELECT_BG = hex("#0f4d63");
    public static final TerminalColor SELECT_FG = hex("#c0f6ff");

    public static TerminalColor hex(String rgb) {
        return new RGBColor(rgb);
    }

    // ------------------------------------------------------------------
    // Named styles
    // ------------------------------------------------------------------
    public static Style plain()      { return Style.newStyle().foreground(TEXT); }
    public static Style dim()        { return Style.newStyle().foreground(DIM); }
    public static Style muted()      { return Style.newStyle().foreground(DIM); }
    public static Style text()       { return Style.newStyle().foreground(TEXT); }
    public static Style title()      { return Style.newStyle().foreground(HEADER).bold(true); }
    public static Style headingCyan()   { return Style.newStyle().foreground(CYAN).bold(true); }
    public static Style headingGreen()  { return Style.newStyle().foreground(GREEN).bold(true); }
    public static Style headingPurple() { return Style.newStyle().foreground(PURPLE).bold(true); }
    public static Style ok()         { return Style.newStyle().foreground(GREEN); }
    public static Style warn()       { return Style.newStyle().foreground(YELLOW); }
    public static Style err()        { return Style.newStyle().foreground(RED).bold(true); }
    public static Style pivot()      { return Style.newStyle().foreground(CYAN); }
    public static Style keyTag()     { return Style.newStyle().foreground(PURPLE).bold(true); }
    public static Style selected()   { return Style.newStyle().background(SELECT_BG).foreground(SELECT_FG).bold(true); }
    public static Style bar()        { return Style.newStyle().foreground(BORDER); }
    public static Style accentOn()   { return Style.newStyle().background(CYAN).foreground(BG).bold(true); }
    public static Style accentGreen() { return Style.newStyle().background(GREEN).foreground(BG).bold(true); }
    public static Style accentPurple() { return Style.newStyle().background(PURPLE).foreground(BG).bold(true); }

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

    private static int displayWidth(int cp) {
        // Covers CJK wide characters and the emoji blocks used by the navigation
        // labels. This keeps padded menu rows stable in common Windows terminals.
        return cp >= 0x1100 ? 2 : 1;
    }


}
