(function () {
  var THUMB_MAX_BYTES = 2 * 1024 * 1024;
  var GRADIENTS = [
    'linear-gradient(135deg,#2B3A67,#6A4C93)',
    'linear-gradient(160deg,#F2994A,#BB4E75 55%,#2D2A55)',
    'linear-gradient(135deg,#1F5F5B,#3A8D7A)',
    'linear-gradient(160deg,#4B6CB7,#182848)'
  ];

  var files = [];
  var selected = null;
  var viewing = false;
  var lastFocus = null;

  var grid = document.getElementById('file-grid');
  var headerCount = document.getElementById('header-count');
  var shareTitle = document.getElementById('share-title');
  var statusMessage = document.getElementById('status-message');
  var modal = document.getElementById('modal');
  var modalDialog = document.getElementById('modal-dialog');
  var modalTitle = document.getElementById('modal-title');
  var modalSub = document.getElementById('modal-sub');
  var modalPreview = document.getElementById('modal-preview');
  var previewFrame = document.getElementById('preview-frame');
  var modalViewer = document.getElementById('modal-viewer');
  var viewerFrame = document.getElementById('viewer-frame');
  var viewerImage = document.getElementById('viewer-image');
  var modalNoPreview = document.getElementById('modal-no-preview');
  var noPreviewExt = document.getElementById('no-preview-ext');
  var btnBack = document.getElementById('btn-back');
  var btnView = document.getElementById('btn-view');
  var btnDownload = document.getElementById('btn-download');
  var btnClose = document.getElementById('modal-close');
  var btnBackdrop = document.getElementById('modal-backdrop');

  function pageToken() {
    return new URLSearchParams(window.location.search).get('t');
  }

  function withToken(path) {
    var t = pageToken();
    if (!t) return path;
    return path + (path.indexOf('?') >= 0 ? '&' : '?') + 't=' + encodeURIComponent(t);
  }

  function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    var value = bytes;
    var units = ['KB', 'MB', 'GB', 'TB'];
    var u = -1;
    while (value >= 1024 && u < units.length - 1) {
      value /= 1024;
      u++;
    }
    return value.toFixed(value >= 10 || u === 0 ? 0 : 1) + ' ' + units[u];
  }

  function formatTime(ms) {
    if (!ms) return '';
    var d = new Date(ms);
    var now = new Date();
    var sameDay = d.toDateString() === now.toDateString();
    if (sameDay) {
      return 'Today, ' + d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
    }
    var yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);
    if (d.toDateString() === yesterday.toDateString()) {
      return 'Yesterday';
    }
    return d.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
  }

  function gradientFor(name) {
    var h = 0;
    for (var i = 0; i < name.length; i++) h = (h + name.charCodeAt(i) * 17) % GRADIENTS.length;
    return GRADIENTS[h];
  }

  function isVideo(f) {
    return f.kindKey === 'VIDEO';
  }

  function isImage(f) {
    return f.kindKey === 'IMAGE';
  }

  function canView(f) {
    return f.viewable && (isVideo(f) || isImage(f));
  }

  function fileUrl(id, download) {
    var url = '/file/' + encodeURIComponent(id);
    var q = [];
    if (download) q.push('download=1');
    var t = pageToken();
    if (t) q.push('t=' + encodeURIComponent(t));
    if (q.length) url += '?' + q.join('&');
    return url;
  }

  function stopPlayback() {
    viewerFrame.src = 'about:blank';
    viewerImage.removeAttribute('src');
    viewerImage.hidden = true;
    viewerFrame.hidden = false;
  }

  function renderGrid() {
    grid.innerHTML = '';
    files.forEach(function (f) {
      var card = document.createElement('button');
      card.type = 'button';
      card.className = 'file-card';
      card.setAttribute('role', 'listitem');
      card.setAttribute('aria-label', f.name + ', ' + f.kind);

      var thumb = document.createElement('div');
      thumb.className = 'file-thumb';
      thumb.style.background = isImage(f) && f.sizeBytes <= THUMB_MAX_BYTES ? '#0B0D11' : gradientFor(f.name);
      if (isImage(f) && f.sizeBytes <= THUMB_MAX_BYTES) {
        var img = document.createElement('img');
        img.src = withToken('/file/' + encodeURIComponent(f.id));
        img.alt = '';
        img.loading = 'lazy';
        img.decoding = 'async';
        thumb.appendChild(img);
      } else if (isVideo(f)) {
        var play = document.createElement('div');
        play.className = 'file-play';
        play.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5v14l11-7z" fill="#FFFFFF"></path></svg>';
        thumb.appendChild(play);
      } else if (!isImage(f)) {
        var ext = document.createElement('div');
        ext.className = 'file-ext-label';
        ext.textContent = f.ext;
        thumb.appendChild(ext);
      }

      var badge = document.createElement('div');
      badge.className = 'file-kind-badge';
      badge.textContent = f.kind;
      thumb.appendChild(badge);

      var meta = document.createElement('div');
      meta.className = 'file-meta';
      var nameEl = document.createElement('div');
      nameEl.className = 'file-name';
      nameEl.textContent = f.name;
      var sizeEl = document.createElement('div');
      sizeEl.className = 'file-size';
      sizeEl.textContent = formatSize(f.sizeBytes);
      meta.appendChild(nameEl);
      meta.appendChild(sizeEl);

      card.appendChild(thumb);
      card.appendChild(meta);
      card.addEventListener('click', function () { openModal(f, card); });
      grid.appendChild(card);
    });
  }

  function openModal(f, cardEl) {
    selected = f;
    viewing = false;
    lastFocus = cardEl || document.activeElement;
    updateModal();
    modal.hidden = false;
    btnClose.focus();
    document.body.style.overflow = 'hidden';
  }

  function closeModal() {
    stopPlayback();
    modal.hidden = true;
    selected = null;
    viewing = false;
    document.body.style.overflow = '';
    if (lastFocus && lastFocus.focus) lastFocus.focus();
  }

  function updateModal() {
    if (!selected) return;
    var f = selected;
    modalTitle.textContent = f.name;
    modalSub.textContent = f.kind + ' · ' + formatSize(f.sizeBytes) + (f.modifiedMillis ? ' · ' + formatTime(f.modifiedMillis) : '');
    btnDownload.href = fileUrl(f.id, true);
    btnDownload.setAttribute('download', f.name);

    var showView = canView(f);
    modalDialog.classList.toggle('viewing', viewing && showView);

    modalPreview.hidden = !(showView && !viewing);
    modalViewer.hidden = !(showView && viewing);
    modalNoPreview.hidden = showView;

    btnView.hidden = !(showView && !viewing);
    btnBack.hidden = !(showView && viewing);

    if (showView && !viewing) {
      previewFrame.innerHTML = '';
      previewFrame.style.background = gradientFor(f.name);
      if (isVideo(f)) {
        var play = document.createElement('div');
        play.className = 'file-play';
        play.style.width = '64px';
        play.style.height = '64px';
        play.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5v14l11-7z" fill="#FFFFFF"></path></svg>';
        previewFrame.appendChild(play);
      } else if (isImage(f)) {
        var img = document.createElement('img');
        img.src = withToken('/file/' + encodeURIComponent(f.id));
        img.alt = f.name;
        previewFrame.style.background = '#0B0D11';
        previewFrame.appendChild(img);
      }
    }

    if (showView && viewing) {
      if (isVideo(f)) {
        viewerImage.hidden = true;
        viewerFrame.hidden = false;
        var watchSrc = withToken('/watch/' + encodeURIComponent(f.id));
        watchSrc += (watchSrc.indexOf('?') >= 0 ? '&' : '?') + 'embed=1';
        viewerFrame.src = watchSrc;
      } else if (isImage(f)) {
        viewerFrame.src = 'about:blank';
        viewerFrame.hidden = true;
        viewerImage.hidden = false;
        viewerImage.src = withToken('/file/' + encodeURIComponent(f.id));
        viewerImage.alt = f.name;
      }
    }

    if (!showView) {
      noPreviewExt.textContent = f.ext;
    }
  }

  btnClose.addEventListener('click', closeModal);
  btnBackdrop.addEventListener('click', closeModal);
  btnBack.addEventListener('click', function () {
    stopPlayback();
    viewing = false;
    updateModal();
    btnView.focus();
  });
  btnView.addEventListener('click', function () {
    viewing = true;
    updateModal();
    if (isVideo(selected)) {
      viewerFrame.focus();
    } else {
      viewerImage.focus();
    }
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && !modal.hidden) {
      e.preventDefault();
      closeModal();
    }
  });

  fetch(withToken('/api/files'), { credentials: 'same-origin', cache: 'no-store' })
    .then(function (r) {
      if (!r.ok) throw new Error('load failed');
      return r.json();
    })
    .then(function (data) {
      files = data.files || [];
      shareTitle.textContent = data.shareTitle || 'Shared files';
      headerCount.textContent = (data.fileCount || files.length) + (files.length === 1 ? ' file shared' : ' files shared');
      if (!files.length) {
        statusMessage.hidden = false;
        statusMessage.textContent = 'No files shared yet';
        statusMessage.className = 'empty-state';
        return;
      }
      renderGrid();
    })
    .catch(function () {
      statusMessage.hidden = false;
      statusMessage.textContent = 'Could not load shared files';
      statusMessage.className = 'error-state';
    });
})();
