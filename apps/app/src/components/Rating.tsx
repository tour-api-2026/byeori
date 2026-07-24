import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';
import { colors, fonts } from '@/lib/theme';

// 골드 별 5개 + 평점 숫자 (Figma 리디자인)
export function Rating({ value, count, size = 12 }: { value: number; count?: number; size?: number }) {
  const v = Number(value) || 0;
  const full = Math.round(v);
  return (
    <View style={styles.row}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Ionicons key={n} name={n <= full ? 'star' : 'star-outline'} size={size} color={colors.star} />
      ))}
      <Text style={[styles.value, { fontSize: size }]}>{v.toFixed(1)}</Text>
      {count != null && <Text style={styles.count}>({count.toLocaleString()})</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  value: { fontFamily: fonts.semibold, fontWeight: '600', color: colors.textSub, marginLeft: 3 },
  count: { fontSize: 11, color: colors.textFaint, marginLeft: 2 },
});
