/*
(function () {
    function getCtx() {
        // 1) Prefer meta tag rendered by Jira
        var meta = document.querySelector('meta[name="ajs-context-path"]');
        if (meta && meta.getAttribute('content')) {
            return meta.getAttribute('content');
        }
        // 2) Fallback to AJS.contextPath()
        if (typeof AJS !== 'undefined' && AJS && typeof AJS.contextPath === 'function') {
            try { return AJS.contextPath(); } catch (e) {}
        }
        // 3) Last resort: empty
        return '';
    }

    function setLink() {
        var link = document.getElementById('myLink');
        if (!link) return false; // panel not in DOM yet
        var href = getCtx() + '/plugins/servlet/my-plugin-dashboard';
        // Only update if different (avoid useless DOM writes)
        if (link.getAttribute('href') !== href) {
            link.setAttribute('href', href);
        }
        return true;
    }

    // Run when AJS initializes (Jira pages)
    if (typeof AJS !== 'undefined' && AJS && typeof AJS.toInit === 'function') {
        AJS.toInit(function () {
            if (setLink()) return; // panel already present

            // Panel may be injected later — watch for it once
            var mo = new MutationObserver(function () {
                if (setLink()) mo.disconnect();
            });
            mo.observe(document.body, { childList: true, subtree: true });
        });
    } else {
        // Super fallback (shouldn't happen on Jira pages)
        if (!setLink()) {
            document.addEventListener('DOMContentLoaded', function () {
                setLink() || setTimeout(setLink, 0);
            });
        }
    }
})();*/
(function () {
  function setAttrs(a) {
    if (!a) return;
    if (a.getAttribute('target') !== '_blank') a.setAttribute('target', '_blank');
    a.setAttribute('rel', 'noopener noreferrer');
  }

  function init() {
    setAttrs(document.getElementById('myLink'));                   // panel button
    setAttrs(document.getElementById('my-plugin-dashboard-link')); // admin web-item
  }

  if (typeof AJS !== 'undefined' && AJS && typeof AJS.toInit === 'function') {
    AJS.toInit(init);
  } else if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();


  