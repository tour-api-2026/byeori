import type { ComponentProps } from 'react';
import { RefreshControl } from 'react-native';
import { useRefresh } from '@/lib/hooks/useRefresh';
import { colors } from '@/lib/theme';

/**
 * 스크롤 목록에 붙이는 당겨서 새로고침.
 *
 *     <ScrollView refreshControl={<Refresh />}>
 *
 * **받은 props 를 반드시 그대로 넘겨야 한다.** ScrollView 는 이 엘리먼트를 복제해
 * **스크롤뷰 전체를 자식으로 집어넣는다**:
 *
 *     React.cloneElement(refreshControl, { style: props.style }, scrollView)
 *
 * 그래서 children 과 style 을 흘려보내지 않으면 화면 내용이 통째로 사라진다. 오류도 나지
 * 않고 그냥 빈 화면이 된다 — 실제로 그렇게 만들어 놓고 한참 찾았다.
 *
 * 색을 여기 모아 두는 이유: 안드로이드는 `colors`, iOS 는 `tintColor` 로 이름이 달라
 * 화면마다 쓰면 한쪽을 빠뜨리기 쉽다.
 */
export function Refresh(props: Partial<ComponentProps<typeof RefreshControl>>) {
  const { refreshing, onRefresh } = useRefresh();
  return (
    <RefreshControl
      {...props}
      refreshing={refreshing}
      onRefresh={onRefresh}
      tintColor={colors.primary}
      colors={[colors.primary]}
    />
  );
}
