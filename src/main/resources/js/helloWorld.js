(function () {
    function getCtx() {
        // Prefer meta tag; fall back to AJS if available; else empty
        var meta = document.querySelector('meta[name="ajs-context-path"]');
        return (meta && meta.getAttribute('content')) ||
            (typeof AJS !== 'undefined' && AJS.contextPath ? AJS.contextPath() : '') ||
            '';
    }

    function setLink() {
        var link = document.getElementById('myLink');
        if (!link) return false; // panel not in DOM yet
        var href = getCtx() + '/plugins/servlet/my-plugin-dashboard';
        link.setAttribute('href', href);
        return true;
    }

    // Run when AJS initializes
    if (typeof AJS !== 'undefined' && AJS.toInit) {
        AJS.toInit(function () {
            if (setLink()) return;

            // Panel may be injected after AJS init — watch for it
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
})();
