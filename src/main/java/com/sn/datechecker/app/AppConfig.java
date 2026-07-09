package com.sn.datechecker.app;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Persistent application configuration stored in ~/.datechecker/config.properties.
 * Replaces the test-resource config.properties for the desktop app.
 */
public class AppConfig {

    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".datechecker");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.properties");

    private final Properties props = new Properties();

    // ──── Keys ────
    public static final String INSTANCE_URL = "sn.instance.url";
    public static final String USERNAME = "sn.username";
    public static final String PASSWORD = "sn.password";
    public static final String LOCALE = "sn.locale";
    public static final String PAGES = "sn.pages";
    public static final String BROWSER = "browser";
    public static final String HEADLESS = "browser.headless";
    public static final String PAGE_TIMEOUT = "page.load.timeout.seconds";
    public static final String SCAN_DELAY = "scan.delay.ms";
    public static final String REPORT_DIR = "report.dir";
    public static final String SCREENSHOT_DIR = "screenshot.dir";
    public static final String EXTENSION_PATH = "extension.path";
    public static final String TRACKER_URL = "tracker.instance.url";
    public static final String TRACKER_TABLE = "tracker.table";
    public static final String TRACKER_USERNAME = "tracker.username";
    public static final String TRACKER_PASSWORD = "tracker.password";
    public static final String TRACKER_AUTO = "tracker.auto.report";

    public AppConfig() {
        setDefaults();
        load();
    }

    private void setDefaults() {
        props.setProperty(INSTANCE_URL, "");
        props.setProperty(USERNAME, "admin");
        props.setProperty(PASSWORD, "");
        props.setProperty(LOCALE, "ja");
        props.setProperty(PAGES, "");
        props.setProperty(BROWSER, "chrome");
        props.setProperty(HEADLESS, "false");
        props.setProperty(PAGE_TIMEOUT, "30");
        props.setProperty(SCAN_DELAY, "10000");
        props.setProperty(REPORT_DIR, CONFIG_DIR.resolve("reports").toString());
        props.setProperty(SCREENSHOT_DIR, CONFIG_DIR.resolve("screenshots").toString());
        props.setProperty(EXTENSION_PATH, "");
        props.setProperty(TRACKER_URL, "");
        props.setProperty(TRACKER_TABLE, "x_all_language_tra_all_language_tracker");
        props.setProperty(TRACKER_USERNAME, "");
        props.setProperty(TRACKER_PASSWORD, "");
        props.setProperty(TRACKER_AUTO, "false");
    }

    public void load() {
        if (Files.exists(CONFIG_FILE)) {
            // Load from saved app config
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("Failed to load config: " + e.getMessage());
            }
        } else {
            // First run — try to load from project's config.properties on classpath
            InputStream cpStream = getClass().getClassLoader()
                    .getResourceAsStream("config.properties");
            if (cpStream != null) {
                try (cpStream) {
                    props.load(cpStream);
                    System.out.println("Loaded config from classpath config.properties");
                } catch (IOException e) {
                    System.err.println("Failed to load classpath config: " + e.getMessage());
                }
            }
            // Also try the project file directly (when running from IDE/Maven)
            Path projectConfig = Paths.get("src/test/resources/config.properties");
            if (Files.exists(projectConfig)) {
                try (InputStream in = Files.newInputStream(projectConfig)) {
                    props.load(in);
                    System.out.println("Loaded config from " + projectConfig.toAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Failed to load project config: " + e.getMessage());
                }
            }
            // Save it to the app config location for future runs
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
                props.store(out, "ServiceNow Date Format Checker Configuration");
            }
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public String get(String key) {
        return props.getProperty(key, "");
    }

    public void set(String key, String value) {
        props.setProperty(key, value != null ? value : "");
    }

    public List<String> getPages() {
        String raw = get(PAGES);
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split("\\s*,\\s*")));
    }

    public void setPages(List<String> pages) {
        set(PAGES, String.join(",", pages));
    }

    public Properties toProperties() {
        return (Properties) props.clone();
    }
}
