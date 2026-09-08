package com.lifeforge.tui4j;

import com.williamcallahan.tui4j.compat.lipgloss.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout kit for the LifeForge TUI. Produces a bordered, consistent page:
 * a title bar, a body built from single-style lines, and a footer with
 * navigation hints. Every line is padded before styling so ANSI escapes
 * never break column alignment.
 */
public final class ScreenKit {

    private ScreenKit() {
    }

    /** A styled, already-plain text line (no ANSI inside {@code text}). */
    public record Line(Style style, String text) {
        public static Line of(Style style, String text) {
            return new Line(style, text);
        }

        public static Line plain(String text) {
            return new Line(Theme.plain(), text);
        }

        public static Line dim(String text) {
            return new Line(Theme.dim(), text);
        }

        public static Line blank() {
            return new Line(Theme.dim(), "");
        }


    }

    // ------------------------------------------------------------------
    // Page assembly
    // ------------------------------------------------------------------
    /** Assembles the shared LifeForge frame: header, content, status and help bar. */
    public static String page(String title, String subtitle, List<Line> body,
                              String status, boolean statusError,
                              List<String[]> footerKeys, int width) {
        int inner = Math.max(40, width - 2);
        List<Line> lines = new ArrayList<>();

        lines.add(Line.of(Theme.bar(), "╔" + Theme.dup('═', inner) + "╗"));
        String head = Theme.padRight(Theme.truncate("  LIFEForge  /  " + title.toUpperCase(), inner), inner);
        lines.add(Line.of(Theme.title(), "║" + head + "║"));
        if (subtitle != null && !subtitle.isEmpty()) {
            lines.add(Line.of(Theme.muted(), "║" + Theme.padRight("  " + subtitle, inner) + "║"));
        }
        lines.add(Line.of(Theme.bar(), "╠" + Theme.dup('═', inner) + "╣"));
        lines.add(Line.blank());

        lines.addAll(body);

        if (status != null && !status.isEmpty()) {
            lines.add(Line.blank());
            Style st = statusError ? Theme.err() : Theme.ok();
            List<String> stLines = Theme.wrap(status, inner - 8);
            lines.add(Line.of(Theme.bar(), "├" + Theme.dup('─', inner) + "┤"));
            for (String l : stLines) {
                String prefix = statusError ? "  ERROR  " : "  OK     ";
                lines.add(Line.of(st, "│" + Theme.padRight(prefix + l, inner) + "│"));
            }
        }

        lines.add(Line.blank());
        lines.add(Line.of(Theme.bar(), "╠" + Theme.dup('═', inner) + "╣"));
        StringBuilder hints = new StringBuilder("  ");
        for (String[] row : footerKeys) {
            if (hints.length() > 2) {
                hints.append("   ·   ");
            }
            hints.append(row[0]).append(' ').append(row[1]);
        }
        for (String hint : Theme.wrap(hints.toString(), inner)) {
            lines.add(Line.of(Theme.keyTag(), "║" + Theme.padRight(hint, inner) + "║"));
        }
        lines.add(Line.of(Theme.bar(), "╚" + Theme.dup('═', inner) + "╝"));
        return renderAll(lines, inner);
    }

