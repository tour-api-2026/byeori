import { Ionicons } from '@expo/vector-icons';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useT } from '@/lib/i18n';
import { colors, fonts } from '@/lib/theme';

export function SectionHeader({ title, onMore }: { title: string; onMore?: () => void }) {
  const t = useT();
  return (
    <View style={styles.row}>
      <Text style={styles.title}>{title}</Text>
      {onMore && (
        <Pressable onPress={onMore} hitSlop={8} style={styles.more}>
          <Text style={styles.moreText}>{t('common.viewAll')}</Text>
          <Ionicons name="chevron-forward" size={14} color={colors.textFaint} />
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  title: { fontSize: 18, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  more: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  moreText: { fontSize: 12, color: colors.textFaint },
});
