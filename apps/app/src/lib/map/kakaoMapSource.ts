// 카카오 지도 화면의 단일 원본.
// 네이티브는 이 CSS/스크립트를 WebView용 HTML로 감싸 쓰고(buildHtml),
// 웹은 iframe 없이 페이지에서 그대로 실행한다. 두 경로가 갈리지 않도록 여기서만 관리한다.

/**
 * 지도 스크립트가 결과를 알릴 때 쓰는 헬퍼. mapScript보다 먼저 정의돼 있어야 한다
 * (없으면 kakao.maps.load 콜백에서 `post is not defined`로 죽어 ready가 영영 안 온다).
 */
export const MAP_POST_FN = `function post(msg){ try{ if(window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify(msg)); }catch(e){} }`;

/** #map 컨테이너와 핀 마커 스타일 */
export const MAP_CSS = `  html,body,#map{margin:0;padding:0;width:100%;height:100%;background:#EAF0E6}
  .pin{display:inline-block;min-width:30px;text-align:center;padding:3px 8px;border-radius:999px;
       color:#fff;font-size:12px;font-weight:800;box-shadow:0 2px 6px rgba(0,0,0,.25);
       font-family:-apple-system,system-ui,sans-serif;border:2px solid #fff}
  .pinE{display:flex;align-items:center;justify-content:center;width:34px;height:34px;border-radius:50%;
       background:#fff;font-size:18px;box-shadow:0 2px 6px rgba(0,0,0,.3);border:2px solid #fff;
       font-family:-apple-system,system-ui,sans-serif}
  .pinK{display:inline-block;min-width:24px;height:24px;line-height:24px;text-align:center;border-radius:999px;
       background:#3177D5;color:#fff;font-size:12px;font-weight:800;border:2px solid #fff;
       box-shadow:0 2px 6px rgba(0,0,0,.3);font-family:-apple-system,system-ui,sans-serif}
  .pinR{display:inline-block;min-width:26px;height:26px;line-height:26px;text-align:center;border-radius:999px;
       background:#263176;color:#fff;font-size:13px;font-weight:800;border:2px solid #fff;
       box-shadow:0 2px 6px rgba(0,0,0,.35);font-family:-apple-system,system-ui,sans-serif}`;

/**
 * 지도 본체 스크립트. window.setMarkers / moveTo / drawRoute / clearRoute /
 * searchKakao / clearKakao 를 전역에 정의하고, 결과는 window.ReactNativeWebView.postMessage 로 알린다.
 * 웹에서는 그 postMessage 를 흉내 내는 shim 을 미리 심어두면 수정 없이 동작한다.
 */
