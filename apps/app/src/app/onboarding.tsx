import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radius, space } from '@/lib/theme';

const INTERESTS = ['한복', '음식', '체험', '문화', '공연'];

export default function OnboardingScreen() {
  const router = useRouter();
  const [selected, setSelected] = useState<string[]>([]);

  const toggle = (i: string) =>
    setSelected((s) => (s.includes(i) ? s.filter((x) => x !== i) : [...s, i]));

  return (
    <View style={styles.safe}>
      <View style={styles.head}>
        <Text style={styles.title}>관심사를 알려주세요</Text>
        <Text style={styles.sub}>취향에 맞는 코스를 추천해 드려요</Text>
      </View>
      <View style={styles.grid}>
        {INTERESTS.map((i) => {
          const on = selected.includes(i);
          return (
            <Pressable key={i} style={[styles.item, on && styles.itemOn]} onPress={() => toggle(i)}>
              <Text style={[styles.itemText, on && styles.itemTextOn]}>{i}</Text>
            </Pressable>
          );
        })}
      </View>
      <View style={styles.footer}>
        <Pressable
          style={[styles.cta, selected.length === 0 && styles.ctaDisabled]}
          disabled={selected.length === 0}
          onPress={() => router.replace('/(tabs)')}>
          <Text style={styles.ctaText}>시작하기 {selected.length > 0 ? `(${selected.length})` : ''}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  head: { padding: space.lg, paddingTop: 24 },
  title: { fontSize: 24, fontWeight: '900', color: colors.text },
  sub: { fontSize: 14, color: colors.textFaint, marginTop: 8 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, padding: space.lg },
  item: { paddingHorizontal: 22, paddingVertical: 14, borderRadius: radius.pill, borderWidth: 1.5, borderColor: colors.border },
  itemOn: { backgroundColor: colors.primarySoft, borderColor: colors.primary },
  itemText: { fontSize: 16, fontWeight: '700', color: colors.textSub },
  itemTextOn: { color: colors.primary },
  footer: { marginTop: 'auto', padding: space.lg },
  cta: { backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 16, alignItems: 'center' },
  ctaDisabled: { backgroundColor: colors.textFaint },
  ctaText: { color: colors.white, fontSize: 16, fontWeight: '800' },
});
