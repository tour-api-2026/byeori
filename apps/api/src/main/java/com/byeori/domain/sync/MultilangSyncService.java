package com.byeori.domain.sync;

import com.byeori.domain.translation.TranslationBatch;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.external.TourApiClient;
import com.byeori.global.external.dto.TourItem;
import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한국관광공사 다국어판에서 공식 번역 이름을 가져와 우리 장소에 붙인다.
 *
 * 지금 외국어 이름은 gpt-4o-mini 가 만든다. 고유명사를 자주 틀린다 — '창경궁'을
 * 'Changdeokgung Palace'(창덕궁, 다른 궁)로 적은 적이 있다. 공사가 같은 데이터를 이미
 * 공식 번역해 공개하고 있으므로(이용허락범위 제한 없음) 그쪽을 우선한다.
 *
 * **contentId 로는 못 잇는다.** 다국어판은 국문판과 id 체계가 달라 같은 경복궁이
 * 국문 126508, 영문 264337 이다. 그래서 이름과 좌표로 맞춘다(MultilangTitle 주석 참고).
 *
 * 실측(운영 데이터 기준): 영문 3,803곳 · 중문 2,478곳 · 일문 1,546곳이 붙고, 합쳐서
 * 3,921곳이다. 필수명소는 1,455곳 중 1,424곳(98%)이 붙는다 — 외국인이 실제로 찾는 곳을
 * 공사가 공들여 담기 때문이다. 전체 35,288곳에 비하면 적지만, 다국어판 자체가 전국
 * 관광정보의 사본이 아니라 외국인에게 보여줄 것만 고른 1만 5천 건짜리 부분집합이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultilangSyncService {

    private static final int ROWS = 100;
    /** 한 언어가 1만 5천 건 남짓이다. 페이지 상한은 넉넉히 두되 무한 루프는 막는다. */
    private static final int MAX_PAGES = 400;

    private final TourApiClient tourClient;
    private final VenueRepository venueRepo;

    /** 세 언어를 모두 돌린다. 돌려주는 값은 이름을 새로 넣은 장소 수(언어별 합). */
    public int syncAll() {
        int total = 0;
        for (Lang lang : List.of(Lang.EN, Lang.JA, Lang.ZH)) {
            total += sync(lang);
        }
        return total;
    }

    @Transactional
    public int sync(Lang lang) {
        List<TourItem> items = fetchAll(lang);
        if (items.isEmpty()) {
            log.warn("다국어 동기화: {} 목록이 비었다 — 활용신청이 안 됐거나 호출 한도를 넘었다", lang);
            return 0;
        }

        // 이름 → 장소. 같은 이름이 여러 곳이면 모두 들고 있다가 좌표로 고른다
        // ('송정해수욕장'은 부산과 강원에 하나씩 있다).
        Map<String, List<Venue>> byName = new HashMap<>();
        for (Venue v : venueRepo.findAllActiveWithName()) {
            byName.computeIfAbsent(v.getName(), k -> new ArrayList<>()).add(v);
        }

        int applied = 0;
        int noMatch = 0;
        int ambiguous = 0;
        List<Venue> changed = new ArrayList<>();

        for (TourItem it : items) {
            String ko = MultilangTitle.koreanPart(it.title());
            if (ko.isEmpty()) continue;              // 원문이 없는 행은 못 맞춘다
            List<Venue> cands = byName.get(ko);
            if (cands == null) { noMatch++; continue; }

            BigDecimal lat = num(it.mapy());
            BigDecimal lng = num(it.mapx());
            if (lat == null || lng == null) continue;

            List<Venue> near = cands.stream()
                    .filter(v -> v.getLat() != null && v.getLng() != null)
                    .filter(v -> MultilangTitle.nearby(v.getLat().doubleValue(), v.getLng().doubleValue(),
                            lat.doubleValue(), lng.doubleValue()))
                    .toList();
            if (near.isEmpty()) { noMatch++; continue; }
            // 이름도 같고 좌표도 가까운 곳이 둘이면 어느 쪽인지 알 수 없다. 운영 데이터에서 22곳이다 —
            // 잘못 붙이느니 건너뛴다.
            if (near.size() > 1) { ambiguous++; continue; }

            Venue v = near.get(0);
            v.applyOfficialTranslation(lang, official(it.title(), ko), it.contentId());
            // 해시를 찍지 않으면 야간 AI 배치가 이 행을 다시 집어 공식 번역을 덮는다.
            v.markHash(TranslationBatch.hashOf(v.getName()));
            changed.add(v);
            applied++;
        }

        venueRepo.saveAll(changed);
        log.info("다국어 동기화 {}: 받은 {}건 중 {}곳에 적용 (못 맞춤 {}, 모호 {})",
                lang, items.size(), applied, noMatch, ambiguous);
        return applied;
    }

    /**
     * 보여 줄 이름. 제목에서 괄호로 달려 온 한국어 원문을 뗀다 —
     * 'Gyeongbokgung Palace (경복궁)' 을 그대로 쓰면 영어 화면에 한글이 섞인다.
     */
    static String official(String title, String koreanPart) {
        String out = title == null ? "" : title;
        for (String wrap : new String[]{"(" + koreanPart + ")", "[" + koreanPart + "]"}) {
            out = out.replace(wrap, "");
        }
        return out.trim();
    }

    /** 한 언어의 목록 전부. 공사가 주는 totalCount 만큼 넘긴다. */
    private List<TourItem> fetchAll(Lang lang) {
        List<TourItem> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<TourItem> got = tourClient.multilangList(lang, page, ROWS);
            if (got.isEmpty()) break;
            all.addAll(got);
            if (got.size() < ROWS) break;
        }
        return all;
    }

    private static BigDecimal num(String s) {
        try {
            return (s == null || s.isBlank()) ? null : new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
