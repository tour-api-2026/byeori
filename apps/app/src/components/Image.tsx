import { Image as ExpoImage, type ImageProps } from 'expo-image';
import { secure } from '@/lib/img';

/**
 * expo-image 래퍼. 넘겨받은 주소를 https로 올려서 그린다.
 *
 * 호출부가 20곳 가까이 되고 앞으로도 늘기 때문에, 주소를 손보는 자리를 한 군데로 모은다.
 * 사유는 `secure()` 주석 참고 — 안드로이드 릴리스 빌드가 평문 HTTP를 막는다.
 *
 * 화면에서는 `expo-image` 대신 이 모듈에서 Image를 가져다 쓴다.
 */
export function Image({ source, ...rest }: ImageProps) {
  return <ExpoImage source={toSecureSource(source)} {...rest} />;
}

function toSecureSource(source: ImageProps['source']): ImageProps['source'] {
  if (typeof source === 'string') return secure(source);
  if (Array.isArray(source)) return source.map(toSecureSource) as ImageProps['source'];
  if (source && typeof source === 'object' && 'uri' in source && typeof source.uri === 'string') {
    return { ...source, uri: secure(source.uri) };
  }
  // 번들 에셋(require 결과의 number)·null·undefined는 그대로 둔다.
  return source;
}
