package com.byeori.domain.itinerary;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 경로 설정. 기준값을 빌드 없이 바꿔 볼 수 있어야 해서 설정으로 뺀다. */
@Component
@ConfigurationProperties(prefix = "byeori.route")
public class RouteProperties {

    /** 이 거리(직선, m) 이내면 걷는 것으로 본다. 실제 루트 구간의 중앙값이 706m 다. */
    private int walkThresholdM = 1000;

    /**
     * 도보 경로를 물어볼 직선거리 상한(m).
     *
     * mode=WALK 는 거리와 상관없이 걷겠다는 뜻이라 기준값을 넘어도 도보로 간다. 그런데
     * 서울→부산 같은 구간을 OSRM 에 물으면 좌표가 수만 점이 되고, 그게 응답·jsonb
     * 칼럼·웹뷰의 LatLng 루프를 그대로 통과한다. 사람이 걸을 길이 아니기도 하다.
     * 이 상한을 넘는 구간은 다른 먼 구간처럼 차로 간다.
     */
    private int walkMaxM = 50_000;

    /**
     * 저장해 둔 경로를 다시 쓰는 기간(일).
     *
     * 경로의 **모양**은 방문지를 고치기 전까지 변하지 않는다. 그런데 자동차 구간의
     * 소요시간은 카카오가 그 시각의 교통량으로 계산해 준 값이다. 출근길에 잰 "25분"을
     * 한 달 뒤 새벽에도 그대로 내려주면 숫자가 거짓말이 된다. 그래서 오래된 줄은 버린다.
     */
    private int cacheMaxAgeDays = 7;

    public int getWalkThresholdM() { return walkThresholdM; }
    public void setWalkThresholdM(int v) { this.walkThresholdM = v; }

    public int getWalkMaxM() { return walkMaxM; }
    public void setWalkMaxM(int v) { this.walkMaxM = v; }

    public int getCacheMaxAgeDays() { return cacheMaxAgeDays; }
    public void setCacheMaxAgeDays(int v) { this.cacheMaxAgeDays = v; }
}
