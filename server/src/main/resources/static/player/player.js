
if (typeof startPlayer !== 'function') {
  (function bootstrapFallback(){
    var parts = window.location.pathname.split('/').filter(Boolean);
    var id = parts.length >= 2 && parts[parts.length - 2] === 'watch' ? parts[parts.length - 1] : parts[parts.length - 1];
    if (!id) return;
    fetch('/api/videos/' + encodeURIComponent(id))
      .then(function(r){ if(!r.ok) throw new Error('not found'); return r.json(); })
      .then(function(data){
        document.title = data.title;
        document.getElementById('video').src = '/stream/' + data.id;
        document.getElementById('thumb-video').src = '/stream/' + data.id;
        startPlayer();
      })
      .catch(function(){ document.body.innerHTML = '<p style="color:#fff;font-family:sans-serif;padding:1rem">Video not found</p>'; });
  })();
}

function startPlayer(){

  var player=document.getElementById('player');
  var video=document.getElementById('video');
  var tapZone=document.getElementById('tap-zone');
  var centerIcon=document.getElementById('center-icon');
  var iconPlay=document.getElementById('icon-play');
  var iconPause=document.getElementById('icon-pause');
  var seek=document.getElementById('seek');
  var timeEl=document.getElementById('time');
  var btnPlay=document.getElementById('btn-play');
  var btnPlayIcon=document.getElementById('btn-play-icon');
  var btnPauseIcon=document.getElementById('btn-pause-icon');
  var btnBack=document.getElementById('btn-back');
  var btnForward=document.getElementById('btn-forward');
  var btnMute=document.getElementById('btn-mute');
  var iconVol=document.getElementById('icon-vol');
  var iconMuted=document.getElementById('icon-muted');
  var speedSelect=document.getElementById('speed-select');
  var btnFullscreen=document.getElementById('btn-fullscreen');
  var btnChapters=document.getElementById('btn-chapters');
  var chapterOverlay=document.getElementById('chapter-overlay');
  var btnChapterClose=document.getElementById('btn-chapter-close');
  var chapterGrid=document.getElementById('chapter-grid');
  var thumbVideo=document.getElementById('thumb-video');
  var thumbCanvas=document.getElementById('thumb-canvas');
  var hideTimer=null;
  var seeking=false;
  var centerFlashTimer=null;
  var SEGMENTS=20;
  var segmentTimes=[];
  var thumbUrls=new Array(SEGMENTS);
  var thumbFailed=new Array(SEGMENTS);
  var chapterGridBuilt=false;
  var thumbsComplete=false;
  var thumbsGenerating=false;
  var wasPlayingBeforeChapters=false;
  var THUMB_SEEK_TIMEOUT=4000;

  function fmt(sec){
    if(!isFinite(sec)||sec<0) sec=0;
    sec=Math.floor(sec);
    var h=Math.floor(sec/3600), m=Math.floor((sec%3600)/60), s=sec%60;
    var ss=s<10?'0'+s:''+s;
    if(h>0){
      var mm=m<10?'0'+m:''+m;
      return h+':'+mm+':'+ss;
    }
    return m+':'+ss;
  }

  function updateTime(){
    var cur=video.currentTime||0, dur=video.duration||0;
    timeEl.textContent=fmt(cur)+' / '+fmt(dur);
    if(!seeking&&dur>0){
      seek.value=Math.round((cur/dur)*1000);
    }
  }

  function setPlayUi(playing){
    btnPlayIcon.style.display=playing?'none':'block';
    btnPauseIcon.style.display=playing?'block':'none';
    btnPlay.setAttribute('aria-label',playing?'Pause':'Play');
  }

  function flashCenter(showPause){
    iconPlay.style.display=showPause?'none':'block';
    iconPause.style.display=showPause?'block':'none';
    centerIcon.classList.add('show');
    clearTimeout(centerFlashTimer);
    centerFlashTimer=setTimeout(function(){centerIcon.classList.remove('show');},600);
  }

  function showControls(){
    player.classList.remove('controls-hidden');
    resetHideTimer();
  }

  function resetHideTimer(){
    clearTimeout(hideTimer);
    if(!video.paused){
      hideTimer=setTimeout(function(){
        player.classList.add('controls-hidden');
      },3000);
    }
  }

  function togglePlay(){
    if(video.paused){video.play();}else{video.pause();}
  }

  tapZone.addEventListener('click',function(){
    showControls();
    var willPlay=video.paused;
    togglePlay();
    flashCenter(willPlay);
  });

  btnPlay.addEventListener('click',function(e){
    e.stopPropagation();
    showControls();
    togglePlay();
  });

  btnBack.addEventListener('click',function(e){
    e.stopPropagation();
    showControls();
    video.currentTime=Math.max(0,video.currentTime-10);
  });

  btnForward.addEventListener('click',function(e){
    e.stopPropagation();
    showControls();
    var dur=video.duration||Infinity;
    video.currentTime=Math.min(dur,video.currentTime+10);
  });

  btnMute.addEventListener('click',function(e){
    e.stopPropagation();
    showControls();
    video.muted=!video.muted;
    iconVol.style.display=video.muted?'none':'block';
    iconMuted.style.display=video.muted?'block':'none';
    btnMute.setAttribute('aria-label',video.muted?'Unmute':'Mute');
  });

  speedSelect.addEventListener('change',function(e){
    e.stopPropagation();
    showControls();
    video.playbackRate=parseFloat(speedSelect.value);
  });

  btnFullscreen.addEventListener('click',function(e){
    e.stopPropagation();
    showControls();
    var el=player;
    if(document.fullscreenElement||document.webkitFullscreenElement){
      (document.exitFullscreen||document.webkitExitFullscreen).call(document);
    }else{
      (el.requestFullscreen||el.webkitRequestFullscreen).call(el);
    }
  });

  function buildSegmentTimes(dur){
    segmentTimes=[];
    for(var i=0;i<SEGMENTS;i++){
      segmentTimes.push((dur/SEGMENTS)*i);
    }
  }

  function updateChapterTile(i){
    var tile=chapterGrid.children[i];
    if(!tile) return;
    var img=tile.querySelector('.chapter-thumb');
    tile.querySelector('.chapter-label').textContent=fmt(segmentTimes[i]);
    tile.classList.remove('loading','failed');
    if(thumbUrls[i]){
      img.src=thumbUrls[i];
    }else if(thumbFailed[i]){
      tile.classList.add('failed');
      img.removeAttribute('src');
    }else{
      tile.classList.add('loading');
      img.removeAttribute('src');
    }
  }

  function ensureChapterGrid(){
    if(chapterGridBuilt) return;
    for(var i=0;i<SEGMENTS;i++){
      var tile=document.createElement('button');
      tile.type='button';
      tile.className='chapter-tile loading';
      tile.dataset.index=''+i;
      tile.innerHTML='<img class="chapter-thumb" alt=""><span class="spinner" aria-hidden="true"></span><span class="failed" aria-hidden="true"></span><span class="chapter-label"></span>';
      tile.addEventListener('click',function(e){
        e.stopPropagation();
        var idx=parseInt(this.dataset.index,10);
        video.currentTime=segmentTimes[idx];
        closeChapters(true);
      });
      chapterGrid.appendChild(tile);
    }
    chapterGridBuilt=true;
  }

  function refreshChapterGrid(){
    for(var i=0;i<SEGMENTS;i++){
      updateChapterTile(i);
    }
  }

  function captureThumb(index,time,cb){
    console.log('[chapters] seeking to '+time+' (tile '+index+')');
    var done=false;
    function finish(url){
      if(done) return;
      done=true;
      clearTimeout(timer);
      thumbVideo.removeEventListener('seeked',onSeeked);
      cb(url);
    }
    function onSeeked(){
      console.log('[chapters] seeked event fired for tile '+index+' at '+time);
      try{
        var w=thumbVideo.videoWidth,h=thumbVideo.videoHeight;
        if(!w||!h){
          console.log('[chapters] error for tile '+index+': missing video dimensions');
          finish(null);
          return;
        }
        thumbCanvas.width=w;
        thumbCanvas.height=h;
        var ctx=thumbCanvas.getContext('2d');
        ctx.drawImage(thumbVideo,0,0,w,h);
        var url=thumbCanvas.toDataURL('image/jpeg',0.6);
        console.log('[chapters] captured thumbnail for tile '+index);
        finish(url);
      }catch(err){
        console.log('[chapters] error for tile '+index+':',err);
        finish(null);
      }
    }
    var timer=setTimeout(function(){
      console.log('[chapters] timeout for tile '+index+' at '+time);
      finish(null);
    },THUMB_SEEK_TIMEOUT);
    thumbVideo.addEventListener('seeked',onSeeked);
    try{
      if(Math.abs(thumbVideo.currentTime-time)<0.05){
        onSeeked();
      }else{
        thumbVideo.currentTime=time;
      }
    }catch(err){
      console.log('[chapters] error for tile '+index+':',err);
      finish(null);
    }
  }

  function startCaptureLoop(){
    console.log('[chapters] beginning sequential capture');
    var i=0;
    (function next(){
      if(i>=SEGMENTS){
        thumbsGenerating=false;
        thumbsComplete=true;
        console.log('[chapters] thumbnail generation complete');
        return;
      }
      if(thumbUrls[i]||thumbFailed[i]){
        i++;
        next();
        return;
      }
      var idx=i;
      captureThumb(idx,segmentTimes[idx],function(url){
        if(url){
          thumbUrls[idx]=url;
        }else{
          thumbFailed[idx]=true;
          console.log('[chapters] marked tile '+idx+' as failed');
        }
        updateChapterTile(idx);
        i++;
        next();
      });
    })();
  }

  function generateThumbnails(){
    if(thumbsComplete||thumbsGenerating) return;
    thumbsGenerating=true;
    console.log('[chapters] starting thumbnail generation');
    console.log('[chapters] calling thumbVideo.load()');
    thumbVideo.addEventListener('loadedmetadata',function(){
      console.log('[chapters] loadedmetadata event fired, duration='+thumbVideo.duration);
      startCaptureLoop();
    },{once:true});
    thumbVideo.load();
  }

  function openChapters(){
    var dur=video.duration;
    if(!isFinite(dur)||dur<=0) return;
    wasPlayingBeforeChapters=!video.paused;
    if(wasPlayingBeforeChapters) video.pause();
    buildSegmentTimes(dur);
    ensureChapterGrid();
    refreshChapterGrid();
    chapterOverlay.classList.add('open');
    chapterOverlay.setAttribute('aria-hidden','false');
    player.classList.remove('controls-hidden');
    clearTimeout(hideTimer);
    generateThumbnails();
  }

  function closeChapters(resumePlayback){
    chapterOverlay.classList.remove('open');
    chapterOverlay.setAttribute('aria-hidden','true');
    if(resumePlayback){
      video.play();
    }else if(wasPlayingBeforeChapters){
      video.play();
    }
  }

  btnChapters.addEventListener('click',function(e){
    e.stopPropagation();
    showControls();
    openChapters();
  });

  btnChapterClose.addEventListener('click',function(e){
    e.stopPropagation();
    closeChapters(false);
  });

  seek.addEventListener('input',function(){
    seeking=true;
    showControls();
    var dur=video.duration||0;
    if(dur>0){
      video.currentTime=(seek.value/1000)*dur;
    }
  });
  seek.addEventListener('change',function(){seeking=false;});

  video.addEventListener('timeupdate',updateTime);
  video.addEventListener('loadedmetadata',updateTime);
  video.addEventListener('durationchange',updateTime);

  video.addEventListener('play',function(){
    setPlayUi(true);
    showControls();
  });
  video.addEventListener('pause',function(){
    setPlayUi(false);
    player.classList.remove('controls-hidden');
    clearTimeout(hideTimer);
  });

  player.addEventListener('mousemove',showControls);
  player.addEventListener('touchstart',showControls,{passive:true});
  document.getElementById('top-bar').addEventListener('click',function(e){e.stopPropagation();showControls();});
  document.getElementById('bottom-bar').addEventListener('click',function(e){e.stopPropagation();showControls();});

  document.addEventListener('fullscreenchange',function(){
    btnFullscreen.setAttribute('aria-label',
      document.fullscreenElement?'Exit fullscreen':'Fullscreen');
  });

  setPlayUi(false);
  updateTime();
}
