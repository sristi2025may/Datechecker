package com.sn.datechecker.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Loads test configuration from config.properties.
 */
public class TestConfig {

    private static final Properties props = new Properties();

    static {
        try (InputStream is = TestConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                System.err.println("[TestConfig] config.properties not found on classpath. Using defaults.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    public static String getInstanceUrl() {
        return props.getProperty("sn.instance.url", "https://yourinstance.service-now.com");
    }

    public static String getUsername() {
        return props.getProperty("sn.username", "");
    }

    public static String getPassword() {
        return props.getProperty("sn.password", "");
    }

    public static String getLocale() {
        return props.getProperty("sn.locale", "ja");
    }

    public static String getBrowser() {
        return props.getProperty("browser", "chrome");
    }

    public static boolean isHeadless() {
        return Boolean.parseBoolean(props.getProperty("browser.headless", "false"));
    }

    public static int getPageLoadTimeoutSeconds() {
        return Integer.parseInt(props.getProperty("page.load.timeout.seconds", "30"));
    }

    public static int getScanDelayMs() {
        return Integer.parseInt(props.getProperty("scan.delay.ms", "3000"));
    }

    public static String getReportDir() {
        return props.getProperty("report.dir", "target/reports");
    }

    public static String getScreenshotDir() {
        return props.getProperty("screenshot.dir", "target/screenshots");
    }

    public static String getExtensionPath() {
        return props.getProperty("extension.path", "");
    }

    /**
     * Returns the list of page paths to scan (relative to instance URL).
     * Configured as comma-separated values in config.properties.
     */
    public static List<String> getPagePaths() {
        String paths = props.getProperty("sn.pages",
                "/now/nav/ui/classic/params/target/incident_list.do," +
                "/now/nav/ui/classic/params/target/change_request_list.do");
        return Arrays.asList(paths.split("\\s*,\\s*"));
    }

    // ─── i18n Global Tracker config ───

    public static String getTrackerInstanceUrl() {
        return props.getProperty("tracker.instance.url", "https://i18ntest.service-now.com");
    }

    public static String getTrackerTable() {
        return props.getProperty("tracker.table", "x_all_language_tra_all_language_tracker");
    }

    public static String getTrackerUsername() {
        return props.getProperty("tracker.username", "");
    }

    public static String getTrackerPassword() {
        return props.getProperty("tracker.password", "");
    }

    public static boolean isAutoReportEnabled() {
        return Boolean.parseBoolean(props.getProperty("tracker.auto.report", "false"));
    }

    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }
}
