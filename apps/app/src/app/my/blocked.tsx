import { Ionicons } from '@expo/vector-icons';
import { Stack } from 'expo-router';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import LoginRequired from '@/components/LoginRequired';
import { useBlockedUsersQuery, useUnblockUserMutation } from '@/lib/hooks/queries';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts, radius, space } from '@/lib/theme';

/** 차단한 사용자 관리 — 차단만 되고 해제할 수 없으면 안 되므로 반드시 필요한 화면. */
export default function BlockedUsersScreen() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const { data, isLoading } = useBlockedUsersQuery();
  const unblock = useUnblockUserMutation();

  if (!isLoggedIn) {
    return (
      <View style={styles.safe}>
        <Stack.Screen options={{ title: '차단한 사용자' }} />
        <LoginRequired description="차단 목록은 로그인 후에 확인할 수 있어요." />
      </View>
    );
  }

  const confirmUnblock = (userId: number) => {
    Alert.alert('차단 해제', '이 사용자의 리뷰가 다시 보이게 됩니다.', [
      { text: '취소', style: 'cancel' },
      {
        text: '해제',
        onPress: () =>
          unblock.mutate(userId, {
            onError: (e: any) => Alert.alert('해제 실패', e?.message ?? '잠시 후 다시 시도해주세요.'),
          }),
      },
    ]);
  };

  const blocked = data ?? [];

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '차단한 사용자' }} />

      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : blocked.length ? (
        <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 28 }}>
          <Text style={styles.count}>총 <Text style={styles.countNum}>{blocked.length}</Text>명을 차단했어요</Text>
          {blocked.map((userId) => (
            <View key={userId} style={styles.row}>
              <View style={styles.avatar}>
                <Ionicons name="person" size={16} color={colors.textFaint} />
              </View>
              <Text style={styles.name}>사용자{userId}</Text>
              <Pressable
                style={styles.btn}
                disabled={unblock.isPending}
                onPress={() => confirmUnblock(userId)}>
                <Text style={styles.btnText}>차단 해제</Text>
              </Pressable>
            </View>
          ))}
        </ScrollView>
      ) : (
        <View style={styles.empty}>
          <Ionicons name="person-remove-outline" size={34} color={colors.textFaint} />
          <Text style={styles.emptyText}>차단한 사용자가 없어요</Text>
          <Text style={styles.emptyDesc}>리뷰 옆의 아이콘을 눌러 차단할 수 있어요.</Text>
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
