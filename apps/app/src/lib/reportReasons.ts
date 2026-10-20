/**
 * 신고 사유 — 서버 reason 컬럼이 varchar(50)이라 **한국어 문자열 그대로** 저장된다.
 *
 * 운영진이 보는 값이므로 앱 언어와 무관하게 바꾸지 않는다. 화면에는 key 로 번역해 보여 주고,
 * 서버에는 value 를 보낸다.
 */
export const REPORT_REASONS = [
  { value: '스팸 또는 광고', key: 'report.reason.spam' },
  { value: '부적절하거나 불쾌한 내용', key: 'report.reason.inappropriate' },
  { value: '허위 정보', key: 'report.reason.false' },
  { value: '욕설·혐오 표현', key: 'report.reason.abuse' },
  { value: '기타', key: 'report.reason.other' },
] as const;
