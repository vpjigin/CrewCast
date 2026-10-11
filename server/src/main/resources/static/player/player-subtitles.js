
(function(){
  function lsKey(id){ return 'crewcast-captions-' + id; }

  function readSaved(id){
    var raw = localStorage.getItem(lsKey(id));
    if (raw === null) return null;
    if (raw === 'off') return 'off';
    var n = parseInt(raw, 10);
    return isNaN(n) ? null : n;
  }

  function writeSaved(id, value){
    localStorage.setItem(lsKey(id), value === 'off' ? 'off' : String(value));
  }

  window.__crewcastInitSubtitles = function(data, id, withToken, fetchOpts){
    var video = document.getElementById('video');
    var select = document.getElementById('captions-select');
    var notice = document.getElementById('captions-notice');
    if (!video || !select) return;

    var tracks = (data.subtitles || []).filter(function(t){ return t.supported; });
    if (!tracks.length) return;

    select.hidden = false;
    var offOpt = document.createElement('option');
    offOpt.value = 'off';
    offOpt.textContent = 'Off';
    select.appendChild(offOpt);

    tracks.forEach(function(tr){
      var opt = document.createElement('option');
      opt.value = String(tr.index);
      var label = tr.languageName;
      if (tr.source === 'external') label += ' · sidecar';
      else label += ' · embedded';
      opt.textContent = label;
      select.appendChild(opt);
    });

    var activeTrackEl = null;
    var loadErrorFor = null;

    function showMenuNotice(msg){
      if (!notice) return;
      notice.textContent = msg || '';
      notice.hidden = !msg;
    }

    function subtitleUrl(index){
      return withToken('/subtitles/' + encodeURIComponent(id) + '/' + index + '.vtt');
    }

    function removeTrackElement(){
      if (activeTrackEl && activeTrackEl.parentNode) {
        activeTrackEl.parentNode.removeChild(activeTrackEl);
      }
      activeTrackEl = null;
      for (var i = 0; i < video.textTracks.length; i++) {
        video.textTracks[i].mode = 'disabled';
      }
    }

    function applyCaptionIndex(index, silent){
      showMenuNotice('');
      removeTrackElement();
      if (index === 'off') {
        writeSaved(id, 'off');
        return;
      }
      var idx = typeof index === 'number' ? index : parseInt(index, 10);
      if (isNaN(idx)) return;
      writeSaved(id, idx);

      var wasPaused = video.paused;
      var t = video.currentTime;

      var trackEl = document.createElement('track');
      trackEl.kind = 'subtitles';
      trackEl.label = 'Captions';
      trackEl.srclang = 'en';
      trackEl.src = subtitleUrl(idx);
      trackEl.default = true;
      video.appendChild(trackEl);
      activeTrackEl = trackEl;

      function enableWhenReady(){
        var tt = trackEl.track;
        if (!tt) {
          requestAnimationFrame(enableWhenReady);
          return;
        }
        tt.mode = 'showing';
        if (!silent && !wasPaused) {
          video.play().catch(function(){});
        }
        if (Math.abs(video.currentTime - t) > 0.05) {
          video.currentTime = t;
        }
      }

      trackEl.addEventListener('load', function(){
        loadErrorFor = null;
        enableWhenReady();
      });
      trackEl.addEventListener('error', function(){
        if (loadErrorFor === idx) return;
        loadErrorFor = idx;
        showMenuNotice('Captions unavailable for this track.');
        ttDisable();
      });

      function ttDisable(){
        if (trackEl.track) trackEl.track.mode = 'disabled';
      }

      enableWhenReady();
    }

    function pickDefaultIndex(){
      var saved = readSaved(id);
      if (saved === 'off') return 'off';
      if (saved !== null && tracks.some(function(t){ return t.index === saved; })) {
        return saved;
      }
      var mode = (data.defaultCaptions || 'OFF').toUpperCase();
      if (mode === 'OFF') return 'off';
      if (mode === 'ENGLISH') {
        var en = tracks.find(function(t){
          return (t.language || '').toLowerCase().indexOf('en') === 0 || t.languageName === 'English';
        });
        return en ? en.index : 'off';
      }
      if (mode === 'PREFERRED_AUDIO_LANGUAGE') {
        var audioIdx = data.selectedAudio;
        var audio = (data.audioTracks || []).find(function(a){ return a.index === audioIdx; });
        var lang = audio && audio.lang ? audio.lang.toLowerCase() : '';
        if (lang) {
          var match = tracks.find(function(t){
            return (t.language || '').toLowerCase().indexOf(lang) === 0;
          });
          if (match) return match.index;
        }
      }
      return 'off';
    }

    var initial = pickDefaultIndex();
    select.value = initial === 'off' ? 'off' : String(initial);
    if (initial !== 'off') {
      applyCaptionIndex(initial, true);
    }

    select.addEventListener('change', function(){
      var v = select.value;
      if (v === 'off') applyCaptionIndex('off', false);
      else applyCaptionIndex(parseInt(v, 10), false);
    });

    document.addEventListener('keydown', function(e){
      if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT' || e.target.tagName === 'TEXTAREA')) {
        return;
      }
      if (e.key === 'c' || e.key === 'C') {
        var opts = select.options;
        var cur = select.selectedIndex;
        var next = (cur + 1) % opts.length;
        select.selectedIndex = next;
        select.dispatchEvent(new Event('change'));
        e.preventDefault();
      }
    });
  };
})();
