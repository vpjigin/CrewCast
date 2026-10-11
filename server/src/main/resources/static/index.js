fetch('/api/videos', { credentials: 'same-origin' })
  .then(function(r){ return r.json(); })
  .then(function(items){
    var ul = document.getElementById('video-list');
    if (!items.length) {
      ul.innerHTML = '<li>No videos shared</li>';
      return;
    }
    items.forEach(function(v){
      var li = document.createElement('li');
      var a = document.createElement('a');
      var t = new URLSearchParams(window.location.search).get('t');
      a.href = '/watch/' + encodeURIComponent(v.id) + (t ? ('?t=' + encodeURIComponent(t)) : '');
      a.textContent = v.title;
      li.appendChild(a);
      ul.appendChild(li);
    });
  })
  .catch(function(){
    document.getElementById('video-list').innerHTML = '<li>Could not load videos</li>';
  });
