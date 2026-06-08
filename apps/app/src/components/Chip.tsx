import { Pressable, StyleSheet, Text } from 'react-native';
import { colors, fonts, radius } from '@/lib/theme';

// 칩: 선택 시 네이비 채움, 미선택 흰색 (Figma 리디자인)
export function Chip({ label, selected, onPress }: { label: string; selected?: boolean; onPress?: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.chip, selected && styles.chipOn]}>
      <Text style={[styles.label, selected && styles.labelOn]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.white,
  },
  chipOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  label: { fontSize: 13, color: colors.textSub, fontFamily: fonts.medium, fontWeight: '500' },
  labelOn: { color: colors.white, fontFamily: fonts.semibold, fontWeight: '600' },
});
