package com.lifeforge;

import com.lifeforge.config.DatabaseConfig;
import com.lifeforge.tui4j.LifeForge;
import com.lifeforge.tui4j.LifeForgeRunner;
import com.lifeforge.tui4j.TerminalSupport;

/**
 * LifeForge application entry point.
 *
 * Builds the whole dependency graph (AppContext) and launches the TUI on an
 * alternate screen. All screens and navigation live inside {@link LifeForge}.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=========================================================================");
        System.out.println("""
                ██╗     ██╗███████╗███████╗███████╗ ██████╗ ██████╗  ██████╗ ███████╗
                ██║     ██║██╔════╝██╔════╝██╔════╝██╔═══██╗██╔══██╗██╔════╝ ██╔════╝
                ██║     ██║█████╗  █████╗  █████╗  ██║   ██║██████╔╝██║  ███╗█████╗ \s
                ██║     ██║██╔══╝  ██╔══╝  ██╔══╝  ██║   ██║██╔══██╗██║   ██║██╔══╝ \s
                ███████╗██║██║     ███████╗██║     ╚██████╔╝██║  ██║╚██████╔╝███████╗
                ╚══════╝╚═╝╚═╝     ╚══════╝╚═╝      ╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚══════╝
                
                
                """);
        System.out.println("            Personalized Health & Lifestyle Recommendation System        ");
        System.out.println("=========================================================================\n");
        try {
            Thread.sleep(2000); // Holds banner on screen for 2 seconds before TUI clears it
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Terminal provider and encoding configured via JVM args in build.gradle
        // Prepare the terminal (UTF-8 console, safe ASCII fallback) BEFORE the
        // TUI4J/JLine terminal is created so box-drawing characters and colours
        // render and align correctly on Windows.
        TerminalSupport.bootstrap();

        AppContext context = AppContext.build();

        // Check the database up-front so we can report a clear status without blocking
        // the TUI. If PostgreSQL is reachable, nothing is printed; if it is not, we show
        // a clear message (password never printed) and STILL start the Welcome screen so
        // the app remains usable offline.
        String dbStatus = DatabaseConfig.connectionStatus();
        if (dbStatus != null) {
            System.err.println("[LifeForge] INFO: " + dbStatus);
            System.err.println("[LifeForge] Login, registration and data access will be "
                    + "limited until the database is available.");
            System.err.println("[LifeForge] Configure via DB_URL / DB_USERNAME / DB_PASSWORD "
                    + "environment variables and ensure PostgreSQL is running.");
        }

        // Run the model with a real-console keyboard driver. The stock Bubble Tea
        // Program/JLine cannot build a system terminal when launched via
        // `./gradlew run` (stdin becomes a pipe), so LifeForgeRunner reads the
        // controlling console directly and feeds each key into the model.
        LifeForgeRunner.run(new LifeForge(context));
    }
}