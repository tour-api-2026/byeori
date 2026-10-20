import { Ionicons } from '@expo/vector-icons';
import { Stack, useRouter } from 'expo-router';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useT } from '@/lib/i18n';
import { useDeleteItineraryMutation, useMyItinerariesQuery } from '@/lib/hooks/queries';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, radius, shadow, space } from '@/lib/theme';

export default function ItinerariesScreen() {
  const insets = useSafeAreaInsets();   // 안드로이드는 edge-to-edge 라 시스템 바 아래까지 그린다
  const router = useRouter();
  const t = useT();
  const { data, isLoading } = useMyItinerariesQuery();
  const del = useDeleteItineraryMutation();

  // 만들기만 되고 지울 수단이 없었다. 서버·API·훅은 이미 있었고 화면만 비어 있었다.
  const confirmDelete = (id: number, title: string) =>
    Alert.alert(t('itinerary.list.deleteTitle'), t('itinerary.list.deleteBody', { title }), [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: t('common.delete'),
        style: 'destructive',
        onPress: () => del.mutate(id, {
          onError: (e: any) => Alert.alert(t('itinerary.list.deleteFail'), e?.message ?? t('common.error.unknown')),
        }),
      },
    ]);

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: t('itinerary.list.title') }} />
      <Pressable style={styles.addBtn} onPress={() => router.push('/itinerary/new')}>
        <Ionicons name="add" size={18} color={colors.white} />
        <Text style={styles.addText}>{t('itinerary.list.add')}</Text>
      </Pressable>
      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : !data?.length ? (
        <View style={styles.empty}><Text style={styles.emptyText}>{t('itinerary.list.empty')}</Text></View>
      ) : (
        <ScrollView contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: 28 + insets.bottom }}>
          {data.map((it) => (
            <Pressable key={it.id} style={styles.card} onPress={() => router.push(`/itinerary/${it.id}`)}>
              <View style={{ flex: 1 }}>
                <Text style={styles.title}>{it.title}</Text>
                <Text style={styles.meta}>{it.startDate} ~ {it.endDate} · {t('common.placeCount', { n: it.itemCount })} · {it.sourceType === 'CURATED' ? t('itinerary.source.curated') : it.sourceType === 'AI' ? t('itinerary.source.ai') : t('itinerary.source.custom')}</Text>
              </View>
              <Pressable
                hitSlop={10}
                style={styles.delBtn}
                onPress={() => confirmDelete(it.id, it.title)}
              >
                <Ionicons name="trash-outline" size={18} color={colors.textFaint} />
              </Pressable>
              <Ionicons name="chevron-forward" size={18} color={colors.textFaint} />
            </Pressable>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  addBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 13, margin: space.lg },
  addText: { color: colors.white, fontSize: 14, fontWeight: '800' },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { color: colors.textFaint, fontSize: 14 },
  card: { flexDirection: 'row', alignItems: 'center', backgroundColor: colors.bgCard, borderRadius: radius.md, padding: 16, marginBottom: 12, ...shadow.card },
  title: { fontSize: 16, fontWeight: '700', color: colors.text },
  meta: { fontSize: 12, color: colors.textFaint, marginTop: 4 },
  delBtn: { padding: 6, marginRight: 2 },
});
