import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Platform,
  Pressable,
  ScrollView,
  Share,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import LoginRequired from "@/components/LoginRequired";
import { Refresh } from "@/components/Refresh";
import { useTabBarHeight } from "@/components/TabBar";
import { ItinerarySummary, shareItinerary } from "@/lib/api/itineraries";
import { useAiStatusQuery, useMyItinerariesQuery } from "@/lib/hooks/queries";
import { useAuthStore } from "@/lib/store/authStore";
import { colors, fonts, radius, shadow, space } from "@/lib/theme";
import { segmentColor } from "@/lib/routeColors";

/**
 * 공유 링크의 바탕 주소.
 *
 * 웹에서는 지금 열려 있는 오리진을 그대로 쓴다 — 로컬에서 띄워 보면 로컬 주소가 나와야
 * 눌러서 확인할 수 있다. 앱에는 주소창이 없으니 배포 도메인을 적는다.
 */
const SITE_ORIGIN =
  Platform.OS === "web" ? window.location.origin : "https://byeori.ernebi.org";

const pad = (n: number) => String(n).padStart(2, "0");
/** '2026-10-20' → '10.20' — 목록 한 줄에 기간을 담으려면 연도를 뺀다. */
const shortDate = (d: string) => d.slice(5).replace("-", ".");
function todayIso() {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

type Phase = "ongoing" | "upcoming" | "past";
function phaseOf(start: string, end: string, today: string): Phase {
  if (today < start) return "upcoming";
  if (today > end) return "past";
  return "ongoing";
}
const isPastTrip = (it: ItinerarySummary) =>
  phaseOf(it.startDate, it.endDate, todayIso()) === "past";

// "지금에 가장 가까운" 순으로 정렬하기 위한 키.
// 1순위: 진행중(0) < 예정(1) < 지난여행(2)
// 2순위: 같은 단계 안에서 오늘과 가까운 순
//  - 진행중: 곧 끝나는 순 / 예정: 시작 임박 순 / 지난: 최근에 끝난 순
function sortKey(it: ItinerarySummary, today: string): [number, number] {
  const t = +new Date(today + "T00:00:00");
  const ph = phaseOf(it.startDate, it.endDate, today);
  if (ph === "ongoing") return [0, +new Date(it.endDate) - t];
  if (ph === "upcoming") return [1, +new Date(it.startDate) - t];
  return [2, t - +new Date(it.endDate)];
}

function dayLabel(start: string, end: string) {
  const t = todayIso();
  if (t < start)
    return {
      big: "D-" + Math.round((+new Date(start) - +new Date(t)) / 86400000),
      small: "여행 전",
    };
  if (t > end) return { big: "완료", small: "지난 여행" };
  const n =
    Math.round(
      (+new Date(t + "T00:00:00") - +new Date(start + "T00:00:00")) / 86400000,
    ) + 1;
  return { big: `${n}일차`, small: "오늘" };
}

export default function RoutesScreen() {
  const router = useRouter();
  const tabH = useTabBarHeight();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const mine = useMyItinerariesQuery();
  const aiStatus = useAiStatusQuery();
  const today = todayIso();
  // 지금에 가까운 순으로 정렬: 대표(상단)·목록 모두 이 순서를 따른다.
  const list = [...(mine.data ?? [])].sort((a, b) => {
    const ka = sortKey(a, today);
    const kb = sortKey(b, today);
    return ka[0] - kb[0] || ka[1] - kb[1];
  });
  const featured = list[0];

  if (!isLoggedIn) {
    return (
      <SafeAreaView style={styles.safe} edges={["top"]}>
        <Text style={styles.h1}>내 여행 루트</Text>
        <LoginRequired description="나만의 여행 루트는 로그인 후에 만들 수 있어요." />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <Text style={styles.h1}>내 여행 루트</Text>
      <ScrollView
        refreshControl={<Refresh />}
        contentContainerStyle={{
          paddingHorizontal: space.lg,
          paddingBottom: tabH + 28,
        }}
        showsVerticalScrollIndicator={false}
      >
        {mine.isLoading ? (
          <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
        ) : (
          <>
            <Text style={styles.lead}>지금 나의 여행 루트는?</Text>
            {featured ? (
              <Featured summary={featured} />
            ) : (
              <View style={styles.emptyCard}>
                <Text style={styles.emptyText}>
                  아직 만든 여행 루트가 없어요
                </Text>
              </View>
            )}
            {aiStatus.data?.enabled ? (
              <Pressable style={styles.aiCard} onPress={() => router.push("/ai-route")}>
                <View style={styles.aiIcon}>
                  <Ionicons name="sparkles" size={20} color={colors.white} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.aiTitle}>AI로 하루 코스 만들기</Text>
                  <Text style={styles.aiDesc}>
                    지역과 테마만 고르면 벼리의 장소·행사로 코스를 짜 드려요
                  </Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.primary} />
              </Pressable>
            ) : null}
            <View style={styles.makeCard}>
              <Text style={styles.makeText}>
                나만의 새로운 루트를{"\n"}만들어 보세요!
              </Text>
              <Pressable
                style={styles.makeBtn}
                onPress={() => router.push("/itinerary/new")}
              >
                <Text style={styles.makeBtnText}>새 루트 만들기</Text>
                <Ionicons
                  name="chevron-forward"
                  size={13}
                  color={colors.white}
                />
              </Pressable>
            </View>
            {list.length > 0 && (
              <Text style={styles.listLabel}>내 여행 루트 목록</Text>
            )}
            {list.map((it) => (
              <RouteCard key={it.id} summary={it} />
            ))}
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function Featured({ summary }: { summary: ItinerarySummary }) {
  const router = useRouter();
  // 목록이 앞 몇 곳을 함께 준다 — 상단 카드가 상세를 따로 받으면 제목·기간은 먼저 뜨고
  // 1-2-3 방문지 줄만 뒤늦게 나타난다.
  const stops = (summary.stops ?? []).slice(0, 3);
  const d = dayLabel(summary.startDate, summary.endDate);

  return (
    <View style={styles.featured}>
      <View style={styles.featuredLeft}>
        <Text style={styles.featuredName} numberOfLines={2}>
          {summary.title}
        </Text>
        <View style={{ alignItems: "center", marginVertical: 12 }}>
          <Text style={styles.featuredSmall}>{d.small}</Text>
          <Text style={styles.featuredBig}>{d.big}</Text>
        </View>
        <Pressable
          style={styles.confirmBtn}
          onPress={() => router.push(`/itinerary/${summary.id}`)}
        >
          <Text style={styles.confirmText}>전체 루트 확인</Text>
          <Ionicons name="chevron-forward" size={13} color={colors.white} />
        </Pressable>
      </View>
      <View style={styles.featuredRight}>
        {stops.length ? (
          stops.map((s, i, arr) => (
            <View key={s.id} style={styles.tRow}>
              <View style={styles.rail}>
                {i > 0 && <View style={[styles.rLine, styles.rTop, { backgroundColor: segmentColor(i - 1) }]} />}
                {i < arr.length - 1 && (
                  <View style={[styles.rLine, styles.rBot, { backgroundColor: segmentColor(i) }]} />
                )}
                <View style={[styles.tlNum, { backgroundColor: segmentColor(i) }]}>
                  <Text style={styles.tlNumText}>{i + 1}</Text>
                </View>
              </View>
              <Text style={styles.tlName} numberOfLines={1}>
                {s.name ?? "장소"}
              </Text>
            </View>
          ))
        ) : (
          <Text style={styles.tlEmpty}>장소를 추가해보세요</Text>
        )}
      </View>
    </View>
  );
}

function RouteCard({ summary }: { summary: ItinerarySummary }) {
  const router = useRouter();
  // 목록이 앞 몇 곳을 함께 준다. 카드마다 상세를 또 받으면 제목만 먼저 뜨고 방문지 줄이
  // 뒤늦게 나타난다(루트 다섯이면 요청 여섯 번). 전체 개수는 summary.itemCount 를 본다.
  const stops = summary.stops ?? [];
  const past = isPastTrip(summary); // 지난 여행은 물빠진 색으로 흐리게

  const [shareState, setShareState] = useState<"idle" | "working" | "copied" | "failed">("idle");

  /**
   * 공유 링크를 받아 보낸다.
   *
   * 토큰은 서버가 처음 한 번만 만들고 그 뒤로는 같은 값을 준다. 공유를 다시 눌러도 링크가
   * 바뀌지 않아, 먼저 보낸 링크가 계속 살아 있다.
   *
   * 웹이 까다롭다. react-native-web 의 Share 는 navigator.share 가 없으면 그냥 거부하는데
   * (데스크톱 파이어폭스·일부 크롬), 같은 RN-web 의 Alert.alert 는 **빈 함수**라 실패를
   * 알릴 수도 없다. 그래서 웹에서는 공유 창 대신 링크를 복사하고, 버튼 글자로 알린다.
   */
  const share = async () => {
    if (shareState === "working") return;
    setShareState("working");
    try {
      const { token } = await shareItinerary(summary.id);
      const url = `${SITE_ORIGIN}/s/${token}`;
      const text = `[벼리] '${summary.title}' 여행 루트 (${summary.startDate} ~ ${summary.endDate})`;

      if (Platform.OS === "web" && !(navigator as any).share) {
        await navigator.clipboard.writeText(url);
        setShareState("copied");
        setTimeout(() => setShareState("idle"), 2000);
        return;
      }

      await Share.share(
        // 안드로이드는 url 필드를 무시하므로 message 에 함께 넣어야 링크가 간다.
        Platform.OS === "ios" ? { message: text, url } : { message: `${text}\n${url}` },
      );
      setShareState("idle");
    } catch (e: any) {
      // 네이티브에서는 Alert 가 보이고, 웹에서는 버튼 글자가 유일한 통로다.
      if (Platform.OS !== "web") {
        Alert.alert("공유하지 못했어요", e?.message ?? "잠시 후 다시 시도해 주세요.");
        setShareState("idle");
      } else {
        setShareState("failed");
        setTimeout(() => setShareState("idle"), 2500);
      }
    }
  };

  const shareLabel =
    shareState === "working" ? "준비 중…"
    : shareState === "copied" ? "복사됨"
    : "실패";

  // 방문지는 한 줄로 줄인다 — 번호 레일은 루트 편집 화면에 이미 있다.
  const stopLine = stops.map((x) => x.name ?? "장소").join(" · ");
  const more = summary.itemCount - stops.length;

  return (
    <Pressable
      style={[styles.card, past && styles.cardPast]}
      onPress={() => router.push(`/itinerary/${summary.id}`)}
    >
      <View style={{ flex: 1 }}>
        <Text style={styles.cardTitle} numberOfLines={1}>
          {summary.title}
        </Text>
        <Text style={styles.cardMeta} numberOfLines={1}>
          {past ? "지난 여행 · " : ""}
          {summary.itemCount}곳 · {shortDate(summary.startDate)} ~ {shortDate(summary.endDate)}
        </Text>
        {stops.length ? (
          <Text style={styles.cardStops} numberOfLines={1}>
            {stopLine}{more > 0 ? ` · +${more}` : ""}
          </Text>
        ) : (
          <Text style={styles.cardEmpty}>아직 담은 장소가 없어요</Text>
        )}
      </View>

      {summary.itemCount >= 2 && (
        <Pressable
          style={styles.iconBtn}
          hitSlop={6}
          onPress={() => router.push(`/route/${summary.id}`)}
        >
          <Ionicons name="navigate-outline" size={17} color={colors.primary} />
        </Pressable>
      )}
      {/* 공유 결과는 이 버튼이 유일한 통로다 — 웹의 Alert.alert 는 빈 함수다(share 주석 참고).
          그래서 진행·완료·실패일 때는 아이콘 대신 짧은 글자를 띄운다. */}
      <Pressable
        style={[styles.iconBtn, shareState !== "idle" && styles.iconBtnWide]}
        hitSlop={6}
        disabled={shareState === "working"}
        onPress={share}
      >
        {shareState === "idle" ? (
          <Ionicons name="share-outline" size={17} color={colors.primary} />
        ) : (
          <Text style={styles.shareText}>{shareLabel}</Text>
        )}
      </Pressable>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  h1: {
    fontSize: 17,
    fontFamily: fonts.bold,
    fontWeight: "800",
    color: colors.text,
    textAlign: "center",
    paddingVertical: 14,
  },
  lead: {
    fontSize: 16,
    fontFamily: fonts.bold,
    fontWeight: "800",
    color: colors.text,
    marginTop: 6,
    marginBottom: 12,
  },
  emptyCard: {
    backgroundColor: colors.bgCard,
    borderRadius: radius.lg,
    padding: 28,
    alignItems: "center",
    ...shadow.card,
  },
  emptyText: { fontSize: 14, color: colors.textFaint },
  // featured
  featured: {
    flexDirection: "row",
    backgroundColor: colors.bgCard,
    borderRadius: radius.lg,
    padding: 16,
    ...shadow.card,
  },
  featuredLeft: {
    width: 128,
    alignItems: "center",
    justifyContent: "center",
    borderRightWidth: 1,
    borderRightColor: colors.border,
    paddingRight: 12,
  },
  featuredName: {
    fontSize: 14,
    fontFamily: fonts.bold,
    fontWeight: "800",
    color: colors.text,
    textAlign: "center",
  },
  featuredSmall: { fontSize: 12, color: colors.textFaint },
  featuredBig: {
    fontSize: 26,
    fontFamily: fonts.bold,
    fontWeight: "800",
    color: colors.text,
    marginTop: 2,
  },
  confirmBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 2,
    backgroundColor: colors.primary,
    borderRadius: radius.pill,
    paddingHorizontal: 12,
    paddingVertical: 7,
  },
  confirmText: {
    color: colors.white,
    fontSize: 11,
    fontFamily: fonts.bold,
    fontWeight: "800",
  },
  featuredRight: { flex: 1, paddingLeft: 8, justifyContent: "center" },
  // 타임라인 (번호 + 연결선)
  tRow: { flexDirection: "row", alignItems: "center", minHeight: 40 },
  rail: {
    width: 26,
    alignSelf: "stretch",
    alignItems: "center",
    justifyContent: "center",
    marginRight: 6,
  },
  rLine: {
    position: "absolute",
    width: 2,
    left: 12,
    backgroundColor: colors.border,
  },
  rTop: { top: 0, bottom: "50%" },
  rBot: { top: "50%", bottom: 0 },
  tlNum: {
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  tlNumText: {
    color: colors.white,
    fontSize: 11,
    fontFamily: fonts.bold,
    fontWeight: "800",
  },
  tlName: {
    flex: 1,
    fontSize: 13,
    color: colors.textSub,
    fontFamily: fonts.medium,
    fontWeight: "500",
  },
  tlEmpty: { fontSize: 13, color: colors.textFaint },
  // 새 루트 만들기 카드
  aiCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: colors.primarySoft,
    borderRadius: radius.lg,
    padding: 16,
    marginTop: 14,
  },
  aiIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  aiTitle: { fontSize: 15, fontFamily: fonts.bold, fontWeight: "800", color: colors.primary },
  aiDesc: { fontSize: 12, color: colors.textSub, marginTop: 3, lineHeight: 17 },
  makeCard: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    backgroundColor: colors.bgCard,
    borderRadius: radius.lg,
    padding: 16,
    marginVertical: 18,
    ...shadow.card,
  },
  makeText: {
    fontSize: 14,
    fontFamily: fonts.bold,
    fontWeight: "800",
    color: colors.text,
    lineHeight: 20,
  },
  makeBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 2,
    backgroundColor: colors.primary,
    borderRadius: radius.pill,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  makeBtnText: {
    color: colors.white,
    fontSize: 13,
    fontFamily: fonts.bold,
    fontWeight: "800",
  },
  listLabel: {
    fontSize: 16,
    fontFamily: fonts.bold,
    fontWeight: "800",
    color: colors.text,
    marginTop: 6,
    marginBottom: 12,
  },
  // route card
  // 목록 카드: 한 줄 요약. 전에는 제목·기간·공유·방문지 5줄·버튼 2개로 240px 가까이 되어
  // 루트가 셋만 넘어도 화면을 넘겼다. 자세한 것은 눌러서 들어가 본다.
  card: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    backgroundColor: colors.bgCard,
    borderRadius: radius.md,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 8,
    ...shadow.card,
  },
  // 지난 여행: 전체적으로 물빠진(흐린) 느낌
  cardPast: { opacity: 0.55 },
  cardTitle: {
    fontSize: 14,
    fontFamily: fonts.bold,
    fontWeight: "800",
    color: colors.text,
  },
  cardMeta: { fontSize: 11, color: colors.textFaint, marginTop: 2 },
  cardStops: { fontSize: 12, color: colors.textSub, marginTop: 3 },
  cardEmpty: { fontSize: 12, color: colors.textFaint, marginTop: 3 },
  iconBtn: {
    width: 34,
    height: 34,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
    justifyContent: "center",
  },
  iconBtnWide: { width: "auto", paddingHorizontal: 10 },
  shareText: {
    color: colors.primary,
    fontSize: 11,
    fontFamily: fonts.semibold,
    fontWeight: "600",
  },
});
