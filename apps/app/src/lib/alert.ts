import { Alert, Platform } from 'react-native';

/**
 * 어느 쪽에서든 보이는 알림과 확인창.
 *
 * react-native-web 의 `Alert.alert` 는 **빈 함수**다.
 *
 *     // react-native-web/dist/exports/Alert/index.js
 *     class Alert { static alert() {} }
 *
 * 그래서 웹에서는 안내가 묻히고, 확인창을 쓴 동작은 콜백이 영영 안 불려 **아무 일도
 * 일어나지 않는다.** 공유하기에서 한 번, 계정 연결 해제에서 또 한 번 같은 데 걸렸다.
 * 화면마다 따로 우회하면 다음 화면에서 또 걸리므로 여기 한 곳에 둔다.
 */
export function notify(title: string, message?: string) {
  if (Platform.OS === 'web') {
    window.alert(message ? `${title}\n\n${message}` : title);
    return;
  }
  Alert.alert(title, message);
}

/** 되돌릴 수 없는 동작을 묻는다. 눌렀는지를 true/false 로 돌려준다. */
export function confirmDestructive(title: string, message: string, confirmLabel = '확인'): Promise<boolean> {
  if (Platform.OS === 'web') {
    return Promise.resolve(window.confirm(`${title}\n\n${message}`));
  }
  return new Promise((resolve) => {
    Alert.alert(title, message, [
      { text: '취소', style: 'cancel', onPress: () => resolve(false) },
      { text: confirmLabel, style: 'destructive', onPress: () => resolve(true) },
    ]);
  });
}
