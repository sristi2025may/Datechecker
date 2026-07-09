package com.sn.datechecker.app;

/**
 * Launcher class for the DateChecker desktop application.
 * This non-JavaFX class is needed for fat JAR packaging because
 * JavaFX Application classes cannot be the main class in a shaded JAR.
 */
public class DateCheckerLauncher {
    public static void main(String[] args) {
        DateCheckerApp.main(args);
    }
}
