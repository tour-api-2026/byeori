package com.byeori.domain.sync;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.external.KopisClient;
import com.byeori.global.external.SyncProperties;
import com.byeori.global.external.TourApiClient;
import com.byeori.global.external.dto.KopisItem;
import com.byeori.global.external.dto.TourItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    private static final int ROWS = 50;
    private static final int MAX_PAGES = 4;
    private static final DateTimeFormatter KOPIS_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

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
        LocalDate to = from.plusMonths(6);
        int count = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<KopisItem> items = kopisClient.performances(from, to, page, ROWS);
            if (items.isEmpty()) break;
            for (KopisItem it : items) count += upsertPerformance(it);
            throttle();
        }
        log.info("공연 동기화 완료: {}건", count);
        return count;
    }

    private int upsertPerformance(KopisItem it) {
        if (it.mt20id() == null || it.prfnm() == null) return 0;
        LocalDate start = date(it.prfpdfrom());
        LocalDate end = date(it.prfpdto());
        String state = stateOf(it.prfstate());
        try {
            perfRepo.findByKopisId(it.mt20id())
                    .ifPresentOrElse(
                            p -> { p.updateFromKopis(it.prfnm(), it.genrenm(), it.poster(), start, end, state); perfRepo.save(p); },
                            () -> perfRepo.save(Performance.fromKopis(it.mt20id(), it.prfnm(), it.genrenm(), it.poster(), start, end, state, null)));
            return 1;
        } catch (Exception e) {
            log.debug("performance upsert skip {}: {}", it.mt20id(), e.getMessage());
            return 0;
        }
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
