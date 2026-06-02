import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import {
  useCreateItineraryMutation, useItineraryItemMutation, useItineraryQuery, useVenuesQuery,
} from '@/lib/hooks/queries';
import { colors, radius, shadow, space } from '@/lib/theme';

export default function ItineraryEditScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  if (id === 'new') return <CreateForm />;
  return <Editor id={Number(id)} />;
}

function CreateForm() {
  const router = useRouter();
  const create = useCreateItineraryMutation();
  const [title, setTitle] = useState('');
  const [startDate, setStartDate] = useState('2026-06-10');
  const [endDate, setEndDate] = useState('2026-06-11');

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '새 여행 일지' }} />
      <View style={{ padding: space.lg }}>
        <Text style={styles.label}>일지 제목</Text>
        <TextInput style={styles.input} value={title} onChangeText={setTitle} placeholder="예) 서울 1박 2일 한복 나들이" placeholderTextColor={colors.textFaint} />
        <Text style={styles.label}>시작일</Text>
        <TextInput style={styles.input} value={startDate} onChangeText={setStartDate} placeholder="YYYY-MM-DD" placeholderTextColor={colors.textFaint} />
        <Text style={styles.label}>종료일</Text>
        <TextInput style={styles.input} value={endDate} onChangeText={setEndDate} placeholder="YYYY-MM-DD" placeholderTextColor={colors.textFaint} />
        <Pressable
          style={[styles.cta, !title && styles.disabled]}
          disabled={!title || create.isPending}
          onPress={() => create.mutate({ title, startDate, endDate, sourceType: 'CUSTOM' }, { onSuccess: (d) => router.replace(`/itinerary/${d.id}`) })}>
          <Text style={styles.ctaText}>{create.isPending ? '생성 중...' : '여행 일지 만들기'}</Text>
        </Pressable>
      </View>
    </View>
  );
}

function Editor({ id }: { id: number }) {
  const { data, isLoading } = useItineraryQuery(id);
  const { add, remove } = useItineraryItemMutation(id);
  const [pickerOpen, setPickerOpen] = useState(false);
  const venues = useVenuesQuery({ size: 30 });

  if (isLoading || !data) return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;

  // 날짜별 그룹
  const byDate: Record<string, typeof data.items> = {};
  data.items.forEach((it) => { (byDate[it.visitDate] ||= []).push(it); });
  const dates = Object.keys(byDate).sort();

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: data.title }} />
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 90 }}>
        <Text style={styles.h1}>{data.title}</Text>
        <Text style={styles.range}>{data.startDate} ~ {data.endDate} · {data.items.length}곳</Text>

        {dates.length === 0 && <Text style={styles.empty}>아직 일정이 없어요. 아래 + 로 장소를 추가하세요.</Text>}
        {dates.map((d, di) => (
          <View key={d} style={{ marginTop: 18 }}>
            <Text style={styles.dayTitle}>{di + 1}일차 · {d}</Text>
            {byDate[d].map((it) => (
              <View key={it.id} style={styles.stop}>
                <Image source={it.imageUrl} style={styles.stopImg} contentFit="cover" />
                <View style={{ flex: 1 }}>
                  <Text style={styles.stopName}>{it.name}</Text>
                  <Text style={styles.stopMeta}>{it.targetType === 'PERFORMANCE' ? '행사' : '장소'}{it.plannedTime ? ` · ${it.plannedTime}` : ''}</Text>
                </View>
                <Pressable onPress={() => remove.mutate(it.id)} hitSlop={8} style={styles.delBtn}>
                  <Text style={styles.delText}>삭제</Text>
                </Pressable>
              </View>
            ))}
          </View>
        ))}
      </ScrollView>

      {/* FAB */}
      <Pressable style={styles.fab} onPress={() => setPickerOpen(true)}>
        <Ionicons name="add" size={28} color={colors.white} />
      </Pressable>

      {/* 장소 선택 모달 */}
      <Modal visible={pickerOpen} animationType="slide" transparent onRequestClose={() => setPickerOpen(false)}>
        <View style={styles.modalBg}>
          <View style={styles.modal}>
            <View style={styles.modalHead}>
              <Text style={styles.modalTitle}>장소 추가</Text>
              <Pressable onPress={() => setPickerOpen(false)}><Ionicons name="close" size={22} color={colors.text} /></Pressable>
            </View>
            <ScrollView>
              {venues.data?.content.map((v) => (
                <Pressable
                  key={v.id}
                  style={styles.pickRow}
                  onPress={() => {
                    add.mutate({ targetType: 'VENUE', targetId: v.id, visitDate: data.startDate });
                    setPickerOpen(false);
                  }}>
                  <Image source={v.imageUrl} style={styles.pickImg} contentFit="cover" />
                  <Text style={styles.pickName}>{v.name}</Text>
                  <Ionicons name="add-circle" size={22} color={colors.primary} style={{ marginLeft: 'auto' }} />
                </Pressable>
              ))}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg },
  label: { fontSize: 13, fontWeight: '700', color: colors.text, marginTop: 12, marginBottom: 6 },
  input: { backgroundColor: colors.bgSoft, borderRadius: radius.md, paddingHorizontal: 14, paddingVertical: 11, fontSize: 14, color: colors.text },
  cta: { backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 16, alignItems: 'center', marginTop: 24 },
  disabled: { backgroundColor: colors.textFaint },
  ctaText: { color: colors.white, fontSize: 15, fontWeight: '800' },
  h1: { fontSize: 22, fontWeight: '900', color: colors.text },
  range: { fontSize: 13, color: colors.textFaint, marginTop: 4 },
  empty: { fontSize: 14, color: colors.textFaint, marginTop: 24, textAlign: 'center' },
  dayTitle: { fontSize: 15, fontWeight: '800', color: colors.primary, marginBottom: 10 },
  stop: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 8 },
  stopImg: { width: 54, height: 54, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  stopName: { fontSize: 15, fontWeight: '700', color: colors.text },
  stopMeta: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  delBtn: { borderWidth: 1, borderColor: colors.border, borderRadius: radius.sm, paddingHorizontal: 10, paddingVertical: 5 },
  delText: { fontSize: 12, color: colors.hanbok, fontWeight: '700' },
  fab: { position: 'absolute', right: 20, bottom: 24, width: 56, height: 56, borderRadius: 28, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center', ...shadow.card },
  modalBg: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'flex-end' },
  modal: { backgroundColor: colors.bg, borderTopLeftRadius: radius.xl, borderTopRightRadius: radius.xl, padding: space.lg, maxHeight: '70%' },
  modalHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  modalTitle: { fontSize: 17, fontWeight: '800', color: colors.text },
  pickRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 8 },
  pickImg: { width: 48, height: 48, borderRadius: radius.sm, backgroundColor: colors.bgSoft },
  pickName: { fontSize: 14, fontWeight: '600', color: colors.text },
});
