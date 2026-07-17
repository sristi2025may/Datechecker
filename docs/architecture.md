# SN Date Format Checker — Architecture Overview

## 1. Framework Purpose

An end-to-end automation framework that validates date/time format localization across ServiceNow pages rendered in non-English languages. It detects incorrect formats, visually highlights them, captures full-page screenshots, generates detailed HTML reports, and enables one-click defect filing to the i18n Global Tracker.

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        TEST EXECUTION ENGINE                            │
│                        (TestNG + Maven)                                 │
│                                                                         │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────────┐    │
│  │  TestConfig   │   │ DriverFactory│   │   DateFormatTest          │    │
│  │ (Properties)  │──▶│  (WebDriver) │──▶│  (Test Orchestrator)      │    │
│  └──────────────┘   └──────────────┘   └────────┬─────────────────┘    │
│                                                  │                      │
│         ┌────────────────────────────────────────┼──────────────┐       │
│         │                                        │              │       │
│         ▼                                        ▼              ▼       │
│  ┌──────────────┐   ┌────────────────────┐  ┌──────────────────┐       │
│  │  SNLoginPage  │   │  DateFormatChecker  │  │ ScreenshotCapture│       │
│  │  (Login +     │   │  (Scan + Detect +   │  │ (CDP Full-Page)  │       │
│  │   Language)   │   │   Highlight)        │  └──────────────────┘       │
│  └──────────────┘   └────────────────────┘                              │
│                                                                         │
│         ┌───────────────────────────────────────────────────┐           │
│         │                  OUTPUT LAYER                      │           │
│         │  ┌─────────────────┐    ┌──────────────────────┐  │           │
│         │  │ ReportGenerator  │    │    BugReporter        │  │           │
│         │  │ (HTML Report +   │    │ (ServiceNow Table API │  │           │
│         │  │  Report Bug UI)  │    │  + Attachment API)    │  │           │
│         │  └─────────────────┘    └──────────────────────┘  │           │
│         └───────────────────────────────────────────────────┘           │
└─────────────────────────────────────────────────────────────────────────┘

         ┌──────────────────┐              ┌──────────────────────┐
         │   ServiceNow     │              │  i18n Global Tracker  │
         │   Test Instance  │              │  (i18ntest.sn.com)    │
         │  (snredecheck)   │              │                       │
         │                  │              │  x_all_language_tra_  │
         │  Pages under     │              │  all_language_tracker  │
         │  test (Arabic,   │              │                       │
         │  Japanese, etc.) │              │  Defects auto-filed   │
         └──────────────────┘              └──────────────────────┘
```

---

## 3. Component Details

### 3.1 Configuration Layer

| Component | File | Responsibility |
|-----------|------|----------------|
| **TestConfig** | `config/TestConfig.java` | Loads all settings from `config.properties` — instance URL, credentials, locale, pages, browser, tracker config |
| **AppConfig** | `app/AppConfig.java` | GUI-mode configuration manager — persists settings to `~/.datechecker/config.properties` |
| **config.properties** | `test/resources/config.properties` | Single source of truth for all runtime parameters |

### 3.2 JavaFX GUI Layer

| Component | File | Responsibility |
|-----------|------|----------------|
| **DateCheckerApp** | `app/DateCheckerApp.java` | JavaFX Application entry point |
| **DateCheckerLauncher** | `app/DateCheckerLauncher.java` | Main class launcher (avoids JavaFX module issues) |
| **MainView** | `app/MainView.java` | Main window layout — tab navigation |
| **ConnectionView** | `app/views/ConnectionView.java` | Instance URL + credential input |
| **ScanConfigView** | `app/views/ScanConfigView.java` | Locale + page selection |
| **ScanRunnerView** | `app/views/ScanRunnerView.java` | Scan progress + live status |
| **ResultsView** | `app/views/ResultsView.java` | Displays scan results + one-click bug reporting |
| **SettingsView** | `app/views/SettingsView.java` | Browser, timeout, tracker settings |
| **ScanService** | `app/ScanService.java` | Orchestrates scanning — login, navigate, scan, report |

### 3.3 Browser Layer

| Component | File | Responsibility |
|-----------|------|----------------|
| **DriverFactory** | `driver/DriverFactory.java` | Creates and manages Chrome/Firefox/Edge WebDriver instances via WebDriverManager |
| **SNLoginPage** | `pages/SNLoginPage.java` | Page Object for ServiceNow login — handles authentication and language preference |

### 3.4 Scanning & Detection Engine

| Component | File | Responsibility |
|-----------|------|----------------|
| **DateFormatChecker** | `scanner/DateFormatChecker.java` | Core engine — scans DOM (including Shadow DOM & iframes), detects 6 types of date format issues using regex + locale-aware validation |
| **DateFormatScanner** | `scanner/DateFormatScanner.java` | JavaScript-based DOM text extractor — traverses shadow roots and iframes |
| **ExtensionContentInjector** | `scanner/ExtensionContentInjector.java` | Injects scanner.js into the page as an alternative scanning approach |
| **scanner.js** | `resources/scanner.js` | Client-side JavaScript scanner for text extraction |

### 3.5 Visual Highlighting

Built into `DateFormatChecker.highlightIssues()`:

```
Issue Detected → Extract Highlight Key → Inject JavaScript
                                              │
                            ┌─────────────────┼──────────────────┐
                            ▼                 ▼                  ▼
                    english-month      date/time values    placeholders
                    "Jun 27"           "2026-06-29"        "YYYY-MM-DD"
                            │                 │                  │
                            └─────────────────┼──────────────────┘
                                              ▼
                              Walk DOM + Shadow DOM
                              Find smallest element containing key
                              Apply red border + box-shadow
