package com.byeori.domain.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 필수 명소를 상호명으로 고르는 규칙.
 *
 * 공사 쇼핑(38)의 신분류가 쓸모없어서 이름으로 가른다. 그래서 **이름 규칙이 이 기능의
 * 전부**다 — 여기가 틀리면 지도가 엉뚱한 것으로 덮인다.
 *
 * 특히 백화점이 위험하다. 서울·제주 쇼핑 4,787건 중 이름에 '백화점'이 들어가는 것이
 * 973건인데 그중 944건은 백화점이 아니라 **그 안에 입점한 매장**이다. 담아야 하는 것은
 * 29건뿐이다.
 */
class CategoryMapperMustVisitTest {

    /** 쇼핑(38)으로 들어오는 실제 이름들. 신분류는 대부분 SH040300 잡탕이라 무의미하다. */
    private static String shop(String title) {
        return CategoryMapper.fromTour("38", title, "SH04", "SH040300");
    }

    @Test
    @DisplayName("올리브영·다이소는 담는다")
    void 브랜드_매장() {
        assertThat(shop("올리브영 IFC몰점")).isEqualTo("필수명소");
        assertThat(shop("올리브영 제주국제공항점")).isEqualTo("필수명소");
        assertThat(shop("다이소 강남본점")).isEqualTo("필수명소");
        assertThat(shop("다이소 경복궁역점")).isEqualTo("필수명소");
    }

    @Test
    @DisplayName("백화점은 건물만 담는다 — 이름이 백화점 브랜드로 시작할 때")
    void 백화점_건물() {
        assertThat(shop("롯데백화점 강남점")).isEqualTo("필수명소");
        assertThat(shop("신세계백화점 본점")).isEqualTo("필수명소");
        assertThat(shop("현대백화점 무역센터점")).isEqualTo("필수명소");
        assertThat(shop("갤러리아백화점 명품관")).isEqualTo("필수명소");
        assertThat(shop("NC백화점 송파점")).isEqualTo("필수명소");
    }

    /**
     * 이 테스트가 이 기능의 핵심이다. '백화점'이 들어가면 담는 식으로 하면 944건이 쓸려
     * 들어온다. 아래는 전부 운영 데이터에 실제로 있는 이름이다.
     */
    @Test
    @DisplayName("백화점 안에 입점한 매장은 버린다")
    void 입점_매장은_버린다() {
        assertThat(shop("ABC마트 GS 롯데백화점 김포공항점")).isNotEqualTo("필수명소");
        assertThat(shop("COS 신세계백화점 강남점")).isNotEqualTo("필수명소");
        assertThat(shop("갤럭시 롯데백화점 강남점")).isNotEqualTo("필수명소");
        assertThat(shop("AK골프 롯데백화점 본점")).isNotEqualTo("필수명소");
        assertThat(shop("CH 캐롤리나 헤레라 롯데백화점 잠실 에비뉴엘점")).isNotEqualTo("필수명소");
        assertThat(shop("가네시 롯데백화점 본점")).isNotEqualTo("필수명소");
    }

    @Test
    @DisplayName("약국·안경원 같은 나머지 쇼핑은 그대로 버린다")
    void 나머지_쇼핑() {
        assertThat(shop("가나안약국")).isNull();
        assertThat(shop("가나안경원 명동점")).isNull();
    }

    /** 한강공원은 쇼핑이 아니라 관광지(12)에 있다. 앞에 지역이 붙어 시작으로는 못 잡는다. */
    @Test
    @DisplayName("한강공원은 관광지로 들어와도 담는다")
    void 한강공원() {
        assertThat(CategoryMapper.fromTour("12", "여의도한강공원", null, null)).isEqualTo("필수명소");
        assertThat(CategoryMapper.fromTour("12", "반포한강공원 달빛광장", null, null)).isEqualTo("필수명소");
        assertThat(CategoryMapper.fromTour("28", "난지한강공원 익스트림장", null, null)).isEqualTo("필수명소");
    }

    @Test
    @DisplayName("기존 분류는 그대로다")
    void 기존_분류_회귀() {
        assertThat(CategoryMapper.fromTour("38", "광장시장", "SH06", "SH060200")).isEqualTo("전통시장");
        assertThat(CategoryMapper.fromTour("38", "손놀림공방", "SH05", "SH050100")).isEqualTo("공예");
        assertThat(CategoryMapper.fromTour("39", "스타벅스 광화문점", "FD05", "FD050100")).isEqualTo("카페");
        assertThat(CategoryMapper.fromTour("39", "토속촌삼계탕", "FD01", "FD010100")).isEqualTo("맛집");
        assertThat(CategoryMapper.fromTour("12", "경복궁", null, null)).isEqualTo("문화");
        assertThat(CategoryMapper.fromTour("14", "국립중앙박물관", null, null)).isEqualTo("문화");
    }

    @Test
    @DisplayName("이름이 없어도 터지지 않는다")
    void 이름_없음() {
        assertThat(CategoryMapper.fromTour("12", null, null, null)).isEqualTo("문화");
        assertThat(CategoryMapper.fromTour("38", null, "SH06", "SH060200")).isEqualTo("전통시장");
    }
}
