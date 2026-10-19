import { Ionicons } from '@expo/vector-icons';
import { Image } from '@/components/Image';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTabBarHeight } from '@/components/TabBar';
import { deleteAccount } from '@/lib/api/account';
import { useAuthStore } from '@/lib/store/authStore';
import { useT } from '@/lib/i18n';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

const CONTACT_EMAIL = 'alstjq1012@gmail.com';

type MenuItem = { icon: string; labelKey: string; route?: string; action?: 'contact'; tint: string; soft: string };
const GROUP1: MenuItem[] = [
  // 온보딩에서 한 번 고르고 끝이면 바꿀 길이 없다. 취향은 변한다.
  { icon: 'sparkles', labelKey: 'profile.menu.interests', route: '/onboarding?edit=1', tint: colors.primary, soft: colors.primarySoft },
  // 카카오·구글을 둘 다 쓰는 사람은 계정이 갈려 있다. 여기서 이어 붙인다.
  { icon: 'link', labelKey: 'profile.menu.accounts', route: '/my/accounts', tint: colors.accent, soft: colors.accentSoft },
  { icon: 'chatbubble-ellipses', labelKey: 'profile.menu.reviews', route: '/my/reviews', tint: colors.accent, soft: colors.accentSoft },
  { icon: 'location', labelKey: 'profile.menu.venues', route: '/my/venues', tint: colors.primary, soft: colors.primarySoft },
  { icon: 'heart', labelKey: 'profile.menu.bookmarks', route: '/bookmarks', tint: colors.hanbok, soft: '#FDECEC' },
];
const GROUP2: MenuItem[] = [
  { icon: 'help-circle', labelKey: 'profile.menu.contact', action: 'contact', tint: colors.textSub, soft: colors.bgSoft },
  { icon: 'person-remove', labelKey: 'profile.menu.blocked', route: '/my/blocked', tint: colors.textSub, soft: colors.bgSoft },
  { icon: 'language', labelKey: 'profile.menu.language', route: '/my/language', tint: colors.textSub, soft: colors.bgSoft },
  { icon: 'information-circle', labelKey: 'profile.menu.serviceInfo', route: '/my/service-info', tint: colors.textSub, soft: colors.bgSoft },
];

