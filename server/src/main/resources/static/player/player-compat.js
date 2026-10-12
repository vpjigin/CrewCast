
(function(){
  var compatEnabled = false;
  var userChangedThisVideo = false;
  var stopCompatPoll = null;
  var viewerMayPrepare = true;
  var hostCompatState = 'NEEDED';
  var encoderAvailable = true;
  var risky = false;
  var autoRetriedAfterError = false;
  var videoId = null;

  var PREP_PREFIX = 'Preparing a compatible version for this device…';

  var TV_UA = /smart-tv|smarttv|tizen|web0s|webos|hbbtv|netcast|viera|bravia|philipstv|appletv|googletv|android tv|crkey|aft|silk\/|roku|dlnadoc|tv safari|\stv\b/i;

  function lsGlobalKey(){ return 'crewcast-compat-default'; }
  function lsVideoKey(id){ return 'crewcast-compat-' + id; }

  function readLs(key){
    try { return localStorage.getItem(key); } catch(e){ return null; }
  }
  function writeLs(key, val){
    try { localStorage.setItem(key, val); } catch(e){}
  }

  function userOptedOut(id){
    return readLs(lsVideoKey(id)) === '0';
  }

  function isTvLikeUa(){
    return TV_UA.test(navigator.userAgent || '');
  }

  function videoDims(data){
    var v = data.videoFormat || {};
    var w = data.width || v.width || 1920;
    var h = data.height || v.height || 1080;
    var fps = v.frameRate || 24;
    if (fps <= 0 || fps > 120) fps = 24;
    return { width: w, height: h, fps: fps };
  }

  function riskyFlag(data){
    if (data.risky === true || data.compatRecommended === true) return true;
    return false;
  }

  function mp4CodecString(data){
    var codec = (data.codec || data.videoCodec || '').toLowerCase();
    var depth = data.bitDepth || (data.videoFormat && data.videoFormat.bitDepth) || 8;
    var profile = (data.profile || (data.videoFormat && data.videoFormat.profile) || '').toLowerCase();
    if (codec.indexOf('hevc') >= 0 || codec.indexOf('h265') >= 0) {
      if (depth > 8 || profile.indexOf('main 10') >= 0 || profile.indexOf('main10') >= 0) {
        return 'video/mp4; codecs="hvc1.2.4.L153.B0"';
      }
      return 'video/mp4; codecs="hvc1.1.6.L93.B0"';
    }
    if (codec === 'h264' || codec === 'avc1' || codec.indexOf('avc') >= 0) {
      if (depth > 8 || profile.indexOf('high 10') >= 0 || profile.indexOf('high10') >= 0) {
        return 'video/mp4; codecs="avc1.6E0033"';
      }
      return 'video/mp4; codecs="avc1.640028"';
    }
    if (codec === 'vp9') return 'video/webm; codecs="vp9"';
    if (codec === 'av1') return 'video/mp4; codecs="av01.0.05M.08"';
    if (codec) return 'video/mp4; codecs="' + codec + '"';
    return 'video/mp4; codecs="avc1.640028"';
  }

  function canPlayTypeForFile(data){
    var v = document.createElement('video');
    var ct = mp4CodecString(data);
    var t = v.canPlayType(ct);
    if (t === 'probably') return { supported: true, smooth: true };
    if (t === 'maybe') return { supported: true, smooth: false };
    if (t === '') return { supported: false, smooth: false };
    return { supported: null, smooth: null };
  }

  function decodingInfoForFile(data, dims){
    var contentType = mp4CodecString(data);
    if (!navigator.mediaCapabilities || !navigator.mediaCapabilities.decodingInfo) {
      return Promise.resolve(canPlayTypeForFile(data));
    }
    return navigator.mediaCapabilities.decodingInfo({
      type: 'file',
      video: {
        contentType: contentType,
        width: dims.width,
        height: dims.height,
        bitrate: 8000000,
        framerate: dims.fps
      }
    }).then(function(r){
      if (!r) return canPlayTypeForFile(data);
      return { supported: !!r.supported, smooth: !!r.smooth };
    }).catch(function(){
      return canPlayTypeForFile(data);
    });
  }

  function shouldAutoEnable(data, decode, mcHevcMain10, id){
    if (userOptedOut(id)) return false;
    var r = riskyFlag(data);
    var state = data.compatState || (data.compat && data.compat.status) || 'NEEDED';
    if (!r) {
      if (decode && decode.supported === false) return true;
      if (decode && decode.supported === true && decode.smooth === false) return true;
      return false;
    }
    if (isTvLikeUa()) return true;
    if (String(state).toUpperCase() === 'READY') return true;
    if (decode && decode.supported === false) return true;
    if (decode && decode.supported === true && decode.smooth === false) return true;
    var codec = (data.codec || data.videoCodec || '').toLowerCase();
    var depth = data.bitDepth || (data.videoFormat && data.videoFormat.bitDepth) || 8;
    if ((codec.indexOf('hevc') >= 0 || codec.indexOf('h265') >= 0) && depth > 8) {
      if (mcHevcMain10 === true) return false;
      if (mcHevcMain10 === false) return true;
      var v = document.createElement('video');
      var t = v.canPlayType('video/mp4; codecs="hvc1.2.4.L153.B0"');
      return t !== 'probably' && t !== 'maybe';
    }
    return false;
  }

  function prepMessage(progress){
    if (progress != null && progress > 0) {
      return PREP_PREFIX + ' ' + progress + '%';
    }
    return PREP_PREFIX;
  }

  function pollCompat(id, audioIdx, withToken, fetchOpts, showPrep, onReady, onFail){
    var statusUrl = withToken('/api/videos/' + encodeURIComponent(id) + '/compat-status?audio=' + audioIdx);
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
            if (onFail) onFail(st);
            return;
          }
          showPrep(st.progress, prepMessage(st.progress));
          setTimeout(tick, 2000);
        })
        .catch(function(){ setTimeout(tick, 2000); });
    }
    tick();
    return function(){ stopped = true; };
  }

  function postCompatPrepare(id, audioIdx, withToken, fetchOpts){
    return fetch(withToken('/api/videos/' + encodeURIComponent(id) + '/compat/prepare?audio=' + audioIdx),
      fetchOpts('POST')).then(function(r){ return r.json(); });
  }

  function compatUnavailableMessage(){
    if (!encoderAvailable) {
      return 'This computer cannot convert video for this device. Try another device or play the file with a native player like VLC.';
    }
    if (!viewerMayPrepare) {
      return 'The host has not prepared a compatible version of this video.';
    }
    return 'Compatibility conversion is not available for this video.';
  }

  function setCompatEnabled(on, id, toggle, opts){
    compatEnabled = on;
    if (toggle) toggle.checked = on;
    if (on) writeLs(lsVideoKey(id), '1');
    if (opts && opts.onToggle) opts.onToggle(on);
  }

  window.__crewcastCompat = {
    isActive: function(){ return compatEnabled; },
    streamExtras: function(){
      return compatEnabled ? ['compat=1'] : [];
    },
    compatUnavailableMessage: compatUnavailableMessage,
    prepareIfNeeded: function(idx, ctx){
      if (!compatEnabled) return false;
      if (stopCompatPoll) { stopCompatPoll(); stopCompatPoll = null; }
      var id = ctx.id;
      function go(){
        ctx.startPlayback();
      }
      function pollOnly(){
        ctx.showPrep(0, prepMessage(0));
        stopCompatPoll = pollCompat(id, idx, ctx.withToken, ctx.fetchOpts, ctx.showPrep, go, ctx.onFail);
      }
      if (hostCompatState === 'READY') {
        go();
        return true;
      }
      if (hostCompatState === 'QUEUED' || hostCompatState === 'CONVERTING') {
        pollOnly();
        return true;
      }
      if (!viewerMayPrepare) {
        if (ctx.onFail) ctx.onFail({ message: compatUnavailableMessage() });
        return true;
      }
      if (!encoderAvailable) {
        if (ctx.onFail) ctx.onFail({ message: compatUnavailableMessage() });
        return true;
      }
      postCompatPrepare(id, idx, ctx.withToken, ctx.fetchOpts).then(function(st){
        if (st.status === 'READY') {
          go();
          return;
        }
        if (st.status === 'FAILED') {
          if (ctx.onFail) ctx.onFail(st);
          return;
        }
        ctx.showPrep(st.progress || 0, prepMessage(st.progress));
        stopCompatPoll = pollCompat(id, idx, ctx.withToken, ctx.fetchOpts, ctx.showPrep, go, ctx.onFail);
      }).catch(function(){
        if (ctx.onFail) ctx.onFail({ message: 'Could not start compatibility conversion' });
      });
      return true;
    },
    prepareForSwitch: function(idx, ctx){
      if (!compatEnabled) return false;
      if (stopCompatPoll) { stopCompatPoll(); stopCompatPoll = null; }
      function pollOnly(){
        ctx.showPrep(0, prepMessage(0));
        stopCompatPoll = pollCompat(ctx.id, idx, ctx.withToken, ctx.fetchOpts, ctx.showPrep, ctx.onReady, ctx.onFailed);
      }
      if (!viewerMayPrepare && hostCompatState !== 'READY') {
        if (ctx.onFailed) ctx.onFailed({ message: compatUnavailableMessage() });
        return true;
      }
      if (!encoderAvailable) {
        if (ctx.onFailed) ctx.onFailed({ message: compatUnavailableMessage() });
        return true;
      }
      if (!viewerMayPrepare) {
        pollOnly();
        return true;
      }
      postCompatPrepare(ctx.id, idx, ctx.withToken, ctx.fetchOpts).then(function(st){
        if (st.status === 'READY') {
          ctx.onReady();
          return;
        }
        if (st.status === 'FAILED') {
          ctx.onFailed(st);
          return;
        }
        ctx.showPrep(st.progress || 0, prepMessage(st.progress));
        stopCompatPoll = pollCompat(ctx.id, idx, ctx.withToken, ctx.fetchOpts, ctx.showPrep, ctx.onReady, ctx.onFailed);
      }).catch(function(){
        ctx.onFailed({ message: 'Could not reach the server for compatibility conversion' });
      });
      return true;
    },
    canAutoEnableAfterError: function(){
      if (compatEnabled || autoRetriedAfterError || userOptedOut(videoId)) return false;
      if (String(hostCompatState).toUpperCase() === 'READY') return true;
      if (!encoderAvailable) return false;
      if (!viewerMayPrepare) return false;
      return true;
    },
    onPlaybackError: function(mediaErrorCode, ctx){
      if (mediaErrorCode !== 3 && mediaErrorCode !== 4) return false;
      if (!window.__crewcastCompat.canAutoEnableAfterError()) return false;
      autoRetriedAfterError = true;
      var toggle = document.getElementById('compat-toggle');
      setCompatEnabled(true, videoId, toggle, { onToggle: ctx.onToggle });
      if (ctx.restartPlayback) ctx.restartPlayback();
      return true;
    },
    init: function(opts){
      var data = opts.data;
      var id = opts.id;
      videoId = id;
      var toggleWrap = document.getElementById('compat-toggle-wrap');
      var toggle = document.getElementById('compat-toggle');
      risky = riskyFlag(data);
      if (!risky && !data.compatEncoderAvailable) {
        if (toggleWrap) toggleWrap.hidden = true;
        return Promise.resolve();
      }
      if (toggleWrap) toggleWrap.hidden = false;

      var dims = videoDims(data);
      viewerMayPrepare = data.compatViewerPrepareAllowed !== false;
      encoderAvailable = data.compatEncoderAvailable !== false;
      hostCompatState = data.compatState || (data.compat && data.compat.status) || 'NEEDED';

      return decodingInfoForFile(data, dims).then(function(decode){
        var perVideo = readLs(lsVideoKey(id));
        var globalPref = readLs(lsGlobalKey());
        var mcMain10 = null;
        if (decode && decode.supported === true) {
          mcMain10 = decode.smooth;
        }

        if (perVideo === '1') compatEnabled = true;
        else if (perVideo === '0') compatEnabled = false;
        else if (globalPref === '1') compatEnabled = true;
        else if (globalPref === '0') compatEnabled = false;
        else compatEnabled = shouldAutoEnable(data, decode, mcMain10, id);

        if (toggle) {
          toggle.checked = compatEnabled;
          toggle.disabled = !encoderAvailable;
          toggle.addEventListener('change', function(){
            userChangedThisVideo = true;
            compatEnabled = toggle.checked;
            writeLs(lsVideoKey(id), compatEnabled ? '1' : '0');
            writeLs(lsGlobalKey(), compatEnabled ? '1' : '0');
            if (opts.onToggle) opts.onToggle(compatEnabled);
          });
        }

        if (!encoderAvailable && compatEnabled) {
          opts.showVideoError(compatUnavailableMessage());
          compatEnabled = false;
          if (toggle) toggle.checked = false;
        }
      });
    }
  };
})();
