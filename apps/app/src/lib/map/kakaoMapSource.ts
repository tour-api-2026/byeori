import { CATEGORY_EMOJI } from '@/lib/categories';

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
  .pinC{display:flex;align-items:center;justify-content:center;border-radius:50%;
       background:rgba(38,49,118,.92);color:#fff;font-weight:800;border:3px solid #fff;
       box-shadow:0 3px 10px rgba(0,0,0,.3);cursor:pointer;
       font-family:-apple-system,system-ui,sans-serif}
  .pinK{display:inline-block;min-width:24px;height:24px;line-height:24px;text-align:center;border-radius:999px;
       background:#3177D5;color:#fff;font-size:12px;font-weight:800;border:2px solid #fff;
       box-shadow:0 2px 6px rgba(0,0,0,.3);font-family:-apple-system,system-ui,sans-serif}
  /* 경로 번호는 Marker(SVG 그림)로 그린다 — numberPin 참고 */`;

/**
 * 지도 본체 스크립트. window.setMarkers / moveTo / drawRoute / clearRoute /
 * searchKakao / clearKakao 를 전역에 정의하고, 결과는 window.ReactNativeWebView.postMessage 로 알린다.
 * 웹에서는 그 postMessage 를 흉내 내는 shim 을 미리 심어두면 수정 없이 동작한다.
 */
export function mapScript(segColors: string[], labels: MapLabels): string {
  const colorsJson = JSON.stringify(segColors);
  return `  var ROUTE_COLORS=${colorsJson};
  function segColor(i){ return ROUTE_COLORS[((i%ROUTE_COLORS.length)+ROUTE_COLORS.length)%ROUTE_COLORS.length]; }
  var map, venueOv=[], kakaoOv=[], ready=false, pending=null, places=null, kakaoData=[], lastList=[];
  var routeLines=[], routeOv=[], pendingRoute=null;
  // 마지막으로 지도를 맞춘 경로의 지문. 같은 경로를 다시 그릴 때는 지도를 건드리지 않는다.
  // 되돌릴 일은 없다 — 경로는 전용 화면(route/[id])에서만 그리고, 그 화면을 떠나면 문서
  // 자체가 사라져 이 값도 함께 사라진다. 날짜·이동수단을 바꾸면 지문이 달라져 다시 맞춘다.
  var fittedKey=null;
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
  var CAT_EMOJI=${JSON.stringify(CATEGORY_EMOJI)};
  function catEmoji(c){
    // 서버가 준 문자열이라 'constructor' 같은 값이 와도 프로토타입을 읽지 않도록 자기 키만 본다.
    return Object.prototype.hasOwnProperty.call(CAT_EMOJI, c) ? CAT_EMOJI[c] : '📍';
  }
  // 주변 조회 마커는 지금 보고 있는 영역에서 받아온 것이라 지도를 다시 맞추지 않는다.
  // fitAll()을 부르면 setBounds가 지도를 움직여 idle이 또 발생하고,
  // 그 idle이 재조회를 부르는 되먹임 고리가 생겨 호출이 몇 배로 늘어난다.
  /**
   * 확대 단계별 격자 크기(도 단위). 레벨이 클수록(축소) 넓게 묶는다.
   * 카카오 기본 클러스터러는 Marker 만 받아 이모지 CustomOverlay 를 못 쓴다. 직접 묶는다.
   *
   * 거리에 따라 세 단계로만 나눈다. 전에는 여섯 단계였는데, 한 단계 확대할 때마다 묶음이
   * 조금씩 쪼개져 숫자가 계속 바뀌는 탓에 지금 몇 곳을 보고 있는지 가늠하기 어려웠다.
   *
   *   가까울 때(레벨 ≤6, 화면 ~1km)  : 묶지 않는다 — 핀을 하나씩 본다.
   *   중간(레벨 7~10, 시·군)         : 0.05도 ≈ 5km — 동네 단위로 묶는다.
   *   멀 때(레벨 ≥11, 도·전국)       : 0.5도 ≈ 50km — 지역 단위로 묶는다.
   *
   * 주변 조회는 50건에서 잘려 내려오므로 "가까울 때" 핀이 쏟아질 일은 없다.
   */
  function cellSize(level){
    if(level<=6) return 0;
    if(level<=10) return 0.05;
    return 0.5;
  }
  function zoomInto(lat,lng){
    map.setLevel(Math.max(1, map.getLevel()-3), {anchor:new kakao.maps.LatLng(lat,lng)});
  }
  window.zoomInto = zoomInto;
  function render(){
    clearArr(venueOv);
    var size=cellSize(map.getLevel());
    if(!size){
      lastList.forEach(function(v,i){
        var html='<div class="pinE" onclick="sel('+v.id+')">'+catEmoji(v.category)+'</div>';
        var ov=new kakao.maps.CustomOverlay({position:new kakao.maps.LatLng(v.lat,v.lng),
          content:html, yAnchor:1, clickable:true});
        ov.setMap(map); venueOv.push(ov);
      });
      return;
    }
    // 같은 격자에 든 장소를 하나로 묶고, 무게중심에 개수를 띄운다.
    var cells={};
    lastList.forEach(function(v){
      var k=Math.floor(v.lat/size)+'_'+Math.floor(v.lng/size);
      if(!cells[k]) cells[k]={n:0, lat:0, lng:0, one:v};
      var c=cells[k]; c.n++; c.lat+=v.lat; c.lng+=v.lng;
    });
    Object.keys(cells).forEach(function(k){
      var c=cells[k], la=c.lat/c.n, ln=c.lng/c.n;
      var html;
      if(c.n===1){
        html='<div class="pinE" onclick="sel('+c.one.id+')">'+catEmoji(c.one.category)+'</div>';
      } else {
        // 개수가 많을수록 원을 키운다(34~60px).
        var d=Math.min(60, 34+Math.round(Math.log(c.n)*9));
        html='<div class="pinC" style="width:'+d+'px;height:'+d+'px;font-size:'+(d>46?15:13)+'px"'
            +' onclick="zoomInto('+la+','+ln+')">'+c.n+'</div>';
      }
      var ov=new kakao.maps.CustomOverlay({position:new kakao.maps.LatLng(la,ln),
        content:html, yAnchor:c.n===1?1:0.5, clickable:true});
      ov.setMap(map); venueOv.push(ov);
    });
  }
  window.setMarkers = function(list){
    if(!ready){ pending=list; return; }
    lastList=(list||[]).filter(function(v){ return v.lat!=null && v.lng!=null; });
    render();
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
      // 목록(바텀시트)에서도 쓰므로 결과를 함께 보낸다. 개수만 보내면 RN 쪽이 다시 못 만든다.
      post({type:'kakaoResults', count:data.length, places:kakaoData});
      fitAll();
    });
  };
  // 검색 해제 시에도 지도를 움직이지 않는다(움직이면 idle → 재조회가 또 돈다).
  window.clearKakao = function(){ clearArr(kakaoOv); kakaoData=[]; };
  /**
   * 방문 순서 번호 마커.
   *
   * CustomOverlay 가 아니라 Marker 를 쓴다. 카카오는 화면 밖으로 나간 CustomOverlay 의
   * DOM 을 떼어내고, 지도가 멈출 때(idle)에만 다시 붙인다. 그래서 지도를 끄는 동안에는
   * 새로 들어오는 번호가 안 보이다가 손을 떼야 나타났다. Marker 는 화면 밖에 있어도
   * DOM 에 남아 지도와 함께 움직인다(따로 띄운 지도로 재 봤다: 오버레이 1→0→1, 마커 1→1→1).
   *
   * 번호와 색이 칸마다 달라 그림을 미리 둘 수 없으므로 SVG 를 그 자리에서 만든다.
   * 패키지는 더하지 않는다 — package.json 이 바뀌면 OTA 가 기존 앱에 닿지 못한다.
   */
  function numberPin(no, color){
    var svg='<svg xmlns="http://www.w3.org/2000/svg" width="30" height="30">'
      +'<circle cx="15" cy="15" r="12" fill="'+color+'" stroke="#fff" stroke-width="2"/>'
      +'<text x="15" y="20" text-anchor="middle" font-family="-apple-system,system-ui,sans-serif"'
      +' font-size="13" font-weight="800" fill="#fff">'+no+'</text></svg>';
    return new kakao.maps.MarkerImage('data:image/svg+xml;charset=utf-8,'+encodeURIComponent(svg),
      new kakao.maps.Size(30,30), {offset:new kakao.maps.Point(15,15)});
  }
  // 여행 경로: 서버가 계산한 보행/도로 경로선 + 번호 마커.
  // data={path:[[lat,lng]...], stops:[{order,lat,lng}...], legs:[{mode}...]}
  window.drawRoute = function(data){
    if(!ready){ pendingRoute=data; return; }
    window.clearRoute();
    clearArr(venueOv); clearArr(kakaoOv); // 경로 모드에선 다른 핀 정리
    var stops=(data.stops||[]).slice().sort(function(a,b){ return a.order-b.order; });
    var path=data.path||[];
    // 전에는 path 를 받아놓고 버린 뒤 방문지를 직선으로 이었다. 이제 실제 경로를 그린다.
    // path 는 구간들을 이어붙인 한 줄이고 legs[].points 가 구간별 점 수다. 걷는 구간은
    // 실선, 타는(또는 직선으로 메운) 구간은 점선으로 따로 그려 어디를 걷는지 보이게 한다.
    // 구간 경계를 믿을 수 없으면(옛 캐시 행은 points 가 0, 합이 안 맞는 경우) 일부만
    // 그리느니 한 줄로 그린다: 전부 걷는 경로만 실선, 섞이면 점선.
    var legs=data.legs||[];
    var sliceable = path.length>1 && legs.length>0 && legs.every(function(l){ return l.points>0; })
      && legs.reduce(function(n,l){ return n+l.points; },0)===path.length;
    if(sliceable){
      var from=0;
      legs.forEach(function(l,i){
        // 이음점은 앞 구간 마지막 점과 같다. 두 선 모두에 넣어 틈 없이 이어 붙인다.
        var start = i===0 ? 0 : from-1;
        var end = from+l.points;
        var seg=path.slice(start,end).map(function(p){ return new kakao.maps.LatLng(p[0],p[1]); });
        from=end;
        if(seg.length<2) return;
        var ln=new kakao.maps.Polyline({path:seg,strokeWeight:5,strokeColor:segColor(i),
          strokeOpacity:0.9,strokeStyle: l.mode==='WALK' ? 'solid' : 'shortdash'});
        ln.setMap(map); routeLines.push(ln);
      });
    } else if(path.length>1){
      var walkOnly = legs.length>0 && legs.every(function(l){ return l.mode==='WALK'; });
      var ll=path.map(function(p){ return new kakao.maps.LatLng(p[0],p[1]); });
      var line=new kakao.maps.Polyline({path:ll,strokeWeight:5,strokeColor:segColor(0),
        strokeOpacity:0.9,strokeStyle: walkOnly ? 'solid' : 'shortdash'});
      line.setMap(map); routeLines.push(line);
    } else {
      // 경로가 없으면 방문지를 직선으로 잇는다 — 빈 지도보다 낫다.
      var sl=stops.map(function(s){ return new kakao.maps.LatLng(s.lat,s.lng); });
      for(var i=0;i<sl.length-1;i++){
        var seg=new kakao.maps.Polyline({path:[sl[i],sl[i+1]],strokeWeight:4,
          strokeColor:segColor(i),strokeOpacity:0.7,strokeStyle:'shortdash'});
        seg.setMap(map); routeLines.push(seg);
      }
    }
    stops.forEach(function(s,i){
      var mk=new kakao.maps.Marker({position:new kakao.maps.LatLng(s.lat,s.lng),
        image:numberPin(s.order+1, segColor(i)), zIndex:5});
      mk.setMap(map); routeOv.push(mk);
    });
    // 지도를 맞추는 것은 **경로가 바뀌었을 때 한 번뿐**이다.
    // 전에는 drawRoute 가 불릴 때마다 맞췄는데, 지도를 움직이면 idle 이 뜨고 그것이
    // React 상태를 바꿔 drawRoute 가 다시 주입된다. 그러면 사용자가 민 지도가 곧바로
    // 경로 범위로 튕겨 돌아와, 확대도 이동도 안 되는 것처럼 보였다.
    var key=stops.map(function(s){ return s.order+':'+s.lat+','+s.lng; }).join('|')+'#'+path.length;
    if(routeOv.length && key!==fittedKey){
      var b=new kakao.maps.LatLngBounds();
      (path.length>1?path:stops.map(function(s){return [s.lat,s.lng];}))
        .forEach(function(p){ b.extend(new kakao.maps.LatLng(p[0],p[1])); });
      map.setBounds(b);
      fittedKey=key;
    }
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
    if(typeof kakao==='undefined'||!kakao.maps){ post({type:'error', msg:${jsStr(labels.sdkLoadFail)}}); return; }
    kakao.maps.load(function(){
      map=new kakao.maps.Map(document.getElementById('map'),{center:new kakao.maps.LatLng(37.5759,126.9769), level:6});
      ready=true; post({type:'ready'});
      // 지도 이동/확대가 멈추면 중심 좌표와 대략의 반경을 알린다.
      // RN이 이 좌표로 공사 OpenAPI를 실시간 조회해 마커를 다시 주입한다.
      kakao.maps.event.addListener(map, 'idle', function(){
        var c=map.getCenter(), b=map.getBounds();
        var sw=b.getSouthWest(), ne=b.getNorthEast();
        // 대각선의 절반을 반경으로 본다(위도 1도 ~111km).
        var dLat=(ne.getLat()-sw.getLat())*111000, dLng=(ne.getLng()-sw.getLng())*88000;
        var r=Math.round(Math.sqrt(dLat*dLat+dLng*dLng)/2);
        post({type:'idle', lat:c.getLat(), lng:c.getLng(), radius:r, level:map.getLevel()});
        render();   // 확대 단계가 바뀌었으면 묶음을 다시 계산한다

      });
      if(pendingRoute){ window.drawRoute(pendingRoute); pendingRoute=null; }
      else if(pending){ window.setMarkers(pending); pending=null; }
    });
  }
  if(document.readyState==='complete') boot(); else window.addEventListener('load', boot);`;
}

/**
 * 지도 쪽에서 사용자에게 닿는 문구. WebView 안의 스크립트에서는 useT() 가 닿지 않으므로
 * 화면이 번역해서 넘긴다(useMapLabels). 필수 prop 으로 받아 하나라도 빠뜨리면 tsc 가 잡는다.
 *
 * 오류 문구는 화면의 mapError 로 올라가 그대로 보이므로 번역 대상이다. {detail} 이 붙는
 * 것은 함수로 받는다 — 보간을 사전 항목 하나로 두려는 것이다.
 */
export type MapLabels = {
  /** 네이티브: 카카오 SDK <script> 가 로드되지 않았을 때. */
  sdkLoadFail: string;
  /** 웹: 같은 상황이지만 도메인 미등록이 흔한 원인이라 안내를 덧붙인다. */
  sdkBlocked: string;
  webViewError: (detail: string) => string;
  httpError: (detail: string) => string;
  scriptError: (detail: string) => string;
  /** 개발 로그. 사용자에게 보이지 않지만 한국어 리터럴을 남기지 않으려 사전에 둔다. */
  injectFail: string;
};

/**
 * 스크립트 안에 넣을 문자열 리터럴. JSON 으로 따옴표를 닫고 `<` 는 \u003c 로 바꾼다 —
 * 네이티브에서는 이 스크립트가 <script> 안에 들어가 `</script>` 가 있으면 문서가 끊긴다.
 */
const jsStr = (s: string) => JSON.stringify(s).replace(/</g, '\\u003c');

/** 속성값 안에 넣을 문자열의 HTML 이스케이프. onerror="…" 안에서 따옴표가 속성을 끊지 않게 한다. */
const attr = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** 네이티브 WebView 용 문서. */
export function buildHtml(key: string, segColors: string[], labels: MapLabels): string {
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
  onerror="post({type:'error', msg:${attr(JSON.stringify(labels.sdkLoadFail))}})"></script>
<script>
${mapScript(segColors, labels)}
</script></body></html>`;
}
