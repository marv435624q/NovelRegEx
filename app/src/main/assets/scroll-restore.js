(() => {
  function restoreScrollPosition() {
    const y = (window._ScrollRestore && window._ScrollRestore.getScrollY(location.href)) || 0;
    if (y <= 0) {
      return;
    }

    const applyScroll = () => {
      window.scrollTo(0, y);
    };

    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", applyScroll, { once: true });
    } else {
      applyScroll();
    }
  }

  window.__npviewerRestoreScrollPosition = restoreScrollPosition;
  restoreScrollPosition();
})();
