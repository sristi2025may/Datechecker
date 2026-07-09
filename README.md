# ServiceNow Date Format Checker — Selenium Automation Framework

Automated testing framework that scans ServiceNow pages for wrong/non-localized date formats using **Selenium WebDriver** and **TestNG**.

## What It Does

Navigates to configured ServiceNow pages, injects a JavaScript scanner (ported from the Chrome extension), and validates that all visible dates match the expected locale format.

| Issue Detected | Example (locale: `ja`) |
|----------------|----------------------|
| English month/day names in non-English locale | `Feb 2026` → should be `2026年2月` |
| Wrong date component order | `2月 2026` → should be `2026年2月` |
| Hardcoded ISO 8601 dates | `2025-03-18` → should be `2025年3月18日` |
| Hardcoded format patterns | `YYYY-MM-DD` → not locale-aware |
| English leaking into localized UI | `March 18, 2025` → should be `2025年3月18日` |

## Scan Targets

- **DOM text nodes** — all visible text on the page
- **SVG `<text>` elements** — Highcharts/D3 chart axis labels
- **Shadow DOM** — web components
- **Same-origin iframes** — embedded frames (e.g. `gsft_main`)
- **Form fields** — input values, placeholders
- **Element attributes** — `title`, `aria-label`, `data-format`

## Project Structure

```
Datechecker/
├── pom.xml                          # Maven config (Selenium, TestNG, WebDriverManager)
├── testng.xml                       # TestNG suite definition
├── src/
│   ├── main/
│   │   ├── java/com/sn/datechecker/
│   │   │   ├── config/TestConfig.java       # Loads config.properties
│   │   │   ├── driver/DriverFactory.java    # WebDriver setup (Chrome/Firefox/Edge)
│   │   │   ├── model/DateIssue.java         # Issue data model
│   │   │   ├── pages/SNLoginPage.java       # ServiceNow login page object
│   │   │   ├── report/ReportGenerator.java  # HTML report generator
│   │   │   └── scanner/DateFormatScanner.java # Core scanner (JS injection)
│   │   └── resources/
│   │       └── scanner.js                   # Date validation JS (injected via Selenium)
│   └── test/
│       ├── java/com/sn/datechecker/tests/
│       │   └── DateFormatTest.java          # TestNG test class
│       └── resources/
│           └── config.properties            # Test configuration
└── target/reports/                          # Generated HTML reports
```

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Chrome** (or Firefox/Edge) installed

## Quick Start

### 1. Configure

Edit `src/test/resources/config.properties`:

```properties
# Your ServiceNow instance
sn.instance.url=https://yourinstance.service-now.com
sn.username=admin
sn.password=yourpassword

# Locale to validate against
sn.locale=ja

# Pages to scan
sn.pages=/now/nav/ui/classic/params/target/incident_list.do,\
         /now/nav/ui/classic/params/target/change_request_list.do

# Browser settings
browser=chrome
browser.headless=false
```

### 2. Run

```bash
mvn clean test
```

Or run a specific locale:

```bash
mvn test -Dsn.locale=de
```

### 3. View Report

After the test run, open the HTML report:

```
target/reports/date-check-report_ja_20260624_163000.html
```

## Supported Locales (30+)

Japanese, Chinese (Simplified & Traditional), Korean, German, French, Spanish, Italian, Portuguese, Dutch, Polish, Russian, Swedish, Finnish, Norwegian, Danish, Czech, Hungarian, Romanian, Greek, Turkish, Ukrainian, Arabic, Hebrew, Hindi, Thai, and ServiceNow special codes (`fq`, `zt`, `pb`).

## How It Works

1. **DriverFactory** creates a Selenium WebDriver instance
2. **SNLoginPage** handles ServiceNow authentication
3. For each configured page URL:
   - Navigate to the page and wait for dynamic content
   - **DateFormatScanner** injects `scanner.js` via `executeScript()`
   - The JS scans DOM text, SVG, shadow DOM, iframes, and form fields
   - Results are returned as JSON and deserialized into `DateIssue` objects
4. **ReportGenerator** produces a styled HTML report
5. TestNG asserts that zero issues are found (test fails if issues exist)

## CI/CD Integration

Run in headless mode for CI pipelines:

```properties
browser.headless=true
```

```bash
mvn clean test -Dbrowser.headless=true
```
