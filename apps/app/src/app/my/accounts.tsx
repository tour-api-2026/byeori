import { Ionicons } from '@expo/vector-icons';
import { Stack } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { confirmDestructive, notify } from '@/lib/alert';
import { fetchLinkedAccounts, unlinkAccount, type LinkedAccount } from '@/lib/api/social';
import { isCancelled, linkGoogle, linkKakao } from '@/lib/auth/oauth';
import { colors, fonts, radius, space } from '@/lib/theme';

const PROVIDERS = [
  { key: 'KAKAO', label: '카카오', icon: 'chatbubble' as const, tint: '#3C1E1E', soft: colors.kakao },
  { key: 'GOOGLE', label: '구글', icon: 'logo-google' as const, tint: '#DB4437', soft: '#FDECEA' },
];

/**
 * 연결된 계정.
 *
 * 카카오로 가입한 사람이 구글로 들어오면 **별개의 계정**이 생겨 찜·루트·관심사가 둘로
 * 갈렸다. 여기서 직접 이어 두면 어느 쪽으로 들어와도 같은 계정이 나온다.
 *
 * 이메일이 같다고 자동으로 잇지 않는다. 카카오는 이메일을 주지 않고(지금 카카오 사용자
 * 전원이 이메일 없음), 검증되지 않은 이메일로 자동 연결하면 남의 이메일을 제 소셜 계정에
 * 넣은 뒤 그 서비스로 들어와 남의 벼리 계정을 차지할 수 있다.
 */
export default function LinkedAccountsScreen() {
  const insets = useSafeAreaInsets();
  const [list, setList] = useState<LinkedAccount[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setList(await fetchLinkedAccounts());
    } catch (e: any) {
      notify('불러오지 못했어요', e?.message ?? '잠시 후 다시 시도해 주세요.');
      setList([]);
    }
  }, []);
  useEffect(() => { load(); }, [load]);

  const linkedOf = (key: string) => list?.find((a) => a.provider === key);

  const onLink = async (key: string, label: string) => {
    if (busy) return;
    setBusy(key);
    try {
      setList(key === 'KAKAO' ? await linkKakao() : await linkGoogle());
    } catch (e: any) {
      // 사용자가 제공자 화면에서 그만둔 것은 실패가 아니다.
      if (!isCancelled(e)) {
        notify(`${label} 연결 실패`, e?.message ?? '잠시 후 다시 시도해 주세요.');
      }
    } finally {
      setBusy(null);
    }
  };

  const onUnlink = async (key: string, label: string) => {
    if ((list?.length ?? 0) <= 1) {
      notify('끊을 수 없어요', '마지막 로그인 수단이에요. 다른 계정을 먼저 연결해 주세요.');
      return;
    }
    const ok = await confirmDestructive(
      `${label} 연결 해제`,
      `${label} 계정으로는 더 이상 로그인할 수 없게 됩니다.`,
      '해제',
    );
    if (!ok) return;
    setBusy(key);
    try {
      setList(await unlinkAccount(key));
    } catch (e: any) {
      notify('해제하지 못했어요', e?.message ?? '잠시 후 다시 시도해 주세요.');
    } finally {
      setBusy(null);
    }
  };

  if (list === null) {
    return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;
  }

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '연결된 계정' }} />
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 28 + insets.bottom }}>
        <Text style={styles.lead}>
          여러 계정을 연결해 두면 어느 쪽으로 로그인해도 같은 벼리 계정으로 들어옵니다.
          찜·루트·관심사가 한곳에 모입니다.
        </Text>

        <View style={styles.card}>
          {PROVIDERS.map((p, i) => {
            const linked = linkedOf(p.key);
            const working = busy === p.key;
            return (
              <View key={p.key}>
                {i > 0 && <View style={styles.divider} />}
                <View style={styles.row}>
                  <View style={[styles.icon, { backgroundColor: p.soft }]}>
                    <Ionicons name={p.icon} size={17} color={p.tint} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.name}>{p.label}</Text>
                    <Text style={styles.state}>
                      {linked ? `연결됨 · ${linked.linkedAt.slice(0, 10)}` : '연결되지 않음'}
                    </Text>
                  </View>
                  {working ? (
                    <ActivityIndicator color={colors.primary} />
                  ) : linked ? (
                    <Pressable hitSlop={8} onPress={() => onUnlink(p.key, p.label)}>
                      <Text style={styles.unlink}>해제</Text>
                    </Pressable>
                  ) : (
                    <Pressable style={styles.linkBtn} hitSlop={8} onPress={() => onLink(p.key, p.label)}>
                      <Text style={styles.linkText}>연결하기</Text>
                    </Pressable>
                  )}
                </View>
              </View>
            );
          })}
        </View>

        <Text style={styles.note}>
          마지막 하나는 해제할 수 없어요. 끊는 순간 로그인할 길이 사라집니다.
        </Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg },
  lead: { fontSize: 13, color: colors.textSub, lineHeight: 20, marginBottom: 14 },
  card: { backgroundColor: colors.bgCard, borderRadius: radius.lg, borderWidth: 1, borderColor: colors.border },
  divider: { height: 1, backgroundColor: colors.border, marginLeft: 56 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 14, paddingVertical: 15 },
  icon: { width: 32, height: 32, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  name: { fontSize: 15, fontFamily: fonts.semibold, fontWeight: '600', color: colors.text },
  state: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  linkBtn: { backgroundColor: colors.primary, borderRadius: radius.pill, paddingHorizontal: 14, paddingVertical: 7 },
  linkText: { color: colors.white, fontSize: 13, fontFamily: fonts.bold, fontWeight: '800' },
  unlink: { color: colors.textFaint, fontSize: 13, fontFamily: fonts.medium, fontWeight: '500' },
  note: { fontSize: 12, color: colors.textFaint, marginTop: 12, lineHeight: 18 },
});
