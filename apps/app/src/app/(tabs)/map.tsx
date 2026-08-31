import { Ionicons } from "@expo/vector-icons";
import { Image } from "expo-image";
import * as Location from "expo-location";
import { useFocusEffect, useLocalSearchParams, useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import KakaoMapView, { type KakaoMapHandle } from "@/components/KakaoMapView";
import { Chip } from "@/components/Chip";
import { useTabBarHeight } from "@/components/TabBar";
import { Rating } from "@/components/Rating";
import { Venue } from "@/lib/api/types";
import { useItineraryRouteQuery, useVenuesQuery } from "@/lib/hooks/queries";
import { colors, fonts, radius, shadow, space } from "@/lib/theme";
import { ROUTE_SEGMENT_COLORS } from "@/lib/routeColors";

const fmtKm = (m: number) => (m / 1000).toFixed(1);
const fmtMin = (s: number) => {
  const m = Math.round(s / 60);
  return m >= 60 ? `${Math.floor(m / 60)}시간 ${m % 60}분` : `${m}분`;
};

const CATS = ["전체", "문화", "카페", "체험", "맛집", "한복"];
const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY ?? "";
// WebView 문서의 origin. 카카오 개발자센터 Web 플랫폼 사이트 도메인에 등록된 값과 일치해야 한다.
const KAKAO_WEB_ORIGIN = "https://byeori.seoulride.site";


// 카카오 키워드 검색 결과 1건
type KakaoPlace = {
  name: string;
  address: string;
  lat: number;
  lng: number;
  category: string;
  phone: string;
  url: string;
};

export default function MapScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const tabH = useTabBarHeight(); // 떠 있는 탭바 높이 — 하단 UI(카드·현재위치 버튼)를 그만큼 올린다.
  const params = useLocalSearchParams<{ itineraryId?: string }>();
  const itineraryId = params.itineraryId
    ? Number(params.itineraryId)
    : undefined;
  const routeMode = !!itineraryId && !Number.isNaN(itineraryId);
  const routeQuery = useItineraryRouteQuery(
    routeMode ? (itineraryId as number) : 0,
  );
  // 경로 보기 '닫기' → '내 루트'로 복귀(내 주변에 머무르지 않도록).
  // 탭 포커스 해제 시 useFocusEffect가 itineraryId를 정리하므로 파라미터는 자동 초기화.
  const exitRouteMode = () => router.replace('/routes');

  // '내 주변' 탭을 떠나면 경로 모드를 해제한다.
  // (여행 루트에서 넘어온 itineraryId 파라미터가 남아 다시 들어왔을 때
  //  경로가 그대로 그려지는 문제 방지)
  useFocusEffect(
    useCallback(() => {
      return () => {
        if (params.itineraryId) router.setParams({ itineraryId: undefined });
      };
    }, [params.itineraryId, router]),
  );
  const [cat, setCat] = useState("전체");
  const [hanbokOnly, setHanbokOnly] = useState(false);
  const [selected, setSelected] = useState<Venue | null>(null);
  const [selectedKakao, setSelectedKakao] = useState<KakaoPlace | null>(null);
  const [query, setQuery] = useState("");
  const [keyword, setKeyword] = useState(""); // 확정된 검색어(우리 장소 필터)
  const [kakaoCount, setKakaoCount] = useState<number | null>(null);
  const webRef = useRef<KakaoMapHandle>(null);
  const [ready, setReady] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);

  const { data } = useVenuesQuery({
    keyword: keyword || undefined,
    category: cat === "전체" ? undefined : cat,
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
    webRef.current?.injectJavaScript(
      q
        ? `window.searchKakao(${JSON.stringify(q)}); true;`
        : "window.clearKakao(); true;",
    );
    if (!q) setKakaoCount(null);
  };
  const clearSearch = () => {
    setQuery("");
    setKeyword("");
    setKakaoCount(null);
    setSelected(null);
    setSelectedKakao(null);
    webRef.current?.injectJavaScript("window.clearKakao(); true;");
  };

  // 키 없을 때 폴백(좌표 정규화 핀 오버레이)용 경계
  const bounds = useMemo(() => {
    if (!venues.length) return null;
    const lats = venues.map((v) => Number(v.lat));
    const lngs = venues.map((v) => Number(v.lng));
    return {
      minLat: Math.min(...lats),
      maxLat: Math.max(...lats),
      minLng: Math.min(...lngs),
      maxLng: Math.max(...lngs),
    };
  }, [venues]);
  const pos = (v: Venue) => {
    if (!bounds) return { left: "50%", top: "50%" } as const;
    const x =
      bounds.maxLng === bounds.minLng
        ? 0.5
        : (Number(v.lng) - bounds.minLng) / (bounds.maxLng - bounds.minLng);
    const y =
      bounds.maxLat === bounds.minLat
        ? 0.5
        : (bounds.maxLat - Number(v.lat)) / (bounds.maxLat - bounds.minLat);
    return { left: `${8 + x * 84}%`, top: `${10 + y * 78}%` } as const;
  };

  // 지도가 준비됐고 venues가 바뀌면 마커를 다시 주입한다.
  const markerPayload = useMemo(
    () =>
      JSON.stringify(
        venues
          // 한국 범위(위도 33~38.7, 경도 124.5~132) 밖 좌표는 제외 —
          // 지오코딩 실패 placeholder(예: 19.69,117.99)가 fitAll을 외국까지 넓히는 것 방지.
          .filter(
            (v) =>
              v.lat != null && v.lng != null &&
              Number(v.lat) >= 33 && Number(v.lat) <= 38.7 &&
              Number(v.lng) >= 124.5 && Number(v.lng) <= 132,
          )
          .map((v) => ({
            id: v.id,
            lat: Number(v.lat),
            lng: Number(v.lng),
            category: v.category ?? "",
          })),
      ),
    [venues],
  );

  // 현재 위치로 지도 중심 이동(권한 요청 → 이동). 최초 진입·'현재 위치' 버튼 공용.
  const locateMe = useCallback(async () => {
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== "granted") return; // 거부 시 지도 기본 중심(서울) 유지
      const moveTo = (lat: number, lng: number) => {
        // 한국 밖 위치(에뮬레이터 기본값 등)면 이동하지 않고 서울 기본값 유지
        if (lat < 33 || lat > 38.7 || lng < 124.5 || lng > 132) return;
        webRef.current?.injectJavaScript(`window.moveTo(${lat}, ${lng}, 4); true;`);
      };
      const last = await Location.getLastKnownPositionAsync(); // 즉시 반응
      if (last) moveTo(last.coords.latitude, last.coords.longitude);
      const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      moveTo(pos.coords.latitude, pos.coords.longitude);
    } catch {
      // 위치 실패/권한 거부 시 기본 중심(서울) 유지
    }
  }, []);

  // 최초 진입 시 1회 현재 위치로 이동(경로 모드 아닐 때).
  const locatedRef = useRef(false);
  useEffect(() => {
    if (!ready || routeMode || locatedRef.current) return;
    locatedRef.current = true;
    locateMe();
  }, [ready, routeMode, locateMe]);

  useEffect(() => {
    if (!ready || !webRef.current) return;
    if (routeMode) {
      if (routeQuery.data) {
        const payload = JSON.stringify({
          path: routeQuery.data.path,
          stops: routeQuery.data.stops,
        });
        webRef.current.injectJavaScript(`window.drawRoute(${payload}); true;`);
      }
    } else {
      webRef.current.injectJavaScript(
        `window.clearRoute(); window.setMarkers(${markerPayload}); true;`,
      );
    }
  }, [ready, routeMode, routeQuery.data, markerPayload]);

  const onMessage = (e: { nativeEvent: { data: string } }) => {
    try {
      const msg = JSON.parse(e.nativeEvent.data);
      if (msg.type === "ready") {
        setReady(true);
        setMapError(null);
      }
      if (msg.type === "error") setMapError(String(msg.msg));
      if (msg.type === "select") {
        const v = venues.find((x) => x.id === msg.id);
        if (v) {
          setSelectedKakao(null);
          setSelected(v);
        }
      }
      if (msg.type === "kakaoResults") setKakaoCount(Number(msg.count));
      if (msg.type === "selectKakao" && msg.place) {
        setSelected(null);
        setSelectedKakao(msg.place as KakaoPlace);
      }
    } catch {
      /* ignore */
    }
  };

  return (
    <View style={styles.root}>
      {/* 지도 (전체화면 배경) */}
      <View style={styles.map}>
        {KAKAO_JS_KEY && !mapError ? (
          <>
            <KakaoMapView
              ref={webRef}
              jsKey={KAKAO_JS_KEY}
              segColors={ROUTE_SEGMENT_COLORS}
              baseUrl={KAKAO_WEB_ORIGIN}
              onMessage={onMessage}
              onError={setMapError}
            />
            {!ready && (
              <View style={styles.mapOverlay} pointerEvents="none">
                <Text style={styles.mapOverlayText}>
                  {mapError ?? "지도 불러오는 중…"}
                </Text>
              </View>
            )}
          </>
        ) : (
          // 카카오 키 미설정 시 폴백: 좌표 정규화 핀 오버레이
          <View style={styles.fallback}>
            <View style={styles.grid} pointerEvents="none" />
            <Text style={styles.mapHint}>서울 도심 · {venues.length}곳</Text>
            {venues.map((v) => (
              <Pressable
                key={v.id}
                style={[
                  styles.pin,
                  pos(v),
                  v.hanbokDiscount && styles.pinHanbok,
                  selected?.id === v.id && styles.pinSelected,
                ]}
                onPress={() => setSelected(v)}
              >
                <Text style={styles.pinText}>
                  {/* 리뷰가 없으면 "0.0"만 깔려 정보가 없다. 그럴 땐 카테고리를 보여준다. */}
                  {v.reviewCount > 0
                    ? Number(v.avgRating).toFixed(1)
                    : v.category || '·'}
                </Text>
              </Pressable>
            ))}
          </View>
        )}
      </View>

      {/* 상단 오버레이: 검색·칩·토글 또는 경로 배너 (지도 위에 떠 있음) */}
      <View
        style={[styles.topOverlay, { paddingTop: insets.top + 8 }]}
        pointerEvents="box-none"
      >
        {/* 경로 모드 배너 (여행 일지 경로 보기) */}
      {routeMode && (
        <View style={styles.routeBanner}>
          <Ionicons name="navigate" size={18} color={colors.white} />
          <View style={{ flex: 1 }}>
            {routeQuery.isLoading ? (
              <Text style={styles.routeBannerText}>경로 계산 중…</Text>
            ) : routeQuery.data ? (
              <Text style={styles.routeBannerText}>
                총 {fmtKm(routeQuery.data.distance)}km · 약{" "}
                {fmtMin(routeQuery.data.duration)} ·{" "}
                {routeQuery.data.stops.length}곳
              </Text>
            ) : (
              <Text style={styles.routeBannerText}>
                경로를 불러오지 못했어요
              </Text>
            )}
          </View>
          <Pressable
            hitSlop={8}
            onPress={exitRouteMode}
            style={styles.routeClose}
          >
            <Ionicons name="close" size={16} color={colors.white} />
            <Text style={styles.routeCloseText}>닫기</Text>
          </Pressable>
        </View>
      )}

      {/* 검색창 (카카오 전체 장소 + 우리 장소 검색) */}
      {!routeMode && (
        <>
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
                <Ionicons
                  name="close-circle"
                  size={18}
                  color={colors.textFaint}
                />
              </Pressable>
            )}
          </View>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            style={styles.chipsScroll}
            contentContainerStyle={styles.chips}
          >
            {CATS.map((c) => (
              <Chip
                key={c}
                label={c}
                selected={c === cat}
                onPress={() => setCat(c)}
              />
            ))}
          </ScrollView>
          {/* 한복 혜택 토글 */}
          <Pressable
            style={styles.toggleWrap}
            onPress={() => setHanbokOnly((p) => !p)}
          >
            <Text style={styles.toggleLabel}>
              👘 한복 입고 방문 시 할인되는 곳만
            </Text>
            <Switch
              value={hanbokOnly}
              onValueChange={setHanbokOnly}
              trackColor={{ true: colors.hanbok, false: "#D1D5DB" }}
              thumbColor={colors.white}
            />
          </Pressable>

          {/* 검색 결과 요약 */}
          {keyword.length > 0 && (
            <Text style={styles.resultHint}>
              '{keyword}' 검색 · 카카오{" "}
              <Text style={styles.resultNum}>{kakaoCount ?? 0}</Text>곳 · 우리
              장소 <Text style={styles.resultNum}>{venues.length}</Text>곳
            </Text>
          )}
        </>
      )}

      </View>

      {/* 현재 위치 버튼 (우하단 FAB) */}
      {!routeMode && (
        <Pressable
          style={[styles.locBtn, { bottom: (selected || selectedKakao ? 96 : 16) + tabH }]}
          onPress={locateMe}
          hitSlop={8}
        >
          <Ionicons name="locate" size={22} color={colors.primary} />
        </Pressable>
      )}

      {/* 선택된 우리 장소 미니 카드 */}
      {selected && (
        <Pressable
          style={[styles.miniCard, { bottom: tabH + 12 }]}
          onPress={() => router.push(`/venue/${selected.id}`)}
        >
          <Image
            source={selected.imageUrl}
            style={styles.miniImg}
            contentFit="cover"
          />
          <View style={{ flex: 1 }}>
            <Text style={styles.miniName}>{selected.name}</Text>
            <Text style={styles.miniAddr} numberOfLines={1}>
              {selected.address}
            </Text>
            <View
              style={{
                flexDirection: "row",
                alignItems: "center",
                gap: 6,
                marginTop: 3,
              }}
            >
              <Rating value={selected.avgRating} count={selected.reviewCount} />
              {selected.hanbokDiscount && (
                <Text style={styles.miniHanbok}>한복할인</Text>
              )}
            </View>
          </View>
          <Ionicons name="chevron-forward" size={20} color={colors.textFaint} />
        </Pressable>
      )}

      {/* 선택된 카카오 장소 미니 카드 */}
      {selectedKakao && (
        <Pressable
          style={[styles.miniCard, { bottom: tabH + 12 }]}
          onPress={() =>
            selectedKakao.url && Linking.openURL(selectedKakao.url)
          }
        >
          <View style={styles.kakaoThumb}>
            <Ionicons name="location" size={24} color={colors.accent} />
          </View>
          <View style={{ flex: 1 }}>
            <View
              style={{ flexDirection: "row", alignItems: "center", gap: 6 }}
            >
              <Text style={styles.miniName} numberOfLines={1}>
                {selectedKakao.name}
              </Text>
              <View style={styles.kakaoBadge}>
                <Text style={styles.kakaoBadgeText}>카카오</Text>
              </View>
            </View>
            <Text style={styles.miniAddr} numberOfLines={1}>
              {selectedKakao.address || "주소 정보 없음"}
            </Text>
            {!!selectedKakao.category && (
              <Text style={styles.kakaoCat}>
                {selectedKakao.category}
                {selectedKakao.phone ? ` · ${selectedKakao.phone}` : ""}
              </Text>
            )}
          </View>
          <Pressable hitSlop={8} onPress={() => setSelectedKakao(null)}>
            <Ionicons name="close" size={20} color={colors.textFaint} />
          </Pressable>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  topOverlay: { position: "absolute", top: 0, left: 0, right: 0 },
  locBtn: {
    position: "absolute",
    right: space.lg,
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: colors.white,
    alignItems: "center",
    justifyContent: "center",
    ...shadow.card,
  },
  search: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginHorizontal: space.lg,
    marginTop: 8,
    backgroundColor: colors.white,
    borderWidth: 1.5,
    borderColor: colors.accent,
    borderRadius: radius.pill,
    paddingHorizontal: 16,
    paddingVertical: 11,
  },
  searchInput: { flex: 1, fontSize: 14, color: colors.text, padding: 0 },
  routeBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    marginHorizontal: space.lg,
    marginTop: 8,
    backgroundColor: colors.primary,
    borderRadius: radius.pill,
    paddingHorizontal: 16,
    paddingVertical: 11,
    ...shadow.card,
  },
  routeBannerText: {
    color: colors.white,
    fontSize: 14,
    fontFamily: fonts.bold,
    fontWeight: "800",
  },
  routeClose: {
    flexDirection: "row",
    alignItems: "center",
    gap: 2,
    backgroundColor: "rgba(255,255,255,0.2)",
    borderRadius: radius.pill,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  routeCloseText: {
    color: colors.white,
    fontSize: 12,
    fontFamily: fonts.semibold,
    fontWeight: "600",
  },
  resultHint: {
    fontSize: 12,
    color: colors.textSub,
    marginHorizontal: space.lg,
    marginTop: 6,
    marginBottom: 2,
    fontFamily: fonts.medium,
    fontWeight: "500",
  },
  resultNum: {
    color: colors.accent,
    fontFamily: fonts.bold,
    fontWeight: "800",
  },
  toggleWrap: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: "#FDECEC",
    borderRadius: radius.md,
    paddingHorizontal: 14,
    paddingVertical: 8,
    marginHorizontal: space.lg,
    marginBottom: 4,
  },
  toggleLabel: {
    fontSize: 13,
    fontFamily: fonts.semibold,
    fontWeight: "600",
    color: colors.hanbok,
  },
  chipsScroll: { flexGrow: 0, flexShrink: 0 },
  chips: {
    gap: 8,
    paddingHorizontal: space.lg,
    paddingVertical: 10,
    alignItems: "center",
  },
  // 전체화면 배경 지도.
  map: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "#EAF0E6",
    overflow: "hidden",
  },
  mapOverlay: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: "center",
    justifyContent: "center",
    padding: 20,
  },
  mapOverlayText: {
    fontSize: 13,
    color: colors.textSub,
    textAlign: "center",
    fontFamily: fonts.medium,
    fontWeight: "500",
  },
  fallback: { flex: 1 },
  grid: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: "#E7EEE2",
  },
  mapHint: {
    position: "absolute",
    top: 10,
    left: 12,
    fontSize: 11,
    color: "#7C8B72",
    fontWeight: "700",
  },
  pin: {
    position: "absolute",
    minWidth: 34,
    height: 24,
    paddingHorizontal: 6,
    borderRadius: radius.pill,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    ...shadow.card,
    transform: [{ translateX: -17 }, { translateY: -12 }],
  },
  pinHanbok: { backgroundColor: colors.hanbok },
  pinSelected: {
    borderWidth: 2,
    borderColor: colors.white,
    transform: [{ translateX: -17 }, { translateY: -12 }, { scale: 1.15 }],
  },
  pinText: { color: colors.white, fontSize: 11, fontWeight: "800" },
  miniCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    position: "absolute",
    left: space.lg,
    right: space.lg,
    bottom: 16,
    backgroundColor: colors.white,
    borderRadius: radius.lg,
    padding: 12,
    ...shadow.card,
  },
  miniImg: {
    width: 60,
    height: 60,
    borderRadius: radius.md,
    backgroundColor: colors.bgSoft,
  },
  miniName: { fontSize: 15, fontWeight: "700", color: colors.text },
  miniAddr: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  miniHanbok: {
    fontSize: 10,
    fontWeight: "800",
    color: colors.hanbok,
    backgroundColor: "#FDECEC",
    borderRadius: 4,
    paddingHorizontal: 5,
    paddingVertical: 1,
  },
  kakaoThumb: {
    width: 60,
    height: 60,
    borderRadius: radius.md,
    backgroundColor: colors.accentSoft,
    alignItems: "center",
    justifyContent: "center",
  },
  kakaoBadge: {
    backgroundColor: colors.accentSoft,
    borderRadius: 4,
    paddingHorizontal: 5,
    paddingVertical: 1,
  },
  kakaoBadgeText: { fontSize: 10, fontWeight: "800", color: colors.accent },
  kakaoCat: { fontSize: 11, color: colors.textFaint, marginTop: 3 },
});
