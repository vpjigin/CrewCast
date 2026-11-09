(function () {
  "use strict";

  var BASE =
    "https://github.com/vpjigin/CrewCast/releases/latest/download/";

  var DOWNLOADS = [
    {
      id: "macos-arm64",
      os: "macOS",
      detail: "Apple Silicon (M1–M4)",
      ext: ".dmg",
      href: BASE + "Crewcast-macos-arm64.dmg",
      req: "Macs with an Apple chip",
    },
    {
      id: "macos-x64",
      os: "macOS",
      detail: "Intel",
      ext: ".dmg",
      href: BASE + "Crewcast-macos-x64.dmg",
      req: "Macs with an Intel processor",
    },
    {
      id: "windows",
      os: "Windows",
      detail: "10 / 11, 64-bit",
      ext: ".msi",
      href: BASE + "Crewcast-windows-x64.msi",
      req: "Installs a Start menu shortcut",
    },
    {
      id: "linux",
      os: "Linux",
      detail: "Debian / Ubuntu / Mint",
      ext: ".deb",
      href: BASE + "Crewcast-linux-x64.deb",
      req: "64-bit (x86_64)",
    },
  ];

  var PRIMARY = {
    mac: {
      label: "Download for Mac",
      href: BASE + "Crewcast-macos-arm64.dmg",
      note: "For Apple Silicon (M1–M4). Intel Mac or another computer? Use the arrow",
      tab: "mac",
    },
    windows: {
      label: "Download for Windows",
      href: BASE + "Crewcast-windows-x64.msi",
      note: "For Windows 10 / 11, 64-bit. Need another platform? Use the arrow",
      tab: "windows",
    },
    linux: {
      label: "Download for Linux",
      href: BASE + "Crewcast-linux-x64.deb",
      note: "For Debian / Ubuntu / Mint, 64-bit. Need another platform? Use the arrow",
      tab: "linux",
    },
    unknown: {
      label: "Download Crewcast",
      href: "#download",
      note: "Crewcast runs on your computer – pick Mac, Windows or Linux with the arrow",
      tab: "mac",
    },
  };

  function detectOs() {
    var ua = navigator.userAgent || "";
    var mobile = false;
    try {
      if (navigator.userAgentData && navigator.userAgentData.mobile === true) {
        mobile = true;
      }
    } catch (e) {}
    if (mobile || /iphone|ipod|ipad|android/i.test(ua)) return "unknown";
    // iPadOS desktop-mode UA looks like Macintosh but has a touchscreen.
    if (/macintosh/i.test(ua) && navigator.maxTouchPoints > 1) return "unknown";

    var platform = "";
    try {
      if (navigator.userAgentData && navigator.userAgentData.platform) {
        platform = String(navigator.userAgentData.platform);
      }
    } catch (e2) {}
    if (!platform) {
      platform = ua || navigator.platform || "";
    }
    var p = platform.toLowerCase();
    if (/cros/.test(p) || /cros/.test(ua.toLowerCase())) return "unknown";
    if (/mac|darwin/.test(p)) return "mac";
    if (/win/.test(p)) return "windows";
    if (/linux/.test(p)) return "linux";
    var ual = ua.toLowerCase();
    if (/mac os|macintosh/.test(ual)) return "mac";
    if (/windows/.test(ual)) return "windows";
    if (/linux/.test(ual)) return "linux";
    return "unknown";
  }

  function $(sel, root) {
    return (root || document).querySelector(sel);
  }

  function $all(sel, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(sel));
  }

  document.documentElement.classList.remove("no-js");

  var os = detectOs();
  var primary = PRIMARY[os] || PRIMARY.unknown;

  var heroBtn = $("#hero-download");
  var heroNote = $("#hero-note");
  var versionEl = $("#version-badge");
  var menuBtn = $("#download-menu-btn");
  var menu = $("#download-menu");

  if (heroBtn) {
    heroBtn.href = primary.href;
    var label = heroBtn.querySelector("[data-label]");
    if (label) label.textContent = primary.label;
  }
  if (heroNote) heroNote.textContent = primary.note;

  fetch("https://api.github.com/repos/vpjigin/CrewCast/releases/latest")
    .then(function (r) {
      if (!r.ok) throw new Error("bad status");
      return r.json();
    })
    .then(function (data) {
      if (versionEl && data && data.tag_name) {
        versionEl.textContent = "Free and open source · " + data.tag_name;
      }
    })
    .catch(function () {
      if (versionEl) versionEl.textContent = "Free and open source";
    });

  /* —— Split button menu —— */
  var menuItems = [];
  if (menu) menuItems = $all('[role="menuitem"]', menu);
  var menuOpen = false;

  function setMenuOpen(open, restoreFocus) {
    menuOpen = open;
    if (!menu || !menuBtn) return;
    menu.classList.toggle("is-open", open);
    menuBtn.setAttribute("aria-expanded", open ? "true" : "false");
    if (!open && restoreFocus) menuBtn.focus();
  }

  function closeMenu(restoreFocus) {
    if (menuOpen) setMenuOpen(false, !!restoreFocus);
  }

  if (menuBtn && menu) {
    menuBtn.addEventListener("click", function (e) {
      e.stopPropagation();
      setMenuOpen(!menuOpen, false);
      if (menuOpen && menuItems.length) menuItems[0].focus();
    });

    document.addEventListener("click", function (e) {
      if (!menuOpen) return;
      if (!menu.contains(e.target) && e.target !== menuBtn) closeMenu(false);
    });

    document.addEventListener("keydown", function (e) {
      if (!menuOpen) return;
      if (e.key === "Escape") {
        e.preventDefault();
        closeMenu(true);
      }
    });

    menu.addEventListener("keydown", function (e) {
      if (!menuOpen) return;
      var idx = menuItems.indexOf(document.activeElement);
      if (e.key === "ArrowDown") {
        e.preventDefault();
        var next = idx < 0 ? 0 : (idx + 1) % menuItems.length;
        menuItems[next].focus();
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        var prev = idx <= 0 ? menuItems.length - 1 : idx - 1;
        menuItems[prev].focus();
      } else if (e.key === "Home") {
        e.preventDefault();
        menuItems[0].focus();
      } else if (e.key === "End") {
        e.preventDefault();
        menuItems[menuItems.length - 1].focus();
      } else if (e.key === "Tab") {
        closeMenu(false);
      }
    });

    menuItems.forEach(function (item) {
      item.addEventListener("click", function (e) {
        // Keyboard activation of a link fires click with detail === 0.
        closeMenu(e.detail === 0);
      });
    });
  }

  /* —— Install tabs —— */
  var tablist = $('[role="tablist"]');
  var tabs = tablist ? $all('[role="tab"]', tablist) : [];
  var panels = $all('[role="tabpanel"]');

  function selectTab(id, focusTab) {
    tabs.forEach(function (tab) {
      var on = tab.getAttribute("data-tab") === id;
      tab.setAttribute("aria-selected", on ? "true" : "false");
      tab.tabIndex = on ? 0 : -1;
      if (on && focusTab) tab.focus();
    });
    panels.forEach(function (panel) {
      var on = panel.getAttribute("data-tab") === id;
      if (on) panel.removeAttribute("hidden");
      else panel.setAttribute("hidden", "");
    });
  }

  if (tabs.length) {
    selectTab(primary.tab || "mac", false);

    tabs.forEach(function (tab) {
      tab.addEventListener("click", function () {
        selectTab(tab.getAttribute("data-tab"), false);
      });
    });

    if (tablist) {
      tablist.addEventListener("keydown", function (e) {
        var current = document.activeElement;
        var idx = tabs.indexOf(current);
        if (idx < 0) return;
        var target = -1;
        if (e.key === "ArrowRight") target = (idx + 1) % tabs.length;
        else if (e.key === "ArrowLeft")
          target = (idx - 1 + tabs.length) % tabs.length;
        else if (e.key === "Home") target = 0;
        else if (e.key === "End") target = tabs.length - 1;
        if (target >= 0) {
          e.preventDefault();
          selectTab(tabs[target].getAttribute("data-tab"), true);
        }
      });
    }
  }

  /* —— Copy buttons —— */
  $all("[data-copy]").forEach(function (btn) {
    var original = btn.textContent;
    btn.addEventListener("click", function () {
      var text = btn.getAttribute("data-copy") || "";
      var code = btn.parentElement && btn.parentElement.querySelector("code");
      function ok() {
        btn.textContent = "Copied";
        setTimeout(function () {
          btn.textContent = original;
        }, 2000);
      }
      function fallback() {
        if (!code) return;
        var range = document.createRange();
        range.selectNodeContents(code);
        var sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
      }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(ok).catch(fallback);
      } else {
        fallback();
      }
    });
  });

  // silence unused DOWNLOADS in case tree-shaken; keep for reference parity
  void DOWNLOADS;
})();
