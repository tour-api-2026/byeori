import { Ionicons } from '@expo/vector-icons';
import { Stack } from 'expo-router';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { CountLine } from '@/components/CountLine';
import LoginRequired from '@/components/LoginRequired';
import { useT } from '@/lib/i18n';
import { useBlockedUsersQuery, useUnblockUserMutation } from '@/lib/hooks/queries';
import { useAuthStore } from '@/lib/store/authStore';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, fonts, radius, space } from '@/lib/theme';

/** 차단한 사용자 관리 — 차단만 되고 해제할 수 없으면 안 되므로 반드시 필요한 화면. */
export default function BlockedUsersScreen() {
  const t = useT();
  const insets = useSafeAreaInsets();   // 안드로이드는 edge-to-edge 라 시스템 바 아래까지 그린다
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const { data, isLoading } = useBlockedUsersQuery();
  const unblock = useUnblockUserMutation();

  if (!isLoggedIn) {
    return (
      <View style={styles.safe}>
        <Stack.Screen options={{ title: t('profile.menu.blocked') }} />
        <LoginRequired description={t('blocked.loginDesc')} />
      </View>
    );
  }

  const confirmUnblock = (userId: number) => {
    Alert.alert(t('blocked.unblockTitle'), t('blocked.unblockBody'), [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: t('blocked.unblockConfirm'),
        onPress: () =>
          unblock.mutate(userId, {
            onError: (e: any) => Alert.alert(t('blocked.unblockFail'), e?.message ?? t('common.tryLater')),
          }),
      },
    ]);
  };

  const blocked = data ?? [];

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: t('profile.menu.blocked') }} />

      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : blocked.length ? (
        <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 28 + insets.bottom }}>
          <CountLine k="blocked.count" n={blocked.length} style={styles.count} numStyle={styles.countNum} />
          {blocked.map((userId) => (
            <View key={userId} style={styles.row}>
              <View style={styles.avatar}>
                <Ionicons name="person" size={16} color={colors.textFaint} />
              </View>
              <Text style={styles.name}>{t('blocked.user', { id: userId })}</Text>
              <Pressable
                style={styles.btn}
                disabled={unblock.isPending}
                onPress={() => confirmUnblock(userId)}>
                <Text style={styles.btnText}>{t('blocked.unblockTitle')}</Text>
              </Pressable>
            </View>
          ))}
        </ScrollView>
      ) : (
        <View style={styles.empty}>
          <Ionicons name="person-remove-outline" size={34} color={colors.textFaint} />
          <Text style={styles.emptyText}>{t('blocked.empty')}</Text>
          <Text style={styles.emptyDesc}>{t('blocked.emptyDesc')}</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  count: { fontSize: 13, color: colors.textSub, marginBottom: 12 },
  countNum: { fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  row: {
    flexDirection: 'row', alignItems: 'center', gap: 12,
    backgroundColor: colors.white, borderRadius: radius.md, padding: 14, marginBottom: 10,
  },
  avatar: { width: 34, height: 34, borderRadius: 17, backgroundColor: colors.bgSoft, alignItems: 'center', justifyContent: 'center' },
  name: { flex: 1, fontSize: 14, fontFamily: fonts.semibold, fontWeight: '600', color: colors.text },
  btn: { borderWidth: 1, borderColor: colors.border, borderRadius: radius.pill, paddingHorizontal: 14, paddingVertical: 7 },
  btnText: { fontSize: 13, fontFamily: fonts.medium, fontWeight: '500', color: colors.textSub },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 6, paddingHorizontal: space.xl },
  emptyText: { fontSize: 15, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 8 },
  emptyDesc: { fontSize: 13, color: colors.textFaint, textAlign: 'center' },
});
