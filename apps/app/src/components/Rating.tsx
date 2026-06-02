import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';
import { colors } from '@/lib/theme';

export function Rating({ value, count }: { value: number; count?: number }) {
  return (
    <View style={styles.row}>
      <Ionicons name="star" size={12} color={colors.star} />
      <Text style={styles.value}>{Number(value).toFixed(1)}</Text>
      {count != null && <Text style={styles.count}>({count.toLocaleString()})</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  value: { fontSize: 12, fontWeight: '700', color: colors.text },
  count: { fontSize: 11, color: colors.textFaint, marginLeft: 2 },
});
