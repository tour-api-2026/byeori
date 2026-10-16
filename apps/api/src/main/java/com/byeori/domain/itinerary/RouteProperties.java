package com.byeori.domain.itinerary;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 경로 설정. 기준값을 빌드 없이 바꿔 볼 수 있어야 해서 설정으로 뺀다. */
@Component
@ConfigurationProperties(prefix = "byeori.route")
public class RouteProperties {

    /** 이 거리(직선, m) 이내면 걷는 것으로 본다. 실제 루트 구간의 중앙값이 706m 다. */
    private int walkThresholdM = 1000;

    public int getWalkThresholdM() { return walkThresholdM; }
    public void setWalkThresholdM(int v) { this.walkThresholdM = v; }
}
