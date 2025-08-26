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
    function getCtx() {
      var meta = document.querySelector('meta[name="ajs-context-path"]');
      if (meta && meta.getAttribute('content')) return meta.getAttribute('content');
      if (typeof AJS !== 'undefined' && AJS && typeof AJS.contextPath === 'function') {
        try { return AJS.contextPath(); } catch (e) {}
      }
      return '';
    }
  
    function wireLink() {
      // *** Sidebar web-item <a> id'si: my-plugin-dashboard-link ***
      var link = document.getElementById('my-plugin-dashboard-link');
      if (!link) return false;
  
      var href = getCtx() + '/plugins/servlet/my-plugin-dashboard';
  
      // href farklıysa güncelle
      if (link.getAttribute('href') !== href) {
        link.setAttribute('href', href);
      }
  
      // YENİ SEKMEDE AÇ
      if (link.getAttribute('target') !== '_blank') {
        link.setAttribute('target', '_blank');
      }
      // Güvenlik (tabnabbing koruması)
      link.setAttribute('rel', 'noopener noreferrer');
  
      return true;
    }
  
    // Jira sayfalarında AJS hazır olunca çalıştır
    if (typeof AJS !== 'undefined' && AJS && typeof AJS.toInit === 'function') {
      AJS.toInit(function () {
        if (wireLink()) return;
  
        // Link sonradan DOM'a gelirse izle
        var mo = new MutationObserver(function () {
          if (wireLink()) mo.disconnect();
        });
        mo.observe(document.body, { childList: true, subtree: true });
      });
    } else {
      // Çok nadir fallback
      if (!wireLink()) {
        document.addEventListener('DOMContentLoaded', function () {
          wireLink() || setTimeout(wireLink, 0);
        });
      }
    }
  })();
  

  (function () {
    function getCtx() {
      var meta = document.querySelector('meta[name="ajs-context-path"]');
      if (meta && meta.getAttribute('content')) return meta.getAttribute('content');
      if (typeof AJS !== 'undefined' && AJS && typeof AJS.contextPath === 'function') {
        try { return AJS.contextPath(); } catch (e) {}
      }
      return '';
    }
  
    // Hedef URL (context path ile)
    function dashboardUrl() {
      return getCtx() + '/plugins/servlet/my-plugin-dashboard';
    }
  
    // Capturing-phase global click interceptor
    function captureOpenInNewTab(e) {
      // En yakın <a> öğesini bul
      var a = e.target && e.target.closest && e.target.closest('a');
      if (!a) return;
  
      // Sadece bizim linkler: ID veya href eşleşmesi
      var url = a.getAttribute('href') || '';
      var abs = dashboardUrl();
      var isTarget =
        a.id === 'my-plugin-dashboard-link' ||
        url.endsWith('/plugins/servlet/my-plugin-dashboard') ||
        url === abs;
  
      if (!isTarget) return;
  
      // Kullanıcı zaten yeni sekme istiyorsa (Cmd/Ctrl/Middle) dokunma
      if (e.metaKey || e.ctrlKey || e.button === 1) return;
  
      // Default davranışı durdur ve biz açalım
      e.preventDefault();
      e.stopPropagation();
      if (typeof e.stopImmediatePropagation === 'function') e.stopImmediatePropagation();
  
      // URL'i normalize et (relative ise context ekle)
      var finalUrl = url || abs;
      if (finalUrl.charAt(0) === '/') finalUrl = getCtx() + finalUrl;
  
      var win = window.open(finalUrl, '_blank');
      if (win) { try { win.opener = null; } catch (err) {} }
    }
  
    // AJS hazır olduğunda kur; yoksa DOMContentLoaded
    if (typeof AJS !== 'undefined' && AJS && typeof AJS.toInit === 'function') {
      AJS.toInit(function () {
        document.addEventListener('click', captureOpenInNewTab, true); // << capturing!
        // Sonradan gelen nodelar için ayrı ek işleme gerek yok; global dinleyici yeterli
      });
    } else {
      document.addEventListener('DOMContentLoaded', function () {
        document.addEventListener('click', captureOpenInNewTab, true);
      });
    }
  })();
  