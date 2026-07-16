# 🛠️ Date Format Checker — Team Setup Guide

**Send this document along with the JAR file to your teammates.**

---

## What You'll Need

| Requirement | Details |
|-------------|---------|
| **Java 21** | Required to run the app |
| **Google Chrome** | The app uses Chrome to scan ServiceNow pages |
| **The JAR file** | `sn-date-checker-selenium-1.0-SNAPSHOT.jar` (~81 MB) |

---

## Step 1: Install Java 21

### macOS
```bash
brew install --cask temurin@21
```
Or download manually: https://adoptium.net/temurin/releases/?version=21

### Verify Java is installed
Open Terminal and run:
```bash
java -version
```
You should see output like:
```
openjdk version "21.x.x" ...
```

---

## Step 2: Install Google Chrome

Download from https://www.google.com/chrome/ if you don't already have it.

---

## Step 3: Get the JAR file

Your teammate will share the file `sn-date-checker-selenium-1.0-SNAPSHOT.jar`.

Save it to a folder you can easily find, for example:
```
~/Desktop/DateChecker/
```

---

## Step 4: Run the app

Open **Terminal** and run:

```bash
java -jar ~/Desktop/DateChecker/sn-date-checker-selenium-1.0-SNAPSHOT.jar
```

> **Tip:** Replace the path above with wherever you saved the JAR file.

A desktop window will open with a sidebar on the left.

---

## Step 5: Set up your connection (first time only)

### 5a. ServiceNow Instance
Click **"Connection"** in the sidebar and fill in:

| Field | Value |
|-------|-------|
| **Instance URL** | `https://snredecheck.service-now.com` (or your instance) |
| **Username** | Your ServiceNow username |
| **Password** | Your ServiceNow password |

### 5b. Tracker (for bug reporting)
Scroll down to the **Tracker** section:

| Field | Value |
|-------|-------|
| **Tracker URL** | `https://i18ntest.service-now.com` |
| **Tracker Username** | Your tracker username |
| **Tracker Password** | Your tracker password |
| **Tracker Table** | `u_i18n_global_tracker` |

Click **Save** — your credentials are stored locally in `~/.datechecker/config.properties` and will be remembered for next time.

---

## Step 6: Configure your scan

Click **"Scan Config"** in the sidebar:

| Setting | What to enter |
|---------|---------------|
| **Locale** | The language code to test (e.g., `ja`, `de`, `fr`, `ko`, `zh-CN`, `es-MX`) |
| **Pages to scan** | Paste one ServiceNow page URL per line (e.g., `https://snredecheck.service-now.com/now/nav/ui/home`) |
| **Headless mode** | Check this to run Chrome without showing the browser window |
| **Scan delay (ms)** | Extra wait time for slow pages (default 3000) |

---

## Step 7: Run the scan

Click **"Run Scan"** in the sidebar, then click **"▶ Start Scan"**.

What happens:
1. Chrome opens automatically
2. Logs in with your credentials
3. Changes the language to your selected locale
4. Visits each page and scans for date format issues
5. Highlights issues with **red boxes** on the page
6. Takes screenshots with the highlights visible
7. Generates an HTML report

Watch the **live log** to see progress.

---

## Step 8: View results

Click **"Results"** in the sidebar:

- **Issues table** — shows all detected date format issues with:
  - Found text (the wrong date)
  - Expected format (what it should look like)
  - Issue type
  - Fix suggestion (API code to fix it)

- **Open HTML Report** — opens the full report in your browser

- **Report Selected Bug** — select a row and click to open a pre-populated bug form on the i18n Global Tracker. The screenshot is copied to your clipboard — paste it (Cmd+V) into the form.

- **Report All Bugs** — opens every issue in a separate tracker tab with all fields pre-populated.

---

## Supported Locales (ServiceNow Language Pack — 24 Languages)

| Code | Language | Code | Language |
|------|----------|------|----------|
| `ar` | Arabic | `ja` | Japanese |
| `pt-BR` | Brazilian Portuguese | `ko` | Korean |
| `zh-CN` | Chinese (Simplified) | `nb` | Norwegian |
| `zh-Hant` | Chinese (Traditional) | `pl` | Polish |
| `cs` | Czech | `pt` | Portuguese |
| `nl` | Dutch | `ru` | Russian |
| `fi` | Finnish | `es` | Spanish |
| `fr` | French | `sv` | Swedish |
| `fr-CA` | French Canadian | `th` | Thai |
| `de` | German | `tr` | Turkish |
| `he` | Hebrew | | |
| `hu` | Hungarian | | |
| `it` | Italian | | |

**ServiceNow alias codes also accepted:** `fq` → French Canadian, `zt` → Traditional Chinese, `pb` → Brazilian Portuguese

---

## Troubleshooting

### "java: command not found"
Java 21 is not installed or not in your PATH. Re-install Java 21 and restart Terminal.

### App window doesn't open
Make sure you're using Java 21 (not Java 8 or 11):
```bash
java -version
```

### Chrome doesn't start / crashes
- Make sure Google Chrome is installed at the default location
- The app automatically downloads the matching ChromeDriver — you don't need to install it manually

### Scan finds 0 issues
- Make sure the **locale** is set to a non-English language (the scanner detects dates that should be localized)
- Make sure the page URLs are correct and accessible with your credentials
- The page might need more load time — try increasing the **scan delay**

### "Step is still running" / scan hangs
- Some pages take a long time to load. Wait a bit longer.
- If it stays stuck for more than 2 minutes, close the app and restart.

---

## Quick Reference

```bash
# Run the app
java -jar sn-date-checker-selenium-1.0-SNAPSHOT.jar

# That's it! Everything else is done through the GUI.
```

---

**Questions?** Contact the team lead or check the [GitHub repo](https://github.com/sristi2025may/Datechecker).
