package com.lifeforge.view;

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

    /**
     * Builds one complete frame row. The visible width is calculated before
     * ANSI styling is applied, so every row has exactly the same terminal
     * width and the left/right borders always line up.
     */
    private static String frameRow(String margin, int inner, String content, Style contentStyle) {
        String safe = content == null ? "" : content;

        // The outer frame must always own the final right border. Never let a
        // long admin row replace that border with Theme.truncate()'s "...".
        // Body rows are presentation text, so when a single row is too long,
        // hard-fit its visible characters to the available inner width.
        if (Theme.width(safe) > inner) {
            safe = fitVisibleWidth(safe, inner);
        }

        int pad = Math.max(0, inner - Theme.width(safe));
        return margin
                + Theme.render(Theme.bar(), "│")
                + Theme.render(contentStyle, safe)
                + " ".repeat(pad)
                + Theme.render(Theme.bar(), "│");
    }

    private static String fitVisibleWidth(String text, int maxWidth) {
        if (text == null || maxWidth <= 0) {
            return "";
        }
        if (Theme.width(text) <= maxWidth) {
            return text;
        }

        // Line.text is normally plain text. If a caller supplied ANSI-styled
        // text inside it, strip the embedded ANSI only for the overflow case;
        // the outer frame remains intact and the line is still readable.
        String plain = text.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "");
        if (Theme.width(plain) <= maxWidth) {
            return plain;
        }

        StringBuilder out = new StringBuilder();
        int visible = 0;
        java.text.BreakIterator charIter = java.text.BreakIterator.getCharacterInstance();
        charIter.setText(plain);
        int start = charIter.first();
        for (int end = charIter.next(); end != java.text.BreakIterator.DONE && visible < maxWidth; start = end, end = charIter.next()) {
            String cluster = plain.substring(start, end);
            int cw = Theme.width(cluster);
            if (visible + cw > maxWidth) {
                break;
            }
            out.append(cluster);
            visible += cw;
        }
        return out.toString();
    }

    private static String frameDivider(String margin, int inner, char left, char right) {
        /*
         * Render the border characters individually. Applying a styled value
         * to one long divider can make the style introduce a width/truncation
         * boundary before the closing corner is emitted. Rendering each
         * character separately guarantees exactly (inner + 2) visible columns.
         */
        StringBuilder out = new StringBuilder(margin);
        out.append(Theme.render(Theme.bar(), String.valueOf(left)));
        for (int i = 0; i < inner; i++) {
            out.append(Theme.render(Theme.bar(), "─"));
        }
        out.append(Theme.render(Theme.bar(), String.valueOf(right)));
        return out.toString();
    }

    /** Assembles the shared LifeForge frame: header, content, status and help bar. */
    public static String page(String title, String subtitle, List<Line> body,
                              String status, boolean statusError,
                              List<String[]> footerKeys, int width) {
        int termWidth = Math.max(40, width);

        // The caller already supplies the desired page width. Build exactly
        // one frame at that width; centering is handled by LifeForge.centerFrame().
        // Keeping the geometry in one place prevents admin tables and footer
        // rows from being rendered at a different width than the outer frame.
        int frameWidth = Math.min(100, termWidth);
        int inner = frameWidth - 2;
        String margin = "";

        List<String> renderedRows = new ArrayList<>();

        // 1. Top border
        renderedRows.add(frameDivider(margin, inner, '┌', '┐'));

        // 2. Header title
        String headText = "LIFEForge  /  " + title.toUpperCase();
        if (Theme.width(headText) > inner) {
            headText = Theme.truncate(headText, inner);
        }
        String head = Theme.padCenter(headText, inner);
        renderedRows.add(frameRow(margin, inner, head, Theme.title()));

        // 3. Subtitle (optional)
        if (subtitle != null && !subtitle.isEmpty()) {
            String subText = subtitle;
            if (Theme.width(subText) > inner) {
                subText = Theme.truncate(subText, inner);
            }
            String sub = Theme.padCenter(subText, inner);
            renderedRows.add(frameRow(margin, inner, sub, Theme.muted()));
        }

        // 4. Header divider
        renderedRows.add(frameDivider(margin, inner, '├', '┤'));

        // 5. Body lines
        for (Line l : body) {
            String t = l.text() == null ? "" : l.text();
            renderedRows.add(frameRow(margin, inner, t, l.style()));
        }

        // 6. Status message (optional)
        if (status != null && !status.isEmpty()) {
            renderedRows.add(frameDivider(margin, inner, '├', '┤'));
            Style st = statusError ? Theme.err() : Theme.ok();
            String prefix = statusError ? "  ERROR: " : "  OK: ";
            int wrapW = Math.max(10, inner - Theme.width(prefix) - 2);
            List<String> stLines = Theme.wrap(status, wrapW);
            for (int i = 0; i < stLines.size(); i++) {
                String lineText = (i == 0 ? prefix : " ".repeat(Theme.width(prefix))) + stLines.get(i);
                renderedRows.add(frameRow(margin, inner, lineText, st));
            }
        }

        // 7. Footer divider
        renderedRows.add(frameDivider(margin, inner, '├', '┤'));

        // 8. Footer hints
        StringBuilder hints = new StringBuilder("  ");
        for (String[] row : footerKeys) {
            if (hints.length() > 2) {
                hints.append("   ");
            }
            hints.append(row[0]).append(' ').append(row[1]);
        }
        List<String> hintLines = Theme.wrap(hints.toString(), inner);
        for (String hint : hintLines) {
            renderedRows.add(frameRow(margin, inner, hint, Theme.keyTag()));
        }

        // 9. Bottom border
        renderedRows.add(frameDivider(margin, inner, '└', '┘'));

        return String.join("\n", renderedRows);
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
        String prefix = "   " + label + ": ";
        int pad = Math.max(0, 24 - Theme.width(prefix));
        String v = (value == null || value.isEmpty()) ? "-" : value;
        return new Line(Theme.text(), prefix + " ".repeat(pad) + v);
    }

    public static Line labelValueStyled(String label, String value, Style valueStyle) {
        String prefix = "   " + label + ": ";
        int pad = Math.max(0, 24 - Theme.width(prefix));
        String v = (value == null || value.isEmpty()) ? "-" : value;
        return new Line(valueStyle, prefix + " ".repeat(pad) + v);
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------
    /**
     * A selectable list. Each item may opt into a richer right-hand tag.
     * The selected entry gets a prominent pointer and accent color without a background box.
     */
    public static List<Line> menu(List<String> labels, int selected, int inner) {
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = menuLabel(labels.get(i));
            boolean sel = i == selected;
            String text = (sel ? "  > " : "    ") + label;
            out.add(sel ? Line.of(Theme.selected(), text) : Line.of(Theme.text(), text));
        }
        return out;
    }

    /**
     * Renders a menu whose options block is centered horizontally within the page.
     */
    public static List<Line> menuCenter(List<String> labels, int selected, int inner) {
        List<Line> out = new ArrayList<>();
        int maxLen = 0;
        for (String l : labels) {
            String lbl = menuLabel(l);
            int w = Theme.width(lbl);
            if (w > maxLen) {
                maxLen = w;
            }
        }
        int blockW = maxLen + 4; // "> " prefix + margin
        int padLeft = Math.max(2, (inner - blockW) / 2);
        String indent = " ".repeat(padLeft);

        for (int i = 0; i < labels.size(); i++) {
            String label = menuLabel(labels.get(i));
            boolean isSel = (i == selected);
            String text = indent + (isSel ? "> " : "  ") + label;
            out.add(Line.of(isSel ? Theme.selected() : Theme.text(), text));
        }
        return out;
    }

    /**
     * Renders a single-line horizontal action menu with distinct boxed buttons.
     * Each button is enclosed in bracket styling, e.g. [ 💾 Save Recommendation ],
     * with spacious gap spacing between buttons so they do not look adjacent.
     * The focused item is indicated with a "> " selection pointer.
     * For example:
     *   "  > [ 💾 Save Recommendation ]    [ 💡 Why This? ]    [ 🤖 Chat with AI ]    [ — Back ]"
     */
    public static List<Line> menuHorizontal(List<String> labels, int selected, int inner) {
        if (labels == null || labels.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> btnBoxes = new ArrayList<>();
        int sumW = 0;
        for (String l : labels) {
            String lbl = menuLabel(l);
            String displayLabel = lbl.equals("⬅️ Back") ? "— Back" : lbl;
            String box = "[ " + displayLabel + " ]";
            btnBoxes.add(box);
            sumW += Theme.width(box);
        }

        int numButtons = btnBoxes.size();
        int numGaps = numButtons - 1;

        int gap;
        int leftMargin;

        if (numGaps > 0 && sumW + 2 + numGaps * 4 <= inner) {
            gap = 4;
            leftMargin = 2;
        } else if (numGaps > 0 && sumW + 2 + numGaps * 3 <= inner) {
            gap = 3;
            leftMargin = 2;
        } else if (numGaps > 0 && sumW + 2 + numGaps * 2 <= inner) {
            gap = 2;
            leftMargin = 2;
        } else if (numGaps > 0 && sumW + 1 + numGaps * 2 <= inner) {
            gap = 2;
            leftMargin = 1;
        } else if (numGaps > 0 && sumW + 1 + numGaps * 1 <= inner) {
            gap = 1;
            leftMargin = 1;
        } else if (sumW <= inner) {
            gap = 1;
            leftMargin = 0;
        } else {
            // Fallback to vertical menu if page is too narrow
            return menu(labels, selected, inner);
        }

        StringBuilder sb = new StringBuilder();

        // Left margin / cursor before button 0
        if (leftMargin >= 2) {
            sb.append(selected == 0 ? "> " : "  ");
        } else if (leftMargin == 1) {
            sb.append(selected == 0 ? ">" : " ");
        }

        for (int i = 0; i < numButtons; i++) {
            sb.append(btnBoxes.get(i));
            if (i < numButtons - 1) {
                boolean nextSelected = (selected == i + 1);
                if (nextSelected) {
                    if (gap >= 3) {
                        sb.append(" ".repeat(gap - 2)).append("> ");
                    } else if (gap == 2) {
                        sb.append(" >");
                    } else {
                        sb.append(">");
                    }
                } else {
                    sb.append(" ".repeat(gap));
                }
            }
        }

        return List.of(Line.of(Theme.text(), sb.toString()));
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
            case "Manage Categories" -> "📁 Manage Categories";
            case "Analytics" -> "📊 Analytics";
            case "Audit Logs" -> "📝 Audit Logs";
            case "System Settings" -> "🔧 System Settings";
            case "Save This Recommendation", "Save Recommendation", "💾 Save Recommendation" -> "💾 Save Recommendation";
            case "LIFEForge Recommendation" -> "💡 LIFEForge Recommendation";
            case "Chat with AI" -> "🤖 Chat with AI";
            case "Why This Recommendation?", "Why This?" -> "💡 Why This?";
            case "Back to Plan", "⬅️ Back" -> "⬅️ Back";
            case "— Back" -> "— Back";
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
                                      int selected, int maxWidth) {
        List<Line> out = new ArrayList<>();
        if (headers == null || headers.length == 0) {
            return out;
        }

        int cols = headers.length;
        int tableWidth = Math.max(16, maxWidth);
        int[] widths = new int[cols];

        for (int i = 0; i < cols; i++) {
            widths[i] = Theme.width(headers[i] == null ? "" : headers[i]);
        }
        if (rows != null) {
            for (String[] row : rows) {
                for (int i = 0; i < cols; i++) {
                    String value = (row != null && i < row.length && row[i] != null)
                            ? row[i] : "";
                    widths[i] = Math.max(widths[i], Theme.width(value));
                }
            }
        }

        // Reserve two visible columns for the selection marker inside the ID
        // cell. The table's left border must always remain "│".
        if (selected >= 0 && rows != null && rows.length > 0 && cols > 0) {
            widths[0] += 2;
        }

        // A table row has this exact visible width:
        // left border + (space + cell + space + separator) for every column.
        // Therefore: sum(widths) + 3 * cols + 1.
        shrinkTableWidths(widths, tableWidth);

        String top = frameLine(widths, '┌', '┬', '┐');
        String mid = frameLine(widths, '├', '┼', '┤');
        String bot = frameLine(widths, '└', '┴', '┘');
        out.add(Line.of(Theme.bar(), top));

        StringBuilder sb = new StringBuilder("│");
        for (int i = 0; i < cols; i++) {
            String h = Theme.truncate(headers[i] == null ? "" : headers[i], widths[i]);
            sb.append(' ').append(Theme.padCenter(h, widths[i])).append(" │");
        }
        out.add(Line.of(Theme.headingPurple(), sb.toString()));
        out.add(Line.of(Theme.bar(), mid));

        if (rows == null || rows.length == 0) {
            sb = new StringBuilder("│");
            String empty = Theme.padCenter("- no data -", Math.max(1, widths[0]));
            sb.append(' ').append(empty).append(" │");
            for (int c = 1; c < cols; c++) {
                sb.append(' ').append(Theme.padRight("", widths[c])).append(" │");
            }
            out.add(Line.of(Theme.dim(), sb.toString()));
        } else {
            for (int rIndex = 0; rIndex < rows.length; rIndex++) {
                String[] row = rows[rIndex];
                sb = new StringBuilder("│");

                for (int c = 0; c < cols; c++) {
                    String cell = (row != null && c < row.length && row[c] != null)
                            ? row[c] : "";

                    if (rIndex == selected && c == 0) {
                        cell = "> " + cell;
                    }

                    cell = Theme.truncate(cell, widths[c]);
                    sb.append(' ').append(Theme.padRight(cell, widths[c])).append(" │");
                }

                // The complete row is guaranteed to be tableWidth columns wide.
                out.add(Line.of(rIndex == selected ? Theme.selected() : Theme.text(),
                        sb.toString()));
            }
        }

        out.add(Line.of(Theme.bar(), bot));
        return out;
    }

    private static void shrinkTableWidths(int[] widths, int maxWidth) {
        int total = tableVisibleWidth(widths);
        if (total <= maxWidth) {
            return;
        }

        int excess = total - maxWidth;
        while (excess > 0) {
            int best = -1;
            int largest = 1;
            for (int i = 0; i < widths.length; i++) {
                // Keep at least 1 visible character per cell.
                if (widths[i] > largest) {
                    largest = widths[i];
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            widths[best]--;
            excess--;
        }
    }

    private static int tableVisibleWidth(int[] widths) {
        int total = 1; // left border
        for (int width : widths) {
            total += width + 3; // leading space + cell + trailing space/separator
        }
        return total;
    }

    private static String cSafe(String[] row, int index) {
        return row != null && index >= 0 && index < row.length && row[index] != null
                ? row[index]
                : "";
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
