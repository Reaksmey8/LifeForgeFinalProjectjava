package com.lifeforge.tui4j;

import com.lifeforge.tui4j.terminal.TerminalInput;
import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbletea.message.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.QuitMessage;
import com.williamcallahan.tui4j.compat.bubbletea.message.WindowSizeMessage;
import com.williamcallahan.tui4j.term.TerminalInfo;

import java.io.PrintStream;

/**
 * Drives the {@link LifeForge} Bubble Tea model with real console keyboard input.
 *
 * <p>The stock tui4j {@code Program} relies on JLine, which fails to create a system
 * terminal when the app is launched via {@code ./gradlew run} (stdin becomes a pipe),
 * leaving the screen frozen. This runner instead reads the controlling console
 * directly via {@link TerminalInput}, forwards each keystroke to
 * {@code LifeForge.update(...)} exactly like the normal event loop, detects quit from
 * the returned {@link Command}, and repaints the screen only when the view changes.
 *
 * <p>All navigation, screen and form logic still lives in {@link LifeForge}; this class
 * only replaces the transport that moves keys into the model.
 */
public final class LifeForgeRunner {

    private LifeForgeRunner() {
    }

    /**
     * Runs the model until it quits. Blocks and never returns until the user chooses
     * to exit the application.
     */
    public static void run(LifeForge model) {
        TerminalInput input = TerminalInput.open();
        if (input == null) {
            // Should not happen (the reader falls back to System.in), but guard anyway.
            System.err.println("[LifeForge] No usable input source is available; "
                    + "the TUI needs a terminal or standard input.");
            return;
        }
        PrintStream out = System.out;
        try {
            // The stock tui4j Program installs a TerminalInfoProvider that wraps the
            // JLine terminal. Because we drive input ourselves, install a provider that
            // reports a real (non-dumb) terminal so color/layout rendering works.
            TerminalInfo.provide(() -> new TerminalInfo(true, null));

            enterAltScreen(out);

            // Give the model the real console width and height so layout is centered.
            int lastCols = input.columns();
            int lastRows = input.rows();
            model.update(new WindowSizeMessage(lastCols, lastRows));

            String last = model.view();
            paint(last, out);

            while (true) {
                Key key = input.nextKey();
                if (key == null) {
                    break;
                }
                int curCols = input.columns();
                int curRows = input.rows();
                if (curCols != lastCols || curRows != lastRows) {
                    lastCols = curCols;
                    lastRows = curRows;
                    model.update(new WindowSizeMessage(curCols, curRows));
                }
                if (key.type() != KeyType.KeyRunes || key.runes().length > 0) {
                    UpdateResult<? extends Model> result = model.update(new KeyPressMessage(key));
                    if (result == null) {
                        break;
                    }
                    if (wantsQuit(result.command())) {
                        break;
                    }
                }
                String view = model.view();
                if (!view.equals(last)) {
                    paint(view, out);
                    last = view;
                }
            }
        } finally {
            leaveAltScreen(out);
            input.close();
        }
        // The event loop is finished and the console has been restored. Some bundled
        // dependencies register shutdown hooks that block on stdin (e.g. a terminal
        // restore hook), which would otherwise keep the process hanging after quit.
        // halt() exits immediately without running those hooks; the terminal state
        // was already restored in the finally block above.
        Runtime.getRuntime().halt(0);
    }

    private static boolean wantsQuit(Command command) {
        if (command == null || Command.isNone(command)) {
            return false;
        }
        Message m = command.execute();
        return m instanceof QuitMessage;
    }

    private static void paint(String view, PrintStream out) {
        // Clear the viewport and print the new screen so no stale/duplicate frames
        // accumulate in the console.
        out.print("\u001b[2J\u001b[H");
        out.print(view);
        out.print("\u001b[0m");
        out.flush();
    }

    private static void enterAltScreen(PrintStream out) {
        // Enter alternate screen buffer, hide the cursor, clear.
        out.print("\u001b[?1049h");
        out.print("\u001b[?25l");
        out.flush();
    }

    private static void leaveAltScreen(PrintStream out) {
        // Reset styling, restore cursor, leave alternate screen buffer.
        out.print("\u001b[0m");
        out.print("\u001b[?25h");
        out.print("\u001b[?1049l");
        out.flush();
    }
}








