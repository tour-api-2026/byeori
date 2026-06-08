import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
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

  const Row = ({ m }: { m: MenuItem }) => (
    <Pressable style={styles.menuRow} onPress={() => m.route && router.push(m.route as any)}>
      <View style={[styles.iconCircle, { backgroundColor: m.soft }]}><Ionicons name={m.icon as any} size={18} color={m.tint} /></View>
      <Text style={styles.menuLabel}>{m.label}</Text>
      <Ionicons name="chevron-forward" size={18} color={colors.textFaint} style={{ marginLeft: 'auto' }} />
    </Pressable>
  );

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView contentContainerStyle={{ paddingBottom: 28, paddingHorizontal: space.lg }}>
        <Text style={styles.h1}>마이페이지</Text>

        <Text style={styles.sectionLabel}>내 정보</Text>
        <View style={styles.profile}>
          <View style={styles.avatar} />
          <View style={{ flex: 1 }}>
            <Text style={styles.name}>벼리입니다</Text>
            <View style={styles.kakaoBadge}><Text style={styles.kakaoBadgeText}>Kakao 로그인</Text></View>
          </View>
          <Pressable hitSlop={8} onPress={() => router.push('/login')}><Ionicons name="create-outline" size={20} color={colors.textFaint} /></Pressable>
        </View>

        <Text style={styles.sectionLabel}>메뉴</Text>
        <View style={styles.card}>{GROUP1.map((m, i) => <View key={m.label}>{i > 0 && <View style={styles.divider} />}<Row m={m} /></View>)}</View>
        <View style={[styles.card, { marginTop: 12 }]}>{GROUP2.map((m, i) => <View key={m.label}>{i > 0 && <View style={styles.divider} />}<Row m={m} /></View>)}</View>

        <Pressable style={styles.logout} onPress={() => router.replace('/login')}>
          <Text style={styles.logoutText}>로그아웃</Text>
        </Pressable>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  h1: { fontSize: 17, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, textAlign: 'center', paddingVertical: 14 },
  sectionLabel: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 18, marginBottom: 12 },
  profile: { flexDirection: 'row', alignItems: 'center', gap: 14, backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 16, ...shadow.card },
  avatar: { width: 56, height: 56, borderRadius: 28, backgroundColor: colors.white, borderWidth: 2, borderColor: colors.accent },
  name: { fontSize: 18, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  kakaoBadge: { alignSelf: 'flex-start', backgroundColor: colors.kakaoBadge, borderRadius: radius.sm, paddingHorizontal: 8, paddingVertical: 3, marginTop: 6 },
  kakaoBadgeText: { fontSize: 11, fontFamily: fonts.semibold, fontWeight: '600', color: '#7A6A00' },
  card: { backgroundColor: colors.bgCard, borderRadius: radius.lg, ...shadow.card, overflow: 'hidden' },
  menuRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 14, paddingVertical: 15 },
  iconCircle: { width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  menuLabel: { fontSize: 15, fontFamily: fonts.medium, fontWeight: '500', color: colors.text },
  divider: { height: 1, backgroundColor: colors.border, marginLeft: 62 },
  logout: { marginTop: 28, borderWidth: 1.5, borderColor: colors.danger, borderRadius: radius.md, paddingVertical: 15, alignItems: 'center' },
  logoutText: { color: colors.danger, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
});
