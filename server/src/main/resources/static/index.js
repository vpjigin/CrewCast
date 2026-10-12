(function () {
  var THUMB_MAX_BYTES = 2 * 1024 * 1024;
  var CHUNK_SIZE = 200;
  var GRADIENTS = [
    'linear-gradient(135deg,#2B3A67,#6A4C93)',
    'linear-gradient(160deg,#F2994A,#BB4E75 55%,#2D2A55)',
    'linear-gradient(135deg,#1F5F5B,#3A8D7A)',
    'linear-gradient(160deg,#4B6CB7,#182848)'
  ];

  var folders = [];
  var files = [];
  var visibleLimit = CHUNK_SIZE;
  var selected = null;
  var lastFocus = null;
  var imageFiles = [];
  var imageIndex = 0;
  var imageActualSize = false;

  var grid = document.getElementById('file-grid');
  var headerCount = document.getElementById('header-count');
  var shareTitle = document.getElementById('share-title');
  var statusMessage = document.getElementById('status-message');
  var browseNav = document.getElementById('browse-nav');
  var btnBack = document.getElementById('btn-back');
  var breadcrumbEl = document.getElementById('breadcrumb');
  var btnShowMore = document.getElementById('btn-show-more');
  var btnDownloadAll = document.getElementById('btn-download-all');
  var modal = document.getElementById('modal');
  var modalTitle = document.getElementById('modal-title');
  var modalSub = document.getElementById('modal-sub');
  var modalPreview = document.getElementById('modal-preview');
  var previewFrame = document.getElementById('preview-frame');
  var modalNoPreview = document.getElementById('modal-no-preview');
  var noPreviewExt = document.getElementById('no-preview-ext');
  var btnView = document.getElementById('btn-view');
  var btnViewLabel = document.getElementById('btn-view-label');
  var btnDownload = document.getElementById('btn-download');
  var btnClose = document.getElementById('modal-close');
  var btnBackdrop = document.getElementById('modal-backdrop');

  var imageViewer = document.getElementById('image-viewer');
  var imageViewerTitle = document.getElementById('image-viewer-title');
  var imageViewerImg = document.getElementById('image-viewer-img');
  var imageViewerStage = document.getElementById('image-viewer-stage');
  var imageViewerDownload = document.getElementById('image-viewer-download');
  var imageViewerClose = document.getElementById('image-viewer-close');
  var imageNavPrev = document.getElementById('image-nav-prev');
  var imageNavNext = document.getElementById('image-nav-next');

  function pageToken() {
    return new URLSearchParams(window.location.search).get('t');
  }

  function currentFolderId() {
    return new URLSearchParams(window.location.search).get('folder') || '';
  }

  function withToken(path) {
    var t = pageToken();
    if (!t) return path;
    return path + (path.indexOf('?') >= 0 ? '&' : '?') + 't=' + encodeURIComponent(t);
  }

  function downloadAllUrl() {
    var q = [];
    var t = pageToken();
    if (t) q.push('t=' + encodeURIComponent(t));
    var folder = currentFolderId();
    if (folder) q.push('folder=' + encodeURIComponent(folder));
    return '/api/download-all' + (q.length ? '?' + q.join('&') : '');
  }

  function updateDownloadAllButton() {
    if (!btnDownloadAll) return;
    var hasContent = files.length > 0 || folders.length > 0;
    btnDownloadAll.hidden = !hasContent;
    if (!hasContent) return;
    btnDownloadAll.href = downloadAllUrl();
    var parts = [];
    if (files.length > 0) {
      var total = 0;
      files.forEach(function (f) { total += f.sizeBytes || 0; });
      parts.push(files.length + (files.length === 1 ? ' file' : ' files') + ' on this page · ' + formatSize(total));
    }
    if (folders.length > 0) {
      parts.push(folders.length + (folders.length === 1 ? ' folder' : ' folders') + ' (full contents included in zip)');
    }
    btnDownloadAll.title = parts.join(' · ');
    btnDownloadAll.setAttribute('aria-label', 'Download all as zip. ' + parts.join('. '));
  }

  function browseApiUrl(folderId) {
    var q = [];
    var t = pageToken();
    if (t) q.push('t=' + encodeURIComponent(t));
    if (folderId) q.push('folder=' + encodeURIComponent(folderId));
    return '/api/browse' + (q.length ? '?' + q.join('&') : '');
  }

  function navigateToFolder(folderId, pushHistory) {
    var p = new URLSearchParams(window.location.search);
    var t = pageToken();
    if (folderId) p.set('folder', folderId);
    else p.delete('folder');
    if (t) p.set('t', t);
    var url = '/?' + p.toString();
    if (pushHistory) history.pushState({ folder: folderId || '' }, '', url);
    else history.replaceState({ folder: folderId || '' }, '', url);
    loadBrowse();
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

  function viewActionLabel(f) {
    return isVideo(f) ? 'Watch' : 'View';
  }

  function viewActionDescription(f) {
    if (isVideo(f)) return 'Watch ' + f.name;
    if (isImage(f)) return 'View ' + f.name;
    return 'View';
  }

  function updateViewActionButton(f) {
    if (!btnViewLabel) return;
    var label = viewActionLabel(f);
    btnViewLabel.textContent = label;
    btnView.setAttribute('aria-label', viewActionDescription(f));
    btnView.setAttribute('title', viewActionDescription(f));
  }

  function watchUrl(id) {
    var q = [];
    var t = pageToken();
    if (t) q.push('t=' + encodeURIComponent(t));
    var folder = currentFolderId();
    if (folder) q.push('folder=' + encodeURIComponent(folder));
    return '/watch/' + encodeURIComponent(id) + (q.length ? '?' + q.join('&') : '');
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

  function rebuildImageList() {
    imageFiles = files.filter(isImage);
  }

  function folderIconSvg() {
    return '<svg width="40" height="40" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="#9AA3B2" stroke-width="1.6" stroke-linejoin="round"><path d="M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V7z"></path></svg>';
  }

  function renderBreadcrumb(items) {
    breadcrumbEl.innerHTML = '';
    if (!items || !items.length || (items.length === 1 && !currentFolderId())) {
      browseNav.hidden = true;
      return;
    }
    browseNav.hidden = false;
    var inFolder = currentFolderId().length > 0;
    btnBack.hidden = !inFolder;
    items.forEach(function (item, idx) {
      var li = document.createElement('li');
      li.className = 'breadcrumb-item';
      if (idx > 0) {
        var sep = document.createElement('span');
        sep.className = 'breadcrumb-sep';
        sep.setAttribute('aria-hidden', 'true');
        sep.textContent = '›';
        li.appendChild(sep);
      }
      if (idx === items.length - 1) {
        var current = document.createElement('span');
        current.className = 'breadcrumb-current';
        current.textContent = item.name;
        li.appendChild(current);
      } else {
        var link = document.createElement('button');
        link.type = 'button';
        link.className = 'breadcrumb-link';
        link.textContent = item.name;
        (function (id) {
          link.addEventListener('click', function () {
            navigateToFolder(id, true);
          });
        })(item.id || '');
        li.appendChild(link);
      }
      breadcrumbEl.appendChild(li);
    });
  }

  function createFolderCard(folder) {
    var card = document.createElement('button');
    card.type = 'button';
    card.className = 'file-card folder-card';
    card.setAttribute('role', 'listitem');
    card.setAttribute('aria-label', folder.name + ', ' + folder.itemCount + ' items');

    var thumb = document.createElement('div');
    thumb.className = 'file-thumb folder-thumb';
    thumb.style.background = gradientFor(folder.name);
    thumb.innerHTML = folderIconSvg();

    var badge = document.createElement('div');
    badge.className = 'file-kind-badge';
    badge.textContent = 'Folder';
    thumb.appendChild(badge);

    var meta = document.createElement('div');
    meta.className = 'file-meta';
    var nameEl = document.createElement('div');
    nameEl.className = 'file-name';
    nameEl.textContent = folder.name;
    var sizeEl = document.createElement('div');
    sizeEl.className = 'file-size';
    var n = folder.itemCount;
    sizeEl.textContent = n + (n === 1 ? ' item' : ' items');
    meta.appendChild(nameEl);
    meta.appendChild(sizeEl);

    card.appendChild(thumb);
    card.appendChild(meta);
    card.addEventListener('click', function () {
      navigateToFolder(folder.id, true);
    });
    return card;
  }

  function createFileCard(f) {
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
    return card;
  }

  function renderGrid() {
    grid.innerHTML = '';
    statusMessage.hidden = true;
    var total = folders.length + files.length;
    if (!total) {
      statusMessage.hidden = false;
      statusMessage.textContent = currentFolderId() ? 'This folder is empty' : 'No files shared yet';
      statusMessage.className = 'empty-state';
      btnShowMore.hidden = true;
      rebuildImageList();
      return;
    }
    var shown = 0;
    var i;
    for (i = 0; i < folders.length && shown < visibleLimit; i++) {
      grid.appendChild(createFolderCard(folders[i]));
      shown++;
    }
    for (i = 0; i < files.length && shown < visibleLimit; i++) {
      grid.appendChild(createFileCard(files[i]));
      shown++;
    }
    btnShowMore.hidden = shown >= total;
    rebuildImageList();
  }

  function openModal(f, cardEl) {
    selected = f;
    lastFocus = cardEl || document.activeElement;
    updateModal();
    modal.hidden = false;
    btnClose.focus();
    document.body.style.overflow = 'hidden';
  }

  function closeModal() {
    modal.hidden = true;
    selected = null;
    document.body.style.overflow = imageViewer.hidden ? '' : 'hidden';
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
    modalPreview.hidden = !showView;
    modalNoPreview.hidden = showView;
    btnView.hidden = !showView;
    if (showView) {
      updateViewActionButton(f);
    }

    if (showView) {
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
    } else {
      noPreviewExt.textContent = f.ext;
    }
  }

  function showImageAtIndex(idx) {
    if (!imageFiles.length) return;
    imageIndex = (idx + imageFiles.length) % imageFiles.length;
    var f = imageFiles[imageIndex];
    imageActualSize = false;
    imageViewerTitle.textContent = f.name;
    imageViewerImg.src = withToken('/file/' + encodeURIComponent(f.id));
    imageViewerImg.alt = f.name;
    imageViewerImg.classList.remove('actual-size');
    imageViewerDownload.href = fileUrl(f.id, true);
    imageViewerDownload.setAttribute('download', f.name);
    var multi = imageFiles.length > 1;
    imageNavPrev.hidden = !multi;
    imageNavNext.hidden = !multi;
    imageViewer.hidden = false;
    document.body.style.overflow = 'hidden';
    imageViewerClose.focus();
  }

  function closeImageViewer() {
    imageViewer.hidden = true;
    imageViewerImg.removeAttribute('src');
    document.body.style.overflow = '';
    if (lastFocus && lastFocus.focus) lastFocus.focus();
  }

  function openImageViewer(f) {
    rebuildImageList();
    var idx = imageFiles.findIndex(function (item) { return item.id === f.id; });
    showImageAtIndex(idx >= 0 ? idx : 0);
  }

  btnClose.addEventListener('click', closeModal);
  btnBackdrop.addEventListener('click', closeModal);
  btnView.addEventListener('click', function () {
    if (!selected) return;
    if (isVideo(selected)) {
      window.location.href = watchUrl(selected.id);
      return;
    }
    if (isImage(selected)) {
      var imgFile = selected;
      closeModal();
      openImageViewer(imgFile);
    }
  });

  btnBack.addEventListener('click', function () {
    history.back();
  });

  btnShowMore.addEventListener('click', function () {
    visibleLimit += CHUNK_SIZE;
    renderGrid();
  });

  imageViewerClose.addEventListener('click', closeImageViewer);
  imageNavPrev.addEventListener('click', function () { showImageAtIndex(imageIndex - 1); });
  imageNavNext.addEventListener('click', function () { showImageAtIndex(imageIndex + 1); });
  imageViewerImg.addEventListener('click', function () {
    imageActualSize = !imageActualSize;
    imageViewerImg.classList.toggle('actual-size', imageActualSize);
    if (!imageActualSize) {
      imageViewerStage.scrollTop = 0;
      imageViewerStage.scrollLeft = 0;
    }
  });

  document.addEventListener('keydown', function (e) {
    if (!imageViewer.hidden) {
      if (e.key === 'Escape') {
        e.preventDefault();
        closeImageViewer();
        return;
      }
      if (e.key === 'ArrowLeft' && !imageNavPrev.hidden) {
        e.preventDefault();
        showImageAtIndex(imageIndex - 1);
        return;
      }
      if (e.key === 'ArrowRight' && !imageNavNext.hidden) {
        e.preventDefault();
        showImageAtIndex(imageIndex + 1);
        return;
      }
    }
    if (e.key === 'Escape' && !modal.hidden) {
      e.preventDefault();
      closeModal();
    }
  });

  window.addEventListener('popstate', function () {
    loadBrowse();
  });

  function loadBrowse() {
    visibleLimit = CHUNK_SIZE;
    var folder = currentFolderId();
    fetch(browseApiUrl(folder), { credentials: 'same-origin', cache: 'no-store' })
      .then(function (r) {
        if (r.status === 404) throw new Error('not found');
        if (!r.ok) throw new Error('load failed');
        return r.json();
      })
      .then(function (data) {
        folders = data.folders || [];
        files = data.files || [];
        shareTitle.textContent = data.title || 'Shared';
        if (data.headerSummary) {
          headerCount.textContent = data.headerSummary;
        } else {
          headerCount.textContent = '';
        }
        renderBreadcrumb(data.breadcrumb || []);
        renderGrid();
        updateDownloadAllButton();
      })
      .catch(function (err) {
        if (err && err.message === 'not found' && currentFolderId()) {
          navigateToFolder('', true);
          return;
        }
        folders = [];
        files = [];
        grid.innerHTML = '';
        browseNav.hidden = true;
        btnShowMore.hidden = true;
        statusMessage.hidden = false;
        statusMessage.textContent = err && err.message === 'not found'
            ? 'Folder not found'
            : 'Could not load shared files';
        statusMessage.className = err && err.message === 'not found' ? 'empty-state' : 'error-state';
        updateDownloadAllButton();
      });
  }

  loadBrowse();
})();
