package com.byeori.domain.sync;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.external.KopisClient;
import com.byeori.global.external.SeoulEventClient;
import com.byeori.global.external.SyncProperties;
import com.byeori.global.external.TourApiClient;
import com.byeori.global.external.dto.KopisItem;
import com.byeori.global.external.dto.SeoulEventItem;
import com.byeori.global.external.dto.TourFestivalItem;
import com.byeori.global.external.dto.TourItem;
import com.byeori.global.external.dto.TourSyncItem;
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

    // 관광지·문화시설·음식점에 더해 레포츠(체험), 쇼핑(전통시장·공예), 숙박(한옥)을 받는다.
    // 쇼핑·숙박은 대부분 우리와 무관해 CategoryMapper 가 null 을 돌려주는 항목을 걸러낸다.
    private static final int[] CONTENT_TYPES = {12, 14, 39, 28, 38, 32};
    private static final int ROWS = 100;        // 페이지당(최대)
    private static final int MAX_PAGES = 400;    // 유형별 안전 상한(음식점 19,900건 = 199페이지)
    private static final int KOPIS_MAX_PAGES = 300;  // 공연: 전국 단일 스트림이라 상한을 크게(최대 3만건)
    private static final int KOPIS_MONTHS = 12;  // 공연 수집 기간(개월)
    private static final DateTimeFormatter KOPIS_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter TOUR_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SyncProperties props;
    private final TourApiClient tourClient;
    private final KopisClient kopisClient;
    private final SeoulEventClient seoulClient;
    private final RegionResolver regionResolver;
    private final VenueRepository venueRepo;
    private final SyncLogRepository syncLogRepo;
    private final PerformanceRepository perfRepo;

    /**
     * 증분 동기화. 공사가 로컬 저장용으로 제공하는 areaBasedSyncList2 를 쓴다.
     *
     * modifiedtime 은 "그 날짜에 수정된 것"을 뜻한다. "그 이후 전부"가 아니다
     * (20260908 → 181건이 전부 수정일 9월 8일, 20200101 → 0건). 그래서 마지막 성공일
     * 다음날부터 오늘까지 하루씩 훑는다. 날짜 하나만 넘기면 그 하루치만 받고 나머지를
     * 놓친다.
     *
     * 평소에는 2~3일 × 유형 6개 = 십여 건으로 끝난다. 장애로 며칠 걸러도 그만큼만
     * 늘어나고, 그 이상 벌어지면 전량 재수집(only=incremental&since=all)이 낫다.
     *
     * showflag=0(공사에서 내린 콘텐츠)은 우리 쪽에서도 비노출로 바꾼다.
     * 커서는 마지막 성공 실행일이라, 실패하면 전진하지 않아 다음 회차가 빠진 구간을
     * 함께 가져간다.
     */
    @Transactional
    public int syncVenuesIncremental() {
        return syncVenuesIncremental(null);
    }

    /**
     * @param mode null 이면 커서부터 오늘까지, "all" 이면 전량(modifiedtime 없이),
     *             yyyyMMdd 면 그 날짜부터 오늘까지.
     */
    @Transactional
    public int syncVenuesIncremental(String mode) {
        if (!props.tourApiEnabled()) {
            log.info("TOURAPI_KEY 미설정 → 증분 동기화 skip");
            return 0;
        }
        boolean full = "all".equalsIgnoreCase(mode);
        List<String> dates = full ? java.util.Collections.singletonList(null) : datesToFetch(mode);
        SyncLog run = syncLogRepo.save(SyncLog.started("TOURAPI", "VENUE_SYNC"));
        int changed = 0, hidden = 0;
        try {
            for (String day : dates) {
                for (int contentType : CONTENT_TYPES) {
                    for (int page = 1; page <= MAX_PAGES; page++) {
                        List<TourSyncItem> items = tourClient.syncList(contentType, day, page, ROWS);
                        if (items.isEmpty()) break;
                        for (TourSyncItem it : items) {
                            if (it.visible()) {
                                changed += upsertVenue(it.item());
                            } else {
                                hidden += hideVenue(it.item().contentId());
                            }
                        }
                        run.progressed(changed + hidden);
                        throttle();
                        if (items.size() < ROWS) break;
                    }
                }
            }
            String span = full ? "전량" : dates.size() + "일치(" + dates.get(0) + "~)";
            run.succeeded(changed + hidden, "갱신 " + changed + "건, 비노출 " + hidden + "건 (" + span + ")");
            log.info("증분 동기화 완료: 갱신 {}건, 비노출 {}건 ({})", changed, hidden, span);
            return changed + hidden;
        } catch (Exception e) {
            // 커서를 전진시키지 않아 다음 회차가 이 구간을 다시 가져간다.
            run.failed(e.getMessage());
            log.warn("증분 동기화 실패: {}", e.getMessage());
            return changed + hidden;
        }
    }

    /** 최대 며칠까지 거슬러 올라갈지. 이보다 벌어지면 전량 재수집이 낫다. */
    private static final int MAX_CATCHUP_DAYS = 30;

    /**
     * 받아야 할 날짜들. 마지막 성공일부터 오늘까지(하루 겹쳐서 — 공사 반영이 늦을 수 있다).
     * 기록이 없으면 저장된 장소의 마지막 수집일을 쓰고, 그것도 없으면 오늘 하루만 본다.
     */
    private List<String> datesToFetch(String from) {
        LocalDate start;
        if (from != null && !from.isBlank()) {
            start = LocalDate.parse(from, TOUR_DATE);
        } else {
            start = syncLogRepo
                    .findTopByProviderAndTargetTypeAndStatusOrderByStartedAtDesc("TOURAPI", "VENUE_SYNC", SyncLog.SUCCESS)
                    .map(s -> s.getStartedAt().toLocalDate())
                    .orElseGet(() -> venueRepo.findMaxSyncedAt()
                            .map(java.time.LocalDateTime::toLocalDate)
                            .orElse(LocalDate.now()))
                    .minusDays(1);
        }
        LocalDate today = LocalDate.now();
        if (start.isBefore(today.minusDays(MAX_CATCHUP_DAYS))) start = today.minusDays(MAX_CATCHUP_DAYS);
        List<String> out = new java.util.ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) out.add(d.format(TOUR_DATE));
        return out;
    }

    /** 공사에서 내린 콘텐츠를 비노출로. 우리가 갖고 있지 않으면 할 일이 없다. */
    private int hideVenue(String contentId) {
        if (contentId == null) return 0;
        return venueRepo.findByTourContentId(contentId)
                .map(v -> { v.deactivateBySync(); venueRepo.save(v); return 1; })
                .orElse(0);
    }

    /** 장소(관광지·문화시설·음식점) 전량 동기화. 초기 적재·수동 백필용. */
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
        // 좌표 없거나 한국 범위(위도 33~38.7, 경도 124.5~132) 밖이면 skip.
        // 지오코딩 실패 placeholder(예: 19.69,117.99 / 9.99,9.99)가 지도를 외국까지 넓히는 것 방지.
        if (lat == null || lng == null) return 0;
        double latD = lat.doubleValue(), lngD = lng.doubleValue();
        if (latD < 33.0 || latD > 38.7 || lngD < 124.5 || lngD > 132.0) return 0;
        String category = CategoryMapper.fromTour(it.contentTypeId(), it.lclsSystm2(), it.lclsSystm3());
        // 쇼핑·숙박은 전통문화와 닿는 소분류만 담는다(약국·모텔 등은 category가 null).
        if (category == null) return 0;
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
            Performance p = perfRepo.findByTourContentId(it.contentId())
                    .map(existing -> { existing.updateFromTour(it.title(), "축제", it.firstImage(), start, end, state, num(it.mapy()), num(it.mapx())); return existing; })
                    .orElseGet(() -> Performance.fromTour(it.contentId(), it.title(), "축제", it.firstImage(), start, end, state, num(it.mapy()), num(it.mapx())));
            p.applyTraditional(TraditionalTagger.isTraditional(p.getTitle(), p.getGenre()));
            perfRepo.save(p);
            return 1;
        } catch (Exception e) {
            log.debug("festival upsert skip {}: {}", it.contentId(), e.getMessage());
            return 0;
        }
    }

    /** 서울 열린데이터 전통 계열 CODENAME(서버 필터로 사용). */
    private static final List<String> SEOUL_TRADITIONAL_CODES = List.of("국악", "축제-전통/역사");
    private static final int SEOUL_ROWS = 1000; // 서울 API 페이지당 최대

    /** 서울 문화행사(전통 테마) 동기화. CODENAME 서버 필터로 국악·전통/역사 축제만 수집. */
    public int syncSeoulEvents() {
        if (!props.seoulEnabled()) {
            log.info("SEOUL_OPEN_API_KEY 미설정 → 서울 문화행사 동기화 skip");
            return 0;
        }
        int count = 0;
        for (String codename : SEOUL_TRADITIONAL_CODES) {
            for (int start = 1; start <= 10 * SEOUL_ROWS; start += SEOUL_ROWS) {
                List<SeoulEventItem> items = seoulClient.events(codename, start, start + SEOUL_ROWS - 1);
                if (items.isEmpty()) break;
                for (SeoulEventItem it : items) count += upsertSeoulEvent(it);
                throttle();
                if (items.size() < SEOUL_ROWS) break; // 마지막 페이지
            }
        }
        log.info("서울 전통 행사 동기화 완료: {}건", count);
        return count;
    }

    private int upsertSeoulEvent(SeoulEventItem it) {
        if (it.title() == null) return 0;
        String seoulId = seoulEventId(it);
        LocalDate start = seoulDate(it.strtdate());
        LocalDate end = seoulDate(it.endDate());
        String state = festivalState(start, end);
        String bookingUrl = it.hmpgAddr() != null ? it.hmpgAddr() : it.orgLink();
        try {
            Performance p = perfRepo.findBySeoulId(seoulId)
                    .map(existing -> { existing.updateFromSeoul(it.title(), it.codename(), it.mainImg(), start, end, state, num(it.lat()), num(it.lot()), bookingUrl); return existing; })
                    .orElseGet(() -> Performance.fromSeoul(seoulId, it.title(), it.codename(), it.mainImg(), start, end, state, num(it.lat()), num(it.lot()), bookingUrl));
            p.applyTraditional(true); // 전통 계열 CODENAME만 수집하므로 항상 true
            perfRepo.save(p);
            return 1;
        } catch (Exception e) {
            log.debug("seoul event upsert skip {}: {}", seoulId, e.getMessage());
            return 0;
        }
    }

    /** 안정 ID: 상세 URL의 cultcode 우선, 없으면 제목+시작일+장소 해시. */
    private static String seoulEventId(SeoulEventItem it) {
        if (it.hmpgAddr() != null) {
            var m = java.util.regex.Pattern.compile("cultcode=(\\d+)").matcher(it.hmpgAddr());
            if (m.find()) return "cult-" + m.group(1);
        }
        String basis = it.title() + "|" + it.strtdate() + "|" + it.place();
        return "hash-" + Integer.toHexString(basis.hashCode());
    }

    /** "yyyy-MM-dd HH:mm:ss.S" → LocalDate (앞 10자리만 사용). */
    private static LocalDate seoulDate(String s) {
        if (s == null || s.length() < 10) return null;
        try { return LocalDate.parse(s.substring(0, 10)); }
        catch (Exception e) { return null; }
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
            p.applyTraditional(TraditionalTagger.isTraditional(p.getTitle(), p.getGenre()));
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
