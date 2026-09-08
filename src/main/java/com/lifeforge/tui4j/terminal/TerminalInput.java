package com.lifeforge.tui4j.terminal;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.Wincon;
import com.sun.jna.platform.win32.WinNT;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Reads raw, per-key console input for the LifeForge TUI.
 *
 * <p>When LifeForge is launched through a parent process such as {@code ./gradlew run},
 * the JVM's {@code System.in} is redirected to a pipe instead of the real console.
 * JLine therefore cannot build a usable system terminal, falls back to a "dumb"
 * terminal, and keyboard input ({@code Arrow}/{@code Enter}/{@code Q}, typing) never
 * arrives -- the screen renders but is frozen.
 *
 * <p>This class bypasses {@code System.in} entirely: on Windows it opens the attached
 * console directly ({@code CONIN$}) through JNA and reads structured
 * {@code INPUT_RECORD}s (so arrow keys, Enter, etc. are reliable regardless of how the
 * JVM was launched). On POSIX it reads the controlling terminal ({@code /dev/tty}) in
 * raw mode. Each keystroke is converted to a tui4j {@link Key}.
 */
public final class TerminalInput {

    /** Windows console-mode flags. */
    private static final int ENABLE_PROCESSED_INPUT = 0x0001;
    private static final int ENABLE_LINE_INPUT = 0x0002;
    private static final int ENABLE_ECHO_INPUT = 0x0004;
    private static final int ENABLE_MOUSE_INPUT = 0x0010;
    private static final int ENABLE_EXTENDED_FLAGS = 0x0080;

    private interface Backend {
        Key nextKey();

        int columns();

        void close();

        /** Short, human-readable description of the active input source, for diagnostics. */
        String description();

        /** True when this backend reads real per-key console events (arrows work). */
        boolean raw();
    }

    private final Backend backend;

    private TerminalInput(Backend backend) {
        this.backend = backend;
    }

