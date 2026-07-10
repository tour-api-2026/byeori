import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTabBarHeight } from '@/components/TabBar';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

type MenuItem = { icon: string; label: string; route?: string; tint: string; soft: string };
const GROUP1: MenuItem[] = [
  { icon: 'chatbubble-ellipses', label: '내가 쓴 리뷰', route: '/my/reviews', tint: colors.accent, soft: colors.accentSoft },
  { icon: 'location', label: '내가 등록한 장소', route: '/my/venues', tint: colors.primary, soft: colors.primarySoft },
  { icon: 'heart', label: '찜한 장소', route: '/bookmarks', tint: colors.hanbok, soft: '#FDECEC' },
];
const GROUP2: MenuItem[] = [
  { icon: 'help-circle', label: '문의하기', tint: colors.textSub, soft: colors.bgSoft },
  { icon: 'information-circle', label: '서비스 정보', tint: colors.textSub, soft: colors.bgSoft },
];

export default function ProfileScreen() {
  const router = useRouter();
  const tabH = useTabBarHeight();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  const onLogout = async () => {
    await logout();
    router.replace('/login');
  };

  const Row = ({ m }: { m: MenuItem }) => (
    <Pressable style={styles.menuRow} onPress={() => m.route && router.push(m.route as any)}>
      <View style={[styles.iconCircle, { backgroundColor: m.soft }]}><Ionicons name={m.icon as any} size={18} color={m.tint} /></View>
      <Text style={styles.menuLabel}>{m.label}</Text>
      <Ionicons name="chevron-forward" size={18} color={colors.textFaint} style={{ marginLeft: 'auto' }} />
    </Pressable>
  );

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView contentContainerStyle={{ paddingBottom: tabH + 28, paddingHorizontal: space.lg }}>
        <Text style={styles.h1}>마이페이지</Text>

        <Text style={styles.sectionLabel}>내 정보</Text>
        {isLoggedIn ? (
          <View style={styles.profile}>
            {user?.profileImageUrl ? (
              <Image source={user.profileImageUrl} style={styles.avatar} contentFit="cover" />
            ) : (
              <View style={styles.avatar} />
            )}
            <View style={{ flex: 1 }}>
              <Text style={styles.name} numberOfLines={1}>{user?.name ?? '벼리 회원'}</Text>
              {!!user?.email && <Text style={styles.email} numberOfLines={1}>{user.email}</Text>}
            </View>
          </View>
        ) : (
          <Pressable style={styles.loginCard} onPress={() => router.push('/login')}>
            <View style={styles.loginIcon}><Ionicons name="person" size={20} color={colors.primary} /></View>
            <View style={{ flex: 1 }}>
              <Text style={styles.name}>로그인이 필요해요</Text>
              <Text style={styles.email}>로그인하고 벼리의 모든 기능을 이용해보세요</Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color={colors.textFaint} />
          </Pressable>
        )}

        <Text style={styles.sectionLabel}>메뉴</Text>
        <View style={styles.card}>{GROUP1.map((m, i) => <View key={m.label}>{i > 0 && <View style={styles.divider} />}<Row m={m} /></View>)}</View>
        <View style={[styles.card, { marginTop: 12 }]}>{GROUP2.map((m, i) => <View key={m.label}>{i > 0 && <View style={styles.divider} />}<Row m={m} /></View>)}</View>

        {isLoggedIn ? (
          <Pressable style={styles.logout} onPress={onLogout}>
            <Text style={styles.logoutText}>로그아웃</Text>
          </Pressable>
        ) : (
          <Pressable style={styles.loginBtn} onPress={() => router.push('/login')}>
            <Text style={styles.loginBtnText}>로그인하기</Text>
          </Pressable>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  h1: { fontSize: 17, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, textAlign: 'center', paddingVertical: 14 },
  sectionLabel: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 18, marginBottom: 12 },
  profile: { flexDirection: 'row', alignItems: 'center', gap: 14, backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 16, ...shadow.card },
  loginCard: { flexDirection: 'row', alignItems: 'center', gap: 14, backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 16, ...shadow.card },
  loginIcon: { width: 56, height: 56, borderRadius: 28, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center' },
  avatar: { width: 56, height: 56, borderRadius: 28, backgroundColor: colors.white, borderWidth: 2, borderColor: colors.accent },
  name: { fontSize: 18, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  email: { fontSize: 13, fontFamily: fonts.medium, fontWeight: '500', color: colors.textSub, marginTop: 4 },
  card: { backgroundColor: colors.bgCard, borderRadius: radius.lg, ...shadow.card, overflow: 'hidden' },
  menuRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 14, paddingVertical: 15 },
  iconCircle: { width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  menuLabel: { fontSize: 15, fontFamily: fonts.medium, fontWeight: '500', color: colors.text },
  divider: { height: 1, backgroundColor: colors.border, marginLeft: 62 },
  logout: { marginTop: 28, borderWidth: 1.5, borderColor: colors.danger, borderRadius: radius.md, paddingVertical: 15, alignItems: 'center' },
  logoutText: { color: colors.danger, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
  loginBtn: { marginTop: 28, backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 15, alignItems: 'center' },
  loginBtnText: { color: colors.white, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
});
