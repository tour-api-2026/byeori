import { Ionicons } from '@expo/vector-icons';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, fonts, radius, space } from '@/lib/theme';

/** 신고 사유 — 서버 reason 컬럼이 varchar(50)이라 그대로 저장된다. */
export const REPORT_REASONS = [
  '스팸 또는 광고',
  '부적절하거나 불쾌한 내용',
  '허위 정보',
  '욕설·혐오 표현',
  '기타',
] as const;

type Props = {
  visible: boolean;
  title: string;
  pending?: boolean;
  onSelect: (reason: string) => void;
  onClose: () => void;
};

/** 신고 사유 선택 시트. 구글 UGC 정책이 요구하는 신고 수단의 공통 UI. */
export function ReportDialog({ visible, title, pending, onSelect, onClose }: Props) {
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
          <Text style={styles.desc}>신고 사유를 선택해주세요. 운영진이 확인 후 조치합니다.</Text>

          {REPORT_REASONS.map((reason) => (
            <Pressable
              key={reason}
              style={styles.row}
              disabled={pending}
              onPress={() => onSelect(reason)}>
              <Text style={[styles.rowText, pending && styles.rowTextOff]}>{reason}</Text>
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