    /**
     * Opens the controlling console in raw mode for the best (per-key, arrow-aware)
     * experience. When no real console can be reached -- for example the app was
     * launched by a build tool such as Gradle that pipes standard input -- this
     * gracefully falls back to reading {@link System#in} instead of returning
     * {@code null}, so the TUI always starts and can be used with the keyboard.
     */
    public static TerminalInput open() {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            Backend b = WindowsBackend.open();
            if (b != null) {
                return new TerminalInput(b);
            }
        } else {
            Backend b = PosixBackend.open();
            if (b != null) {
                return new TerminalInput(b);
            }
        }
        // No attached console. Fall back to standard input so the app still runs and
        // accepts keyboard input instead of exiting.
        return new TerminalInput(new SystemInBackend());
    }

    /** Blocks until a key is available and returns it. */
    public Key nextKey() {
        return backend.nextKey();
    }

    /** Console width in columns (for correct screen layout). */
    public int columns() {
        return backend.columns();
    }

    /** Restores the console and releases resources. */
    public void close() {
        backend.close();
    }

    /** Human-readable description of which input source is active, for diagnostics. */
    public String description() {
        return backend.description();
    }

    /**
     * True when arrow keys, Tab, and every other key are delivered per-keystroke from
     * the real console. False means the fallback line-buffered {@link System#in}
     * reader is in use, in which case arrow keys will NOT work (the console's own
     * cooked/line-editing mode swallows them before they ever reach this process) --
     * only typed characters followed by Enter get through.
     */
    public boolean raw() {
        return backend.raw();
    }

    // ------------------------------------------------------------------
    // Windows backend (JNA + ReadConsoleInputW on CONIN$)
    // ------------------------------------------------------------------
    private static final class WindowsBackend implements Backend {
        private static final int VK_BACK = 0x08;
        private static final int VK_TAB = 0x09;
        private static final int VK_RETURN = 0x0D;
        private static final int VK_ESCAPE = 0x1B;
        private static final int VK_SPACE = 0x20;
        private static final int VK_PRIOR = 0x21;
        private static final int VK_NEXT = 0x22;
        private static final int VK_END = 0x23;
        private static final int VK_HOME = 0x24;
        private static final int VK_LEFT = 0x25;
        private static final int VK_UP = 0x26;
        private static final int VK_RIGHT = 0x27;
        private static final int VK_DOWN = 0x28;
        private static final int VK_INSERT = 0x2D;
        private static final int VK_DELETE = 0x2E;

        private static final int SHIFT_PRESSED = 0x0010;

        private static final int GENERIC_READ = 0x80000000;
        private static final int GENERIC_WRITE = 0x40000000;
        private static final int FILE_SHARE_READ = 0x00000001;
        private static final int FILE_SHARE_WRITE = 0x00000002;
        private static final int OPEN_EXISTING = 3;
        private static final int LEFT_CTRL_PRESSED = 0x0008;
        private static final int RIGHT_CTRL_PRESSED = 0x0004;

        /** kernel32 functions JNA's default Kernel32 interface omits. */
        private interface K32 extends Library {
            K32 I = Native.load("kernel32", K32.class);

            WinNT.HANDLE GetStdHandle(int nStdHandle);

            WinNT.HANDLE CreateFileW(String name, int access, int share,
                                     WinBase.SECURITY_ATTRIBUTES attr, int create,
                                     int flags, WinNT.HANDLE tmpl);

            boolean GetConsoleMode(WinNT.HANDLE h, IntByReference mode);

            boolean AttachConsole(int dwProcessId);

            boolean FreeConsole();

            boolean SetConsoleMode(WinNT.HANDLE h, int mode);

            boolean ReadConsoleInputW(WinNT.HANDLE h, Wincon.INPUT_RECORD[] buf,
                                      int len, IntByReference read);

            boolean GetConsoleScreenBufferInfo(WinNT.HANDLE h,
                                               Wincon.CONSOLE_SCREEN_BUFFER_INFO info);

            boolean CloseHandle(WinNT.HANDLE h);
        }

        // Standard input handle id, valid when the process is attached to a console.
        private static final int STD_INPUT_HANDLE = -10;

        // AttachConsole flag: attach to the console that started the process, so a
        // JVM launched by a parent (e.g. `gradlew run`) can reach the real console.
        private static final int ATTACH_PARENT_PROCESS = 0xFFFFFFFF;

        private final WinNT.HANDLE hIn;
        private final boolean closeIn;
        private final WinNT.HANDLE hOut;
        private final int originalMode;
        private final boolean attached;
        private final Wincon.INPUT_RECORD[] buffer;
        private final IntByReference numRead = new IntByReference();

        private WindowsBackend(WinNT.HANDLE hIn, boolean closeIn, WinNT.HANDLE hOut,
                               int originalMode, boolean attached) {
            this.hIn = hIn;
            this.closeIn = closeIn;
            this.hOut = hOut;
            this.originalMode = originalMode;
            this.attached = attached;
            Wincon.INPUT_RECORD rec = new Wincon.INPUT_RECORD();
            this.buffer = (Wincon.INPUT_RECORD[]) rec.toArray(1);
        }

        static Backend open() {
            try {
                K32 k32 = K32.I;
                WinNT.HANDLE hIn = null;
                boolean closeIn = false;
                WinNT.HANDLE hOut = null;
                boolean attached = false;
                try {
                    // Prefer the process's standard input handle when it really is the
                    // console (works under plain `java` and interactive `gradlew run`).
                    WinNT.HANDLE stdIn = k32.GetStdHandle(STD_INPUT_HANDLE);
                    IntByReference mode = new IntByReference();
                    boolean isConsole =
                            stdIn != null && k32.GetConsoleMode(stdIn, mode);
                    if (!isConsole) {
                        // stdin is a pipe (e.g. launched via `gradlew run`), so the JVM
                        // process isn't attached to the parent console and `CONIN$` does
                        // not exist yet. Attach to the parent's console, then re-acquire
                        // the standard input handle which now points at the console.
                        k32.FreeConsole();
                        attached = k32.AttachConsole(ATTACH_PARENT_PROCESS);
                        stdIn = k32.GetStdHandle(STD_INPUT_HANDLE);
                        isConsole = stdIn != null && k32.GetConsoleMode(stdIn, mode);
                    }
                    if (isConsole) {
                        hIn = stdIn;
                        closeIn = false;
                    } else {
                        // Fall back to opening the attached console by name.
                        hIn = k32.CreateFileW("CONIN$", GENERIC_READ | GENERIC_WRITE,
                                FILE_SHARE_READ | FILE_SHARE_WRITE, null, OPEN_EXISTING, 0, null);
                        if (hIn == null) {
                            return null;
                        }
                        closeIn = true;
                        if (!k32.GetConsoleMode(hIn, mode)) {
                            return null;
                        }
                    }
                    int original = mode.getValue();
                    int raw = (original & ~(ENABLE_PROCESSED_INPUT | ENABLE_LINE_INPUT
                            | ENABLE_ECHO_INPUT | ENABLE_MOUSE_INPUT))
                            | ENABLE_EXTENDED_FLAGS;
                    if (!k32.SetConsoleMode(hIn, raw)) {
                        return null;
                    }
                    hOut = k32.CreateFileW("CONOUT$", GENERIC_READ | GENERIC_WRITE,
                            FILE_SHARE_READ | FILE_SHARE_WRITE, null, OPEN_EXISTING, 0, null);
                    return new WindowsBackend(hIn, closeIn, hOut, original, attached);
                } catch (Throwable t) {
                    closeQuietly(hIn, closeIn, hOut);
                    return null;
                }
            } catch (Throwable t) {
                return null;
            }
        }

        private static void closeQuietly(WinNT.HANDLE hIn, boolean closeIn,
                                         WinNT.HANDLE hOut) {
            try {
                if (hIn != null && closeIn) {
                    K32.I.CloseHandle(hIn);
                }
            } catch (Throwable ignored) {
            }
            try {
                if (hOut != null) {
                    K32.I.CloseHandle(hOut);
                }
            } catch (Throwable ignored) {
            }
        }

        @Override
        public Key nextKey() {
            K32 k32 = K32.I;
            // Consecutive, unrecoverable-looking failures (bad handle, etc.) are the
            // only reason to give up. A single transient hiccup while decoding one
            // INPUT_RECORD (e.g. an unexpected/partial native structure) must NOT end
            // the whole session -- returning null here bubbles up as "no more input"
            // in LifeForgeRunner and silently ends the program, which looks exactly
            // like the TUI froze. So on a decode error we log once (first time only)
            // and keep reading instead of bailing out.
            int consecutiveFailures = 0;
            while (true) {
                try {
                    if (!k32.ReadConsoleInputW(hIn, buffer, 1, numRead)) {
                        // ReadConsoleInputW itself failing (not just a decode hiccup)
                        // means the handle is no longer usable -- that is fatal.
                        return null;
                    }
                    consecutiveFailures = 0;
                    if (numRead.getValue() != 1) {
                        continue;
                    }
                    buffer[0].read();
                    if (buffer[0].EventType != Wincon.INPUT_RECORD.KEY_EVENT) {
                        continue;
                    }
                    Wincon.KEY_EVENT_RECORD ke =
                            (Wincon.KEY_EVENT_RECORD) buffer[0].Event.readField("KeyEvent");
                    if (!ke.bKeyDown) {
                        continue;
                    }
                    int vk = ke.wVirtualKeyCode & 0xFFFF;
                    char uc = ke.uChar;
                    int repeat = Math.max(1, ke.wRepeatCount & 0xFFFF);
                    boolean shift = (ke.dwControlKeyState & SHIFT_PRESSED) != 0;
                    boolean control = (ke.dwControlKeyState & (LEFT_CTRL_PRESSED | RIGHT_CTRL_PRESSED)) != 0;
                    Key k = mapKey(vk, uc, shift, control);
                    if (k != null) {
                        if (repeat > 1 && k.type() == KeyType.KeyRunes && k.runes().length == 1) {
                            char[] repeated = new char[repeat];
                            java.util.Arrays.fill(repeated, k.runes()[0]);
                            return new Key(KeyType.KeyRunes, repeated);
                        }
                        return k;
                    }
                    // Key event we don't map to anything (e.g. a bare modifier key
                    // press) -- keep reading, this is normal and not an error.
                } catch (Throwable t) {
                    // A single malformed/unexpected record should not kill the whole
                    // input loop. Only give up after several failures in a row, which
                    // indicates the console handle itself has gone bad.
                    consecutiveFailures++;
                    if (consecutiveFailures > 25) {
                        return null;
                    }
                }
            }
        }

        private Key mapKey(int vk, char uc, boolean shift, boolean control) {
            // Processed console input is disabled, so Ctrl+C is delivered as a key
            // event. Preserve it for the focused text field instead of allowing the
            // operating system to terminate the application.
            if ((control && (uc == 3 || vk == 'C')) || uc == 3) {
                return new Key(KeyType.keyETX);
            }
            switch (vk) {
                case VK_UP: return new Key(KeyType.KeyUp);
                case VK_DOWN: return new Key(KeyType.KeyDown);
                case VK_LEFT: return new Key(KeyType.KeyLeft);
                case VK_RIGHT: return new Key(KeyType.KeyRight);
                case VK_HOME: return new Key(KeyType.KeyHome);
                case VK_END: return new Key(KeyType.KeyEnd);
                case VK_PRIOR: return new Key(KeyType.KeyPgUp);
                case VK_NEXT: return new Key(KeyType.KeyPgDown);
                case VK_INSERT: return new Key(KeyType.KeyInsert);
                case VK_DELETE: return new Key(KeyType.KeyDelete);
                case VK_BACK: return new Key(KeyType.keyBS);
                case VK_ESCAPE: return new Key(KeyType.keyESC);
                case VK_SPACE: return new Key(KeyType.KeySpace);
                case VK_TAB:
                    return new Key(shift ? KeyType.KeyShiftTab : KeyType.keyHT);
                case VK_RETURN:
                    return new Key(KeyType.keyCR);
                default:
                    break;
            }
            if (uc >= 0x20 && uc != 0x7F) {
                return new Key(KeyType.KeyRunes, new char[] { uc });
            }
            return null;
        }

        @Override
        public int columns() {
            try {
                Wincon.CONSOLE_SCREEN_BUFFER_INFO info = new Wincon.CONSOLE_SCREEN_BUFFER_INFO();
                if (K32.I.GetConsoleScreenBufferInfo(hOut, info)) {
                    int w = info.srWindow.Right - info.srWindow.Left + 1;
                    if (w >= 40) {
                        return w;
                    }
                }
            } catch (Throwable ignored) {
            }
            return 80;
        }

        @Override
        public void close() {
            try {
                K32.I.SetConsoleMode(hIn, originalMode);
            } catch (Throwable ignored) {
            }
            closeQuietly(hIn, closeIn, hOut);
            if (attached) {
                // Detach the console we attached to in open() so we don't leave the
                // process owning one after the app exits.
                K32.I.FreeConsole();
            }
        }

        @Override
        public String description() {
            return "Windows console (raw ReadConsoleInputW on " + (closeIn ? "CONIN$" : "STDIN")
                    + (attached ? ", attached via AttachConsole" : "") + ")";
        }

        @Override
        public boolean raw() {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // POSIX backend (/dev/tty + raw mode via stty + VT escape parsing)
    // ------------------------------------------------------------------
    private static final class PosixBackend implements Backend {
        private final InputStream tty;
        private final VtKeyReader reader;

        private PosixBackend(InputStream tty) {
            this.tty = tty;
            this.reader = new VtKeyReader(tty);
        }

        static Backend open() {
            try {
                FileInputStream fis = new FileInputStream("/dev/tty");
                stty("/bin/sh", "-c", "stty raw -echo < /dev/tty 2>/dev/null");
                return new PosixBackend(fis);
            } catch (Throwable t) {
                return null;
            }
        }

        private static void stty(String... cmd) {
            try {
                new ProcessBuilder(cmd).redirectErrorStream(true).start()
                        .waitFor();
            } catch (Throwable ignored) {
            }
        }

        @Override
        public Key nextKey() {
            return reader.nextKey();
        }

        @Override
        public int columns() {
            return 80;
        }

        @Override
        public void close() {
            try {
                tty.close();
            } catch (IOException ignored) {
            }
            stty("/bin/sh", "-c", "stty sane < /dev/tty 2>/dev/null");
        }

        @Override
        public String description() {
            return "POSIX /dev/tty (raw mode via stty)";
        }

        @Override
        public boolean raw() {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // System.in fallback backend.
    //
    // Used when no real console is attached (e.g. launched via `gradlew run`, where
    // stdin is piped from the terminal). Reads plain bytes from System.in and decodes
    // VT/ANSI escape sequences so arrow keys still work when the terminal delivers
    // them raw. Lets the TUI always start and accept keyboard input instead of exiting.
    //
    // IMPORTANT: on Windows this backend can only see arrow keys if the real console
    // happens to already be in raw/VT mode; by default a Windows console is in cooked
    // (line-buffered) mode, where arrow keys are consumed by the console's own line
    // editor and never reach this process at all -- only typed text followed by Enter
    // gets through. This backend is a last resort; WindowsBackend succeeding is what
    // makes arrow-key navigation actually work.
    // ------------------------------------------------------------------
    private static final class SystemInBackend implements Backend {
        private final VtKeyReader reader = new VtKeyReader(System.in);

        @Override
        public Key nextKey() {
            return reader.nextKey();
        }

        @Override
        public int columns() {
            return 80;
        }

        @Override
        public void close() {
            // Do not close System.in.
        }

        @Override
        public String description() {
            return "System.in fallback (line-buffered -- arrow keys may not work)";
        }

        @Override
        public boolean raw() {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Shared VT/ANSI escape-sequence key parser for byte streams.
    // ------------------------------------------------------------------
    private static final class VtKeyReader {
        private final InputStream in;

        private VtKeyReader(InputStream in) {
            this.in = in;
        }

        Key nextKey() {
            // Only a genuine end of stream (read() < 0, or the stream throwing
            // IOException because it was closed) should return null -- that is what
            // tells LifeForgeRunner's loop to stop. A control byte or escape sequence
            // we don't recognize is normal (extra terminal-specific bytes, a stray
            // malformed sequence, etc.) and must simply be skipped so the loop reads
            // the next real keystroke, instead of null bubbling up and silently
            // ending the whole session (which looks exactly like the app froze/quit
            // on its own).
            while (true) {
                int c;
                try {
                    c = in.read();
                } catch (IOException e) {
                    return null;
                }
                if (c < 0) {
                    return null;
                }
                Key k;
                try {
                    k = parse(c);
                } catch (RuntimeException malformed) {
                    continue;
                }
                if (k != null) {
                    return k;
                }
                // Unrecognized/ignored byte -- keep reading rather than stopping.
            }
        }

        private int readByte() {
            try {
                int v = in.read();
                return v < 0 ? -1 : v;
            } catch (IOException e) {
                return -1;
            }
        }

        private Key parse(int first) {
            if (first == 0x1B) {
                int b = readByte();
                if (b == '[') {
                    return parseCsi();
                }
                if (b == 'O') {
                    int last = readByte();
                    switch (last) {
                        case 'H': return new Key(KeyType.KeyHome);
                        case 'F': return new Key(KeyType.KeyEnd);
                        default: return new Key(KeyType.keyESC);
                    }
                }
                if (b == 0x1B) {
                    return new Key(KeyType.keyESC);
                }
                if (b >= 0x20) {
                    return new Key(KeyType.KeyRunes, new char[] { (char) b });
                }
                return new Key(KeyType.keyESC);
            }
            switch (first) {
                case 0x03: return new Key(KeyType.keyETX);
                case '\r': return new Key(KeyType.keyCR);
                case '\n': return new Key(KeyType.keyLF);
                case '\t': return new Key(KeyType.keyHT);
                case 0x7F: return new Key(KeyType.keyBS);
                case '\b': return new Key(KeyType.keyBS);
                default:
                    if (first >= 0x20) {
                        return new Key(KeyType.KeyRunes, new char[] { (char) first });
                    }
                    return null;
            }
        }

        private Key parseCsi() {
            StringBuilder params = new StringBuilder();
            int last = readByte();
            while (last >= '0' && last <= '9' || last == ';') {
                params.append((char) last);
                last = readByte();
            }
            switch (last) {
                case 'A': return new Key(KeyType.KeyUp);
                case 'B': return new Key(KeyType.KeyDown);
                case 'C': return new Key(KeyType.KeyRight);
                case 'D': return new Key(KeyType.KeyLeft);
                case 'H': return new Key(KeyType.KeyHome);
                case 'F': return new Key(KeyType.KeyEnd);
                case 'Z': return new Key(KeyType.KeyShiftTab);
                case '~':
                    int n = params.length() == 0 ? 0 : Integer.parseInt(params.toString());
                    switch (n) {
                        case 1: return new Key(KeyType.KeyHome);
                        case 2: return new Key(KeyType.KeyInsert);
                        case 3: return new Key(KeyType.KeyDelete);
                        case 4: return new Key(KeyType.KeyEnd);
                        case 5: return new Key(KeyType.KeyPgUp);
                        case 6: return new Key(KeyType.KeyPgDown);
                        default: return new Key(KeyType.keyESC);
                    }
                default:
                    return new Key(KeyType.keyESC);
            }
        }
    }
}
