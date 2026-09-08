package com.lifeforge.tui4j;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Terminal bootstrap + safe-rendering support for the LifeForge TUI.
 *
 * Responsibilities:
 * <ul>
 *   <li>On Windows, switch the OS console code page to UTF-8 (65001) so the
 *       bytes Java writes as UTF-8 are decoded by the console as UTF-8 instead of
 *       the default OEM code page (CP437/CP850/CP1252). This is what fixes
 *       mojibake such as "ΓöÇ" where a box-drawing glyph was mangled.</li>
 *   <li>Defensively re-wrap {@code System.out}/{@code System.err} in UTF-8
 *       {@link PrintStream}s as a fallback when the JVM was launched without the
 *       UTF-8 encoding flags.</li>
 *   <li>Decide whether Unicode box-drawing glyphs are trustworthy. If the console
 *       could not be switched to UTF-8 (or the user forces ASCII), {@link #useUnicode()}
 *       returns {@code false} and {@link #sanitize(String)} translates decorative
 *       glyphs to pure-ASCII equivalents so the layout never corrupts alignment.</li>
 * </ul>
 */
public final class TerminalSupport {

    private static final int CODEPAGE_UTF8 = 65001;

    private static volatile boolean asciiMode;
    private static boolean consoleSet;

    private TerminalSupport() {
    }

    /**
     * Runs before the Bubble Tea {@code Program} builds its terminal.
     * Safe to call more than once.
     */
    public static void bootstrap() {
        boolean windows = isWindows();

        // JLine provider configuration is now handled in Main.java and build.gradle
        // to avoid conflicting with auto-detection. We only ensure UTF-8 encoding.
        System.setProperty("org.jline.terminal.encoding", "UTF-8");

        if (windows) {
            consoleSet = setWindowsConsoleToUtf8();
        }

        wrapStreams();

        asciiMode = decideAsciiMode(windows);
    }

    /** True when decorative Unicode (box drawing, check marks, etc.) is safe. */
    public static boolean useUnicode() {
        return !asciiMode;
    }

    /**
     * Translates decorative Unicode glyphs to ASCII-safe equivalents when the
     * console cannot be trusted to render them. Is ANSI-escape aware so text that
     * already carries colour codes is left intact. All replacements are single
     * terminal columns, so pre-computed padding/alignment is preserved.
     */
    public static String sanitize(String text) {
        if (!asciiMode || text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\u001b') {
                i = copyAnsi(text, i, sb);
                continue;
            }
            int cp = text.codePointAt(i);
            sb.append(mapCodePoint(cp));
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static int copyAnsi(String text, int i, StringBuilder sb) {
        int n = text.length();
        char c = text.charAt(i); // ESC
        sb.append(c);
        i++;
        if (i >= n) {
            return i;
        }
        char nxt = text.charAt(i);
        sb.append(nxt);
        i++;
        // CSI ... final byte in 0x40..0x7E
        if (nxt == '[') {
            while (i < n) {
                char ch = text.charAt(i);
                sb.append(ch);
                i++;
                if (ch >= 0x40 && ch <= 0x7E) {
                    break;
                }
            }
        } else if (nxt == ']') {
            // OSC ... terminated by BEL or ESC \
            while (i < n) {
                char ch = text.charAt(i);
                sb.append(ch);
                i++;
                if (ch == 0x07) {
                    break;
                }
                if (ch == '\u001b' && i < n && text.charAt(i) == '\\') {
                    sb.append(text.charAt(i));
                    i++;
                    break;
                }
            }
        } else if (nxt >= 0x40 && nxt <= 0x7E) {
            // ESC + one control char
        } else {
            // Two-char escape; treat the rest as plain text by not consuming further.
        }
        return i;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Switches the Windows console's input and output code pages to UTF-8 via JNA.
     * Returns true when the output code page is confirmed to be UTF-8 afterwards.
     */
    private static boolean setWindowsConsoleToUtf8() {
        try {
            com.sun.jna.platform.win32.Kernel32 k32 =
                    com.sun.jna.platform.win32.Kernel32.INSTANCE;
            k32.SetConsoleCP(CODEPAGE_UTF8);
            k32.SetConsoleOutputCP(CODEPAGE_UTF8);
            int cp = k32.GetConsoleOutputCP();
            return cp == CODEPAGE_UTF8;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void wrapStreams() {
        try {
            PrintStream utf8Out =
                    new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),
                            true, StandardCharsets.UTF_8);
            System.setOut(utf8Out);
            PrintStream utf8Err =
                    new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err),
                            true, StandardCharsets.UTF_8);
            System.setErr(utf8Err);
        } catch (Throwable t) {
            // Non-fatal; the JVM encoding flags usually already handled this.
        }
    }

    private static boolean decideAsciiMode(boolean windows) {
        // Explicit opt-out should always win.
        String prop = System.getProperty("lifeforge.unicode");
        if (prop != null) {
            return !Boolean.parseBoolean(prop.trim());
        }
        // When JLine is forced into dumb mode, raw ANSI/Unicode support is unreliable.
        if ("true".equalsIgnoreCase(System.getProperty("org.jline.terminal.dumb"))) {
            return true;
        }
        // On Windows, keep Unicode only if we actually switched the console to UTF-8.
        if (windows) {
            return !consoleSet;
        }
        // Non-Windows terminals are assumed Unicode-capable (VT/ANSI).
        return false;
    }

    private static String mapCodePoint(int cp) {
        switch (cp) {
            // box drawing
            case 0x2500: return "-";   // ─
            case 0x2502: return "|";   // │
            case 0x250C: return "+";   // ┌
            case 0x2510: return "+";   // ┐
            case 0x2514: return "+";   // └
            case 0x2518: return "+";   // ┘
            case 0x251C: return "+";   // ├
            case 0x2524: return "+";   // ┤
            case 0x252C: return "+";   // ┬
            case 0x2534: return "+";   // ┴
            case 0x253C: return "+";   // ┼
            // double-line frame drawing
            case 0x2550: return "=";   // ═
            case 0x2554: return "+";   // ╔
            case 0x2557: return "+";   // ╗
            case 0x255A: return "+";   // ╚
            case 0x255D: return "+";   // ╝
            case 0x2560: return "+";   // ╠
            case 0x2563: return "+";   // ╣
            case 0x256D: return "+";   // ╭
            case 0x256E: return "+";   // ╮
            case 0x256F: return "+";   // ╯
            case 0x2570: return "+";   // ╰
            // line / block / cursor glyphs
            case 0x2501: return "-";   // ━
            case 0x2503: return "|";   // ┃
            case 0x2591: return "#";   // ░
            case 0x2592: return "#";   // ▒
            case 0x2593: return "#";   // ▓
            case 0x2588: return "#";   // █
            // bullets / selection
            case 0x25C6: return "*";   // ◆
            case 0x25B6: return ">";   // ▶
            case 0x25C0: return "<";   // ◀
            case 0x25BA: return ">";   // ►
            case 0x25C4: return "<";   // ◄
            case 0x25AA: return ".";   // ▪
            case 0x25CF: return "*";   // ●
            case 0x25B2: return "^";   // ▲
            case 0x25BC: return "v";   // ▼
            case 0x2022: return "*";   // •
            // misc icons
            case 0x2713: return "v";   // ✓
            case 0x2717: return "x";   // ✕
            case 0x26A0: return "!";   // ⚠
            case 0x26D4: return "!";   // ⛔
            case 0x1F389: return "*";  // 🎉
            case 0x2192: return "->";  // →
            case 0x2190: return "<-";  // ←
            // cursor bars
            case 0x258F: return "|";   // ▏
            case 0x2590: return "|";   // ▐
            case 0x2595: return "|";   // ▕
            case 0x258D: return "|";   // ▍
            default: return new String(Character.toChars(cp));
        }
    }
}
