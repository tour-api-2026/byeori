import { Text, type StyleProp, type TextStyle } from 'react-native';
import { useT } from '@/lib/i18n';

// 문장 속 숫자만 굵게 보여 주는 한 줄. 어순이 언어마다 달라("총 3명을" / "3 users") 문장을
// 이어 붙이지 않고 사전 항목 하나를 번역한 뒤, 숫자 자리에서만 잘라 숫자에 스타일을 입힌다.
const MARK = '\u0001';

export function CountLine({
  k, n, style, numStyle,
}: { k: string; n: number; style?: StyleProp<TextStyle>; numStyle?: StyleProp<TextStyle> }) {
  const t = useT();
  const [head, ...rest] = t(k, { n: MARK }).split(MARK);
  return (
    <Text style={style}>
      {head}
      <Text style={numStyle}>{n}</Text>
      {rest.join(String(n))}
    </Text>
  );
}