    private static String renderAll(List<Line> lines, int inner) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Line l : lines) {
            if (!first) {
                sb.append('\n');
            }
            String padded = Theme.padRight(l.text(), inner + 2);
            sb.append(Theme.render(l.style(), padded));
            first = false;
        }
        return sb.toString();
    }

    public static String[] keys(String[][] rows) {
        // flatten rows of key tags -> one line each, as footer keys
        String[] out = new String[1];
        StringBuilder sb = new StringBuilder();
        for (String[] r : rows) {
            if (sb.length() > 0) {
                sb.append("   ");
            }
            sb.append("[").append(r[0]).append("] ").append(r[1]);
        }
        out[0] = sb.toString();
        return out;
    }

    // ------------------------------------------------------------------
    // Reusable row factories
    // ------------------------------------------------------------------
    public static Line section(String text) {
        return Line.of(Theme.headingPurple(), "[ " + text.toUpperCase() + " ]");
    }

    public static Line hr(int inner) {
        return Line.of(Theme.dim(), Theme.dup('─', inner - 4));
    }

    public static Line keyRow(String key, String value, int inner) {
        String k = Theme.padRight("[" + key + "]", 10);
        return new Line(Theme.text(), k + value);
    }

    public static List<Line> paragraph(String text, int inner) {
        List<Line> out = new ArrayList<>();
        for (String l : Theme.wrap(text, inner - 4)) {
            out.add(Line.of(Theme.plain(), "   " + l));
        }
        return out;
    }

    public static Line labelValue(String label, String value) {
        String k = Theme.padRight("   " + label + ":", 18);
        String v = (value == null || value.isEmpty()) ? "-" : value;
        return new Line(Theme.text(), k + v);
    }

    public static Line labelValueStyled(String label, String value, Style valueStyle) {
        String k = Theme.padRight("   " + label + ":", 18);
        String v = (value == null || value.isEmpty()) ? "-" : value;
        return new Line(valueStyle, k + v);
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------
    /**
     * A selectable list. Each item may opt into a richer right-hand tag.
     * The selected entry gets a full-width highlight bar.
     */
    public static List<Line> menu(List<String> labels, int selected, int inner) {
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = menuLabel(labels.get(i));
            boolean sel = i == selected;
            String text;
            if (sel) {
                text = Theme.padRight("> " + label, inner - 2);
            } else {
                text = Theme.padRight("  " + label, inner - 2);
            }
            out.add(sel ? Line.of(Theme.selected(), text) : Line.of(Theme.text(), text));
        }
        return out;
    }

    /** Presentation-only icons for primary navigation and recommendation actions. */
    private static String menuLabel(String label) {
        if (label == null || label.isBlank()) {
            return "";
        }
        return switch (label) {
            case "Recommendation Hub" -> "✦ Recommendation Hub";
            case "My Saved Recommendations" -> "💾 My Saved Recommendations";
            case "My Profile" -> "👤 My Profile";
            case "Choose a Goal", "Change Goal" -> "🎯 " + label;
            case "Manage Users" -> "👥 Manage Users";
            case "Recommendation CMS" -> "💡 Recommendation CMS";
            case "Manage Goals" -> "🎯 Manage Goals";
            case "Manage Categories" -> "🗂 Manage Categories";
            case "Analytics" -> "📊 Analytics";
            case "Audit Logs" -> "📝 Audit Logs";
            case "System Settings" -> "⚙ Settings";
            case "Save This Recommendation" -> "💾 Save This Recommendation";
            case "LIFEForge Recommendation" -> "💡 LIFEForge Recommendation";
            case "Chat with AI" -> "🤖 Chat with AI";
            case "Why This Recommendation?" -> "💡 Why This Recommendation?";
            case "Back" -> "🔙 Back";
            default -> label;
        };
    }

    /** Menu items with a leading icon and a trailing tag column. */
    public static List<Line> menuWithTags(String[] icons, String[] labels, String[] tags,
                                          int selected, int inner) {
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            boolean sel = i == selected;
            String icon = icons != null && i < icons.length ? icons[i] : ".";
            String tag = tags != null && i < tags.length ? tags[i] : "      ";
            String line = Theme.padRight("  " + icon + " " + labels[i] + " " + Theme.dup(' ', 2) + tag,
                    inner - 2);
            if (sel) {
                line = ">" + line.substring(1);
                out.add(Line.of(Theme.selected(), line));
            } else {
                out.add(Line.of(Theme.text(), line));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------------
    public static List<Line> buttons(String[] labels, boolean[] primary, int focused, int inner) {
        List<Line> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            if (i > 0) {
                sb.append("   ");
            }
            boolean foc = i == focused;
            if (foc) {
                sb.append(Theme.render(focusedStyle(primary, i), "[ " + labels[i] + " ]"));
            } else {
                Style s = primary[i] ? Theme.headingCyan() : Theme.headingPurple();
                sb.append(Theme.render(s, "[ ") + Theme.render(Theme.text(), labels[i])
                        + Theme.render(s, " ]"));
            }
        }
        out.add(Line.of(Theme.dim(), padBottom(sb.toString(), inner - 2)));
        return out;
    }

    private static Style focusedStyle(boolean[] primary, int i) {
        return primary[i] ? Theme.accentOn() : Theme.accentPurple();
    }

    // ------------------------------------------------------------------
    // Professional box-drawing table
    // ------------------------------------------------------------------
    /**
     * Builds a bordered terminal table using box-drawing characters. Column
     * widths are derived from the widest cell (capped to fit {@code inner}),
     * the selected row gets a prominent {@code >} marker plus the shared
     * selection highlight so it stays visible without colour alone, and every
     * line is produced with plain text (padding/truncation happen before any
     * styling) so ANSI escapes can never break alignment.
     */
    public static List<Line> proTable(String[] headers, String[][] rows,
                                      int selected, int inner) {
        List<Line> out = new ArrayList<>();
        if (headers == null || headers.length == 0) {
            return out;
        }
        int cols = headers.length;
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = Theme.width(headers[i]);
        }
        for (String[] r : rows) {
            for (int i = 0; i < cols && i < r.length; i++) {
                widths[i] = Math.max(widths[i], Theme.width(r[i] == null ? "" : r[i]));
            }
        }

        // Shrink the widest trailing columns to fit the available frame.
        int maxInner = Math.max(cols, inner - 2);
        int total = 1; // left frame
        for (int i = 0; i < cols; i++) {
            total += widths[i] + (i < cols - 1 ? 3 : 2); // cell + separators + right frame
        }
        if (total > maxInner) {
            int excess = total - maxInner;
            for (int i = cols - 1; i >= 0 && excess > 0; i--) {
                int cut = Math.min(Math.max(widths[i] - 3, 0), excess);
                widths[i] -= cut;
                excess -= cut;
            }
        }

        String top = frameLine(widths, '\u250C', '\u252C', '\u2510');
        String mid = frameLine(widths, '\u251C', '\u253C', '\u2524');
        String bot = frameLine(widths, '\u2514', '\u2534', '\u2518');
        out.add(Line.of(Theme.bar(), top));

        StringBuilder sb = new StringBuilder("│");
        for (int i = 0; i < cols; i++) {
            sb.append(' ').append(Theme.padCenter(Theme.truncate(headers[i], widths[i]), widths[i]))
                    .append(i < cols - 1 ? " │" : " │");
        }
        out.add(Line.of(Theme.headingPurple(), sb.toString()));
        out.add(Line.of(Theme.bar(), mid));

        if (rows.length == 0) {
            sb = new StringBuilder("│");
            sb.append(Theme.padCenter("- no data -", Math.max(0, maxInner - 2))).append(" │");
            out.add(Line.of(Theme.dim(), sb.toString()));
        } else {
            for (int i = 0; i < rows.length; i++) {
                String[] r = rows[i];
                sb = new StringBuilder("│");
                for (int c = 0; c < cols; c++) {
                    String cell = c < r.length && r[c] != null ? r[c] : "";
                    cell = Theme.truncate(cell, widths[c]);
                    sb.append(' ').append(Theme.padLeft(cell, widths[c]))
                            .append(c < cols - 1 ? " │" : " │");
                }
                String rowText = sb.toString();
                if (i == selected) {
                    out.add(Line.of(Theme.selected(), ">" + rowText.substring(1)));
                } else {
                    out.add(Line.of(Theme.text(), rowText));
                }
            }
        }
        out.add(Line.of(Theme.bar(), bot));
        return out;
    }

    private static String frameLine(int[] widths, char left, char cross, char right) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < widths.length; i++) {
            sb.append(Theme.dup('─', widths[i] + 2));
            sb.append(i < widths.length - 1 ? cross : right);
        }
        return sb.toString();
    }

    private static String padBottom(String s, int inner) {
        // The combined button line may contain ANSI codes; pad on plain length only.
        int plain = Theme.width(s);
        if (plain >= inner) {
            return s;
        }
        return s + Theme.dup(' ', inner - plain);
    }

    // ------------------------------------------------------------------
    // Table
    // ------------------------------------------------------------------
    public static List<Line> table(String[] headers, String[][] rows, int inner) {
        List<Line> out = new ArrayList<>();
        int cols = headers.length;
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = Theme.width(headers[i]);
        }
        for (String[] r : rows) {
            for (int i = 0; i < cols && i < r.length; i++) {
                widths[i] = Math.max(widths[i], Theme.width(r[i] == null ? "" : r[i]));
            }
        }
        // shrink to fit
        int avail = Math.max(cols, inner - 4);
        int total = (cols - 1) * 3 + 2; // separators + frame
        for (int w : widths) {
            total += w;
        }
        if (total > avail) {
            int excess = total - avail;
            for (int i = cols - 1; i >= 0 && excess > 0; i--) {
                int cut = Math.min(widths[i] - 3, excess);
                widths[i] -= cut;
                excess -= cut;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("+");
        for (int i = 0; i < cols; i++) {
            sb.append(Theme.dup('-', widths[i] + 2));
            sb.append(i < cols - 1 ? "+" : "+");
        }
        out.add(Line.of(Theme.bar(), sb.toString()));

        sb = new StringBuilder();
        sb.append("|");
        for (int i = 0; i < cols; i++) {
            String h = Theme.truncate(headers[i], widths[i]);
            sb.append(' ').append(Theme.padCenter(h, widths[i])).append(" |");
        }
        out.add(Line.of(Theme.headingPurple(), sb.toString()));

        sb = new StringBuilder();
        sb.append("+");
        for (int i = 0; i < cols; i++) {
            sb.append(Theme.dup('-', widths[i] + 2));
            sb.append(i < cols - 1 ? "+" : "+");
        }
        out.add(Line.of(Theme.bar(), sb.toString()));
        if (rows.length == 0) {
            sb = new StringBuilder();
            sb.append("|").append(Theme.padCenter("- no data -", inner - 2)).append("|");
            out.add(Line.of(Theme.dim(), sb.toString()));
        }
        for (String[] r : rows) {
            sb = new StringBuilder();
            sb.append("|");
            for (int i = 0; i < cols; i++) {
                String cell = i < r.length && r[i] != null ? r[i] : "";
                cell = Theme.truncate(cell, widths[i]);
                sb.append(' ').append(Theme.padLeft(cell, widths[i])).append(" |");
            }
            out.add(Line.of(Theme.text(), sb.toString()));
        }
        sb = new StringBuilder();
        sb.append("+");
        for (int i = 0; i < cols; i++) {
            sb.append(Theme.dup('-', widths[i] + 2));
            sb.append(i < cols - 1 ? "+" : "+");
        }
        out.add(Line.of(Theme.bar(), sb.toString()));
        return out;
    }

    // ------------------------------------------------------------------
    // Input field box
    // ------------------------------------------------------------------
    public static List<Line> field(String label, String displayValue, boolean focused,
                                   boolean fgError, int boxWidth) {
        List<Line> out = new ArrayList<>();
        Style labelStyle = fgError ? Theme.err() : (focused ? Theme.headingCyan() : Theme.dim());
        out.add(Line.of(labelStyle, "   " + label + (focused ? " |" : "")));
        String content = Theme.truncate(displayValue, boxWidth - 2);
        String body = "[ " + Theme.padRight(content, boxWidth - 4) + (focused ? "|" : " ") + " ]";
        out.add(Line.of(focused ? Theme.text() : Theme.dim(), "  " + body));
        return out;
    }

    // ------------------------------------------------------------------
    // Choice row (Left/Right cycling select)
    // ------------------------------------------------------------------
    public static List<Line> choiceField(String label, String value, boolean focused, int inner) {
        List<Line> out = new ArrayList<>();
        Style labelStyle = focused ? Theme.headingGreen() : Theme.dim();
        String box = Theme.padRight("  < " + value + " >    ", inner - 6);
        out.add(Line.of(labelStyle, "   " + label));
        out.add(Line.of(focused ? Theme.pivot() : Theme.dim(), box));
        return out;
    }
}