```

### 3.6 Screenshot Capture

| Component | File | Responsibility |
|-----------|------|----------------|
| **ScreenshotCapture** | `screenshot/ScreenshotCapture.java` | Full-page screenshot via Chrome DevTools Protocol (CDP) — resizes viewport to full page dimensions, captures, then resets |

```
Page.getLayoutMetrics → Get full page width × height
         ▼
Emulation.setDeviceMetricsOverride → Expand viewport
         ▼
Page.captureScreenshot → Capture entire page
         ▼
Emulation.clearDeviceMetricsOverride → Reset viewport
```

### 3.7 Reporting & Bug Filing

| Component | File | Responsibility |
|-----------|------|----------------|
| **ReportGenerator** | `report/ReportGenerator.java` | Generates self-contained HTML report with embedded screenshots, issue tables, and "Report Bug" buttons |
| **BugReporter** | `report/BugReporter.java` | Java client for ServiceNow Table API — creates defects and attaches screenshots |

### 3.8 Data Model

| Component | File | Fields |
|-----------|------|--------|
| **DateIssue** | `model/DateIssue.java` | `found`, `reason`, `expected`, `suggestion`, `type`, `xpath`, `context`, `pageUrl`, `screenshotPath`, `frameUrl` |

---

## 4. Detection Types

| Issue Type | Pattern | Example |
|------------|---------|---------|
| `non-localized-date` | Date/time not localized for target language | `2026-06-29 02:20:52`, `06/29/2026` |
| `non-localized-placeholder` | Date format pattern not localized | `YYYY-MM-DD HH:mm:ss` |
| `english-month` | English month name found in non-English locale | `Jun 27`, `January 18` |
| `english-weekday` | English weekday name found in non-English locale | `Monday`, `Tue` |
| `wrong-order` | Month before year — wrong order for locale | `3月 2023` instead of `2023年3月` |
| `wrong-format` | Date not localized for target language | English-style date in non-English locale |

**Supported Locales (24):** ar, pt-BR, zh-CN, zh-Hant, cs, nl, fi, fr, fr-CA, de, he, hu, it, ja, ko, nb, pl, pt, ru, es, sv, th, tr + English (base)

**SN Locale Aliases:** fq → fr-CA, zt → zh-Hant, pb → pt-BR

---

## 5. Execution Flow

```
 ┌─────────────┐
 │   START      │
 └──────┬──────┘
        ▼
 ┌──────────────────┐
 │ Load Config       │  config.properties
 │ (locale, pages,   │
 │  credentials)     │
 └──────┬───────────┘
        ▼
 ┌──────────────────┐
 │ Launch Browser    │  Chrome via WebDriverManager
 │ (DriverFactory)   │
 └──────┬───────────┘
        ▼
 ┌──────────────────┐
 │ Login + Set       │  SNLoginPage + User Preference API
 │ Language to       │  sysparm_language=ar
 │ Target Locale     │
 └──────┬───────────┘
        ▼
 ┌──────────────────┐
 │ For each page:    │◀─── DataProvider (from config)
 │                   │
 │  1. Navigate      │
 │  2. Wait for load │
 │  3. Scan DOM      │──── DateFormatChecker.scan()
 │  4. Highlight     │──── Inject JS red borders
 │  5. Screenshot    │──── CDP full-page capture
 │  6. Collect       │──── DateIssue list
 │     issues        │
 └──────┬───────────┘
        ▼
 ┌──────────────────┐
 │ Generate HTML     │  ReportGenerator
 │ Report            │  (embedded screenshots,
 │                   │   Report Bug buttons)
 └──────┬───────────┘
        ▼
 ┌──────────────────┐
 │ Auto-Report Bugs? │── if tracker.auto.report=true
 │ (BugReporter)     │   POST to SN Table API
 │                   │   + attach screenshots
 └──────┬───────────┘
        ▼
 ┌──────────────────┐
 │ Close Browser     │
 │ END               │
 └──────────────────┘
```

---

## 6. Bug Reporting Flow

### From HTML Report (Interactive)

```
User opens HTML report in browser
        │
        ▼
 ┌──────────────────────────────────────────┐
 │  "🐛 Report Bug" button per issue        │
 │  "🐛 Report All Bugs" button in header   │
 └──────────────────┬───────────────────────┘
                    │ onclick
                    ▼
         JavaScript fetch() → POST
         /api/now/table/x_all_language_tra_all_language_tracker
         Authorization: Basic (encoded credentials)
         Body: {
           short_description: "[AR] Wrong date format: ...",
           description: "Full issue details + steps to reproduce",
           priority: 3,
           state: 1
         }
                    │
                    ▼
         ┌────────────────────┐
         │  i18n Global       │
         │  Tracker           │
         │  (i18ntest.sn.com) │
         │                    │
         │  Defect created ✅  │
         └────────────────────┘
