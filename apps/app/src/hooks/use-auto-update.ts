import * as Updates from 'expo-updates';
import { useEffect, useRef } from 'react';
import { AppState } from 'react-native';

// 포그라운드 복귀 시 재체크 최소 간격
const CHECK_INTERVAL_MS = 30 * 60 * 1000;

async function applyUpdateIfAvailable() {
  const update = await Updates.checkForUpdateAsync();
  if (!update.isAvailable) return;
  await Updates.fetchUpdateAsync();
  await Updates.reloadAsync();
}

// OTA 자동 적용: 콜드 스타트 + 포그라운드 복귀(30분 스로틀) 시
// 새 번들을 받아 즉시 reload한다. 실패는 조용히 넘기고 다음 체크에서 재시도.
export function useAutoUpdate() {
  const lastCheckedAt = useRef(0);

  useEffect(() => {
    if (__DEV__ || !Updates.isEnabled) return;

    const check = () => {
      const now = Date.now();
      if (now - lastCheckedAt.current < CHECK_INTERVAL_MS) return;
      lastCheckedAt.current = now;
      applyUpdateIfAvailable().catch(() => {});
    };

    check();

    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') check();
    });
    return () => sub.remove();
  }, []);
}