export default function ProfileScreen() {
  const router = useRouter();
  const t = useT();
  const tabH = useTabBarHeight();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  const [deleting, setDeleting] = useState(false);

  const onLogout = async () => {
    await logout();
    router.replace('/login');
  };

  // 계정 삭제: 되돌릴 수 없으므로 2단계 확인
  const onDeleteAccount = () => {
    Alert.alert(
      t('profile.delete.title'),
      t('profile.delete.body'),
      [
        { text: t('common.cancel'), style: 'cancel' },
        {
          text: t('common.delete'),
          style: 'destructive',
          onPress: async () => {
            if (deleting) return;
            setDeleting(true);
            try {
              await deleteAccount();
              await logout();
              Alert.alert(t('profile.delete.doneTitle'), t('profile.delete.doneBody'));
              router.replace('/login');
            } catch (e: any) {
              Alert.alert(t('profile.delete.failTitle'), e?.message ?? t('common.tryLater'));
            } finally {
              setDeleting(false);
            }
          },
        },
      ],
    );
  };

  const onMenuPress = (m: MenuItem) => {
    if (m.action === 'contact') {
      Linking.openURL(`mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent(t('profile.mail.subject'))}`);
      return;
    }
    if (m.route) router.push(m.route as any);
  };

  const Row = ({ m }: { m: MenuItem }) => (
    <Pressable style={styles.menuRow} onPress={() => onMenuPress(m)}>
      <View style={[styles.iconCircle, { backgroundColor: m.soft }]}><Ionicons name={m.icon as any} size={18} color={m.tint} /></View>
      <Text style={styles.menuLabel}>{t(m.labelKey)}</Text>
      <Ionicons name="chevron-forward" size={18} color={colors.textFaint} style={{ marginLeft: 'auto' }} />
    </Pressable>
  );

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView contentContainerStyle={{ paddingBottom: tabH + 28, paddingHorizontal: space.lg }}>
        <Text style={styles.h1}>{t('profile.title')}</Text>

        <Text style={styles.sectionLabel}>{t('profile.section.info')}</Text>
        {isLoggedIn ? (
          <Pressable style={styles.profile} onPress={() => router.push('/my/profile')} accessibilityLabel={t('profile.edit.a11y')}>
            {user?.profileImageUrl ? (
              <Image source={user.profileImageUrl} style={styles.avatar} contentFit="cover" />
            ) : (
              <View style={[styles.avatar, styles.avatarEmpty]}>
                <Ionicons name="person" size={26} color={colors.textFaint} />
              </View>
            )}
            <View style={{ flex: 1 }}>
              <Text style={styles.name} numberOfLines={1}>{user?.name ?? t('profile.defaultName')}</Text>
              {!!user?.email && <Text style={styles.email} numberOfLines={1}>{user.email}</Text>}
            </View>
            <View style={styles.editChip}>
              <Ionicons name="pencil" size={12} color={colors.primary} />
              <Text style={styles.editChipText}>{t('profile.edit')}</Text>
            </View>
          </Pressable>
        ) : (
          <Pressable style={styles.loginCard} onPress={() => router.push('/login')}>
            <View style={styles.loginIcon}><Ionicons name="person" size={20} color={colors.primary} /></View>
            <View style={{ flex: 1 }}>
              <Text style={styles.name}>{t('profile.loginNeeded')}</Text>
              <Text style={styles.email}>{t('profile.loginHint')}</Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color={colors.textFaint} />
          </Pressable>
        )}

        <Text style={styles.sectionLabel}>{t('profile.section.menu')}</Text>
        <View style={styles.card}>{GROUP1.map((m, i) => <View key={m.labelKey}>{i > 0 && <View style={styles.divider} />}<Row m={m} /></View>)}</View>
        <View style={[styles.card, { marginTop: 12 }]}>{GROUP2.map((m, i) => <View key={m.labelKey}>{i > 0 && <View style={styles.divider} />}<Row m={m} /></View>)}</View>

        {isLoggedIn ? (
          <>
            <Pressable style={styles.logout} onPress={onLogout}>
              <Text style={styles.logoutText}>{t('profile.logout')}</Text>
            </Pressable>
            <Pressable style={styles.deleteAccount} onPress={onDeleteAccount} disabled={deleting}>
              <Text style={styles.deleteAccountText}>{deleting ? t('profile.deleting') : t('profile.delete')}</Text>
            </Pressable>
          </>
        ) : (
          <Pressable style={styles.loginBtn} onPress={() => router.push('/login')}>
            <Text style={styles.loginBtnText}>{t('profile.login')}</Text>
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
  avatarEmpty: { alignItems: 'center', justifyContent: 'center', borderColor: colors.border },
  name: { fontSize: 18, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  editChip: {
    flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 10, paddingVertical: 6,
    borderRadius: radius.pill, backgroundColor: colors.primarySoft,
  },
  editChipText: { fontSize: 12, fontFamily: fonts.bold, fontWeight: '700', color: colors.primary },
  email: { fontSize: 13, fontFamily: fonts.medium, fontWeight: '500', color: colors.textSub, marginTop: 4 },
  card: { backgroundColor: colors.bgCard, borderRadius: radius.lg, ...shadow.card, overflow: 'hidden' },
  menuRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 14, paddingVertical: 15 },
  iconCircle: { width: 36, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  menuLabel: { fontSize: 15, fontFamily: fonts.medium, fontWeight: '500', color: colors.text },
  divider: { height: 1, backgroundColor: colors.border, marginLeft: 62 },
  logout: { marginTop: 28, borderWidth: 1.5, borderColor: colors.danger, borderRadius: radius.md, paddingVertical: 15, alignItems: 'center' },
  logoutText: { color: colors.danger, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
  deleteAccount: { marginTop: 12, paddingVertical: 12, alignItems: 'center' },
  deleteAccountText: { color: colors.textFaint, fontSize: 13, fontFamily: fonts.medium, textDecorationLine: 'underline' },
  loginBtn: { marginTop: 28, backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 15, alignItems: 'center' },
  loginBtnText: { color: colors.white, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
});
