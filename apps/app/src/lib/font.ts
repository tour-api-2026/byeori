import React from 'react';
import { StyleSheet, Text, TextInput } from 'react-native';

// fontWeight → Pretendard 패밀리 매핑.
// (안드로이드는 커스텀 폰트에 fontWeight를 적용하지 않으므로 패밀리로 굵기를 지정한다)
const FAMILY: Record<string, string> = {
  '100': 'Pretendard-Regular', '200': 'Pretendard-Regular', '300': 'Pretendard-Regular',
  '400': 'Pretendard-Regular', normal: 'Pretendard-Regular',
  '500': 'Pretendard-Medium',
  '600': 'Pretendard-SemiBold',
  '700': 'Pretendard-Bold', '800': 'Pretendard-Bold', '900': 'Pretendard-Bold', bold: 'Pretendard-Bold',
};

function patch(Comp: any) {
  if (!Comp || Comp.__pretendardPatched) return;
  const orig = Comp.render;
  if (typeof orig !== 'function') return;
  Comp.render = function (props: any, ref: any) {
    const el = orig.call(this, props, ref);
    try {
      if (!el || !React.isValidElement(el)) return el;
      const flat = StyleSheet.flatten((el.props as any).style) || {};
      const fam = FAMILY[String((flat as any).fontWeight ?? '400')] || 'Pretendard-Regular';
      // 평탄화된 단일 객체로 머지한다. (배열을 넘기면 중첩 <Text>가 raw <span>으로
      // 렌더되는 react-native-web에서 style 배열이 그대로 DOM에 전달돼
      // "indexed property [0] on CSSStyleDeclaration" 에러가 난다)
      return React.cloneElement(el, { style: { fontFamily: fam, ...flat } } as any);
    } catch {
      return el;
    }
  };
  Comp.__pretendardPatched = true;
}

// 모든 Text/TextInput에 Pretendard를 가중치에 맞게 자동 적용
export function applyGlobalFont() {
  patch(Text);
  patch(TextInput);
}
