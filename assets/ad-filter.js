(() => {
  const FILTER_STYLE_ELEMENT_ID = "npviewer-ad-filter-style";
  const HIDDEN_MARKER_ATTRIBUTE = "data-npviewer-hidden";

  function ensureStyle(cssText) {
    let style = document.getElementById(FILTER_STYLE_ELEMENT_ID);
    if (!style) {
      style = document.createElement("style");
      style.id = FILTER_STYLE_ELEMENT_ID;
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent = cssText;
  }

  function markHidden(selectors, root) {
    if (!root || !("querySelectorAll" in root)) {
      return;
    }

    selectors.forEach((selector) => {
      try {
        root.querySelectorAll(selector).forEach((element) => {
          if (element.getAttribute(HIDDEN_MARKER_ATTRIBUTE) === "1") {
            return;
          }
          element.style.setProperty("display", "none", "important");
          element.setAttribute(HIDDEN_MARKER_ATTRIBUTE, "1");
        });
      } catch {
        // Ignore invalid selectors emitted by imperfect filter conversions.
      }
    });
  }

  function hideSelectors(selectors) {
    if (selectors.length === 0) {
      return;
    }

    markHidden(selectors, document);

    if (!window.__npviewerObserver) {
      window.__npviewerObserver = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
          mutation.addedNodes.forEach((node) => {
            if (node.nodeType === Node.ELEMENT_NODE) {
              markHidden(selectors, node);
            }
          });
        });
      });

      window.__npviewerObserver.observe(document.documentElement || document, {
        childList: true,
        subtree: true
      });
    }
  }

  function applyCosmeticPayload(payload) {
    if (!payload) {
      return;
    }

    ensureStyle(payload.css || "");
    hideSelectors(payload.selectors || []);
  }

  function refreshCosmeticPayload() {
    try {
      const cosmeticJson = window._AdFilter && window._AdFilter.getCosmetic(location.href);
      if (cosmeticJson) {
        applyCosmeticPayload(JSON.parse(cosmeticJson));
      }
    } catch {
      // Keep page rendering even if the bridge payload is malformed.
    }
  }

  window.__npviewerApplyCosmetic = applyCosmeticPayload;
  window.__npviewerRefreshCosmetic = refreshCosmeticPayload;
  refreshCosmeticPayload();
})();
