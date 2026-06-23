import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Linking, Platform, Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WebView } from 'react-native-webview';
import { Chip } from '@/components/Chip';
import { Rating } from '@/components/Rating';
import { Venue } from '@/lib/api/types';
import { useItineraryRouteQuery, useVenuesQuery } from '@/lib/hooks/queries';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

const fmtKm = (m: number) => (m / 1000).toFixed(1);
const fmtMin = (s: number) => {
  const m = Math.round(s / 60);
  return m >= 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m}분`;
};

const CATS = ['전체', '문화', '카페', '체험', '맛집', '한복'];
const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY ?? '';
// WebView 문서의 origin. 카카오 개발자센터 Web 플랫폼 사이트 도메인에 등록된 값과 일치해야 한다.
const KAKAO_WEB_ORIGIN = 'https://byeori.seoulride.site';

// 카카오 지도 JS SDK를 WebView로 렌더링한다(네이티브 앱 안에서 실제 카카오 지도).
// 핀(마커)은 RN에서 venues를 주입하고, 마커 클릭 시 postMessage로 RN에 알린다.
function buildHtml(key: string): string {
  return `<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
  html,body,#map{margin:0;padding:0;width:100%;height:100%;background:#EAF0E6}
  .pin{display:inline-block;min-width:30px;text-align:center;padding:3px 8px;border-radius:999px;
       color:#fff;font-size:12px;font-weight:800;box-shadow:0 2px 6px rgba(0,0,0,.25);
       font-family:-apple-system,system-ui,sans-serif;border:2px solid #fff}
  .pinK{display:inline-block;min-width:24px;height:24px;line-height:24px;text-align:center;border-radius:999px;
       background:#3177D5;color:#fff;font-size:12px;font-weight:800;border:2px solid #fff;
       box-shadow:0 2px 6px rgba(0,0,0,.3);font-family:-apple-system,system-ui,sans-serif}
  .pinR{display:inline-block;min-width:26px;height:26px;line-height:26px;text-align:center;border-radius:999px;
       background:#263176;color:#fff;font-size:13px;font-weight:800;border:2px solid #fff;
       box-shadow:0 2px 6px rgba(0,0,0,.35);font-family:-apple-system,system-ui,sans-serif}
</style></head>
<body><div id="map"></div>
<script>
  function post(msg){ try{ if(window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify(msg)); }catch(e){} }
  window.onerror = function(m){ post({type:'error', msg:String(m)}); };
</script>
<script src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=${key}&autoload=false&libraries=services"
  onerror="post({type:'error', msg:'카카오 SDK 스크립트 로드 실패'})"></script>
<script>
  var map, venueOv=[], kakaoOv=[], ready=false, pending=null, places=null, kakaoData=[];
  var routeLine=null, routeOv=[], pendingRoute=null;
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
  window.setMarkers = function(list){
    if(!ready){ pending=list; return; }
    clearArr(venueOv);
    list.forEach(function(v){
      var pos=new kakao.maps.LatLng(v.lat, v.lng);
      var bg=v.hanbok ? '#E5484D' : '#263176';
      var html='<div class="pin" style="background:'+bg+'" onclick="sel('+v.id+')">'+v.rating+'</div>';
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
    var pathLL=(data.path||[]).map(function(p){ return new kakao.maps.LatLng(p[0], p[1]); });
    if(pathLL.length){
      routeLine=new kakao.maps.Polyline({path:pathLL, strokeWeight:5, strokeColor:'#3177D5', strokeOpacity:0.9, strokeStyle:'solid'});
      routeLine.setMap(map);
    }
    (data.stops||[]).forEach(function(s){
      var pos=new kakao.maps.LatLng(s.lat, s.lng);
      var html='<div class="pinR">'+(s.order+1)+'</div>';
      var ov=new kakao.maps.CustomOverlay({position:pos, content:html, yAnchor:1});
      ov.setMap(map); routeOv.push(ov);
    });
    var b=new kakao.maps.LatLngBounds();
    if(pathLL.length) pathLL.forEach(function(ll){ b.extend(ll); });
    else (data.stops||[]).forEach(function(s){ b.extend(new kakao.maps.LatLng(s.lat, s.lng)); });
    if(routeOv.length) map.setBounds(b);
  };
  window.clearRoute = function(){
    if(routeLine){ routeLine.setMap(null); routeLine=null; }
    clearArr(routeOv);
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
  if(document.readyState==='complete') boot(); else window.addEventListener('load', boot);
</script></body></html>`;
}

// 카카오 키워드 검색 결과 1건
type KakaoPlace = { name: string; address: string; lat: number; lng: number; category: string; phone: string; url: string };

export default function MapScreen() {
  const router = useRouter();
  const params = useLocalSearchParams<{ itineraryId?: string }>();
  const itineraryId = params.itineraryId ? Number(params.itineraryId) : undefined;
  const routeMode = !!itineraryId && !Number.isNaN(itineraryId);
  const routeQuery = useItineraryRouteQuery(routeMode ? (itineraryId as number) : 0);
  const exitRouteMode = () => router.setParams({ itineraryId: undefined });
  const [cat, setCat] = useState('전체');
  const [hanbokOnly, setHanbokOnly] = useState(false);
  const [selected, setSelected] = useState<Venue | null>(null);
  const [selectedKakao, setSelectedKakao] = useState<KakaoPlace | null>(null);
  const [query, setQuery] = useState('');
  const [keyword, setKeyword] = useState(''); // 확정된 검색어(우리 장소 필터)
  const [kakaoCount, setKakaoCount] = useState<number | null>(null);
  const webRef = useRef<WebView>(null);
  const [ready, setReady] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);

  const { data } = useVenuesQuery({
    keyword: keyword || undefined,
    category: cat === '전체' ? undefined : cat,
    hanbokDiscount: hanbokOnly || undefined,
    size: 50,
  });
  const venues = data?.content ?? [];

  // 검색 실행: 우리 장소는 쿼리 키워드로, 카카오 장소는 WebView keywordSearch로.
  const runSearch = () => {
    const q = query.trim();
    setKeyword(q);
    setSelected(null);
    setSelectedKakao(null);
    if (Platform.OS !== 'web') {
      webRef.current?.injectJavaScript(q ? `window.searchKakao(${JSON.stringify(q)}); true;` : 'window.clearKakao(); true;');
    }
    if (!q) setKakaoCount(null);
  };
  const clearSearch = () => {
    setQuery('');
    setKeyword('');
    setKakaoCount(null);
    setSelected(null);
    setSelectedKakao(null);
    if (Platform.OS !== 'web') webRef.current?.injectJavaScript('window.clearKakao(); true;');
  };

  // 키 없을 때 폴백(좌표 정규화 핀 오버레이)용 경계
  const bounds = useMemo(() => {
    if (!venues.length) return null;
    const lats = venues.map((v) => Number(v.lat));
    const lngs = venues.map((v) => Number(v.lng));
    return { minLat: Math.min(...lats), maxLat: Math.max(...lats), minLng: Math.min(...lngs), maxLng: Math.max(...lngs) };
  }, [venues]);
  const pos = (v: Venue) => {
    if (!bounds) return { left: '50%', top: '50%' } as const;
    const x = bounds.maxLng === bounds.minLng ? 0.5 : (Number(v.lng) - bounds.minLng) / (bounds.maxLng - bounds.minLng);
    const y = bounds.maxLat === bounds.minLat ? 0.5 : (bounds.maxLat - Number(v.lat)) / (bounds.maxLat - bounds.minLat);
    return { left: `${8 + x * 84}%`, top: `${10 + y * 78}%` } as const;
  };

  // 지도가 준비됐고 venues가 바뀌면 마커를 다시 주입한다.
  const markerPayload = useMemo(
    () =>
      JSON.stringify(
        venues
          .filter((v) => v.lat != null && v.lng != null)
          .map((v) => ({ id: v.id, lat: Number(v.lat), lng: Number(v.lng), hanbok: !!v.hanbokDiscount, rating: Number(v.avgRating).toFixed(1) })),
      ),
    [venues],
  );

  useEffect(() => {
    if (!ready || !webRef.current) return;
    if (routeMode) {
      if (routeQuery.data) {
        const payload = JSON.stringify({ path: routeQuery.data.path, stops: routeQuery.data.stops });
        webRef.current.injectJavaScript(`window.drawRoute(${payload}); true;`);
      }
    } else {
      webRef.current.injectJavaScript(`window.clearRoute(); window.setMarkers(${markerPayload}); true;`);
    }
  }, [ready, routeMode, routeQuery.data, markerPayload]);

  const onMessage = (e: { nativeEvent: { data: string } }) => {
    try {
      const msg = JSON.parse(e.nativeEvent.data);
      if (msg.type === 'ready') { setReady(true); setMapError(null); }
      if (msg.type === 'error') setMapError(String(msg.msg));
      if (msg.type === 'select') {
        const v = venues.find((x) => x.id === msg.id);
        if (v) { setSelectedKakao(null); setSelected(v); }
      }
      if (msg.type === 'kakaoResults') setKakaoCount(Number(msg.count));
      if (msg.type === 'selectKakao' && msg.place) { setSelected(null); setSelectedKakao(msg.place as KakaoPlace); }
    } catch {
      /* ignore */
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* 경로 모드 배너 (여행 일지 경로 보기) */}
      {routeMode && (
        <View style={styles.routeBanner}>
          <Ionicons name="navigate" size={18} color={colors.white} />
          <View style={{ flex: 1 }}>
            {routeQuery.isLoading ? (
              <Text style={styles.routeBannerText}>경로 계산 중…</Text>
            ) : routeQuery.data ? (
              <Text style={styles.routeBannerText}>
                총 {fmtKm(routeQuery.data.distance)}km · 약 {fmtMin(routeQuery.data.duration)} · {routeQuery.data.stops.length}곳
              </Text>
            ) : (
              <Text style={styles.routeBannerText}>경로를 불러오지 못했어요</Text>
            )}
          </View>
          <Pressable hitSlop={8} onPress={exitRouteMode} style={styles.routeClose}>
            <Ionicons name="close" size={16} color={colors.white} />
            <Text style={styles.routeCloseText}>닫기</Text>
          </Pressable>
        </View>
      )}

      {/* 검색창 (카카오 전체 장소 + 우리 장소 검색) */}
      {!routeMode && (<>
      <View style={styles.search}>
        <Ionicons name="search" size={18} color={colors.accent} />
        <TextInput
          style={styles.searchInput}
          placeholder="장소·주소 검색 (예: 경복궁, 전주 한옥마을)"
          placeholderTextColor={colors.textFaint}
          value={query}
          onChangeText={setQuery}
          onSubmitEditing={runSearch}
          returnKeyType="search"
        />
        {query.length > 0 && (
          <Pressable hitSlop={8} onPress={clearSearch}>
            <Ionicons name="close-circle" size={18} color={colors.textFaint} />
          </Pressable>
        )}
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsScroll} contentContainerStyle={styles.chips}>
        {CATS.map((c) => <Chip key={c} label={c} selected={c === cat} onPress={() => setCat(c)} />)}
      </ScrollView>
      {/* 한복 혜택 토글 */}
      <Pressable style={styles.toggleWrap} onPress={() => setHanbokOnly((p) => !p)}>
        <Text style={styles.toggleLabel}>👘 한복 입고 방문 시 할인되는 곳만</Text>
        <Switch
          value={hanbokOnly}
          onValueChange={setHanbokOnly}
          trackColor={{ true: colors.hanbok, false: '#D1D5DB' }}
          thumbColor={colors.white}
        />
      </Pressable>

      {/* 검색 결과 요약 */}
      {keyword.length > 0 && (
        <Text style={styles.resultHint}>
          '{keyword}' 검색 · 카카오 <Text style={styles.resultNum}>{kakaoCount ?? 0}</Text>곳 · 우리 장소 <Text style={styles.resultNum}>{venues.length}</Text>곳
        </Text>
      )}
      </>)}

      {/* 카카오 지도 (WebView는 네이티브 전용 — 웹에서는 핀 폴백) */}
      <View style={styles.map}>
        {KAKAO_JS_KEY && !mapError && Platform.OS !== 'web' ? (
          <>
            <WebView
              ref={webRef}
              originWhitelist={['*']}
              source={{ html: buildHtml(KAKAO_JS_KEY), baseUrl: KAKAO_WEB_ORIGIN }}
              onMessage={onMessage}
              javaScriptEnabled
              domStorageEnabled
              mixedContentMode="always"
              setSupportMultipleWindows={false}
              onError={(e) => setMapError('WebView 오류: ' + e.nativeEvent.description)}
              onHttpError={(e) => setMapError('HTTP 오류: ' + e.nativeEvent.statusCode)}
              style={{ flex: 1, backgroundColor: '#EAF0E6' }}
            />
            {!ready && (
              <View style={styles.mapOverlay} pointerEvents="none">
                <Text style={styles.mapOverlayText}>{mapError ?? '지도 불러오는 중…'}</Text>
              </View>
            )}
          </>
        ) : (
          // 카카오 키 미설정 시 폴백: 좌표 정규화 핀 오버레이
          <View style={styles.fallback}>
            <View style={styles.grid} pointerEvents="none" />
            <Text style={styles.mapHint}>서울 도심 · {venues.length}곳</Text>
            {venues.map((v) => (
              <Pressable key={v.id} style={[styles.pin, pos(v), v.hanbokDiscount && styles.pinHanbok, selected?.id === v.id && styles.pinSelected]} onPress={() => setSelected(v)}>
                <Text style={styles.pinText}>{Number(v.avgRating).toFixed(1)}</Text>
              </Pressable>
            ))}
          </View>
        )}
      </View>

      {/* 선택된 우리 장소 미니 카드 */}
      {selected && (
        <Pressable style={styles.miniCard} onPress={() => router.push(`/venue/${selected.id}`)}>
          <Image source={selected.imageUrl} style={styles.miniImg} contentFit="cover" />
          <View style={{ flex: 1 }}>
            <Text style={styles.miniName}>{selected.name}</Text>
            <Text style={styles.miniAddr} numberOfLines={1}>{selected.address}</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 3 }}>
              <Rating value={selected.avgRating} count={selected.reviewCount} />
              {selected.hanbokDiscount && <Text style={styles.miniHanbok}>한복할인</Text>}
            </View>
          </View>
          <Ionicons name="chevron-forward" size={20} color={colors.textFaint} />
        </Pressable>
      )}

      {/* 선택된 카카오 장소 미니 카드 */}
      {selectedKakao && (
        <Pressable
          style={styles.miniCard}
          onPress={() => selectedKakao.url && Linking.openURL(selectedKakao.url)}>
          <View style={styles.kakaoThumb}><Ionicons name="location" size={24} color={colors.accent} /></View>
          <View style={{ flex: 1 }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
              <Text style={styles.miniName} numberOfLines={1}>{selectedKakao.name}</Text>
              <View style={styles.kakaoBadge}><Text style={styles.kakaoBadgeText}>카카오</Text></View>
            </View>
            <Text style={styles.miniAddr} numberOfLines={1}>{selectedKakao.address || '주소 정보 없음'}</Text>
            {!!selectedKakao.category && <Text style={styles.kakaoCat}>{selectedKakao.category}{selectedKakao.phone ? ` · ${selectedKakao.phone}` : ''}</Text>}
          </View>
          <Pressable hitSlop={8} onPress={() => setSelectedKakao(null)}><Ionicons name="close" size={20} color={colors.textFaint} /></Pressable>
        </Pressable>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  search: {
    flexDirection: 'row', alignItems: 'center', gap: 8, marginHorizontal: space.lg, marginTop: 8,
    backgroundColor: colors.white, borderWidth: 1.5, borderColor: colors.accent, borderRadius: radius.pill,
    paddingHorizontal: 16, paddingVertical: 11,
  },
  searchInput: { flex: 1, fontSize: 14, color: colors.text, padding: 0 },
  routeBanner: {
    flexDirection: 'row', alignItems: 'center', gap: 10, marginHorizontal: space.lg, marginTop: 8,
    backgroundColor: colors.primary, borderRadius: radius.pill, paddingHorizontal: 16, paddingVertical: 11, ...shadow.card,
  },
  routeBannerText: { color: colors.white, fontSize: 14, fontFamily: fonts.bold, fontWeight: '800' },
  routeClose: { flexDirection: 'row', alignItems: 'center', gap: 2, backgroundColor: 'rgba(255,255,255,0.2)', borderRadius: radius.pill, paddingHorizontal: 10, paddingVertical: 5 },
  routeCloseText: { color: colors.white, fontSize: 12, fontFamily: fonts.semibold, fontWeight: '600' },
  resultHint: { fontSize: 12, color: colors.textSub, marginHorizontal: space.lg, marginTop: 6, marginBottom: 2, fontFamily: fonts.medium, fontWeight: '500' },
  resultNum: { color: colors.accent, fontFamily: fonts.bold, fontWeight: '800' },
  toggleWrap: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    backgroundColor: '#FDECEC', borderRadius: radius.md, paddingHorizontal: 14, paddingVertical: 8,
    marginHorizontal: space.lg, marginBottom: 4,
  },
  toggleLabel: { fontSize: 13, fontFamily: fonts.semibold, fontWeight: '600', color: colors.hanbok },
  chipsScroll: { flexGrow: 0, flexShrink: 0 },
  chips: { gap: 8, paddingHorizontal: space.lg, paddingVertical: 10, alignItems: 'center' },
  map: { flex: 1, margin: space.lg, marginTop: 0, borderRadius: radius.lg, backgroundColor: '#EAF0E6', overflow: 'hidden' },
  mapOverlay: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, alignItems: 'center', justifyContent: 'center', padding: 20 },
  mapOverlayText: { fontSize: 13, color: colors.textSub, textAlign: 'center', fontFamily: fonts.medium, fontWeight: '500' },
  fallback: { flex: 1 },
  grid: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: '#E7EEE2' },
  mapHint: { position: 'absolute', top: 10, left: 12, fontSize: 11, color: '#7C8B72', fontWeight: '700' },
  pin: {
    position: 'absolute', minWidth: 34, height: 24, paddingHorizontal: 6, borderRadius: radius.pill,
    backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center', ...shadow.card,
    transform: [{ translateX: -17 }, { translateY: -12 }],
  },
  pinHanbok: { backgroundColor: colors.hanbok },
  pinSelected: { borderWidth: 2, borderColor: colors.white, transform: [{ translateX: -17 }, { translateY: -12 }, { scale: 1.15 }] },
  pinText: { color: colors.white, fontSize: 11, fontWeight: '800' },
  miniCard: {
    flexDirection: 'row', alignItems: 'center', gap: 12, position: 'absolute', left: space.lg, right: space.lg, bottom: 16,
    backgroundColor: colors.white, borderRadius: radius.lg, padding: 12, ...shadow.card,
  },
  miniImg: { width: 60, height: 60, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  miniName: { fontSize: 15, fontWeight: '700', color: colors.text },
  miniAddr: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  miniHanbok: { fontSize: 10, fontWeight: '800', color: colors.hanbok, backgroundColor: '#FDECEC', borderRadius: 4, paddingHorizontal: 5, paddingVertical: 1 },
  kakaoThumb: { width: 60, height: 60, borderRadius: radius.md, backgroundColor: colors.accentSoft, alignItems: 'center', justifyContent: 'center' },
  kakaoBadge: { backgroundColor: colors.accentSoft, borderRadius: 4, paddingHorizontal: 5, paddingVertical: 1 },
  kakaoBadgeText: { fontSize: 10, fontWeight: '800', color: colors.accent },
  kakaoCat: { fontSize: 11, color: colors.textFaint, marginTop: 3 },
});
