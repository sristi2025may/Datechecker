/**
 * ServiceNow Date Format Checker — Background Service Worker
 */

chrome.runtime.onMessage.addListener((msg, sender) => {
  if (msg.action === "updateBadge") {
    const count = msg.count || 0;
    const tabId = sender.tab?.id;
    if (tabId) {
      chrome.action.setBadgeText({ text: count > 0 ? String(count) : "", tabId });
      chrome.action.setBadgeBackgroundColor({ color: "#e53935", tabId });
    }
  }
});
