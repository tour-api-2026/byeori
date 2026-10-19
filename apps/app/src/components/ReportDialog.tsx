import { Ionicons } from '@expo/vector-icons';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { useT } from '@/lib/i18n';
import { REPORT_REASONS } from '@/lib/reportReasons';
import { colors, fonts, radius, space } from '@/lib/theme';

type Props = {
  visible: boolean;
  title: string;
  pending?: boolean;
  onSelect: (reason: string) => void;
  onClose: () => void;
};

/** 신고 사유 선택 시트. 구글 UGC 정책이 요구하는 신고 수단의 공통 UI. */
export function ReportDialog({ visible, title, pending, onSelect, onClose }: Props) {
  const t = useT();
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        {/* 시트 내부 탭이 배경으로 전파되지 않도록 별도 Pressable로 감쌈 */}
        <Pressable style={styles.sheet} onPress={() => {}}>
          <View style={styles.head}>
            <Text style={styles.title}>{title}</Text>
            <Pressable hitSlop={8} onPress={onClose}>
              <Ionicons name="close" size={22} color={colors.textFaint} />
            </Pressable>
          </View>
          <Text style={styles.desc}>{t('report.desc')}</Text>

          {/* 서버에는 한국어 value 를 보내고, 화면에는 번역한 라벨을 보인다. */}
          {REPORT_REASONS.map((reason) => (
            <Pressable
              key={reason.value}
              style={styles.row}
              disabled={pending}
              onPress={() => onSelect(reason.value)}>
              <Text style={[styles.rowText, pending && styles.rowTextOff]}>{t(reason.key)}</Text>
              <Ionicons name="chevron-forward" size={16} color={colors.textFaint} />
            </Pressable>
          ))}
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(20,24,45,0.4)', justifyContent: 'flex-end' },
  sheet: {
    backgroundColor: colors.bg, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg,
    paddingHorizontal: space.lg, paddingTop: 18, paddingBottom: 28,
  },
  head: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  title: { fontSize: 17, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  desc: { fontSize: 13, color: colors.textFaint, marginTop: 6, marginBottom: 10, lineHeight: 19 },
  row: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: colors.border,
  },
  rowText: { fontSize: 15, fontFamily: fonts.medium, fontWeight: '500', color: colors.text },
  rowTextOff: { color: colors.textFaint },
});
