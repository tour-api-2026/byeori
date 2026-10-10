import { useCallback, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

/**
 * 당겨서 새로고침.
 *
 * 화면마다 어떤 조회를 쓰는지 적지 않는다 — **지금 화면에 떠 있는 조회를 전부** 다시
 * 부른다(`type: 'active'`). 목록을 적어 두면 섹션을 더할 때마다 거기도 고쳐야 하고,
 * 빠뜨리면 당겨도 그 부분만 안 바뀐다. 홈 하나만 해도 조회가 아홉 개다.
 *
 * 실패해도 돌아가는 표시는 멈춘다. 네트워크가 끊겼다고 영영 도는 것보다, 멈춘 뒤 다시
 * 당겨 보게 하는 편이 낫다.
 *
 * 웹에서는 아무 일도 일어나지 않는다 — react-native-web 의 RefreshControl 은 빈 View 를
 * 그린다. 브라우저 자체 새로고침이 그 자리를 대신한다.
 */
export function useRefresh() {
  const qc = useQueryClient();
  const [refreshing, setRefreshing] = useState(false);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    qc.refetchQueries({ type: 'active' }).finally(() => setRefreshing(false));
  }, [qc]);

  return { refreshing, onRefresh };
}
