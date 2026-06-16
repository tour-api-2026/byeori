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

    public SyncProperties(@Value("${byeori.sync.tourapi-key:}") String tourApiKey,
                          @Value("${byeori.sync.kopis-key:}") String kopisKey) {
        this.tourApiKey = tourApiKey;
        this.kopisKey = kopisKey;
    }

    public boolean tourApiEnabled() { return tourApiKey != null && !tourApiKey.isBlank(); }
    public boolean kopisEnabled() { return kopisKey != null && !kopisKey.isBlank(); }

    /** TourAPI 지역코드(areaCode) + 시군구코드(sigunguCode, 없으면 null). 실제 코드는 키 발급 후 응답으로 보정. */
    public record Area(int areaCode, Integer sigunguCode, String label) {}

    public static final List<Area> AREAS = List.of(
            new Area(1, null, "서울"),
            new Area(6, null, "부산"),
            new Area(39, null, "제주"),
            new Area(35, 2, "경주"),   // 경북(35)·경주 sigungu — 보정 필요
            new Area(37, 1, "전주")    // 전북(37)·전주 sigungu — 보정 필요
    );
}
