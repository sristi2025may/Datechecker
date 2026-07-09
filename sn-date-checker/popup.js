/**
 * ServiceNow Date Format Checker — Popup Script
 */

const SN_TO_BCP47 = { fq: "fr-CA", zt: "zh-Hant", pb: "pt-BR" };

function toBcp47(code) {
  return SN_TO_BCP47[code] || code;
}

const localeSelect = document.getElementById("locale");
const scanBtn = document.getElementById("scanBtn");
const clearBtn = document.getElementById("clearBtn");
const statusDiv = document.getElementById("status");
const issuesList = document.getElementById("issuesList");
const refTable = document.getElementById("refTable");

// Restore last locale
chrome.storage?.local?.get("lastLocale", (data) => {
  if (data?.lastLocale) localeSelect.value = data.lastLocale;
  updateRefTable();
});

localeSelect.addEventListener("change", () => {
  chrome.storage?.local?.set({ lastLocale: localeSelect.value });
  updateRefTable();
});

scanBtn.addEventListener("click", async () => {
  scanBtn.disabled = true;
  scanBtn.textContent = "Scanning…";
  statusDiv.className = "";
  statusDiv.style.display = "none";

  const locale = localeSelect.value;
  chrome.storage?.local?.set({ lastLocale: locale });

  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab) {
      showStatus("error", "No active tab found.");
      scanBtn.disabled = false;
      scanBtn.textContent = "🔍 Scan this page";
      return;
    }

    // Run scan in ALL frames (top + iframes like gsft_main)
    const results = await chrome.scripting.executeScript({
      target: { tabId: tab.id, allFrames: true },
      func: (loc) => {
        if (typeof window.__snDateScan === "function") {
          return window.__snDateScan(loc);
        }
        return [];
      },
      args: [locale],
    });

    // Aggregate issues from all frames
    const allIssues = [];
    for (const frame of results) {
      if (frame.result && Array.isArray(frame.result)) {
        allIssues.push(...frame.result);
      }
    }

    scanBtn.disabled = false;
    scanBtn.textContent = "🔍 Scan this page";

    const count = allIssues.length;
    if (count > 0) {
      showStatus("error", `Found ${count} date format issue${count !== 1 ? "s" : ""}!`);
      renderIssues(allIssues);
      chrome.action.setBadgeText({ text: String(count), tabId: tab.id });
      chrome.action.setBadgeBackgroundColor({ color: "#e53935", tabId: tab.id });
    } else {
      showStatus("success", "✅ No date format issues found on this page.");
      issuesList.innerHTML = "";
      chrome.action.setBadgeText({ text: "", tabId: tab.id });
    }
  } catch (err) {
    showStatus("error", "Error: " + err.message);
    scanBtn.disabled = false;
    scanBtn.textContent = "🔍 Scan this page";
  }
});

clearBtn.addEventListener("click", async () => {
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (tab) {
      await chrome.scripting.executeScript({
        target: { tabId: tab.id, allFrames: true },
        func: () => {
          if (typeof window.__snDateClear === "function") window.__snDateClear();
        },
      });
      chrome.action.setBadgeText({ text: "", tabId: tab.id });
    }
  } catch (_) {}
  statusDiv.className = "";
  statusDiv.style.display = "none";
  issuesList.innerHTML = "";
});

function escapeHtml(str) {
  const el = document.createElement("span");
  el.textContent = str;
  return el.innerHTML;
}

function renderIssues(issues) {
  issuesList.innerHTML = "";
  for (const issue of issues) {
    const row = document.createElement("div");
    row.className = "issue-row";

    const suggestionLines = (issue.suggestion || "").split("\n").filter(Boolean);
    const suggestionHtml = suggestionLines.length
      ? `<div class="issue-suggestion">
          <div class="suggestion-label">🔧 How to fix (KB0562050):</div>
          ${suggestionLines.map(line => `<code>${escapeHtml(line)}</code>`).join("")}
        </div>`
      : "";

    row.innerHTML = `
      <div class="issue-found">❌ <span class="wrong-date">${escapeHtml(issue.found)}</span></div>
      <div class="issue-reason">${escapeHtml(issue.reason)}</div>
      <div class="issue-expected">✅ <span class="correct-date">${escapeHtml(issue.expected)}</span></div>
      ${suggestionHtml}
    `;
    issuesList.appendChild(row);
  }
}

function showStatus(type, message) {
  statusDiv.className = type;
  statusDiv.textContent = message;
  statusDiv.style.display = "block";
}

function updateRefTable() {
  const locale = localeSelect.value;
  const bcp = toBcp47(locale);
  const now = new Date();
  const sample = new Date(2026, 1, 15); // Feb 15, 2026

  let html = `<div style="font-size:11px;color:#555;margin-bottom:6px;font-weight:600;">
    Correct formats for <code>${bcp}</code>:
  </div>`;
  html += `<table style="width:100%;font-size:11px;border-collapse:collapse;">
    <tr style="background:#f5f5f5;">
      <th style="text-align:left;padding:3px 6px;">Style</th>
      <th style="text-align:left;padding:3px 6px;">Feb 15, 2026</th>
    </tr>`;

  const styles = ["full", "long", "medium", "short"];
  for (const style of styles) {
    try {
      const formatted = new Intl.DateTimeFormat(bcp, { dateStyle: style }).format(sample);
      html += `<tr><td style="padding:3px 6px;color:#888;">${style}</td><td style="padding:3px 6px;">${formatted}</td></tr>`;
    } catch (_) {
      html += `<tr><td style="padding:3px 6px;color:#888;">${style}</td><td style="padding:3px 6px;color:#ccc;">—</td></tr>`;
    }
  }

  // Year-month formats (common in charts)
  html += `<tr style="background:#f5f5f5;"><th colspan="2" style="text-align:left;padding:3px 6px;">Year + Month (chart axes)</th></tr>`;
  try {
    const ym1 = new Intl.DateTimeFormat(bcp, { year: "numeric", month: "long" }).format(sample);
    html += `<tr><td style="padding:3px 6px;color:#888;">year-month-long</td><td style="padding:3px 6px;">${ym1}</td></tr>`;
  } catch (_) {}
  try {
    const ym2 = new Intl.DateTimeFormat(bcp, { year: "numeric", month: "short" }).format(sample);
    html += `<tr><td style="padding:3px 6px;color:#888;">year-month-short</td><td style="padding:3px 6px;">${ym2}</td></tr>`;
  } catch (_) {}

  html += `</table>`;
  refTable.innerHTML = html;
}

// Initial render
updateRefTable();
