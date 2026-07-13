# ServiceNow Date Format Checker

A **macOS desktop application** that scans ServiceNow pages for wrong/non-localized date formats. Built with **JavaFX**, **Selenium WebDriver**, and **TestNG**.

## What It Does

Navigates to configured ServiceNow pages, scans all visible text, and detects date formats that don't match the expected locale.

| Issue Detected | Example (locale: `ja`) |
|----------------|----------------------|
| English month/day names in non-English locale | `Feb 2026` → should be `2026年2月` |
| Wrong date component order | `2月 2026` → should be `2026年2月` |
| Hardcoded ISO 8601 dates | `2025-03-18` → should be `2025年3月18日` |
| Hardcoded format patterns | `YYYY-MM-DD` → not locale-aware |
| English leaking into localized UI | `March 18, 2025` → should be `2025年3月18日` |

## Scan Targets

- **DOM text nodes** — all visible text on the page
- **SVG `<text>` elements** — chart axis labels
- **Shadow DOM** — web components (Polaris/Next Experience)
- **Same-origin iframes** — embedded frames (e.g. `gsft_main`)
- **Form fields** — input values, placeholders

## Features

- **GUI Desktop App** — native macOS look with sidebar navigation
- **Red highlight boxes** — issues highlighted directly on the page with red borders
- **Smart page load wait** — waits for Polaris/React content to fully render
- **5-step language switching** — reliable language change for Polaris pages
- **HTML reports** — detailed reports with screenshots
- **Bug reporting** — pre-populated forms on i18n Global Tracker
- **30+ locales** — Japanese, Chinese, Korean, German, French, Spanish, and more

---

## Prerequisites

- **Java 21+** — [Download Temurin](https://adoptium.net/temurin/releases/?version=21)
- **Maven 3.8+** — `brew install maven`
- **Google Chrome** installed

---

## Quick Start (from source)

### 1. Clone the repo

```bash
git clone https://github.com/sristi2025may/Datechecker.git
cd Datechecker
```

### 2. Set up credentials

```bash
cp src/test/resources/config.properties.template src/test/resources/config.properties
```

Edit `src/test/resources/config.properties` with your ServiceNow credentials:

```properties
servicenow.url=https://yourinstance.service-now.com
servicenow.username=your_username
servicenow.password=your_password

tracker.url=https://i18ntest.service-now.com
tracker.username=your_tracker_username
tracker.password=your_tracker_password
```

### 3. Run the app

```bash
mvn javafx:run -DskipTests
```

The app window will open. Use the sidebar to:
1. **Connection** — verify/update instance URL and credentials
2. **Scan Config** — set locale, pages to scan, browser settings
3. **Run Scan** — start the scan and watch live progress
4. **Results** — view issues table, HTML report, and report bugs

---

## Quick Start (JAR — no Maven needed)

If someone just wants to run the app without cloning the repo:

### 1. Build the JAR (on your machine)

```bash
cd Datechecker
mvn package -DskipTests
```

### 2. Share the JAR file

Send `target/sn-date-checker-selenium-1.0-SNAPSHOT.jar` (~81 MB) to your teammate.

### 3. Run the JAR

```bash
java -jar sn-date-checker-selenium-1.0-SNAPSHOT.jar
```

Enter credentials in the **Connection** screen on first launch.

---

## Running Tests (CLI mode)

You can also run scans via TestNG without the GUI:

```bash
mvn clean test
```

Or for a specific locale:

```bash
mvn test -Dsn.locale=de
```

Reports are saved to `target/reports/`.

---

## Project Structure

```
Datechecker/
├── pom.xml                                    # Maven config (JavaFX, Selenium, TestNG)
├── testng.xml                                 # TestNG suite definition
├── src/
│   ├── main/
│   │   ├── java/com/sn/datechecker/
│   │   │   ├── app/                           # Desktop app (JavaFX)
│   │   │   │   ├── DateCheckerApp.java        #   Main application class
│   │   │   │   ├── DateCheckerLauncher.java   #   JAR launcher
│   │   │   │   ├── AppConfig.java             #   Persistent config (~/.datechecker/)
│   │   │   │   ├── MainView.java              #   Sidebar + content layout
│   │   │   │   ├── ScanService.java           #   Background scan engine
│   │   │   │   └── views/                     #   UI screens
│   │   │   │       ├── ConnectionView.java    #     Instance & tracker setup
│   │   │   │       ├── ScanConfigView.java    #     Locale, pages, browser config
│   │   │   │       ├── ScanRunnerView.java    #     Start/stop, progress, logs
│   │   │   │       ├── ResultsView.java       #     Issues table, report, bug reporting
│   │   │   │       └── SettingsView.java      #     Directories, preferences
│   │   │   ├── config/TestConfig.java         # Loads config.properties
│   │   │   ├── driver/DriverFactory.java      # WebDriver setup (Chrome/Firefox/Edge)
│   │   │   ├── model/DateIssue.java           # Issue data model
│   │   │   ├── report/ReportGenerator.java    # HTML report generator
│   │   │   └── scanner/                       # Core scanning engine
│   │   │       ├── DateFormatChecker.java     #   Date format detection + highlighting
│   │   │       ├── DateFormatScanner.java     #   Full page scanner with retries
│   │   │       └── ExtensionContentInjector.java # Chrome extension injection
│   │   └── resources/
│   │       └── styles/app.css                 # macOS-themed UI styles
│   └── test/
│       ├── java/com/sn/datechecker/tests/
│       │   └── DateFormatTest.java            # TestNG test class
│       └── resources/
│           ├── config.properties              # Your config (gitignored)
│           └── config.properties.template     # Template for team members
└── target/
    └── reports/                               # Generated HTML reports
```

## Supported Locales (30+)

Japanese, Chinese (Simplified & Traditional), Korean, German, French, Spanish, Italian, Portuguese, Dutch, Polish, Russian, Swedish, Finnish, Norwegian, Danish, Czech, Hungarian, Romanian, Greek, Turkish, Ukrainian, Arabic, Hebrew, Hindi, Thai, and ServiceNow special codes (`fq`, `zt`, `pb`).

## How It Works

1. **Launch Chrome** via Selenium WebDriver (with optional extension)
2. **Log in** to ServiceNow with provided credentials
3. **Set language** using 5-step process (classic UI + REST API + user preference)
4. For each configured page:
   - Navigate and **wait for full page load** (DOM + AJAX + content stabilization)
   - **Scan** all visible text nodes, SVG, shadow DOM, and iframes
   - **Detect** non-localized date formats using locale-specific patterns
   - **Highlight** issues with red border boxes on the page
   - **Take screenshot** with highlights visible
5. **Generate HTML report** with all issues and screenshots
6. **Report bugs** to i18n Global Tracker with pre-populated form fields
