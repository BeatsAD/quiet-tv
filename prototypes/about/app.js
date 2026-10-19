// ES5 syntax and DOM APIs keep the bundled page usable in Android 6 WebView.
function $(selector) { return document.querySelector(selector); }
var paths = {
  back: '<path d="m14 6-6 6 6 6M8 12h13"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7h.01"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  settings: '<path d="m9 3 1-2h4l1 2 3 2 3 1v4l-2 2v3l2 2-2 4-3-1-3 1-2 2-4-2v-3l-2-2-3-1v-4l3-1 2-2z" transform="translate(1 0) scale(.9)"/><circle cx="12" cy="12" r="3"/>',
  network: '<rect x="9" y="2" width="6" height="6"/><path d="M12 8v5M5 13h14M5 13v3M19 13v3"/><rect x="2" y="16" width="6" height="6"/><rect x="16" y="16" width="6" height="6"/>',
  input: '<path d="M6 3h12v8l-4 4v6h-4v-6l-4-4zM10 6v4M14 6v4"/>'
};

function each(selector, callback, root) {
  var nodes=(root || document).querySelectorAll(selector);
  for(var i=0;i<nodes.length;i++)callback(nodes[i]);
}
function icons(root) {
  each('[data-icon]',function(el) {
    el.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true">'+paths[el.dataset.icon]+'</svg>';
  },root);
}
icons();
var embedded=document.body.classList.contains('embedded');
if(!window.CSS || !CSS.supports || !CSS.supports('font-variation-settings','normal'))document.body.className+=' legacy-webview';
function query(name) {
  var fields=location.search.substring(1).split('&');
  for(var i=0;i<fields.length;i++) {
    var pair=fields[i].split('=');
    if(decodeURIComponent(pair[0])===name)return decodeURIComponent((pair[1] || '').replace(/\+/g,' '));
  }
  return '';
}
if(embedded) {
  $('#app-name').textContent=query('name');
  $('#version-name').textContent='版本 '+query('version');
  $('#version-build').textContent='构建 '+query('build')+' · '+(query('debug')==='true'?'测试版':'正式版');
}
var currentPage='about',lastFocus=$('#back-home'),toastTimer;
function resize() {
  var full=embedded || document.fullscreenElement===$('#viewport');
  var available=full?innerWidth:Math.min(innerWidth-(innerWidth<900?24:64),1600);
  var scale=full?Math.min(innerWidth/1920,innerHeight/1080):available/1920;
  $('#viewport').style.width=1920*scale+'px';
  $('#viewport').style.height=1080*scale+'px';
  $('#tv').style.transform='scale('+scale+')';
  if(embedded) {
    $('#tv').style.left=(innerWidth-1920*scale)/2+'px';
    $('#tv').style.top=(innerHeight-1080*scale)/2+'px';
  }
}
addEventListener('resize',resize);
document.addEventListener('fullscreenchange',resize);
resize();
function showPage(page) {
  if(embedded && page==='home'){location.href='exit';return;}
  currentPage=page;
  $('#home').hidden=page!=='home';$('#about').hidden=page!=='about';
  each('[data-preview]',function(el){el.classList.toggle('selected',el.dataset.preview===page);});
  (page==='home'?$('#entry-about'):$('#back-home')).focus();
}
each('[data-preview]',function(el){el.onclick=function(){showPage(el.dataset.preview);};});
$('#back-home').onclick=function(){showPage('home');};
if(!embedded) {
  $('#entry-about').onclick=function(){showPage('about');};
  $('#fullscreen').onclick=function(){
    try {
      var result=document.fullscreenElement?document.exitFullscreen():$('#viewport').requestFullscreen();
      if(result && result.then)result.then(function(){lastFocus.focus();},function(){toast('当前浏览器暂不支持全屏，请放大预览窗口');});
    }catch(error){toast('当前浏览器暂不支持全屏，请放大预览窗口');}
  };
}
function toast(message) {
  clearTimeout(toastTimer);$('#toast').textContent=message;$('#toast').hidden=false;
  toastTimer=setTimeout(function(){$('#toast').hidden=true;},2600);
}
each('[data-demo]',function(el){el.onclick=function(){toast('演示入口：'+el.dataset.demo+'。本次可体验「关于」功能');};});
function focusables() {
  var result=[];
  each('button,a[href]',function(el){if(el.getClientRects().length)result.push(el);},$('#'+currentPage));
  return result;
}
$('#tv').addEventListener('focusin',function(event){if(event.target!==$('#tv'))lastFocus=event.target;});
function navigate(key) {
  var items=focusables();
  if(key==='Escape' || key==='Backspace') {
    if(currentPage==='about')showPage('home');else toast('已在桌面首页');
    return;
  }
  if(items.indexOf(document.activeElement)<0) {
    var fallback=items.indexOf(lastFocus)>=0?lastFocus:items[0];
    if(fallback)fallback.focus();
  }
  var active=document.activeElement;
  if(key==='Enter'){active.click();return;}
  if(currentPage==='about') {
    var routes={
      'back-home':{ArrowRight:'license-link',ArrowDown:'license-link'},
      'license-link':{ArrowUp:'back-home',ArrowLeft:'back-home',ArrowRight:'upstream-link'},
      'upstream-link':{ArrowUp:'back-home',ArrowLeft:'license-link'}
    };
    var route=routes[active.id],next=route && route[key];
    if(next)$('#'+next).focus();
    return;
  }
  var vertical=key==='ArrowUp' || key==='ArrowDown';
  var direction=key==='ArrowLeft' || key==='ArrowUp'?-1:1;
  var rect=active.getBoundingClientRect(),x=rect.left+rect.width/2,y=rect.top+rect.height/2;
  var candidates=[];
  for(var i=0;i<items.length;i++) {
    if(items[i]===active)continue;
    var bounds=items[i].getBoundingClientRect();
    var dx=bounds.left+bounds.width/2-x,dy=bounds.top+bounds.height/2-y;
    var primary=vertical?dy:dx,secondary=vertical?dx:dy;
    if(primary*direction>5)candidates.push({el:items[i],score:Math.abs(primary)+Math.abs(secondary)*2.5});
  }
  candidates.sort(function(a,b){return a.score-b.score;});
  if(candidates[0])candidates[0].el.focus();
}
document.addEventListener('keydown',function(event){
  if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Enter','Escape','Backspace'].indexOf(event.key)<0)return;
  var active=document.activeElement;
  while(active && !$('#tv').contains(active) && !active.classList.contains('remote'))active=active.parentElement;
  if(!active)return;
  event.preventDefault();navigate(event.key);
});
each('[data-key]',function(el){
  el.onmousedown=function(event){event.preventDefault();};
  el.onclick=function(){navigate(el.dataset.key);};
});
$('#back-home').focus();
if(embedded)window.quietTVAbout={
  ready:false,move:navigate,
  activate:function(){navigate('Enter');},
  focusInitial:function(){$('#back-home').focus();},
  restoreLicense:function(){$('#license-link').focus();}
};