export function mapScript(segColors: string[]): string {
  const colorsJson = JSON.stringify(segColors);
  return `  var ROUTE_COLORS=${colorsJson};
  function segColor(i){ return ROUTE_COLORS[((i%ROUTE_COLORS.length)+ROUTE_COLORS.length)%ROUTE_COLORS.length]; }
  var map, venueOv=[], kakaoOv=[], ready=false, pending=null, places=null, kakaoData=[];
  var routeLines=[], routeOv=[], pendingRoute=null;
  function sel(id){ post({type:'select', id:id}); }
  function selK(i){ post({type:'selectKakao', place:kakaoData[i]}); }
  window.sel = sel; window.selK = selK;
  function clearArr(a){ a.forEach(function(o){o.setMap(null)}); a.length=0; }
  function fitAll(){
    var all=venueOv.concat(kakaoOv);
    if(!all.length) return;
    var b=new kakao.maps.LatLngBounds();
    all.forEach(function(o){ b.extend(o.getPosition()); });
    map.setBounds(b);
  }
  // 카테고리별 이모지 마커. 음식점·행사(문화)·카페 등을 한눈에 구분.
  function catEmoji(c){
    if(c==='맛집') return '🍽️';
    if(c==='카페') return '☕';
    if(c==='문화') return '🎭';
    if(c==='체험') return '🎨';
    if(c==='한복') return '👘';
    return '📍';
  }
  window.setMarkers = function(list){
    if(!ready){ pending=list; return; }
    clearArr(venueOv);
    list.forEach(function(v){
      var pos=new kakao.maps.LatLng(v.lat, v.lng);
      var html='<div class="pinE" onclick="sel('+v.id+')">'+catEmoji(v.category)+'</div>';
      var ov=new kakao.maps.CustomOverlay({position:pos, content:html, yAnchor:1, clickable:true});
      ov.setMap(map); venueOv.push(ov);
    });
    fitAll();
  };
  window.searchKakao = function(q){
    if(!ready || !q) return;
    if(!places) places=new kakao.maps.services.Places();
    places.keywordSearch(q, function(data, status){
      clearArr(kakaoOv); kakaoData=[];
      if(status!==kakao.maps.services.Status.OK){ post({type:'kakaoResults', count:0}); fitAll(); return; }
      data.forEach(function(p, i){
        kakaoData.push({ name:p.place_name, address:(p.road_address_name||p.address_name||''),
          lat:Number(p.y), lng:Number(p.x), category:(p.category_group_name||''), phone:(p.phone||''), url:(p.place_url||'') });
        var pos=new kakao.maps.LatLng(Number(p.y), Number(p.x));
        var html='<div class="pinK" onclick="selK('+i+')">'+(i+1)+'</div>';
        var ov=new kakao.maps.CustomOverlay({position:pos, content:html, yAnchor:1, clickable:true});
        ov.setMap(map); kakaoOv.push(ov);
      });
      post({type:'kakaoResults', count:data.length});
      fitAll();
    });
  };
  window.clearKakao = function(){ clearArr(kakaoOv); kakaoData=[]; fitAll(); };
  // 여행 경로: polyline(도로 경로선) + 번호 마커. data={path:[[lat,lng]...], stops:[{order,lat,lng}...]}
  window.drawRoute = function(data){
    if(!ready){ pendingRoute=data; return; }
    window.clearRoute();
    clearArr(venueOv); clearArr(kakaoOv); // 경로 모드에선 다른 핀 정리
    // 도로 경로(data.path)는 쓰지 않고, 장소(stops)를 방문 순서대로 직선으로 연결한다.
    var stops=(data.stops||[]).slice().sort(function(a,b){ return a.order-b.order; });
    var stopLL=stops.map(function(s){ return new kakao.maps.LatLng(s.lat, s.lng); });
    for(var i=0;i<stopLL.length-1;i++){
      var seg=new kakao.maps.Polyline({path:[stopLL[i],stopLL[i+1]],strokeWeight:4,strokeColor:segColor(i),strokeOpacity:0.9,strokeStyle:'solid'});
      seg.setMap(map); routeLines.push(seg);
    }
    stops.forEach(function(s,i){
      var pos=new kakao.maps.LatLng(s.lat,s.lng);
      var c=segColor(i);
      var html='<div class="pinR" style="background:'+c+'">'+(s.order+1)+'</div>';
      var ov=new kakao.maps.CustomOverlay({position:pos,content:html,yAnchor:1});
      ov.setMap(map); routeOv.push(ov);
    });
    var b=new kakao.maps.LatLngBounds();
    stopLL.forEach(function(ll){ b.extend(ll); });
    if(routeOv.length) map.setBounds(b);
  };
  window.clearRoute = function(){
    routeLines.forEach(function(l){ l.setMap(null); }); routeLines=[];
    clearArr(routeOv);
  };
  // 현재 위치로 지도 중심 이동 + 내 위치 점 표시(RN에서 좌표 주입).
  window.moveTo = function(lat,lng,level){
    if(!map) return;
    var ll=new kakao.maps.LatLng(lat,lng);
    map.setCenter(ll);
    if(level){ map.setLevel(level); }
    if(window.__me){ window.__me.setMap(null); }
    window.__me=new kakao.maps.CustomOverlay({position:ll, xAnchor:0.5, yAnchor:0.5,
      content:'<div style="width:16px;height:16px;border-radius:50%;background:#3177D5;border:3px solid #fff;box-shadow:0 0 0 3px rgba(49,119,213,.35)"></div>'});
    window.__me.setMap(map);
  };
  function boot(){
    if(typeof kakao==='undefined'||!kakao.maps){ post({type:'error', msg:'SDK_LOAD_FAIL'}); return; }
    kakao.maps.load(function(){
      map=new kakao.maps.Map(document.getElementById('map'),{center:new kakao.maps.LatLng(37.5759,126.9769), level:6});
      ready=true; post({type:'ready'});
      if(pendingRoute){ window.drawRoute(pendingRoute); pendingRoute=null; }
      else if(pending){ window.setMarkers(pending); pending=null; }
    });
  }
  if(document.readyState==='complete') boot(); else window.addEventListener('load', boot);`;
}

/** 네이티브 WebView 용 문서. 기존 동작 그대로. */
export function buildHtml(key: string, segColors: string[]): string {
  return `<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
${MAP_CSS}
</style></head>
<body><div id="map"></div>
<script>
  ${MAP_POST_FN}
  window.onerror = function(m){ post({type:'error', msg:String(m)}); };
</script>
<script src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=${key}&autoload=false&libraries=services"
  onerror="post({type:'error', msg:'카카오 SDK 스크립트 로드 실패'})"></script>
<script>
${mapScript(segColors)}
</script></body></html>`;
}
