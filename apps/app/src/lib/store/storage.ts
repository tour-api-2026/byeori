import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

// SecureStore는 웹을 지원하지 않는다. 웹에서 그냥 무시하면 새로고침할 때마다 로그아웃되므로
// localStorage로 대체한다. 브라우저 저장소라 네이티브의 키체인만큼 안전하진 않지만,
// 웹앱에서 세션을 유지할 다른 수단이 없다(시크릿 모드·저장소 차단 시엔 조용히 실패).
const isWeb = Platform.OS === 'web';

export async function setItem(key: string, value: string) {
  if (isWeb) {
    try {
      window.localStorage.setItem(key, value);
    } catch {
      // 저장소 차단(시크릿 모드 등): 이번 세션 메모리로만 유지
    }
    return;
  }
  try {
    await SecureStore.setItemAsync(key, value);
  } catch {
    // 미지원 환경: 영속 생략 (세션 메모리만 유지)
  }
}
export async function getItem(key: string): Promise<string | null> {
  if (isWeb) {
    try {
      return window.localStorage.getItem(key);
    } catch {
      return null;
    }
  }
  try {
    return await SecureStore.getItemAsync(key);
  } catch {
    return null;
  }
}
export async function delItem(key: string) {
  if (isWeb) {
    try {
      window.localStorage.removeItem(key);
    } catch {
      // 무시
    }
    return;
  }
  try {
    await SecureStore.deleteItemAsync(key);
  } catch {
    // 무시
  }
}
