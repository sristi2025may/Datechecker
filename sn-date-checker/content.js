/**
 * ServiceNow Date Format Checker — Content Script (v2)
 *
 * Scans the page for date strings that don't match the expected locale format.
 * Covers: DOM text nodes, SVG <text> (Highcharts axis labels), shadow DOM, iframes.
 */

(() => {
  "use strict";

  /* ──────────────── Locale & Date Pattern Helpers ──────────────── */

  // ServiceNow language codes that don't map 1:1 to BCP-47
  const SN_TO_BCP47 = {
    fq: "fr-CA",
    zt: "zh-Hant",
    pb: "pt-BR",
  };

  function toBcp47(code) {
    return SN_TO_BCP47[code] || code;
  }

  // English month names & abbreviations (used to detect English leaking)
  const EN_MONTHS_LONG = [
    "january","february","march","april","may","june",
    "july","august","september","october","november","december",
  ];
  const EN_MONTHS_SHORT = [
    "jan","feb","mar","apr","may","jun",
    "jul","aug","sep","oct","nov","dec",
  ];
  const EN_DAYS_LONG = [
    "sunday","monday","tuesday","wednesday","thursday","friday","saturday",
  ];
  const EN_DAYS_SHORT = ["sun","mon","tue","wed","thu","fri","sat"];

  // Regex to find candidate date-like strings in text
  // Matches things like: "Feb 2026", "2026年2月", "18.03.2025", "03/18/2025", "March 18, 2025", etc.
  const DATE_CANDIDATE_RE = new RegExp(
    "(?:" +
      // Month name (English or CJK) + optional day + year
      "(?:(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")[\\.\\s,]*\\d{1,2}[,\\s]*\\d{2,4})" +
      "|" +
      // Day? + Month name (English) + year?
      "(?:\\d{1,2}[\\s,]*(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")[\\.,\\s]*\\d{2,4})" +
      "|" +
      // Month name only + 4-digit year (e.g. "Feb 2026", "March 2026")
      "(?:(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")\\s+\\d{4})" +
      "|" +
      // 4-digit year + Month name (e.g. "2026 February")
      "(?:\\d{4}\\s+(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + "))" +
      "|" +
      // CJK year-month-day: 2026年2月15日 or 2026年2月
      "(?:\\d{4}年\\d{1,2}月(?:\\d{1,2}日)?)" +
      "|" +
      // CJK month-year (wrong order): 2月 2026
      "(?:\\d{1,2}月\\s*\\d{4})" +
      "|" +
      // Month name + day only (no year): "June 14", "Mar 15"
      "(?:(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")\\s+\\d{1,2})(?!\\s*[,.]?\\s*\\d)" +
      "|" +
      // Day + month name only (no year): "14 June", "15 Mar"
      "(?:\\d{1,2}\\s+(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + "))(?!\\s*[,.]?\\s*\\d)" +
      "|" +
      // ISO datetime: "2023-01-18 15:55:59" or "2023-01-18T15:55:59"
      "(?:\\d{4}-\\d{2}-\\d{2}[\\sT]\\d{1,2}:\\d{2}(?::\\d{2})?)" +
      "|" +
      // Numeric: DD/MM/YYYY, MM/DD/YYYY, DD.MM.YYYY, DD-MM-YYYY, YYYY/MM/DD, YYYY-MM-DD
      "(?:\\d{1,4}[/\\-\\.]\\d{1,2}[/\\-\\.]\\d{1,4})" +
      "|" +
      // Literal format patterns: "YYYY-MM-DD", "YYYY-MM-DD HH:mm:ss", "DD/MM/YYYY"
      "(?:(?:[Yy]{2,4}[-/\\.][Mm]{1,4}[-/\\.][Dd]{1,4}|[Dd]{1,4}[-/\\.][Mm]{1,4}[-/\\.][Yy]{2,4}|[Mm]{1,4}[-/\\.][Dd]{1,4}[-/\\.][Yy]{2,4})(?:[\\sT][Hh]{1,2}:[Mm]{1,2}(?::[Ss]{1,2})?)?)" +
      "|" +
      // Standalone English day names: "Sunday", "Mon", etc. (word boundaries to avoid partial matches)
      "(?:\\b(?:" + EN_DAYS_LONG.join("|") + "|" + EN_DAYS_SHORT.join("|") + ")\\b)" +
      "|" +
      // Standalone English month names: "January", "Feb", etc.
      "(?:\\b(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")\\b)" +
    ")",
    "gi"
  );

  // Anchored regex for literal format pattern strings
  const FORMAT_STR_RE = /^(?:[Yy]{2,4}[-/.][Mm]{1,4}[-/.][Dd]{1,4}|[Dd]{1,4}[-/.][Mm]{1,4}[-/.][Yy]{2,4}|[Mm]{1,4}[-/.][Dd]{1,4}[-/.][Yy]{2,4})(?:[\sT][Hh]{1,2}:[Mm]{1,2}(?::[Ss]{1,2})?)?$/;

  /* ──────────────── Reference Format Generation ──────────────── */

  /**
   * For a given locale, generate a set of "correct" date string examples
   * covering recent years/months so we can compare against found strings.
   */
  function buildReferenceFormats(locale) {
    const bcp = toBcp47(locale);
    const styles = ["full", "long", "medium", "short"];
    const refs = new Map(); // canonical lowercase → { style, formatted }

    // Generate references for several sample dates
    const sampleDates = [];
    for (let y = 2024; y <= 2027; y++) {
      for (let m = 0; m < 12; m++) {
        sampleDates.push(new Date(y, m, 1));
        sampleDates.push(new Date(y, m, 15));
      }
    }

    for (const d of sampleDates) {
      for (const style of styles) {
        try {
          // Date only
          const fd = new Intl.DateTimeFormat(bcp, { dateStyle: style }).format(d);
          refs.set(fd.toLowerCase().trim(), { style, formatted: fd, date: d });
        } catch (_) { /* locale/style combo unsupported */ }
        try {
          // Year + month only (common in chart axes)
          const fym = new Intl.DateTimeFormat(bcp, { year: "numeric", month: "long" }).format(d);
          refs.set(fym.toLowerCase().trim(), { style: "year-month-long", formatted: fym, date: d });
        } catch (_) {}
        try {
          const fym2 = new Intl.DateTimeFormat(bcp, { year: "numeric", month: "short" }).format(d);
          refs.set(fym2.toLowerCase().trim(), { style: "year-month-short", formatted: fym2, date: d });
        } catch (_) {}
        try {
          const fym3 = new Intl.DateTimeFormat(bcp, { year: "numeric", month: "numeric" }).format(d);
          refs.set(fym3.toLowerCase().trim(), { style: "year-month-numeric", formatted: fym3, date: d });
        } catch (_) {}
      }
    }
    return refs;
  }

  /**
   * Given a found date string and the target locale, determine if it's wrong.
   * Returns { isWrong, reason, expected } or null if not a date.
   */
  function validateDateString(found, locale, referenceFormats) {
    const bcp = toBcp47(locale);
    const lower = found.toLowerCase().trim();
    const isEnglishLocale = bcp.startsWith("en");

    // 1. If found text exactly matches a reference format → it's correct
    if (referenceFormats.has(lower)) {
      return null; // correct
    }

    // 2. Check if English month/day names appear in a non-English locale
    if (!isEnglishLocale) {
      const allEnglish = [...EN_MONTHS_LONG, ...EN_MONTHS_SHORT, ...EN_DAYS_LONG, ...EN_DAYS_SHORT];
      for (const en of allEnglish) {
        if (lower.includes(en)) {
          // Try to extract a date from this English string and show what it should be
          const expected = tryReformatToLocale(found, bcp);
          return {
            isWrong: true,
            reason: `English text "${en}" found — locale is ${bcp}`,
            expected: expected || `(use Intl.DateTimeFormat with locale '${bcp}')`,
            suggestion: `Server: gd.getDisplayValueLang("long", "${bcp}")\nClient: new Intl.DateTimeFormat("${bcp}", { dateStyle: "long" }).format(date)`,
          };
        }
      }
    }

    // 3. Check CJK wrong order: e.g. "2月 2026" instead of "2026年2月"
    const cjkWrongOrder = found.match(/^(\d{1,2})月\s*(\d{4})$/);
    if (cjkWrongOrder) {
      const month = parseInt(cjkWrongOrder[1], 10);
      const year = parseInt(cjkWrongOrder[2], 10);
      if (month >= 1 && month <= 12 && year >= 1900 && year <= 2100) {
        const d = new Date(year, month - 1, 1);
        const expected = new Intl.DateTimeFormat(bcp, { year: "numeric", month: "long" }).format(d);
        return {
          isWrong: true,
          reason: `Wrong order — month before year`,
          expected,
          suggestion: `Server: gd.getDisplayValueLang("long", "${bcp}")\nClient: new Intl.DateTimeFormat("${bcp}", { year: "numeric", month: "long" }).format(date)`,
        };
      }
    }

    // 4. If text matches a "Month YYYY" pattern in English for a non-English locale
    const monthYearEn = found.match(
      new RegExp("^(" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")\\s+(\\d{4})$", "i")
    );
    if (monthYearEn && !isEnglishLocale) {
      const monthIdx = EN_MONTHS_LONG.indexOf(monthYearEn[1].toLowerCase());
      const monthIdxShort = EN_MONTHS_SHORT.indexOf(monthYearEn[1].toLowerCase());
      const mi = monthIdx >= 0 ? monthIdx : monthIdxShort;
      const year = parseInt(monthYearEn[2], 10);
      if (mi >= 0) {
        const d = new Date(year, mi, 1);
        const expected = new Intl.DateTimeFormat(bcp, { year: "numeric", month: "long" }).format(d);
        return {
          isWrong: true,
          reason: `English month name in ${bcp} locale`,
          expected,
          suggestion: `Server: gd.getDisplayValueLang("long", "${bcp}")\nClient: new Intl.DateTimeFormat("${bcp}", { dateStyle: "long" }).format(date)`,
        };
      }
    }

    // 5. "Month Day" or "Day Month" without year in non-English locale: "June 14", "15 Mar"
    if (!isEnglishLocale) {
      const mdPattern = found.match(
        new RegExp("^(" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")\\s+(\\d{1,2})$", "i")
      );
      const dmPattern = found.match(
        new RegExp("^(\\d{1,2})\\s+(" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")$", "i")
      );
      const mp = mdPattern || dmPattern;
      if (mp) {
        const monthStr = mdPattern ? mp[1] : mp[2];
        const dayStr = mdPattern ? mp[2] : mp[1];
        const mi = EN_MONTHS_LONG.indexOf(monthStr.toLowerCase());
        const miS = EN_MONTHS_SHORT.indexOf(monthStr.toLowerCase());
        const idx = mi >= 0 ? mi : miS;
        const day = parseInt(dayStr, 10);
        if (idx >= 0 && day >= 1 && day <= 31) {
          const d = new Date(new Date().getFullYear(), idx, day);
          const expected = new Intl.DateTimeFormat(bcp, { month: "long", day: "numeric" }).format(d);
          return {
            isWrong: true,
            reason: `English date "${found}" in ${bcp} locale`,
            expected,
            suggestion: `Server: gd.getDisplayValueLang("long", "${bcp}")\nClient: new Intl.DateTimeFormat("${bcp}", { dateStyle: "long" }).format(date)`,
          };
        }
      }
    }

    // 8. Standalone English day names: "Sunday", "Mon", etc.
    const dayLongIdx = EN_DAYS_LONG.indexOf(lower);
    const dayShortIdx = EN_DAYS_SHORT.indexOf(lower);
    const dayIdx = dayLongIdx >= 0 ? dayLongIdx : dayShortIdx;
    if (dayIdx >= 0 && !isEnglishLocale) {
      // Generate expected localized day name
      // Use a known Sunday (Jan 4, 2026 is a Sunday) + offset
      const refSunday = new Date(2026, 0, 4); // Sunday
      const d = new Date(2026, 0, 4 + dayIdx);
      const isLong = dayLongIdx >= 0;
      const expected = new Intl.DateTimeFormat(bcp, { weekday: isLong ? "long" : "short" }).format(d);
      return {
        isWrong: true,
        reason: `English day name "${found}" in ${bcp} locale`,
        expected,
        suggestion: "Client: new Intl.DateTimeFormat(\"" + bcp + "\", { weekday: \"" + (isLong ? "long" : "short") + "\" }).format(date)",
      };
    }

    // 9. Standalone English month names: "January", "Feb", etc.
    const monthLongIdx = EN_MONTHS_LONG.indexOf(lower);
    const monthShortIdx = EN_MONTHS_SHORT.indexOf(lower);
    const monthIdx = monthLongIdx >= 0 ? monthLongIdx : monthShortIdx;
    if (monthIdx >= 0 && !isEnglishLocale) {
      const d = new Date(2026, monthIdx, 1);
      const isLong = monthLongIdx >= 0;
      const expected = new Intl.DateTimeFormat(bcp, { month: isLong ? "long" : "short" }).format(d);
      return {
        isWrong: true,
        reason: `English month name "${found}" in ${bcp} locale`,
        expected,
        suggestion: "Client: new Intl.DateTimeFormat(\"" + bcp + "\", { month: \"" + (isLong ? "long" : "short") + "\" }).format(date)",
      };
    }

    // 7. Literal date format pattern strings: "YYYY-MM-DD", "YYYY-MM-DD HH:mm:ss"
    if (FORMAT_STR_RE.test(found)) {
      const sample = new Date(2026, 1, 15, 14, 30, 0);
      const hasTime = /[Hh]{1,2}:[Mm]{1,2}/.test(found);
      let expected;
      if (hasTime) {
        expected = new Intl.DateTimeFormat(bcp, { dateStyle: "long", timeStyle: "short" }).format(sample);
      } else {
        expected = new Intl.DateTimeFormat(bcp, { dateStyle: "long" }).format(sample);
      }
      return {
        isWrong: true,
        reason: `Hardcoded format pattern "${found}" — not locale-aware`,
        expected: `e.g. ${expected}`,
        suggestion: hasTime
          ? `Server: gdt.getDisplayValueLang("long")\nClient: new Intl.DateTimeFormat("${bcp}", { dateStyle: "long", timeStyle: "short" }).format(date)`
          : `Server: gd.getDisplayValueLang("long")\nClient: new Intl.DateTimeFormat("${bcp}", { dateStyle: "long" }).format(date)`,
      };
    }

    // 6. Hardcoded ISO 8601 format: YYYY-MM-DD or YYYY-MM-DD HH:MM:SS
    const isoMatch = found.match(/^(\d{4})-(\d{2})-(\d{2})(?:[\sT](\d{1,2}):(\d{2})(?::(\d{2}))?)?$/);
    if (isoMatch) {
      const y = parseInt(isoMatch[1], 10);
      const mo = parseInt(isoMatch[2], 10);
      const dy = parseInt(isoMatch[3], 10);
      if (y >= 1900 && y <= 2100 && mo >= 1 && mo <= 12 && dy >= 1 && dy <= 31) {
        const date = new Date(y, mo - 1, dy);
        const hasTime = !!isoMatch[4];
        if (hasTime) {
          // Date+time ISO is always hardcoded — never a valid locale format
          date.setHours(
            parseInt(isoMatch[4], 10),
            parseInt(isoMatch[5], 10),
            isoMatch[6] ? parseInt(isoMatch[6], 10) : 0
          );
          const expected = new Intl.DateTimeFormat(bcp, { dateStyle: "long", timeStyle: "short" }).format(date);
          return {
            isWrong: true,
            reason: `Hardcoded ISO 8601 datetime — use getDisplayValueLang() or Intl.DateTimeFormat`,
            expected,
            suggestion: `Server: gdt.getDisplayValueLang("long")\nClient: new Intl.DateTimeFormat("${bcp}", { dateStyle: "long", timeStyle: "short" }).format(date)`,
          };
        } else {
          // Date-only: check if any locale style naturally produces this format
          const styles = ["full", "long", "medium", "short"];
          let isLocaleFormat = false;
          for (const s of styles) {
            try {
              const fmt = new Intl.DateTimeFormat(bcp, { dateStyle: s }).format(date);
              if (fmt.toLowerCase().trim() === lower) { isLocaleFormat = true; break; }
            } catch (_) {}
          }
          if (!isLocaleFormat) {
            const expected = new Intl.DateTimeFormat(bcp, { dateStyle: "long" }).format(date);
            return {
              isWrong: true,
              reason: `Hardcoded ISO 8601 date — use getDisplayValueLang() or Intl.DateTimeFormat`,
              expected,
              suggestion: `Server: gd.getDisplayValueLang("long")\nClient: new Intl.DateTimeFormat("${bcp}", { dateStyle: "long" }).format(date)`,
            };
          }
        }
      }
    }

    // Not clearly wrong or not a recognizable date
    return null;
  }

  /**
   * Try to parse an English date string and reformat it in the target locale.
   */
  function tryReformatToLocale(engStr, bcp) {
    try {
      // Try native Date parse (works for many English formats)
      const d = new Date(engStr);
      if (!isNaN(d.getTime())) {
        return new Intl.DateTimeFormat(bcp, { year: "numeric", month: "long", day: "numeric" }).format(d);
      }
      // Try "Month YYYY" pattern
      const m = engStr.match(
        new RegExp("^(" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")\\s+(\\d{4})$", "i")
      );
      if (m) {
        const mi = EN_MONTHS_LONG.indexOf(m[1].toLowerCase());
        const miS = EN_MONTHS_SHORT.indexOf(m[1].toLowerCase());
        const idx = mi >= 0 ? mi : miS;
        if (idx >= 0) {
          const d2 = new Date(parseInt(m[2], 10), idx, 1);
          return new Intl.DateTimeFormat(bcp, { year: "numeric", month: "long" }).format(d2);
        }
      }
    } catch (_) {}
    return null;
  }

  /* ──────────────── DOM Scanning ──────────────── */

  /**
   * Walk all text nodes in a root element and collect date candidates.
   */
  function collectTextNodeCandidates(root) {
    const results = [];
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
    let node;
    while ((node = walker.nextNode())) {
      // Skip script/style/hidden
      const parent = node.parentElement;
      if (!parent) continue;
      const tag = parent.tagName;
      if (tag === "SCRIPT" || tag === "STYLE" || tag === "NOSCRIPT") continue;
      if (parent.closest("#sn-date-panel")) continue;

      const text = node.textContent.trim();
      if (text.length < 3 || text.length > 100) continue;

      let match;
      DATE_CANDIDATE_RE.lastIndex = 0;
      while ((match = DATE_CANDIDATE_RE.exec(text)) !== null) {
        results.push({
          text: match[0],
          node,
          element: parent,
          type: "dom",
        });
      }
    }
    return results;
  }

  /**
   * Scan SVG <text> elements (Highcharts axis labels).
   */
  function collectSvgTextCandidates(root) {
    const results = [];
    const svgTexts = root.querySelectorAll("svg text, svg tspan");
    for (const el of svgTexts) {
      const text = el.textContent.trim();
      if (text.length < 3 || text.length > 100) continue;

      let match;
      DATE_CANDIDATE_RE.lastIndex = 0;
      while ((match = DATE_CANDIDATE_RE.exec(text)) !== null) {
        results.push({
          text: match[0],
          node: el,
          element: el,
          type: "svg",
        });
      }
    }
    return results;
  }

  /**
   * Recursively scan shadow DOMs.
   */
  function collectShadowDomCandidates(root) {
    let results = [];
    const allEls = root.querySelectorAll("*");
    for (const el of allEls) {
      if (el.shadowRoot) {
        results = results.concat(collectTextNodeCandidates(el.shadowRoot));
        results = results.concat(collectSvgTextCandidates(el.shadowRoot));
        results = results.concat(collectFormFieldCandidates(el.shadowRoot));
        results = results.concat(collectShadowDomCandidates(el.shadowRoot));
      }
    }
    return results;
  }

  /**
   * Scan form field values, placeholders, and attribute-based hints for date format issues.
   */
  function collectFormFieldCandidates(root) {
    const results = [];

    // 1. Scan input/textarea values and placeholders
    const inputs = root.querySelectorAll("input, textarea");
    for (const el of inputs) {
      if (el.closest("#sn-date-panel")) continue;
      const type = (el.getAttribute("type") || "text").toLowerCase();
      if (type === "hidden" || type === "password" || type === "checkbox" || type === "radio" || type === "file" || type === "submit" || type === "button") continue;

      const texts = [el.value, el.placeholder, el.getAttribute("placeholder")].filter(Boolean);
      for (const raw of texts) {
        const text = raw.trim();
        if (text.length < 3 || text.length > 100) continue;

        if (FORMAT_STR_RE.test(text)) {
          results.push({ text, node: el, element: el, type: "input" });
          continue;
        }

        let match;
        DATE_CANDIDATE_RE.lastIndex = 0;
        while ((match = DATE_CANDIDATE_RE.exec(text)) !== null) {
          results.push({ text: match[0], node: el, element: el, type: "input" });
        }
      }
    }

    // 2. Scan all visible elements for title, aria-label, and data-format attributes
    const allEls = root.querySelectorAll("[title], [aria-label], [data-format], [data-date-format]");
    for (const el of allEls) {
      if (el.closest("#sn-date-panel")) continue;
      const attrs = [
        el.getAttribute("title"),
        el.getAttribute("aria-label"),
        el.getAttribute("data-format"),
        el.getAttribute("data-date-format"),
      ].filter(Boolean);
      for (const raw of attrs) {
        const text = raw.trim();
        if (text.length < 3 || text.length > 100) continue;
        if (FORMAT_STR_RE.test(text)) {
          results.push({ text, node: el, element: el, type: "input" });
        }
      }
    }

    return results;
  }

  /* ──────────────── Highlighting ──────────────── */

  let currentHighlights = [];
  let panelEl = null;

  function clearHighlights() {
    for (const h of currentHighlights) {
      if (h.badge && h.badge.parentNode) h.badge.parentNode.removeChild(h.badge);
      if (h.element) h.element.classList.remove("sn-date-issue");
      if (h.overlay && h.overlay.parentNode) h.overlay.parentNode.removeChild(h.overlay);
    }
    currentHighlights = [];
    if (panelEl && panelEl.parentNode) panelEl.parentNode.removeChild(panelEl);
    panelEl = null;
  }

  function highlightIssue(candidate, issue) {
    const el = candidate.element;
    const record = { element: el, badge: null, overlay: null, issue, candidate };

    if (candidate.type === "svg") {
      // For SVG elements, add a red rect overlay
      try {
        const svg = el.closest("svg");
        if (svg) {
          const bbox = el.getBBox();
          const rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
          rect.setAttribute("x", bbox.x - 2);
          rect.setAttribute("y", bbox.y - 2);
          rect.setAttribute("width", bbox.width + 4);
          rect.setAttribute("height", bbox.height + 4);
          rect.classList.add("sn-date-svg-overlay");
          rect.setAttribute("fill", "rgba(229,57,53,0.15)");
          rect.setAttribute("stroke", "#e53935");
          rect.setAttribute("stroke-width", "1.5");
          rect.setAttribute("rx", "2");
          rect.style.pointerEvents = "none";
          svg.appendChild(rect);
          record.overlay = rect;
        }
      } catch (_) {}
    } else {
      // For DOM elements, add CSS class + badge
      el.classList.add("sn-date-issue");
      const badge = document.createElement("span");
      badge.className = "sn-date-badge";
      badge.textContent = "i18n";
      el.insertAdjacentElement("afterend", badge);
      record.badge = badge;
    }

    currentHighlights.push(record);
    return record;
  }

  function scrollToIssue(record) {
    const target = record.candidate.type === "svg" ? record.overlay : record.element;
    if (!target) return;
    if (target.scrollIntoView) {
      target.scrollIntoView({ behavior: "smooth", block: "center" });
    }
    if (record.element) {
      record.element.classList.add("sn-date-pulse");
      setTimeout(() => record.element.classList.remove("sn-date-pulse"), 1800);
    }
  }

  /* ──────────────── Issues Panel ──────────────── */

  function showPanel(issues) {
    if (panelEl && panelEl.parentNode) panelEl.parentNode.removeChild(panelEl);

    panelEl = document.createElement("div");
    panelEl.id = "sn-date-panel";

    const header = document.createElement("div");
    header.id = "sn-date-panel-header";
    header.innerHTML = `<span>${issues.length} date format issue${issues.length !== 1 ? "s" : ""} found</span>`;

    const closeBtn = document.createElement("button");
    closeBtn.textContent = "✕";
    closeBtn.addEventListener("click", () => {
      clearHighlights();
    });
    header.appendChild(closeBtn);
    panelEl.appendChild(header);

    const body = document.createElement("div");
    body.id = "sn-date-panel-body";

    for (const { record, issue } of issues) {
      const row = document.createElement("div");
      row.className = "sn-date-panel-row";
      row.innerHTML = `
        <div class="sn-date-panel-found">❌ ${escapeHtml(record.candidate.text)}</div>
        <div class="sn-date-panel-reason">${escapeHtml(issue.reason)}</div>
        <div class="sn-date-panel-expected">✅ Expected: ${escapeHtml(issue.expected)}</div>
      `;
      row.addEventListener("click", () => scrollToIssue(record));
      body.appendChild(row);
    }

    panelEl.appendChild(body);
    document.body.appendChild(panelEl);
  }

  function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str;
    return div.innerHTML;
  }

  /* ──────────────── Main Scan Function ──────────────── */

  function runScan(locale) {
    clearHighlights();

    const refs = buildReferenceFormats(locale);

    // Collect candidates from all sources
    let candidates = [];
    candidates = candidates.concat(collectTextNodeCandidates(document.body));
    candidates = candidates.concat(collectSvgTextCandidates(document));
    candidates = candidates.concat(collectShadowDomCandidates(document.body));
    candidates = candidates.concat(collectFormFieldCandidates(document.body));

    // Also scan iframes (same-origin only)
    try {
      const iframes = document.querySelectorAll("iframe");
      for (const iframe of iframes) {
        try {
          const iDoc = iframe.contentDocument || iframe.contentWindow.document;
          if (iDoc && iDoc.body) {
            candidates = candidates.concat(collectTextNodeCandidates(iDoc.body));
            candidates = candidates.concat(collectSvgTextCandidates(iDoc));
            candidates = candidates.concat(collectShadowDomCandidates(iDoc.body));
            candidates = candidates.concat(collectFormFieldCandidates(iDoc.body));
          }
        } catch (_) { /* cross-origin iframe — skip */ }
      }
    } catch (_) {}

    // Deduplicate by text + element
    const seen = new Set();
    const unique = [];
    for (const c of candidates) {
      const key = c.text + "|" + (c.element.id || c.element.textContent?.slice(0, 30) || "") + "|" + c.type;
      if (!seen.has(key)) {
        seen.add(key);
        unique.push(c);
      }
    }

    // Validate each candidate
    const issues = [];
    for (const c of unique) {
      const result = validateDateString(c.text, locale, refs);
      if (result && result.isWrong) {
        const record = highlightIssue(c, result);
        issues.push({ record, issue: result });
      }
    }

    // Show panel
    if (issues.length > 0) {
      showPanel(issues);
    }

    return issues.length;
  }

  /* ──────────────── Message Listener ──────────────── */

  chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (msg.action === "scan") {
      const count = runScan(msg.locale);
      const issueDetails = currentHighlights.map(h => ({
        found: h.candidate.text,
        reason: h.issue.reason,
        expected: h.issue.expected,
        suggestion: h.issue.suggestion || "",
      }));
      sendResponse({ count, issues: issueDetails });
    } else if (msg.action === "clear") {
      clearHighlights();
      sendResponse({ ok: true });
    } else if (msg.action === "ping") {
      sendResponse({ ok: true });
    }
    return true; // keep channel open for async
  });

  /* ──────────────── MutationObserver (auto-rescan on DOM changes) ──────────────── */

  let autoScanLocale = null;
  let rescanTimeout = null;

  chrome.runtime.onMessage.addListener((msg) => {
    if (msg.action === "scan") {
      autoScanLocale = msg.locale;
    }
  });

  const observer = new MutationObserver(() => {
    if (!autoScanLocale) return;
    clearTimeout(rescanTimeout);
    rescanTimeout = setTimeout(() => {
      runScan(autoScanLocale);
      // Update badge
      chrome.runtime.sendMessage({ action: "updateBadge", count: currentHighlights.length }).catch(() => {});
    }, 1500);
  });

  observer.observe(document.body, { childList: true, subtree: true, characterData: true });

  /* ──────────────── Expose for cross-frame scanning ──────────────── */
  window.__snDateScan = (locale) => {
    console.log("[SN-DateChecker] Scanning frame:", window.location.href, "locale:", locale);
    const inputCount = document.querySelectorAll("input, textarea").length;
    console.log("[SN-DateChecker] Found", inputCount, "input/textarea elements in this frame");

    // Log placeholders for debugging
    document.querySelectorAll("input").forEach(el => {
      const ph = el.placeholder || el.getAttribute("placeholder");
      const val = el.value;
      if (ph || val) {
        console.log("[SN-DateChecker] Input:", el.name || el.id || el.className, "placeholder:", ph, "value:", val?.slice(0, 50));
      }
    });

    const count = runScan(locale);
    const issues = currentHighlights.map(h => ({
      found: h.candidate.text,
      reason: h.issue.reason,
      expected: h.issue.expected,
      suggestion: h.issue.suggestion || "",
    }));
    console.log("[SN-DateChecker] Frame scan complete. Issues found:", issues.length, issues);
    return issues;
  };
  window.__snDateClear = () => clearHighlights();

})();
