package com.byeori.domain.itinerary;

import jakarta.persistence.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
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

    @JdbcTypeCode(SqlTypes.JSON) private String path;
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

    public boolean matches(String hash) { return stopsHash != null && stopsHash.equals(hash); }

    /**
     * 방문지 좌표열의 지문.
     *
     * 좌표를 소수점 6자리로 끊어 잇는다. 그보다 잘게 보면 저장 과정의 반올림 차이로
     * 같은 루트가 다른 해시가 되어, 바뀐 것이 없는데도 매번 다시 계산한다.
     */
    public static String hashOf(List<double[]> latLngs) {
        StringBuilder sb = new StringBuilder();
        for (double[] p : latLngs) sb.append(String.format("%.6f,%.6f;", p[0], p[1]));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) hex.append(String.format("%02x", d[i]));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }
}
