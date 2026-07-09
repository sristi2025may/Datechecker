/**
 * ServiceNow Date Format Checker — Selenium Scanner Script (v3)
 *
 * Injected via Selenium executeScript(). Scans the page for date strings
 * that don't match the expected locale format.
 *
 * Features:
 * - Deep recursive shadow DOM traversal (ServiceNow Seismic components)
 * - Visual highlighting: red outlines + "i18n" badges on offending elements
 * - Floating issues panel (bottom-right corner)
 * - Returns a JSON array of issues found
 *
 * Usage: arguments[0] = locale code (e.g. "ja", "de", "fr")
 */
(function(locale) {
  "use strict";

  /* ── 1. Inject highlight CSS ── */

  var STYLE_ID = "sn-date-checker-styles";
  if (!document.getElementById(STYLE_ID)) {
    var style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent =
      ".sn-date-issue{outline:2px solid #e53935!important;outline-offset:2px;background:rgba(229,57,53,.08)!important;border-radius:3px;position:relative}" +
      ".sn-date-badge{display:inline-block;font-size:10px;font-weight:700;line-height:1;color:#fff;background:#e53935;border-radius:3px;padding:2px 5px;margin-left:4px;vertical-align:middle;pointer-events:none;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;z-index:2147483646}" +
      "@keyframes sn-date-pulse{0%{box-shadow:0 0 0 0 rgba(229,57,53,.6)}70%{box-shadow:0 0 0 10px rgba(229,57,53,0)}100%{box-shadow:0 0 0 0 rgba(229,57,53,0)}}" +
      ".sn-date-pulse{animation:sn-date-pulse .8s ease-out 2}" +
      "#sn-date-panel{position:fixed;bottom:12px;right:12px;width:400px;max-height:360px;background:#fff;border:1px solid #ccc;border-radius:8px;box-shadow:0 4px 20px rgba(0,0,0,.18);z-index:2147483647;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:13px;overflow:hidden;display:flex;flex-direction:column}" +
      "#sn-date-panel-header{display:flex;align-items:center;justify-content:space-between;padding:8px 12px;background:#e53935;color:#fff;font-weight:600;font-size:13px}" +
      "#sn-date-panel-header button{background:none;border:none;color:#fff;font-size:16px;cursor:pointer;padding:0 4px}" +
      "#sn-date-panel-body{overflow-y:auto;max-height:300px;padding:0;margin:0}" +
      ".sn-date-panel-row{display:flex;flex-direction:column;padding:8px 12px;border-bottom:1px solid #eee;cursor:pointer;transition:background .15s}" +
      ".sn-date-panel-row:hover{background:#fbe9e7}" +
      ".sn-date-panel-found{color:#c62828;font-weight:600;word-break:break-all}" +
      ".sn-date-panel-expected{color:#2e7d32;font-size:12px;margin-top:2px}" +
      ".sn-date-panel-reason{color:#757575;font-size:11px;margin-top:1px}" +
      ".sn-date-svg-overlay{fill:rgba(229,57,53,.15);stroke:#e53935;stroke-width:1.5;rx:2;pointer-events:none}";
    document.head.appendChild(style);
  }

  /* ── 2. Clean up previous scan ── */

  (function cleanup() {
    document.querySelectorAll(".sn-date-badge").forEach(function(b) { b.remove(); });
    document.querySelectorAll(".sn-date-issue").forEach(function(e) { e.classList.remove("sn-date-issue"); });
    document.querySelectorAll(".sn-date-svg-overlay").forEach(function(r) { r.remove(); });
    var oldPanel = document.getElementById("sn-date-panel");
    if (oldPanel) oldPanel.remove();
  })();

  /* ── 3. Locale & Date Pattern Helpers ── */

  var SN_TO_BCP47 = { fq: "fr-CA", zt: "zh-Hant", pb: "pt-BR" };
  function toBcp47(code) { return SN_TO_BCP47[code] || code; }

  var EN_MONTHS_LONG = ["january","february","march","april","may","june","july","august","september","october","november","december"];
  var EN_MONTHS_SHORT = ["jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"];
  var EN_DAYS_LONG = ["sunday","monday","tuesday","wednesday","thursday","friday","saturday"];
  var EN_DAYS_SHORT = ["sun","mon","tue","wed","thu","fri","sat"];

  var DATE_CANDIDATE_RE = new RegExp(
    "(?:" +
      "(?:(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")[\\.\\s,]*\\d{1,2}[,\\s]*\\d{2,4})" + "|" +
      "(?:\\d{1,2}[\\s,]*(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")[\\.,\\s]*\\d{2,4})" + "|" +
      "(?:(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")\\s+\\d{4})" + "|" +
      "(?:\\d{4}\\s+(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + "))" + "|" +
      "(?:\\d{4}\u5E74\\d{1,2}\u6708(?:\\d{1,2}\u65E5)?)" + "|" +
      "(?:\\d{1,2}\u6708\\s*\\d{4})" + "|" +
      "(?:(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")\\s+\\d{1,2})(?!\\s*[,.]?\\s*\\d)" + "|" +
      "(?:\\d{1,2}\\s+(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + "))(?!\\s*[,.]?\\s*\\d)" + "|" +
      "(?:\\d{4}-\\d{2}-\\d{2}[\\sT]\\d{1,2}:\\d{2}(?::\\d{2})?)" + "|" +
      "(?:\\d{1,4}[/\\-\\.]\\d{1,2}[/\\-\\.]\\d{1,4})" + "|" +
      "(?:(?:[Yy]{2,4}[-/.][Mm]{1,4}[-/.][Dd]{1,4}|[Dd]{1,4}[-/.][Mm]{1,4}[-/.][Yy]{2,4}|[Mm]{1,4}[-/.][Dd]{1,4}[-/.][Yy]{2,4})(?:[\\sT][Hh]{1,2}:[Mm]{1,2}(?::[Ss]{1,2})?)?)" + "|" +
      "(?:\\b(?:" + EN_DAYS_LONG.join("|") + "|" + EN_DAYS_SHORT.join("|") + ")\\b)" + "|" +
      "(?:\\b(?:" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")\\b)" +
    ")", "gi"
  );

  var FORMAT_STR_RE = /^(?:[Yy]{2,4}[-/.][Mm]{1,4}[-/.][Dd]{1,4}|[Dd]{1,4}[-/.][Mm]{1,4}[-/.][Yy]{2,4}|[Mm]{1,4}[-/.][Dd]{1,4}[-/.][Yy]{2,4})(?:[\sT][Hh]{1,2}:[Mm]{1,2}(?::[Ss]{1,2})?)?$/;

  /* ── 4. Reference Format Generation ── */

  function buildReferenceFormats(loc) {
    var bcp = toBcp47(loc);
    var styles = ["full", "long", "medium", "short"];
    var refs = {};
    var sampleDates = [];
    for (var y = 2024; y <= 2027; y++) {
      for (var m = 0; m < 12; m++) {
        sampleDates.push(new Date(y, m, 1));
        sampleDates.push(new Date(y, m, 15));
      }
    }
    for (var i = 0; i < sampleDates.length; i++) {
      var d = sampleDates[i];
      for (var j = 0; j < styles.length; j++) {
        try { refs[new Intl.DateTimeFormat(bcp, { dateStyle: styles[j] }).format(d).toLowerCase().trim()] = true; } catch(e){}
        try { refs[new Intl.DateTimeFormat(bcp, { year:"numeric",month:"long" }).format(d).toLowerCase().trim()] = true; } catch(e){}
        try { refs[new Intl.DateTimeFormat(bcp, { year:"numeric",month:"short" }).format(d).toLowerCase().trim()] = true; } catch(e){}
        try { refs[new Intl.DateTimeFormat(bcp, { year:"numeric",month:"numeric" }).format(d).toLowerCase().trim()] = true; } catch(e){}
      }
    }
    return refs;
  }

  /* ── 5. Date Validation (same logic as extension) ── */

  function tryReformatToLocale(engStr, bcp) {
    try {
      var d = new Date(engStr);
      if (!isNaN(d.getTime())) return new Intl.DateTimeFormat(bcp, { year:"numeric",month:"long",day:"numeric" }).format(d);
      var m = engStr.match(new RegExp("^(" + EN_MONTHS_LONG.join("|") + "|" + EN_MONTHS_SHORT.join("|") + ")\\s+(\\d{4})$", "i"));
      if (m) {
        var mi = EN_MONTHS_LONG.indexOf(m[1].toLowerCase()), miS = EN_MONTHS_SHORT.indexOf(m[1].toLowerCase());
        var idx = mi >= 0 ? mi : miS;
        if (idx >= 0) return new Intl.DateTimeFormat(bcp, { year:"numeric",month:"long" }).format(new Date(parseInt(m[2],10), idx, 1));
      }
    } catch(e){} return null;
  }

  function validateDateString(found, loc, refs) {
    var bcp = toBcp47(loc), lower = found.toLowerCase().trim(), isEn = bcp.startsWith("en");
    if (refs[lower]) return null;

    if (!isEn) {
      var allEng = EN_MONTHS_LONG.concat(EN_MONTHS_SHORT, EN_DAYS_LONG, EN_DAYS_SHORT);
      for (var i = 0; i < allEng.length; i++) {
        if (lower.indexOf(allEng[i]) >= 0) {
          var exp = tryReformatToLocale(found, bcp);
          return { isWrong:true, reason:"English text \""+allEng[i]+"\" found — locale is "+bcp, expected:exp||"(use Intl.DateTimeFormat with locale '"+bcp+"')",
            suggestion:"Server: gd.getDisplayValueLang(\"long\", \""+bcp+"\")\nClient: new Intl.DateTimeFormat(\""+bcp+"\", { dateStyle: \"long\" }).format(date)" };
        }
      }
    }

    var cjk = found.match(/^(\d{1,2})\u6708\s*(\d{4})$/);
    if (cjk) { var mo=parseInt(cjk[1],10), yr=parseInt(cjk[2],10);
      if (mo>=1&&mo<=12&&yr>=1900&&yr<=2100) return { isWrong:true, reason:"Wrong order — month before year",
        expected:new Intl.DateTimeFormat(bcp,{year:"numeric",month:"long"}).format(new Date(yr,mo-1,1)),
        suggestion:"Server: gd.getDisplayValueLang(\"long\", \""+bcp+"\")\nClient: new Intl.DateTimeFormat(\""+bcp+"\", { year: \"numeric\", month: \"long\" }).format(date)" };
    }

    var mye = found.match(new RegExp("^("+EN_MONTHS_LONG.join("|")+"|"+EN_MONTHS_SHORT.join("|")+")\\s+(\\d{4})$","i"));
    if (mye && !isEn) { var mi=EN_MONTHS_LONG.indexOf(mye[1].toLowerCase()), mis=EN_MONTHS_SHORT.indexOf(mye[1].toLowerCase()), mx=mi>=0?mi:mis;
      if (mx>=0) return { isWrong:true, reason:"English month name in "+bcp+" locale",
        expected:new Intl.DateTimeFormat(bcp,{year:"numeric",month:"long"}).format(new Date(parseInt(mye[2],10),mx,1)),
        suggestion:"Server: gd.getDisplayValueLang(\"long\", \""+bcp+"\")\nClient: new Intl.DateTimeFormat(\""+bcp+"\", { dateStyle: \"long\" }).format(date)" };
    }

    if (!isEn) {
      var mdP = found.match(new RegExp("^("+EN_MONTHS_LONG.join("|")+"|"+EN_MONTHS_SHORT.join("|")+")\\s+(\\d{1,2})$","i"));
      var dmP = found.match(new RegExp("^(\\d{1,2})\\s+("+EN_MONTHS_LONG.join("|")+"|"+EN_MONTHS_SHORT.join("|")+")$","i"));
      var mp = mdP || dmP;
      if (mp) { var ms=mdP?mp[1]:mp[2], ds=mdP?mp[2]:mp[1]; var mi2=EN_MONTHS_LONG.indexOf(ms.toLowerCase()), miS2=EN_MONTHS_SHORT.indexOf(ms.toLowerCase()), ix=mi2>=0?mi2:miS2, dy=parseInt(ds,10);
        if (ix>=0&&dy>=1&&dy<=31) return { isWrong:true, reason:"English date \""+found+"\" in "+bcp+" locale",
          expected:new Intl.DateTimeFormat(bcp,{month:"long",day:"numeric"}).format(new Date(new Date().getFullYear(),ix,dy)),
          suggestion:"Server: gd.getDisplayValueLang(\"long\", \""+bcp+"\")\nClient: new Intl.DateTimeFormat(\""+bcp+"\", { dateStyle: \"long\" }).format(date)" };
      }
    }

    var dli=EN_DAYS_LONG.indexOf(lower), dsi=EN_DAYS_SHORT.indexOf(lower), dix=dli>=0?dli:dsi;
    if (dix>=0&&!isEn) { var il=dli>=0; return { isWrong:true, reason:"English day name \""+found+"\" in "+bcp+" locale",
      expected:new Intl.DateTimeFormat(bcp,{weekday:il?"long":"short"}).format(new Date(2026,0,4+dix)),
      suggestion:"Client: new Intl.DateTimeFormat(\""+bcp+"\", { weekday: \""+(il?"long":"short")+"\" }).format(date)" }; }

    var mli=EN_MONTHS_LONG.indexOf(lower), msi=EN_MONTHS_SHORT.indexOf(lower), mix=mli>=0?mli:msi;
    if (mix>=0&&!isEn) { var il2=mli>=0; return { isWrong:true, reason:"English month name \""+found+"\" in "+bcp+" locale",
      expected:new Intl.DateTimeFormat(bcp,{month:il2?"long":"short"}).format(new Date(2026,mix,1)),
      suggestion:"Client: new Intl.DateTimeFormat(\""+bcp+"\", { month: \""+(il2?"long":"short")+"\" }).format(date)" }; }

    if (FORMAT_STR_RE.test(found)) {
      var samp=new Date(2026,1,15,14,30,0), ht=/[Hh]{1,2}:[Mm]{1,2}/.test(found);
      var ex = ht ? new Intl.DateTimeFormat(bcp,{dateStyle:"long",timeStyle:"short"}).format(samp) : new Intl.DateTimeFormat(bcp,{dateStyle:"long"}).format(samp);
      return { isWrong:true, reason:"Hardcoded format pattern \""+found+"\" — not locale-aware", expected:"e.g. "+ex,
        suggestion: ht ? "Server: gdt.getDisplayValueLang(\"long\")\nClient: new Intl.DateTimeFormat(\""+bcp+"\", { dateStyle: \"long\", timeStyle: \"short\" }).format(date)"
                       : "Server: gd.getDisplayValueLang(\"long\")\nClient: new Intl.DateTimeFormat(\""+bcp+"\", { dateStyle: \"long\" }).format(date)" };
    }

    var iso = found.match(/^(\d{4})-(\d{2})-(\d{2})(?:[\sT](\d{1,2}):(\d{2})(?::(\d{2}))?)?$/);
    if (iso) { var iy=parseInt(iso[1],10), im=parseInt(iso[2],10), id=parseInt(iso[3],10);
      if (iy>=1900&&iy<=2100&&im>=1&&im<=12&&id>=1&&id<=31) { var dt=new Date(iy,im-1,id), ht2=!!iso[4];
        if (ht2) { dt.setHours(parseInt(iso[4],10),parseInt(iso[5],10),iso[6]?parseInt(iso[6],10):0);
          return { isWrong:true, reason:"Hardcoded ISO 8601 datetime", expected:new Intl.DateTimeFormat(bcp,{dateStyle:"long",timeStyle:"short"}).format(dt),
            suggestion:"Server: gdt.getDisplayValueLang(\"long\")\nClient: new Intl.DateTimeFormat(\""+bcp+"\", { dateStyle: \"long\", timeStyle: \"short\" }).format(date)" };
        } else { var ilf=false; ["full","long","medium","short"].forEach(function(s){ try{ if(new Intl.DateTimeFormat(bcp,{dateStyle:s}).format(dt).toLowerCase().trim()===lower) ilf=true; }catch(e){} });
          if (!ilf) return { isWrong:true, reason:"Hardcoded ISO 8601 date", expected:new Intl.DateTimeFormat(bcp,{dateStyle:"long"}).format(dt),
            suggestion:"Server: gd.getDisplayValueLang(\"long\")\nClient: new Intl.DateTimeFormat(\""+bcp+"\", { dateStyle: \"long\" }).format(date)" };
        }
      }
    }
    return null;
  }

  /* ── 6. Deep Shadow DOM Traversal ── */

  /**
   * Recursively collect ALL elements across all shadow DOM boundaries.
   * This is critical for ServiceNow's Seismic web components.
   */
  function getAllShadowRoots(root, depth) {
    if (depth > 30) return [];
    var roots = [];
    var els;
    try { els = root.querySelectorAll("*"); } catch(e) { return roots; }
    for (var i = 0; i < els.length; i++) {
      var sr = els[i].shadowRoot;
      if (sr) {
        roots.push(sr);
        roots = roots.concat(getAllShadowRoots(sr, depth + 1));
      }
    }
    return roots;
  }

  /* ── 7. DOM Scanning ── */

  function collectTextNodeCandidates(root) {
    var results = [];
    try {
      var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
      var node;
      while ((node = walker.nextNode())) {
        var parent = node.parentElement;
        if (!parent) continue;
        var tag = parent.tagName;
        if (tag === "SCRIPT" || tag === "STYLE" || tag === "NOSCRIPT") continue;
        if (parent.closest && parent.closest("#sn-date-panel")) continue;
        var text = node.textContent.trim();
        if (text.length < 3 || text.length > 100) continue;
        var match; DATE_CANDIDATE_RE.lastIndex = 0;
        while ((match = DATE_CANDIDATE_RE.exec(text)) !== null) {
          results.push({ text: match[0], element: parent, type: "dom", context: text.substring(0,80) });
        }
      }
    } catch(e){}
    return results;
  }

  function collectSvgTextCandidates(root) {
    var results = [];
    try {
      var svgTexts = root.querySelectorAll("svg text, svg tspan");
      for (var i = 0; i < svgTexts.length; i++) {
        var el = svgTexts[i], text = el.textContent.trim();
        if (text.length < 3 || text.length > 100) continue;
        var match; DATE_CANDIDATE_RE.lastIndex = 0;
        while ((match = DATE_CANDIDATE_RE.exec(text)) !== null) {
          results.push({ text: match[0], element: el, type: "svg", context: text.substring(0,80) });
        }
      }
    } catch(e){}
    return results;
  }

  function collectFormFieldCandidates(root) {
    var results = [];
    try {
      var inputs = root.querySelectorAll("input, textarea");
      for (var i = 0; i < inputs.length; i++) {
        var el = inputs[i];
        if (el.closest && el.closest("#sn-date-panel")) continue;
        var type = (el.getAttribute("type") || "text").toLowerCase();
        if (type==="hidden"||type==="password"||type==="checkbox"||type==="radio"||type==="file"||type==="submit"||type==="button") continue;
        var texts = [el.value, el.placeholder, el.getAttribute("placeholder")].filter(Boolean);
        for (var t = 0; t < texts.length; t++) {
          var text = texts[t].trim();
          if (text.length < 3 || text.length > 100) continue;
          if (FORMAT_STR_RE.test(text)) { results.push({ text:text, element:el, type:"input", context:text }); continue; }
          var match; DATE_CANDIDATE_RE.lastIndex = 0;
          while ((match = DATE_CANDIDATE_RE.exec(text)) !== null) { results.push({ text:match[0], element:el, type:"input", context:text }); }
        }
      }
      var attrEls = root.querySelectorAll("[title],[aria-label],[data-format],[data-date-format]");
      for (var j = 0; j < attrEls.length; j++) {
        var el2 = attrEls[j];
        if (el2.closest && el2.closest("#sn-date-panel")) continue;
        var attrs = [el2.getAttribute("title"),el2.getAttribute("aria-label"),el2.getAttribute("data-format"),el2.getAttribute("data-date-format")].filter(Boolean);
        for (var a = 0; a < attrs.length; a++) {
          var text2 = attrs[a].trim();
          if (text2.length < 3 || text2.length > 100) continue;
          if (FORMAT_STR_RE.test(text2)) { results.push({ text:text2, element:el2, type:"attribute", context:text2 }); }
        }
      }
    } catch(e){}
    return results;
  }

  /* ── 8. Highlighting ── */

  function highlightIssue(candidate, issue) {
    var el = candidate.element;
    if (!el) return;
    try {
      if (candidate.type === "svg") {
        var svg = el.closest("svg");
        if (svg) {
          var bbox = el.getBBox();
          var rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
          rect.setAttribute("x", bbox.x - 2); rect.setAttribute("y", bbox.y - 2);
          rect.setAttribute("width", bbox.width + 4); rect.setAttribute("height", bbox.height + 4);
          rect.classList.add("sn-date-svg-overlay");
          rect.style.pointerEvents = "none";
          svg.appendChild(rect);
        }
      } else {
        el.classList.add("sn-date-issue");
        var badge = document.createElement("span");
        badge.className = "sn-date-badge";
        badge.textContent = "i18n";
        el.insertAdjacentElement("afterend", badge);
      }
    } catch(e){}
  }

  /* ── 9. Issues Panel ── */

  function escapeHtml(str) { var d = document.createElement("div"); d.textContent = str; return d.innerHTML; }

  function showPanel(issues, bcp) {
    var panel = document.createElement("div");
    panel.id = "sn-date-panel";

    var header = document.createElement("div");
    header.id = "sn-date-panel-header";
    header.innerHTML = "<span>"+issues.length+" date format issue"+(issues.length!==1?"s":"")+" found</span>";
    var closeBtn = document.createElement("button");
    closeBtn.textContent = "\u2715";
    closeBtn.addEventListener("click", function() { panel.remove(); });
    header.appendChild(closeBtn);
    panel.appendChild(header);

    var body = document.createElement("div");
    body.id = "sn-date-panel-body";
    for (var i = 0; i < issues.length; i++) {
      var iss = issues[i];
      var row = document.createElement("div");
      row.className = "sn-date-panel-row";
      row.innerHTML =
        '<div class="sn-date-panel-found">\u274C ' + escapeHtml(iss.found) + '</div>' +
        '<div class="sn-date-panel-reason">' + escapeHtml(iss.reason) + '</div>' +
        '<div class="sn-date-panel-expected">\u2705 Expected: ' + escapeHtml(iss.expected) + '</div>';
      (function(el) {
        row.addEventListener("click", function() {
          if (el && el.scrollIntoView) {
            el.scrollIntoView({ behavior:"smooth", block:"center" });
            el.classList.add("sn-date-pulse");
            setTimeout(function(){ el.classList.remove("sn-date-pulse"); }, 1800);
          }
        });
      })(iss._element);
      body.appendChild(row);
    }
    panel.appendChild(body);

    // Add reference table
    var refDiv = document.createElement("div");
    refDiv.style.cssText = "padding:8px 12px;border-top:1px solid #eee;background:#f9f9f9;font-size:11px;";
    var sample = new Date(2026, 1, 15);
    var refHtml = '<div style="font-weight:600;color:#555;margin-bottom:4px;">Correct formats for <code>'+bcp+'</code>:</div>';
    refHtml += '<table style="width:100%;font-size:11px;border-collapse:collapse;">';
    ["full","long","medium","short"].forEach(function(s) {
      try { var f = new Intl.DateTimeFormat(bcp, { dateStyle: s }).format(sample);
        refHtml += '<tr><td style="padding:2px 4px;color:#888;">'+s+'</td><td style="padding:2px 4px;">'+f+'</td></tr>';
      } catch(e){}
    });
    refHtml += '</table>';
    refDiv.innerHTML = refHtml;
    panel.appendChild(refDiv);

    document.body.appendChild(panel);
  }

  /* ── 10. Main Scan ── */

  var bcp = toBcp47(locale);
  var refs = buildReferenceFormats(locale);
  var candidates = [];

  // Scan document.body (text nodes, SVG, form fields)
  if (document.body) {
    candidates = candidates.concat(collectTextNodeCandidates(document.body));
    candidates = candidates.concat(collectSvgTextCandidates(document));
    candidates = candidates.concat(collectFormFieldCandidates(document.body));
  }

  // Deep scan ALL shadow DOMs recursively
  var shadowRoots = getAllShadowRoots(document, 0);
  console.log("[SN-DateChecker] Found " + shadowRoots.length + " shadow roots");
  for (var s = 0; s < shadowRoots.length; s++) {
    candidates = candidates.concat(collectTextNodeCandidates(shadowRoots[s]));
    candidates = candidates.concat(collectSvgTextCandidates(shadowRoots[s]));
    candidates = candidates.concat(collectFormFieldCandidates(shadowRoots[s]));
  }

  // Scan same-origin iframes
  try {
    var iframes = document.querySelectorAll("iframe");
    for (var f = 0; f < iframes.length; f++) {
      try {
        var iDoc = iframes[f].contentDocument || iframes[f].contentWindow.document;
        if (iDoc && iDoc.body) {
          candidates = candidates.concat(collectTextNodeCandidates(iDoc.body));
          candidates = candidates.concat(collectSvgTextCandidates(iDoc));
          candidates = candidates.concat(collectFormFieldCandidates(iDoc.body));
          var iframeShadows = getAllShadowRoots(iDoc, 0);
          for (var is = 0; is < iframeShadows.length; is++) {
            candidates = candidates.concat(collectTextNodeCandidates(iframeShadows[is]));
            candidates = candidates.concat(collectSvgTextCandidates(iframeShadows[is]));
            candidates = candidates.concat(collectFormFieldCandidates(iframeShadows[is]));
          }
        }
      } catch(e){}
    }
  } catch(e){}

  console.log("[SN-DateChecker] Total candidates found: " + candidates.length);

  // Deduplicate by text + type
  var seen = {};
  var unique = [];
  for (var i = 0; i < candidates.length; i++) {
    var c = candidates[i];
    var key = c.text + "|" + c.type + "|" + (c.context || "");
    if (!seen[key]) { seen[key] = true; unique.push(c); }
  }

  // Validate and collect issues
  var issues = [];
  for (var i = 0; i < unique.length; i++) {
    var c = unique[i];
    var result = validateDateString(c.text, locale, refs);
    if (result && result.isWrong) {
      // Highlight on the page
      highlightIssue(c, result);
      issues.push({
        found: c.text,
        reason: result.reason,
        expected: result.expected,
        suggestion: result.suggestion || "",
        type: c.type,
        context: c.context,
        frameUrl: window.location.href,
        _element: c.element  // for panel click-to-scroll
      });
    }
  }

  console.log("[SN-DateChecker] Issues found: " + issues.length);

  // Show floating panel if issues found
  if (issues.length > 0) {
    showPanel(issues, bcp);
  }

  // Return JSON (strip _element references which can't be serialized)
  var jsonIssues = issues.map(function(iss) {
    return {
      found: iss.found,
      reason: iss.reason,
      expected: iss.expected,
      suggestion: iss.suggestion,
      type: iss.type,
      context: iss.context,
      frameUrl: iss.frameUrl
    };
  });

  return JSON.stringify(jsonIssues);

})(arguments[0]);