```

### Auto-Report (Programmatic)

```
DateFormatTest.tearDown()
        │
        ▼
 BugReporter.reportBug()  ──── POST /api/now/table/{table}
        │
        ▼
 BugReporter.attachScreenshot() ── POST /api/now/attachment/file
```

---

## 7. Technology Stack

| Layer | Technology |
|-------|------------|
| **Language** | Java 17+ |
| **Build** | Maven |
| **Test Framework** | TestNG |
| **Browser Automation** | Selenium WebDriver 4.21 |
| **Driver Management** | WebDriverManager 5.8 |
| **Full-Page Screenshots** | Chrome DevTools Protocol (CDP) |
| **Shadow DOM Traversal** | JavaScript injection via `executeScript()` |
| **Date Locale Validation** | `java.time.format.DateTimeFormatter` + `java.util.Locale` |
| **Pattern Detection** | `java.util.regex.Pattern` |
| **JSON Handling** | Gson 2.11 |
| **Logging** | SLF4J + slf4j-simple |
| **Bug Reporting** | ServiceNow Table API (REST) + Attachment API |
| **GUI** | JavaFX 21 |
| **Report Format** | Self-contained HTML (Base64 embedded screenshots) |

---

## 8. Project Structure

```
Datechecker/
├── pom.xml                          # Maven build config
├── testng.xml                       # TestNG suite definition
├── docs/
│   └── architecture.md              # This file
├── src/
│   ├── main/
│   │   ├── java/com/sn/datechecker/
│   │   │   ├── app/                         # JavaFX GUI application
│   │   │   │   ├── DateCheckerApp.java      # JavaFX Application entry point
│   │   │   │   ├── DateCheckerLauncher.java  # Main class launcher
│   │   │   │   ├── MainView.java            # Main window layout
│   │   │   │   ├── ScanService.java         # Scan orchestrator
│   │   │   │   ├── AppConfig.java           # GUI config manager
│   │   │   │   └── views/
│   │   │   │       ├── ConnectionView.java   # Instance + credentials
│   │   │   │       ├── ScanConfigView.java   # Locale + page selection
│   │   │   │       ├── ScanRunnerView.java   # Scan progress
│   │   │   │       ├── ResultsView.java      # Results + bug reporting
│   │   │   │       └── SettingsView.java     # App settings
│   │   │   ├── config/
│   │   │   │   └── TestConfig.java           # Configuration loader
│   │   │   ├── driver/
│   │   │   │   └── DriverFactory.java        # WebDriver lifecycle
│   │   │   ├── model/
│   │   │   │   └── DateIssue.java            # Issue data model
│   │   │   ├── pages/
│   │   │   │   ├── SNLoginPage.java          # Login page object
│   │   │   │   └── ExtensionPopupPage.java   # Extension popup page object
│   │   │   ├── report/
│   │   │   │   ├── ReportGenerator.java      # HTML report + bug buttons
│   │   │   │   └── BugReporter.java          # SN Table API client
│   │   │   ├── scanner/
│   │   │   │   ├── DateFormatChecker.java    # Core detection engine
│   │   │   │   ├── DateFormatScanner.java    # JS-based DOM scanner
│   │   │   │   └── ExtensionContentInjector.java
│   │   │   └── screenshot/
│   │   │       └── ScreenshotCapture.java    # CDP full-page capture
│   │   └── resources/
│   │       └── scanner.js                    # Client-side text extractor
│   └── test/
│       ├── java/com/sn/datechecker/tests/
│       │   └── DateFormatTest.java           # Test orchestrator
│       └── resources/
│           └── config.properties.template    # Config template
└── target/
    ├── reports/                              # Generated HTML reports
    └── screenshots/                          # Captured page screenshots
```

---

## 9. Key Design Decisions

1. **Shadow DOM traversal** — ServiceNow uses Web Components extensively; the scanner injects JavaScript that walks `shadowRoot` recursively to reach all text nodes
2. **Smallest-element highlighting** — Instead of highlighting large containers, the JS finds the smallest DOM element containing the date text to provide precise visual markers
3. **CDP for screenshots** — Standard Selenium `TakesScreenshot` only captures the viewport; CDP `Page.captureScreenshot` with viewport resizing captures the entire scrollable page
4. **Short highlight keys** — For english-month issues, only the English date portion (e.g., `Jun 27`) is used as the search key, avoiding false matches on large containers
5. **Self-contained HTML reports** — Screenshots are Base64-encoded inline, making reports portable with no external dependencies
6. **Dual bug reporting** — Interactive (from browser via HTML report buttons) and programmatic (auto-report via Java) paths to the Global Tracker
