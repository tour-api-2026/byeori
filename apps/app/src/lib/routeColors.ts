export const ROUTE_SEGMENT_COLORS = [
  '#38BDF8', // 하늘색
  '#4ADE80', // 초록
  '#FB923C', // 주황
  '#A78BFA', // 보라
  '#F472B6', // 핑크
  '#FACC15', // 노랑
  '#F87171', // 코랄
];

export function segmentColor(index: number): string {
  const n = ROUTE_SEGMENT_COLORS.length;
  return ROUTE_SEGMENT_COLORS[((index % n) + n) % n];
}
