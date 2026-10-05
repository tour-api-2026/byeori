package com.byeori.domain.performance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "performances")
@Getter
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long venueId;
    private String title;
    private String genre;
    private String posterImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private String state;
    private BigDecimal lat;
    private BigDecimal lng;
    private String externalBookingUrl;
    private BigDecimal avgRating;
    private Integer reviewCount;
    private LocalDateTime createdAt;
    private String source;
    private String kopisId;
    private String tourContentId;
    private String seoulId;
    private LocalDateTime syncedAt;
    private boolean traditional;

    /**
     * 기준일로 본 행사 상태.
     *
     * state 컬럼은 동기화 시점에 계산해 넣은 값이라, 저장된 뒤 기간이 지나도 그대로 남는다.
     * 2026-10-05 기준 '진행 중' 목록 200건 중 192건이 이미 끝난 행사였다(최대 59일 경과).
     * 시간에 따라 변하는 값이므로 읽을 때마다 계산한다 — 판정 규칙은 기존과 같다.
     */
    public static String stateOn(LocalDate start, LocalDate end, LocalDate today) {
        if (end != null && end.isBefore(today)) return "ENDED";
        if (start != null && start.isAfter(today)) return "UPCOMING";
        if (start != null && end != null) return "ONGOING"; // start <= today <= end
        return "UPCOMING";                                  // 기간 정보가 비면 예정으로 본다
    }

    /** 오늘 기준 상태. 저장된 state 컬럼 대신 이 값을 응답에 쓴다. */
    public String currentState() {
        return stateOn(startDate, endDate, LocalDate.now());
    }

    /** KOPIS 공연으로 신규 생성 */
    public static Performance fromKopis(String kopisId, String title, String genre, String posterImageUrl,
                                        LocalDate startDate, LocalDate endDate, String state, String externalBookingUrl) {
        Performance p = new Performance();
        p.kopisId = kopisId;
        p.title = title;
        p.genre = genre;
        p.posterImageUrl = posterImageUrl;
        p.startDate = startDate;
        p.endDate = endDate;
        p.state = state;
        p.externalBookingUrl = externalBookingUrl;
        p.source = "KOPIS";
        p.avgRating = BigDecimal.ZERO;
        p.reviewCount = 0;
        p.createdAt = LocalDateTime.now();
        p.syncedAt = LocalDateTime.now();
        return p;
    }

    /** KOPIS 재동기화 갱신 */
    public void updateFromKopis(String title, String genre, String posterImageUrl,
                                LocalDate startDate, LocalDate endDate, String state) {
        if (title != null) this.title = title;
        if (genre != null) this.genre = genre;
        if (posterImageUrl != null) this.posterImageUrl = posterImageUrl;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (state != null) this.state = state;
        this.syncedAt = LocalDateTime.now();
    }

    /** TourAPI 축제/행사로 신규 생성 */
    public static Performance fromTour(String tourContentId, String title, String genre, String posterImageUrl,
                                       LocalDate startDate, LocalDate endDate, String state,
                                       BigDecimal lat, BigDecimal lng) {
        Performance p = new Performance();
        p.tourContentId = tourContentId;
        p.title = title;
        p.genre = genre;
        p.posterImageUrl = posterImageUrl;
        p.startDate = startDate;
        p.endDate = endDate;
        p.state = state;
        p.lat = lat;
        p.lng = lng;
        p.source = "TOURAPI";
        p.avgRating = BigDecimal.ZERO;
        p.reviewCount = 0;
        p.createdAt = LocalDateTime.now();
        p.syncedAt = LocalDateTime.now();
        return p;
    }

    /** TourAPI 재동기화 갱신 */
    public void updateFromTour(String title, String genre, String posterImageUrl,
                               LocalDate startDate, LocalDate endDate, String state,
                               BigDecimal lat, BigDecimal lng) {
        if (title != null) this.title = title;
        if (genre != null) this.genre = genre;
        if (posterImageUrl != null) this.posterImageUrl = posterImageUrl;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (state != null) this.state = state;
        if (lat != null) this.lat = lat;
        if (lng != null) this.lng = lng;
        this.syncedAt = LocalDateTime.now();
    }

    /** 서울 열린데이터 문화행사로 신규 생성 */
    public static Performance fromSeoul(String seoulId, String title, String genre, String posterImageUrl,
                                        LocalDate startDate, LocalDate endDate, String state,
                                        BigDecimal lat, BigDecimal lng, String externalBookingUrl) {
        Performance p = new Performance();
        p.seoulId = seoulId;
        p.title = title;
        p.genre = genre;
        p.posterImageUrl = posterImageUrl;
        p.startDate = startDate;
        p.endDate = endDate;
        p.state = state;
        p.lat = lat;
        p.lng = lng;
        p.externalBookingUrl = externalBookingUrl;
        p.source = "SEOUL";
        p.avgRating = BigDecimal.ZERO;
        p.reviewCount = 0;
        p.createdAt = LocalDateTime.now();
        p.syncedAt = LocalDateTime.now();
        return p;
    }

    /** 서울 문화행사 재동기화 갱신 */
    public void updateFromSeoul(String title, String genre, String posterImageUrl,
                                LocalDate startDate, LocalDate endDate, String state,
                                BigDecimal lat, BigDecimal lng, String externalBookingUrl) {
        if (title != null) this.title = title;
        if (genre != null) this.genre = genre;
        if (posterImageUrl != null) this.posterImageUrl = posterImageUrl;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (state != null) this.state = state;
        if (lat != null) this.lat = lat;
        if (lng != null) this.lng = lng;
        if (externalBookingUrl != null) this.externalBookingUrl = externalBookingUrl;
        this.syncedAt = LocalDateTime.now();
    }

    /** 전통 테마 태깅(동기화 시 TraditionalTagger 판정 결과 반영). */
    public void applyTraditional(boolean traditional) {
        this.traditional = traditional;
    }

    /** 좌표 보강(KOPIS 공연: 공연시설상세에서 위경도 확보 후 설정). */
    public void setCoordinates(BigDecimal lat, BigDecimal lng) {
        if (lat != null) this.lat = lat;
        if (lng != null) this.lng = lng;
        this.syncedAt = LocalDateTime.now();
    }

    public boolean hasCoordinates() {
        return lat != null && lng != null;
    }
}
