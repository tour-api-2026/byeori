// AI 루트 생성의 지역·기본 테마. 값은 서버로 가는 한국어 그대로고(areaName·categories),
// 화면에는 사전을 거친 이름만 보인다. 화면 파일에 한국어 리터럴을 두지 않으려고 따로 뺐다.
/**
 * AI 루트 생성 지역. 서버는 중심에서 반경 3km 안의 장소를 후보로 쓰므로,
 * 도시 전체가 아니라 걸어서 도는 권역(구도심·한옥마을 등)의 중심을 잡는다.
 */
export const AREAS = [
  { name: '서울 종로구', lat: 37.573, lng: 126.9794 },
  { name: '서울 중구', lat: 37.5636, lng: 126.9976 },
  { name: '서울 용산구', lat: 37.5326, lng: 126.9906 },
  { name: '전주 한옥마을', lat: 35.815, lng: 127.153 },
  { name: '경주 황리단길', lat: 35.8364, lng: 129.2106 },
  { name: '부산 중구', lat: 35.098, lng: 129.032 },
  { name: '대구 중구', lat: 35.869, lng: 128.594 },
  { name: '제주시', lat: 33.5, lng: 126.531 },
] as const;

export const MY_LOCATION = '내 위치 주변';
export const DEFAULT_THEMES = ['문화', '맛집'];
