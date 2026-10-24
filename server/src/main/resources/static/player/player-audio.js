
(function bootstrapAudio(){
  var parts = window.location.pathname.split('/').filter(Boolean);
  var id = parts.length >= 2 && parts[parts.length - 2] === 'watch' ? parts[parts.length - 1] : parts[parts.length - 1];
  if (!id) return;

  var pendingSwitch = null;
  var stopPoll = null;
  var activeAudioIdx = null;

  function pageToken(){
    return new URLSearchParams(window.location.search).get('t');
  }
  function debugMode(){
    return new URLSearchParams(window.location.search).get('debug') === '1';
  }
  function lsKey(){ return 'crewcast-audio-' + id; }
  function legacyLsKey(){ return 'lanstream-audio-' + id; }
  function readSavedAudioChoice(){
    var savedRaw = localStorage.getItem(lsKey());
    if (savedRaw === null) {
      var legacy = localStorage.getItem(legacyLsKey());
      if (legacy !== null) {
        localStorage.setItem(lsKey(), legacy);
        savedRaw = legacy;
      }
    }
    if (savedRaw === null) return null;
    var n = parseInt(savedRaw, 10);
    return isNaN(n) ? null : n;
  }
  function streamSrc(audioIdx, includeAudioParam){
    var url = '/stream/' + encodeURIComponent(id);
    var q = [];
    if (includeAudioParam !== false && audioIdx != null) {
      q.push('audio=' + audioIdx);
    }
    var t = pageToken();
    if (t) q.push('t=' + encodeURIComponent(t));
    if (window.__crewcastCompat && window.__crewcastCompat.streamExtras) {
      var extra = window.__crewcastCompat.streamExtras();
      if (extra && extra.length) q = q.concat(extra);
    }
    if (q.length) url += '?' + q.join('&');
    return url;
  }
  function withToken(path){
    var t = pageToken();
    if (!t) return path;
    return path + (path.indexOf('?') >= 0 ? '&' : '?') + 't=' + encodeURIComponent(t);
  }
  function clientId(){
    var k = 'crewcast-client-id';
    try {
      var cid = sessionStorage.getItem(k);
      if (!cid) {
        cid = 'c-' + Math.random().toString(36).slice(2) + Date.now().toString(36);
        sessionStorage.setItem(k, cid);
      }
      return cid;
    } catch (e) {
      return '';
    }
  }
  function withClientId(path){
    var cid = clientId();
    if (!cid) return path;
    return path + (path.indexOf('?') >= 0 ? '&' : '?') + 'clientId=' + encodeURIComponent(cid);
  }
  function fetchOpts(method){
    return { method: method || 'GET', credentials: 'same-origin', cache: 'no-store' };
  }

  function showVideoError(msg){
    var el = document.getElementById('video-error');
    if (!el) return;
    el.textContent = msg;
    el.hidden = false;
  }
  function clearVideoError(){
    var el = document.getElementById('video-error');
    if (el) { el.textContent = ''; el.hidden = true; }
  }

  function bindVideoErrors(video, ctx){
    if (!video || video.__crewcastErrBound) return;
    video.__crewcastErrBound = true;
    video.addEventListener('error', function(){
      if (pendingSwitch) return;
      var e = video.error;
      if (!e) {
        showVideoError('Playback failed (unknown media error).');
        return;
      }
      if (window.__crewcastCompat && window.__crewcastCompat.onPlaybackError) {
        var retried = window.__crewcastCompat.onPlaybackError(e.code, {
          onToggle: ctx.onCompatToggle,
          restartPlayback: ctx.restartPlayback
        });
        if (retried) return;
      }
      var codes = {1:'MEDIA_ERR_ABORTED',2:'MEDIA_ERR_NETWORK',3:'MEDIA_ERR_DECODE',4:'MEDIA_ERR_SRC_NOT_SUPPORTED'};
      var msg = 'Playback failed: ' + (codes[e.code] || ('code ' + e.code)) +
        (e.message ? ' — ' + e.message : '') + '.';
      if (e.code === 3 || e.code === 4) {
        if (window.__crewcastCompat && !window.__crewcastCompat.isActive()) {
          var canAuto = window.__crewcastCompat.canAutoEnableAfterError
            && window.__crewcastCompat.canAutoEnableAfterError();
          if (!canAuto && window.__crewcastCompat.compatUnavailableMessage) {
            msg += ' ' + window.__crewcastCompat.compatUnavailableMessage();
          } else if (!canAuto) {
            msg += ' Try refreshing.';
          }
        }
      } else {
        msg += ' Try refreshing.';
      }
      var multiAudio = ctx.multiAudioTracks;
      if ((e.code === 3 || e.code === 4) && multiAudio) {
        msg += ' You can also try switching audio track.';
      }
      showVideoError(msg);
    });
  }

  function trackNeedsPrepare(tr){
    return !tr.ready && !tr.browserPlayable;
  }

  function filesHomeHref(){
    var p = new URLSearchParams();
    var t = pageToken();
    if (t) p.set('t', t);
    var folder = new URLSearchParams(window.location.search).get('folder');
    if (folder) p.set('folder', folder);
    var qs = p.toString();
    return qs ? '/?' + qs : '/';
  }

  var backLink = document.getElementById('back-to-files');
  if (backLink) {
    backLink.href = filesHomeHref();
  }

  fetch(withClientId(withToken('/api/videos/' + encodeURIComponent(id))), fetchOpts())
    .then(function(r){ if(!r.ok) throw new Error('not found'); return r.json(); })
    .then(function(data){
      document.title = data.title;
      window.__crewcastVideo = data;
      if (window.__crewcastInitSubtitles) {
        window.__crewcastInitSubtitles(data, id, withToken, fetchOpts);
      }
      var video = document.getElementById('video');
      var thumbVideo = document.getElementById('thumb-video');

      function restartPlaybackAtCurrentTrack(autoplay){
        var idx = activeAudioIdx != null ? activeAudioIdx : (data.selectedAudio || 0);
        attachWhenReady(idx, autoplay !== false);
      }

      var multiAudioTracks = data.audioTracks && data.audioTracks.length > 1;
      bindVideoErrors(video, {
        multiAudioTracks: multiAudioTracks,
        onCompatToggle: function(){ restartPlaybackAtCurrentTrack(false); },
        restartPlayback: function(){ restartPlaybackAtCurrentTrack(false); }
      });

      var compatReady = window.__crewcastCompat
        ? window.__crewcastCompat.init({
            data: data,
            id: id,
            video: video,
            withToken: withToken,
            fetchOpts: fetchOpts,
            showVideoError: showVideoError,
            onToggle: function(){
              restartPlaybackAtCurrentTrack(false);
            }
          })
        : Promise.resolve();
      compatReady = compatReady || Promise.resolve();

      var tracksByIndex = {};
      (data.audioTracks || []).forEach(function(tr){ tracksByIndex[tr.index] = tr; });

      function trackLabel(idx){
        var tr = tracksByIndex[idx];
        if (!tr) return 'audio track ' + idx;
        return tr.languageName || tr.lang || ('Track ' + idx);
      }

      var savedChoice = readSavedAudioChoice();

      var hevc = document.getElementById('hevc-notice');
      if (hevc && data.warning && debugMode()) {
        hevc.textContent = data.warning;
        hevc.hidden = false;
      }

      var audioSelect = document.getElementById('audio-select');
      var audioNone = document.getElementById('audio-none');
      var prepCancel = document.getElementById('audio-prep-cancel');
      var pickerOverlay = document.getElementById('audio-picker-overlay');
      var pickerList = document.getElementById('audio-picker-list');

      function showPrep(pct, msg){
        var ov = document.getElementById('audio-prep-overlay');
        var tx = document.getElementById('audio-prep-text');
        if (!ov || !tx) return;
        ov.hidden = false;
        tx.textContent = msg || ('Preparing audio for your browser… ' + (pct||0) + '%');
        if (prepCancel) prepCancel.hidden = !pendingSwitch;
      }
      function hidePrep(){
        var ov = document.getElementById('audio-prep-overlay');
        if (ov) ov.hidden = true;
        if (prepCancel) prepCancel.hidden = true;
      }
      function hidePicker(){
        if (pickerOverlay) pickerOverlay.hidden = true;
      }
      function showPicker(preselectIdx){
        if (!pickerOverlay || !pickerList) return;
        pickerList.innerHTML = '';
        data.audioTracks.forEach(function(tr){
          var btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'audio-picker-btn';
          var title = tr.languageName + ' · ' + tr.codec + ' · ' + tr.channels + 'ch';
          if (tr.title) title = tr.languageName + ' — ' + tr.title + ' · ' + tr.codec;
          var tag = tr.ready ? 'Ready' : ('First time: ' + (tr.prepareTimeHint || 'a few minutes'));
          btn.innerHTML = '<span class="tag">' + tag + '</span>' + title +
            '<span class="sub">' + (tr.ready ? 'Plays immediately' : 'One-time conversion on this device') + '</span>';
          btn.addEventListener('click', function(){
            hidePicker();
            localStorage.setItem(lsKey(), String(tr.index));
            if (audioSelect) audioSelect.value = String(tr.index);
            startPlaybackForTrack(tr.index, false);
          });
          pickerList.appendChild(btn);
        });
        pickerOverlay.hidden = false;
      }

      function cancelPendingSwitch(){
        if (!pendingSwitch) return;
        if (stopPoll) { stopPoll(); stopPoll = null; }
        var ps = pendingSwitch;
        pendingSwitch = null;
        hidePrep();
        clearVideoError();
        if (audioSelect) audioSelect.value = String(activeAudioIdx);
        if (ps.wasPlaying) video.play().catch(function(){});
      }
      if (prepCancel) {
        prepCancel.addEventListener('click', function(e){
          e.stopPropagation();
          cancelPendingSwitch();
        });
      }

      function postPrepare(idx){
        return fetch(withClientId(withToken('/api/videos/' + encodeURIComponent(id) + '/audio/prepare?audio=' + idx)),
          fetchOpts('POST')).then(function(r){ return r.json(); });
      }

      function pollUntilReady(idx, onReady, onFailed){
        var statusUrl = withClientId(withToken('/api/videos/' + encodeURIComponent(id) + '/audio-status?audio=' + idx));
        var stopped = false;
        function tick(){
          if (stopped) return;
          fetch(statusUrl, fetchOpts())
            .then(function(r){ return r.json(); })
            .then(function(st){
              if (st.status === 'READY') {
                stopped = true;
                onReady();
                return;
              }
              if (st.status === 'FAILED') {
                stopped = true;
                if (onFailed) onFailed(st);
                return;
              }
              showPrep(st.progress, 'Preparing ' + trackLabel(idx) + ' audio… ' + (st.progress || 0) + '%');
              setTimeout(tick, 2000);
            })
            .catch(function(){
              setTimeout(tick, 2000);
            });
        }
        tick();
        return function(){ stopped = true; };
      }

      var hasAudioTracks = data.hasAudio === true
        || (data.audioTracks && data.audioTracks.length > 0);

      function applyStreamToVideo(idx, autoplay){
        clearVideoError();
        if (hasAudioTracks && idx != null) {
          localStorage.setItem(lsKey(), String(idx));
          activeAudioIdx = idx;
        }
        var url = streamSrc(idx, hasAudioTracks);
        video.pause();
        video.removeAttribute('src');
        video.load();
        if (thumbVideo) {
          thumbVideo.pause();
          thumbVideo.removeAttribute('src');
          thumbVideo.load();
        }
        video.src = url;
        if (thumbVideo) thumbVideo.src = url;
        video.load();
        if (autoplay) video.play().catch(function(){});
      }

      function waitForMetadataThen(cb){
        if (video.readyState >= 1 && isFinite(video.duration) && video.duration > 0) {
          cb();
          return;
        }
        video.addEventListener('loadedmetadata', function once(){
          video.removeEventListener('loadedmetadata', once);
          cb();
        });
      }

      function attachWhenReady(idx, autoplay, onFail){
        if (stopPoll) { stopPoll(); stopPoll = null; }
        pendingSwitch = null;

        function startPlayback(){
          hidePrep();
          applyStreamToVideo(idx, autoplay);
        }

        function runAttach(){
          var tr = tracksByIndex[idx];
          if (window.__crewcastCompat && window.__crewcastCompat.isActive()) {
            window.__crewcastCompat.prepareIfNeeded(idx, {
              id: id,
              withToken: withToken,
              fetchOpts: fetchOpts,
              showPrep: showPrep,
              startPlayback: startPlayback,
              onFail: onFail || function failed(st){
                showVideoError(st.message || 'Compatibility conversion failed');
              }
            });
            return;
          }
          if (!tr || tr.ready || tr.browserPlayable) {
            startPlayback();
            return;
          }

          postPrepare(idx).then(function(st){
          if (st.status === 'READY') {
            startPlayback();
            return;
          }
          showPrep(st.progress || 0, 'Preparing ' + trackLabel(idx) + ' audio… ' + (st.progress || 0) + '%');
          stopPoll = pollUntilReady(idx, startPlayback, onFail || function failed(st){
            showVideoError(st.message || 'Audio preparation failed');
          });
          }).catch(function(){
            if (onFail) onFail({ message: 'Could not start audio preparation' });
            else showVideoError('Could not start audio preparation');
          });
        }

        compatReady.then(function(){ runAttach(); });
      }

      function startPlaybackForTrack(idx, autoplay){
        attachWhenReady(idx, autoplay);
      }

      function switchAudio(idx){
        if (idx === activeAudioIdx && !pendingSwitch) return;
        if (pendingSwitch && pendingSwitch.targetIdx === idx) return;

        if (stopPoll) { stopPoll(); stopPoll = null; }

        var savedTime = video.currentTime || 0;
        var wasPlaying = !video.paused;
        if (wasPlaying) video.pause();

        pendingSwitch = { targetIdx: idx, savedTime: savedTime, wasPlaying: wasPlaying };

        function completeSwitch(){
          var ps = pendingSwitch;
          if (!ps || ps.targetIdx !== idx) return;
          pendingSwitch = null;
          hidePrep();
          applyStreamToVideo(idx, false);
          waitForMetadataThen(function(){
            try { video.currentTime = ps.savedTime; } catch(e){}
            if (ps.wasPlaying) video.play().catch(function(){});
          });
        }

        function abortSwitch(message){
          if (!pendingSwitch || pendingSwitch.targetIdx !== idx) return;
          pendingSwitch = null;
          hidePrep();
          if (audioSelect) audioSelect.value = String(activeAudioIdx);
          showVideoError(message || 'Could not switch audio track.');
        }

        if (window.__crewcastCompat && window.__crewcastCompat.isActive()) {
          pendingSwitch = { targetIdx: idx, savedTime: savedTime, wasPlaying: wasPlaying };
          window.__crewcastCompat.prepareForSwitch(idx, {
            id: id,
            withToken: withToken,
            fetchOpts: fetchOpts,
            showPrep: showPrep,
            onReady: completeSwitch,
            onFailed: function(st){
              abortSwitch(st.message || 'Compatibility conversion failed');
            }
          });
          return;
        }

        var tr = tracksByIndex[idx];
        if (tr && tr.ready) {
          pendingSwitch = { targetIdx: idx, savedTime: savedTime, wasPlaying: wasPlaying };
          completeSwitch();
          return;
        }

        postPrepare(idx).then(function(st){
          if (st.status === 'READY') {
            completeSwitch();
            return;
          }
          showPrep(st.progress || 0, 'Preparing ' + trackLabel(idx) + ' audio… ' + (st.progress || 0) + '%');
          stopPoll = pollUntilReady(idx, completeSwitch, function failed(st){
            abortSwitch(st.message || 'Audio preparation failed for ' + trackLabel(idx) + '.');
          });
        }).catch(function(){
          abortSwitch('Could not reach the server to prepare ' + trackLabel(idx) + ' audio.');
        });
      }

      window.__crewcastSwitchAudio = switchAudio;

      if (audioSelect && data.audioTracks && data.audioTracks.length >= 1) {
        audioSelect.hidden = false;
        data.audioTracks.forEach(function(tr){
          var opt = document.createElement('option');
          opt.value = tr.index;
          var label = tr.languageName + ' · ' + tr.codec + ' · ' + tr.channels + 'ch';
          if (tr.title) label = tr.languageName + ' — ' + tr.title + ' · ' + tr.codec;
          opt.textContent = label;
          audioSelect.appendChild(opt);
        });
        audioSelect.addEventListener('change', function(){
          var idx = parseInt(audioSelect.value, 10);
          if (isNaN(idx)) return;
          switchAudio(idx);
        });
      }

      if (!hasAudioTracks) {
        if (audioSelect) {
          audioSelect.hidden = true;
          audioSelect.innerHTML = '';
        }
        if (audioNone) audioNone.hidden = false;
        compatReady.then(function(){
          if (window.__crewcastCompat && window.__crewcastCompat.isActive()) {
            window.__crewcastCompat.prepareIfNeeded(0, {
              id: id,
              withToken: withToken,
              fetchOpts: fetchOpts,
              showPrep: showPrep,
              startPlayback: function(){
                applyStreamToVideo(null, false);
                video.play().catch(function(){});
              },
              onFail: function(st){ showVideoError(st.message || 'Compatibility conversion failed'); }
            });
          } else {
            applyStreamToVideo(null, false);
            video.play().catch(function(){});
          }
        });
        if (!data.chaptersEnabled) {
          var btn0 = document.getElementById('btn-chapters');
          var overlay0 = document.getElementById('chapter-overlay');
          if (btn0) btn0.style.display = 'none';
          if (overlay0) overlay0.style.display = 'none';
        }
        if (typeof startPlayer === 'function') startPlayer();
        return;
      }
      if (audioNone) audioNone.hidden = true;
      if (audioSelect && data.audioTracks.length === 1) {
        audioSelect.value = String(data.audioTracks[0].index);
      }

      var multi = data.audioTracks && data.audioTracks.length > 1;
      var anyPrepare = data.audioTracks && data.audioTracks.some(trackNeedsPrepare);

      if (multi && anyPrepare && savedChoice === null) {
        showPicker(data.selectedAudio);
      } else {
        var idx = savedChoice !== null ? savedChoice : (data.selectedAudio || 0);
        if (audioSelect) audioSelect.value = String(idx);
        if (!multi && anyPrepare) {
          startPlaybackForTrack(idx, false);
        } else if (tracksByIndex[idx] && tracksByIndex[idx].ready) {
          startPlaybackForTrack(idx, false);
        } else if (anyPrepare) {
          startPlaybackForTrack(idx, false);
        } else {
          startPlaybackForTrack(idx, false);
        }
      }

      if (!data.chaptersEnabled) {
        var btn = document.getElementById('btn-chapters');
        var overlay = document.getElementById('chapter-overlay');
        if (btn) btn.style.display = 'none';
        if (overlay) overlay.style.display = 'none';
      }
      if (typeof startPlayer === 'function') startPlayer();
    })
    .catch(function(){ document.body.innerHTML = '<p style="color:#fff;font-family:sans-serif;padding:1rem">Video not found</p>'; });
})();
