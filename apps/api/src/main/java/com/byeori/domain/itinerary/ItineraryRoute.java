package com.byeori.domain.itinerary;

import jakarta.persistence.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 계산해 둔 경로. 방문지가 그대로면 다시 쓰고, 바뀌면 버린다. */
@Entity
@Table(name = "itinerary_routes")
@IdClass(ItineraryRoute.Key.class)
@Getter
public class ItineraryRoute {

    /** (루트, 모드) 복합키. 모드마다 답이 달라 따로 저장한다. */
    public static class Key implements Serializable {
        private Long itineraryId;
        private String mode;
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(itineraryId, k.itineraryId) && Objects.equals(mode, k.mode);
        }
        @Override public int hashCode() { return Objects.hash(itineraryId, mode); }
    }

    @Id private Long itineraryId;
    @Id private String mode;

    private String stopsHash;
    // 이름이 대문자 한 글자로 끝나면 스프링 네이밍이 밑줄을 넣지 못해(distancem) 칼럼을 못 찾는다.
    @Column(name = "distance_m") private int distanceM;
    @Column(name = "duration_s") private int durationS;

    /** [[위도, 경도], ...] */
    @JdbcTypeCode(SqlTypes.JSON) private String path;

    /**
     * [{distance, duration, mode, points}, ...] — 구간별 거리·시간·이동 수단·점의 수.
     *
     * points 가 늘어난 뒤에도 V27 의 칼럼 주석은 셋만 적고 있다. **V27 은 이미 적용된
     * 마이그레이션이라 고칠 수 없다** — Flyway 체크섬은 주석까지 포함하고 validate 가
     * 기본으로 돌아서, 한 글자만 바꿔도 다음 기동이 체크섬 불일치로 실패한다.
     * 그래서 모양 설명은 고칠 수 있는 이쪽에 둔다(필드별 뜻은 RouteLeg javadoc 참고).
     */
    @JdbcTypeCode(SqlTypes.JSON) private String legs;

    private LocalDateTime computedAt;

    protected ItineraryRoute() {}

    public ItineraryRoute(Long itineraryId, String mode, String stopsHash,
                          int distanceM, int durationS, String path, String legs) {
        this.itineraryId = itineraryId;
        this.mode = mode;
        update(stopsHash, distanceM, durationS, path, legs);
    }

    public void update(String stopsHash, int distanceM, int durationS, String path, String legs) {
        this.stopsHash = stopsHash;
        this.distanceM = distanceM;
        this.durationS = durationS;
        this.path = path;
        this.legs = legs;
        this.computedAt = LocalDateTime.now();
    }

    /**
     * 저장해 둔 이 줄을 그대로 써도 되는가.
     *
     * 해시만 보면 안 된다. 좌표열이 그대로면 경로의 **모양**은 변하지 않지만, 자동차
     * 구간의 duration 은 카카오가 그 시각의 교통량으로 준 값이다. 출근길에 계산한
     * "25분"이 해시가 맞는 한 영원히 나오면 숫자가 거짓말이 된다. 그래서 나이도 본다.
     *
     * computedAt 이 비어 있으면(있을 수 없지만) 오래된 것으로 보고 다시 계산하게 둔다 —
     * 모르는 값을 믿는 쪽이 위험하다.
     */
    public boolean matches(String hash, Duration maxAge) {
        if (stopsHash == null || !stopsHash.equals(hash)) return false;
        if (maxAge == null) return true;
        return computedAt != null && computedAt.isAfter(LocalDateTime.now().minus(maxAge));
    }

    /**
     * 방문지 좌표열의 지문.
     *
     * 좌표를 소수점 6자리로 끊어 잇는다. 그보다 잘게 보면 저장 과정의 반올림 차이로
     * 같은 루트가 다른 해시가 되어, 바뀐 것이 없는데도 매번 다시 계산한다.
     *
     * 도보 기준값(walkThresholdM)도 넣는다. 이 값을 바꾸면 같은 방문지라도 구간별 수단이
     * 달라지는데, 좌표만 보면 저장본이 그대로 맞아떨어져서 이미 캐시가 있는 사용자에게는
     * 설정을 바꾼 효과가 영원히 나타나지 않는다. 빌드 없이 돌려 볼 수 있는 손잡이가
     * 아무 일도 하지 않게 되는 셈이다.
     *
     * Locale.ROOT 를 고정한다. 기본 로케일이 쉼표 소수점(de, fr, ru)이면 문자열이 달라져
     * 저장본이 오류 없이 전부 빗나가기 때문이다.
     */
    public static String hashOf(List<double[]> latLngs, int walkThresholdM) {
        StringBuilder sb = new StringBuilder();
        for (double[] p : latLngs) sb.append(String.format(Locale.ROOT, "%.6f,%.6f;", p[0], p[1]));
        sb.append("t").append(walkThresholdM);
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) hex.append(String.format(Locale.ROOT, "%02x", d[i]));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }
}
