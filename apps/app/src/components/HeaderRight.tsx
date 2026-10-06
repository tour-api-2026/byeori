import { Platform, StyleSheet, View, type ViewStyle } from 'react-native';
import { space } from '@/lib/theme';

/**
 * 헤더 오른쪽 버튼을 담는 자리.
 *
 * 웹에서 오른쪽 버튼이 화면 끝에 딱 붙어 있었다. 제목은 왼쪽에서 16 떨어져 있는데
 * 오른쪽만 0이라 한쪽으로 쏠려 보였다(420px 폭에서 재니 저장 버튼 right=420).
 *
 * 네이티브에는 여백을 넣지 않는다. native-stack 의 헤더는 OS 가 그리고 자체 여백을
 * 이미 갖고 있어서, 여기서 또 더하면 그쪽만 벌어진다. 고친 것은 웹에서 본 문제이고
 * 안드로이드에서는 재 보지 않았다.
 *
 * headerRightContainerStyle 로 한 번에 못 맞춘다 — native-stack 이 그 옵션을 받지 않는다.
 * 그래서 쓰는 쪽을 이 컴포넌트로 모은다.
 */
export function HeaderRight({ children, style }: { children: React.ReactNode; style?: ViewStyle }) {
  return <View style={[styles.wrap, style]}>{children}</View>;
}

const styles = StyleSheet.create({
  wrap: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    ...(Platform.OS === 'web' ? { paddingRight: space.lg } : null),
  },
});
