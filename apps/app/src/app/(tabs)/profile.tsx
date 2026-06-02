import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useBookmarkStore } from '@/lib/store/bookmarkStore';
import { colors, radius, space } from '@/lib/theme';

type MenuItem = { icon: string; label: string; route?: string };
const MENU: MenuItem[] = [
  { icon: 'bookmark-outline', label: '즐겨찾기', route: '/bookmarks' },
  { icon: 'chatbox-ellipses-outline', label: '내가 쓴 리뷰' },
  { icon: 'location-outline', label: '내가 등록한 장소' },
  { icon: 'help-circle-outline', label: '문의하기' },
  { icon: 'information-circle-outline', label: '서비스 정보' },
];

export default function ProfileScreen() {
  const router = useRouter();
  const bookmarkCount = useBookmarkStore((s) => s.venueIds.length);

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView contentContainerStyle={{ paddingBottom: 24 }}>
        <Text style={styles.h1}>마이페이지</Text>

        {/* 프로필 */}
        <View style={styles.profile}>
          <View style={styles.avatar}><Ionicons name="person" size={32} color={colors.textFaint} /></View>
          <View style={{ flex: 1 }}>
            <Text style={styles.name}>벼리</Text>
            <Text style={styles.email}>여행을 시작해보세요</Text>
          </View>
          <Pressable onPress={() => router.push('/login')}><Ionicons name="create-outline" size={20} color={colors.textFaint} /></Pressable>
        </View>

        {/* 여행 계획 프로모 */}
        <View style={styles.promo}>
          <Text style={styles.promoText}>아직 여행 계획이 없어요.{'\n'}나만의 여행 루트를 만들어 볼까요?</Text>
          <Pressable style={styles.promoBtn} onPress={() => router.push('/(tabs)/routes')}>
            <Text style={styles.promoBtnText}>내 여행 계획 만들기</Text>
            <Ionicons name="chevron-forward" size={14} color={colors.white} />
          </Pressable>
        </View>

        {/* 메뉴 */}
        <View style={styles.menu}>
          {MENU.map((m) => (
            <Pressable key={m.label} style={styles.menuRow} onPress={() => m.route && router.push(m.route as any)}>
              <Ionicons name={m.icon as any} size={20} color={colors.textSub} />
              <Text style={styles.menuLabel}>{m.label}</Text>
              {m.label === '즐겨찾기' && bookmarkCount > 0 && <Text style={styles.badge}>{bookmarkCount}</Text>}
              <Ionicons name="chevron-forward" size={18} color={colors.textFaint} style={{ marginLeft: 'auto' }} />
            </Pressable>
          ))}
        </View>

        <Pressable style={styles.logout} onPress={() => router.push('/login')}>
          <Text style={styles.logoutText}>로그아웃</Text>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  h1: { fontSize: 18, fontWeight: '800', color: colors.text, textAlign: 'center', paddingVertical: 12 },
  profile: { flexDirection: 'row', alignItems: 'center', gap: 14, paddingHorizontal: space.lg, paddingVertical: 12 },
  avatar: { width: 64, height: 64, borderRadius: 32, backgroundColor: colors.bgSoft, alignItems: 'center', justifyContent: 'center' },
  name: { fontSize: 20, fontWeight: '800', color: colors.text },
  email: { fontSize: 13, color: colors.textFaint, marginTop: 2 },
  promo: { margin: space.lg, backgroundColor: colors.bgSoft, borderRadius: radius.lg, padding: 18, alignItems: 'center' },
  promoText: { fontSize: 14, color: colors.textSub, textAlign: 'center', lineHeight: 21, marginBottom: 12 },
  promoBtn: { flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: colors.primary, borderRadius: radius.pill, paddingHorizontal: 18, paddingVertical: 10 },
  promoBtnText: { color: colors.white, fontSize: 13, fontWeight: '700' },
  menu: { paddingHorizontal: space.lg, marginTop: 4 },
  menuRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: colors.border },
  menuLabel: { fontSize: 15, color: colors.text },
  badge: { backgroundColor: colors.primarySoft, color: colors.primary, fontSize: 11, fontWeight: '800', borderRadius: 10, paddingHorizontal: 7, paddingVertical: 2, marginLeft: 6 },
  logout: { margin: space.lg, marginTop: 24, backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 15, alignItems: 'center' },
  logoutText: { color: colors.white, fontSize: 15, fontWeight: '800' },
});
