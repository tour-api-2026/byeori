import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { WebView } from 'react-native-webview';
import { Chip } from '@/components/Chip';
import { Rating } from '@/components/Rating';
import { Venue } from '@/lib/api/types';
import { useVenuesQuery } from '@/lib/hooks/queries';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

const CATS = ['전체', '문화', '카페', '체험', '맛집'];
const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY ?? '';

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
</style></head>
<body><div id="map"></div>
<script>
  function post(msg){ try{ if(window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify(msg)); }catch(e){} }
  window.onerror = function(m){ post({type:'error', msg:String(m)}); };
</script>
<script src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=${key}&autoload=false"
  onerror="post({type:'error', msg:'카카오 SDK 스크립트 로드 실패'})"></script>
<script>
  var map, overlays=[], ready=false, pending=null;
  function sel(id){ post({type:'select', id:id}); }
  window.sel = sel;
  function clear(){ overlays.forEach(function(o){o.setMap(null)}); overlays=[]; }
  window.setMarkers = function(list){
    if(!ready){ pending=list; return; }
    clear();
    if(!list.length) return;
    var bounds=new kakao.maps.LatLngBounds();
    list.forEach(function(v){
      var pos=new kakao.maps.LatLng(v.lat, v.lng);
      var bg=v.hanbok ? '#E5484D' : '#263176';
      var html='<div class="pin" style="background:'+bg+'" onclick="sel('+v.id+')">'+v.rating+'</div>';
      var ov=new kakao.maps.CustomOverlay({position:pos, content:html, yAnchor:1, clickable:true});
      ov.setMap(map); overlays.push(ov); bounds.extend(pos);
    });
    map.setBounds(bounds);
  };
  function boot(){
    if(typeof kakao==='undefined'||!kakao.maps){ post({type:'error', msg:'SDK_LOAD_FAIL'}); return; }
    kakao.maps.load(function(){
      map=new kakao.maps.Map(document.getElementById('map'),{center:new kakao.maps.LatLng(37.5759,126.9769), level:6});
      ready=true; post({type:'ready'});
      if(pending){ window.setMarkers(pending); pending=null; }
    });
  }
  if(document.readyState==='complete') boot(); else window.addEventListener('load', boot);
</script></body></html>`;
}

export default function MapScreen() {
  const router = useRouter();
  const [cat, setCat] = useState('전체');
  const [hanbokOnly, setHanbokOnly] = useState(false);
  const [selected, setSelected] = useState<Venue | null>(null);
  const webRef = useRef<WebView>(null);
  const [ready, setReady] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);

  const { data } = useVenuesQuery({
    category: cat === '전체' ? undefined : cat,
    hanbokDiscount: hanbokOnly || undefined,
    size: 50,
  });
  const venues = data?.content ?? [];

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
    if (ready && webRef.current) {
      webRef.current.injectJavaScript(`window.setMarkers(${markerPayload}); true;`);
    }
  }, [ready, markerPayload]);

  const onMessage = (e: { nativeEvent: { data: string } }) => {
    try {
      const msg = JSON.parse(e.nativeEvent.data);
      if (msg.type === 'ready') { setReady(true); setMapError(null); }
      if (msg.type === 'error') setMapError(String(msg.msg));
      if (msg.type === 'select') {
        const v = venues.find((x) => x.id === msg.id);
        if (v) setSelected(v);
      }
    } catch {
      /* ignore */
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* 검색창 */}
      <Pressable style={styles.search} onPress={() => router.push('/search')}>
        <Ionicons name="search" size={18} color={colors.accent} />
        <Text style={styles.searchText}>장소를 검색해보세요</Text>
      </Pressable>
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

      {/* 카카오 지도 */}
      <View style={styles.map}>
        {KAKAO_JS_KEY && !mapError ? (
          <>
            <WebView
              ref={webRef}
              originWhitelist={['*']}
              source={{ html: buildHtml(KAKAO_JS_KEY), baseUrl: 'https://localhost' }}
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

      {/* 선택된 장소 미니 카드 */}
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
  searchText: { color: colors.textFaint, fontSize: 14 },
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
});
