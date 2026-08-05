package com.byeori.global.external;

import java.util.List;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 외부 동기화 설정(키·대상지역). 키는 백엔드 환경변수로 주입. */
@Component
@Getter
public class SyncProperties {

    private final String tourApiKey;
    private final String kopisKey;
    private final String seoulKey;

    public SyncProperties(@Value("${byeori.sync.tourapi-key:}") String tourApiKey,
                          @Value("${byeori.sync.kopis-key:}") String kopisKey,
                          @Value("${byeori.sync.seoul-key:}") String seoulKey) {
        this.tourApiKey = tourApiKey;
        this.kopisKey = kopisKey;
        this.seoulKey = seoulKey;
    }

    public boolean tourApiEnabled() { return tourApiKey != null && !tourApiKey.isBlank(); }
    public boolean kopisEnabled() { return kopisKey != null && !kopisKey.isBlank(); }
    public boolean seoulEnabled() { return seoulKey != null && !seoulKey.isBlank(); }

    /**
     * 동기화 대상 도시. 법정동코드는 런타임에 ldongCode2로 동적 조회(RegionResolver).
     * sidoKeywords: 시도명 매칭 후보(개명 대응), sigunguKeyword: 시군구명(null이면 시도 전체).
     */
    public record Target(String[] sidoKeywords, String sigunguKeyword, String label) {}

    public static final List<Target> TARGETS = List.of(
            new Target(new String[]{"서울"}, null, "서울"),
            new Target(new String[]{"부산"}, null, "부산"),
            new Target(new String[]{"대구"}, null, "대구"),
            new Target(new String[]{"제주"}, null, "제주"),
            new Target(new String[]{"경상북", "경북"}, "경주", "경주"),
            new Target(new String[]{"전북", "전라북"}, "전주", "전주")
    );
}
