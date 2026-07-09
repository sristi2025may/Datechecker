# ServiceNow Date Format Checker — Chrome Extension (v2)

Detects wrong/non-localized date formats on ServiceNow pages, including **Highcharts SVG chart axis labels**.

## What it detects

| Issue | Example (locale: `ja`) |
|-------|----------------------|
| English month/day names in non-English locale | `Feb 2026` → should be `2026年2月` |
| Wrong date component order | `2月 2026` → should be `2026年2月` |
| English format leaking into localized UI | `March 18, 2025` → should be `2025年3月18日` |

## Supported scan targets

- **DOM text nodes** — all visible text on the page
- **SVG `<text>` elements** — Highcharts/D3 chart axis labels, tooltips
- **Shadow DOM** — web components used in ServiceNow's modern UI
- **Same-origin iframes** — embedded frames within the instance

## Supported locales (30+)

Japanese, Chinese (Simplified & Traditional), Korean, German, French, Spanish, Italian, Portuguese, Dutch, Polish, Russian, Swedish, Finnish, Norwegian, Danish, Czech, Hungarian, Romanian, Greek, Turkish, Ukrainian, Arabic, Hebrew, Hindi, Thai, and ServiceNow special codes (`fq`, `zt`, `pb`).

## Install

1. Clone or download this folder (`sn-date-checker/`)
2. Open Chrome → `chrome://extensions`
3. Enable **Developer mode** (top-right toggle)
4. Click **Load unpacked** → select the `sn-date-checker` folder
5. Pin the extension icon in the toolbar

## Usage

1. Navigate to your ServiceNow instance (e.g. `https://yourco.service-now.com/...`)
2. Click the extension icon in the toolbar
3. Select the **target locale** (e.g. Japanese)
4. Click **🔍 Scan this page**
5. Wrong dates are highlighted in red with `i18n` badges
6. A panel in the bottom-right lists all issues with expected corrections
7. Click any issue row → scrolls to and pulses the offending element

## Auto-rescan

The extension watches for DOM changes (e.g. chart re-renders, SPA navigation) and automatically rescans after 1.5 seconds of inactivity.

## Limitations

- **Canvas-rendered text**: If a chart draws labels on a `<canvas>` (not SVG), the extension cannot read those pixels. Most ServiceNow charts use Highcharts (SVG), so this is rare.
- **Cross-origin iframes**: Cannot scan iframes from different origins due to browser security.
- **Numeric-only dates**: Formats like `15/02/2026` are harder to validate without knowing the expected locale pattern — the tool focuses on detecting English text leaking into non-English locales.
