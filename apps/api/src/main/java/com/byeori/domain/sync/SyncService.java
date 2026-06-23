package com.byeori.domain.sync;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.external.KopisClient;
import com.byeori.global.external.SyncProperties;
import com.byeori.global.external.TourApiClient;
import com.byeori.global.external.dto.KopisItem;
import com.byeori.global.external.dto.TourFestivalItem;
import com.byeori.global.external.dto.TourItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TourAPI/KOPIS → 우리 DB 업서트 동기화. 키 없으면 skip. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private static final int[] CONTENT_TYPES = {12, 14, 39}; // 관광지/문화시설/음식점
    private static final int ROWS = 100;        // 페이지당(최대)
    private static final int MAX_PAGES = 50;     // 장소(지역×타입별) 안전 상한
    private static final int KOPIS_MAX_PAGES = 300;  // 공연: 전국 단일 스트림이라 상한을 크게(최대 3만건)
    private static final int KOPIS_MONTHS = 12;  // 공연 수집 기간(개월)
    private static final DateTimeFormatter KOPIS_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter TOUR_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SyncProperties props;
    private final TourApiClient tourClient;
    private final KopisClient kopisClient;
    private final RegionResolver regionResolver;
    private final VenueRepository venueRepo;
    private final PerformanceRepository perfRepo;

    /** 장소(관광지·문화시설·음식점) 동기화. 항목별 독립 저장(실패 시 해당 항목만 skip). */
    public int syncVenues() {
        if (!props.tourApiEnabled()) {
            log.info("TOURAPI_KEY 미설정 → 장소 동기화 skip");
            return 0;
        }
        int count = 0;
        for (RegionResolver.Region region : regionResolver.resolve()) {
            for (int contentType : CONTENT_TYPES) {
                for (int page = 1; page <= MAX_PAGES; page++) {
                    List<TourItem> items = tourClient.areaBasedList(region.regnCd(), region.signguCd(), contentType, page, ROWS);
                    if (items.isEmpty()) break;
                    for (TourItem it : items) count += upsertVenue(it);
                    throttle();
                    if (items.size() < ROWS) break; // 마지막 페이지
                }
            }
        }
        log.info("장소 동기화 완료: {}건", count);
        return count;
    }

    private int upsertVenue(TourItem it) {
        if (it.contentId() == null || it.title() == null) return 0;
        if (it.addr1() == null || it.addr1().isBlank()) return 0; // 주소 없으면 skip
        BigDecimal lat = num(it.mapy());
        BigDecimal lng = num(it.mapx());
        if (lat == null || lng == null || lat.signum() == 0 || lng.signum() == 0) return 0; // 좌표 없으면 skip(지도용)
        String category = CategoryMapper.fromTour(it.contentTypeId(), it.lclsSystm2(), it.lclsSystm3());
        try {
            venueRepo.findByTourContentId(it.contentId())
                    .ifPresentOrElse(
                            v -> { v.updateFromTourApi(it.title(), it.addr1(), lat, lng, category, it.firstImage(), it.tel()); venueRepo.save(v); },
                            () -> venueRepo.save(Venue.fromTourApi(it.contentId(), it.title(), it.addr1(), lat, lng, category, it.firstImage(), it.tel())));
            return 1;
        } catch (Exception e) {
            log.debug("venue upsert skip {}: {}", it.contentId(), e.getMessage());
            return 0;
        }
    }

    /** 공연(KOPIS) 동기화. 항목별 독립 저장(실패 시 해당 항목만 skip). */
    public int syncPerformances() {
        if (!props.kopisEnabled()) {
            log.info("KOPIS_KEY 미설정 → 공연 동기화 skip");
            return 0;
        }
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusMonths(KOPIS_MONTHS);
        // 공연시설(mt10id) 좌표 캐시 — 여러 공연이 같은 공연장을 공유하므로 중복 호출 방지.
        Map<String, BigDecimal[]> facilityCoords = new HashMap<>();
        int count = 0;
        for (int page = 1; page <= KOPIS_MAX_PAGES; page++) {
            List<KopisItem> items = kopisClient.performances(from, to, page, ROWS);
            if (items.isEmpty()) break;
            for (KopisItem it : items) count += upsertPerformance(it, facilityCoords);
            throttle();
            if (items.size() < ROWS) break; // 마지막 페이지
        }
        log.info("공연 동기화 완료: {}건", count);
        return count;
    }

    /** 축제/행사(TourAPI searchFestival2) 동기화. 공연과 같은 Performance 테이블에 source=TOURAPI로 저장. */
    public int syncFestivals() {
        if (!props.tourApiEnabled()) {
            log.info("TOURAPI_KEY 미설정 → 축제 동기화 skip");
            return 0;
        }
        String eventStart = LocalDate.now().format(TOUR_DATE); // 오늘 이후 진행/예정 행사
        int count = 0;
        for (RegionResolver.Region region : regionResolver.resolve()) {
            for (int page = 1; page <= MAX_PAGES; page++) {
                List<TourFestivalItem> items = tourClient.searchFestival(eventStart, region.regnCd(), region.signguCd(), page, ROWS);
                if (items.isEmpty()) break;
                for (TourFestivalItem it : items) count += upsertFestival(it);
                throttle();
                if (items.size() < ROWS) break; // 마지막 페이지
            }
        }
        log.info("축제 동기화 완료: {}건", count);
        return count;
    }

    private int upsertFestival(TourFestivalItem it) {
        if (it.contentId() == null || it.title() == null) return 0;
        LocalDate start = tourDate(it.eventStartDate());
        LocalDate end = tourDate(it.eventEndDate());
        String state = festivalState(start, end);
        try {
            perfRepo.findByTourContentId(it.contentId())
                    .ifPresentOrElse(
                            p -> { p.updateFromTour(it.title(), "축제", it.firstImage(), start, end, state, num(it.mapy()), num(it.mapx())); perfRepo.save(p); },
                            () -> perfRepo.save(Performance.fromTour(it.contentId(), it.title(), "축제", it.firstImage(), start, end, state, num(it.mapy()), num(it.mapx()))));
            return 1;
        } catch (Exception e) {
            log.debug("festival upsert skip {}: {}", it.contentId(), e.getMessage());
            return 0;
        }
    }

    /** 행사 기간 기준 상태 산출. 기간 정보 없으면 예정으로 처리. */
    private static String festivalState(LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now();
        if (end != null && end.isBefore(today)) return "ENDED";
        if (start != null && start.isAfter(today)) return "UPCOMING";
        if (start != null && end != null) return "ONGOING"; // start<=today<=end
        return "UPCOMING";
    }

    private static LocalDate tourDate(String s) {
        try { return (s == null || s.isBlank()) ? null : LocalDate.parse(s, TOUR_DATE); }
        catch (Exception e) { return null; }
    }

    private int upsertPerformance(KopisItem it, Map<String, BigDecimal[]> facilityCoords) {
        if (it.mt20id() == null || it.prfnm() == null) return 0;
        LocalDate start = date(it.prfpdfrom());
        LocalDate end = date(it.prfpdto());
        String state = stateOf(it.prfstate());
        try {
            Performance p = perfRepo.findByKopisId(it.mt20id())
                    .map(existing -> { existing.updateFromKopis(it.prfnm(), it.genrenm(), it.poster(), start, end, state); return existing; })
                    .orElseGet(() -> Performance.fromKopis(it.mt20id(), it.prfnm(), it.genrenm(), it.poster(), start, end, state, null));
            // 좌표 미보유 시에만 공연시설상세에서 위경도 보강(재동기화 비용 최소화).
            if (!p.hasCoordinates()) {
                BigDecimal[] coords = resolvePerformanceCoords(it.mt20id(), facilityCoords);
                if (coords != null) p.setCoordinates(coords[0], coords[1]);
            }
            perfRepo.save(p);
            return 1;
        } catch (Exception e) {
            log.debug("performance upsert skip {}: {}", it.mt20id(), e.getMessage());
            return 0;
        }
    }

    /** 공연 → 공연시설ID → 공연장 좌표. 공연장 좌표는 캐시(null도 캐시해 재조회 방지). */
    private BigDecimal[] resolvePerformanceCoords(String mt20id, Map<String, BigDecimal[]> cache) {
        String mt10id = kopisClient.facilityId(mt20id);
        throttle();
        if (mt10id == null) return null;
        if (cache.containsKey(mt10id)) return cache.get(mt10id);
        BigDecimal[] coords = kopisClient.facilityCoords(mt10id);
        throttle();
        cache.put(mt10id, coords);
        return coords;
    }

    private static String stateOf(String prfstate) {
        if (prfstate == null) return "UPCOMING";
        return switch (prfstate) {
            case "공연중" -> "ONGOING";
            case "공연완료" -> "ENDED";
            default -> "UPCOMING"; // 공연예정
        };
    }

    private static BigDecimal num(String s) {
        try { return (s == null || s.isBlank()) ? null : new BigDecimal(s); }
        catch (NumberFormatException e) { return null; }
    }

    private static LocalDate date(String s) {
        try { return (s == null || s.isBlank()) ? null : LocalDate.parse(s, KOPIS_DATE); }
        catch (Exception e) { return null; }
    }

    private static void throttle() {
        try { Thread.sleep(120); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
