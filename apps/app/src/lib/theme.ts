// 벼리 디자인 토큰 (Figma 기준)
export const colors = {
  primary: '#5A6CF3',        // 인디고 (주요 버튼/탭 활성)
  primaryDark: '#3F4FD0',
  primarySoft: '#EEF0FE',
  hanbok: '#E5484D',         // 한복 혜택 강조 (지도 핀)
  text: '#1A1A1E',
  textSub: '#6B7280',
  textFaint: '#9CA3AF',
  bg: '#FFFFFF',
  bgSoft: '#F5F6F8',
  bgCard: '#FFFFFF',
  border: '#ECEEF1',
  star: '#FFB020',
  white: '#FFFFFF',
};

export const radius = { sm: 8, md: 12, lg: 16, xl: 20, pill: 999 };
export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 };

export const shadow = {
  // boxShadow: RN 0.81+/web 표준 (shadow* deprecated). elevation: 안드로이드 폴백
  card: {
    boxShadow: '0px 4px 12px rgba(0,0,0,0.06)',
    elevation: 2,
  },
};
