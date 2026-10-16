package com.byeori.domain.itinerary;

import com.byeori.domain.course.CuratedCourseItem;
import com.byeori.domain.course.CuratedCourseItemRepository;
import com.byeori.domain.itinerary.dto.ItineraryDtos.*;
import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.content.ContentTarget;
import com.byeori.global.content.ContentType;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import com.byeori.global.external.KakaoLocalClient;
import com.byeori.global.external.KakaoMobilityClient;
import com.byeori.global.external.OsrmClient;
import com.byeori.global.external.dto.KakaoRoute;
import com.byeori.global.external.dto.OsrmRoute;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(readOnly = true)
public class ItineraryService {

    private final ItineraryRepository repo;
    private final ItineraryItemRepository itemRepo;
    private final CuratedCourseItemRepository courseItemRepo;
    private final VenueRepository venueRepo;
    private final PerformanceRepository performanceRepo;
    private final KakaoMobilityClient mobilityClient;
    private final OsrmClient osrmClient;
    private final ItineraryRouteRepository routeRepo;
    private final ItineraryRouteSaver routeSaver;
    private final RouteProperties props;
    private final ObjectMapper om = new ObjectMapper();

    public ItineraryService(ItineraryRepository repo, ItineraryItemRepository itemRepo,
                            CuratedCourseItemRepository courseItemRepo,
                            VenueRepository venueRepo, PerformanceRepository performanceRepo,
                            KakaoMobilityClient mobilityClient,
                            OsrmClient osrmClient, ItineraryRouteRepository routeRepo,
                            ItineraryRouteSaver routeSaver, RouteProperties props) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.courseItemRepo = courseItemRepo;
        this.venueRepo = venueRepo;
        this.performanceRepo = performanceRepo;
        this.mobilityClient = mobilityClient;
        this.osrmClient = osrmClient;
        this.routeRepo = routeRepo;
        this.routeSaver = routeSaver;
        this.props = props;
    }

    /** 공유 토큰 생성용. SecureRandom 은 스레드 안전해서 하나를 돌려 쓴다. */
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    public List<Summary> listMine(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(i -> Summary.from(i, itemRepo.findByItineraryIdOrderByVisitDateAscSortOrderAsc(i.getId()).size()))
                .toList();
    }

    public Detail get(Long userId, Long id) {
        Itinerary i = own(userId, id);
        List<ItemResponse> items = itemRepo.findByItineraryIdOrderByVisitDateAscSortOrderAsc(id).stream()
                .map(this::toItemResponse).toList();
        return Detail.from(i, items);
    }

    // ── 공유 링크 ─────────────────────────────

    /**
     * 공유 링크 토큰을 내준다. 없으면 만들고, 있으면 있던 것을 그대로 준다.
     *
     * 공유 버튼을 누를 때마다 부르는 자리라 매번 새로 만들면 토큰이 쌓이고, 먼저 보낸
     * 링크가 죽는다. 그래서 처음 한 번만 심는다(Itinerary.shareWith).
     */
    @Transactional
    public ShareResponse share(Long userId, Long id) {
        Itinerary i = own(userId, id);
        if (i.getShareToken() == null) i.shareWith(newToken());
        return new ShareResponse(i.getShareToken());
    }

    /**
     * 토큰으로 보는 루트. 로그인을 묻지 않는다 — 토큰 자체가 열쇠다.
     *
     * 128비트 난수를 URL 안전 문자로 적어 22자가 된다. 훑어서 맞히는 건 현실적으로 불가능하고,
     * id 가 응답에 없어 이걸로 다른 루트를 짚어 볼 수도 없다.
     */
    public SharedDetail getShared(String token) {
        if (token == null || token.isBlank()) {
            throw new NotFoundException("SHARED_NOT_FOUND", "공유된 루트를 찾을 수 없습니다.");
        }
        Itinerary i = repo.findByShareToken(token)
                .orElseThrow(() -> new NotFoundException("SHARED_NOT_FOUND", "공유된 루트를 찾을 수 없습니다."));
        List<SharedStop> stops = itemRepo.findByItineraryIdOrderByVisitDateAscSortOrderAsc(i.getId()).stream()
                .map(this::toSharedStop).toList();
        return new SharedDetail(i.getTitle(), i.getStartDate(), i.getEndDate(), stops);
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 공유로 내보낼 모양. memo 는 **옮기지 않는다** — 개인 메모가 적혀 있을 수 있다. */
    private SharedStop toSharedStop(ItineraryItem item) {
        ItemResponse r = toItemResponse(item);
        return new SharedStop(r.targetType(), r.targetId(), r.name(), r.imageUrl(),
                r.visitDate(), r.sortOrder(), r.plannedTime(), r.lat(), r.lng());
    }

    /**
     * 여행 일지의 방문지들을 순서대로 잇는 경로. 구간마다 걷거나 탄다.
     *
     * 좌표 없는 항목은 건너뛰고, 좌표 있는 방문지가 2곳 이상일 때만 경로를 준다.
     * 읽기처럼 보이지만 계산한 경로를 저장해 두므로 쓰기 트랜잭션이 필요하다 —
     * 클래스 기본값(readOnly)을 그대로 쓰면 저장이 조용히 사라진다.
     */
    @Transactional
    public RouteResponse route(Long userId, Long id, String mode) {
        own(userId, id);
        // 카카오 키가 없다고 미리 막지 않는다. directions 가 스스로 null 을 주므로 구간마다
        // 알아서 내려가고, 도보로 충분한 루트는 키 없이도 온전히 나온다. 막아 두면 그 루트가
        // 400 이 되고 mode=WALK 가 카카오 키 없이는 불가능해진다.
        List<RouteStop> stops = new ArrayList<>();
        List<double[]> coords = new ArrayList<>(); // [경도, 위도] 순서(카카오 요청용)
        int order = 0;
        for (ItineraryItem item : itemRepo.findByItineraryIdOrderByVisitDateAscSortOrderAsc(id)) {
            BigDecimal lat, lng;
            String name, type;
            Long targetId;
            if (item.getPerformanceId() != null) {
                Performance p = performanceRepo.findById(item.getPerformanceId()).orElse(null);
                if (p == null) continue;
                lat = p.getLat(); lng = p.getLng(); name = p.getTitle();
                type = "PERFORMANCE"; targetId = p.getId();
            } else if (item.getVenueId() != null) {
                Venue v = venueRepo.findById(item.getVenueId()).orElse(null);
                if (v == null) continue;
                lat = v.getLat(); lng = v.getLng(); name = v.getName();
                type = "VENUE"; targetId = v.getId();
            } else {
                continue;
            }
            if (lat == null || lng == null) continue; // 좌표 없는 항목은 경로에서 제외
            stops.add(new RouteStop(order++, type, targetId, name, lat.doubleValue(), lng.doubleValue()));
            coords.add(new double[]{lng.doubleValue(), lat.doubleValue()});
        }
        if (coords.size() < 2) {
            throw new BadRequestException("ROUTE_TOO_FEW_POINTS", "경로를 그리려면 좌표가 있는 방문지가 2곳 이상이어야 합니다.");
        }

        // 모르는 값은 AUTO 로 본다 — 경로 보기는 읽기 동작이라 오타로 400 을 주지 않는다.
        String wanted = RouteModePlanner.WALK.equals(mode) || RouteModePlanner.DRIVE.equals(mode)
                ? mode : RouteModePlanner.AUTO;

        // 저장해 둔 것이 아직 맞으면 그대로 쓴다. 외부 호출이 한 번도 없다.
        int threshold = props.getWalkThresholdM();
        List<double[]> latLngs = coords.stream().map(c -> new double[]{c[1], c[0]}).toList();
        String hash = ItineraryRoute.hashOf(latLngs, threshold);
        ItineraryRoute saved = routeRepo.findByItineraryIdAndMode(id, wanted).orElse(null);
        if (saved != null && saved.matches(hash)) {
            RouteResponse cached = fromSaved(saved, wanted, stops);
            if (cached != null) return cached;   // 못 읽으면 아래로 내려가 다시 계산한다
        }

        List<RouteLeg> legs = new ArrayList<>();
        List<double[]> path = new ArrayList<>();
        int totalM = 0, totalS = 0;
        // 요청 하나 안에서만 쓰는 차단기. OSRM 이 죽은 게 아니라 멈춰 있으면 한 번에 4초를
        // 기다리는데, 구간마다 다시 물으면 5구간 루트가 20초를 잡아먹는다. 한 번 빗나가면
        // 남은 구간은 묻지 않고 바로 자동차로 간다.
        boolean osrmDown = false;
        // 좌표를 직선으로 메운 구간 수. 수단 이름은 WALK/DRIVE 로 멀쩡해 보이지만 지도에
        // 그려지는 선은 우리가 지어낸 직선이다. 이것도 저장하면 안 된다.
        int fakedPaths = 0;

        for (int i = 0; i < coords.size() - 1; i++) {
            double[] a = coords.get(i), b = coords.get(i + 1);   // [경도, 위도]
            double straight = RouteModePlanner.distanceMeters(a[1], a[0], b[1], b[0]);
            String legWanted = RouteModePlanner.legMode(wanted, straight, threshold);

            OsrmRoute walk = null;
            int[] driveSummary = null;
            List<double[]> legPath = null;

            if (RouteModePlanner.WALK.equals(legWanted) && !osrmDown) {
                walk = osrmClient.walk(List.of(a, b));
                if (walk != null) legPath = walk.path();
                else osrmDown = true;
            }
            if (walk == null) {   // 도보를 안 고를 수도, 실패했을 수도 있다
                KakaoRoute kr = mobilityClient.directions(List.of(a, b), "RECOMMEND");
                if (kr != null) {
                    driveSummary = new int[]{kr.distance(), kr.duration()};
                    legPath = kr.path().stream().map(p -> new double[]{p[1], p[0]}).toList();
                }
            }
            // 비어 있는 경로도 실패다. 카카오는 길을 못 찾아도 result_code 0 에 빈 좌표열을
            // 주는 때가 있는데, null 만 걸러내면 뒤의 subList(1, 0) 에서 터져 루트 전체가 500 이 된다.
            if (legPath == null || legPath.isEmpty()) {   // 둘 다 실패 — 직선으로 잇는다
                legPath = List.of(new double[]{a[1], a[0]}, new double[]{b[1], b[0]});
                fakedPaths++;
            }

            RouteLeg built = legOrFallback(walk, driveSummary, straight, legWanted);
            // 구간이 이어지는 지점은 한 번만 넣는다
            List<double[]> added = i == 0 ? legPath : legPath.subList(1, legPath.size());
            path.addAll(added);
            // 넣은 점의 수는 여기서야 안다. 지도가 path 를 구간별로 자를 수 있게 적어 둔다.
            RouteLeg leg = new RouteLeg(built.distance(), built.duration(), built.mode(), added.size());
            legs.add(leg);
            totalM += leg.distance();
            totalS += leg.duration();
        }

        // 온전하지 않은 결과는 저장하지 않는다.
        //
        // matches() 는 좌표 해시만 본다. OSRM 이 멈춰 있는 동안 계산한 직선·차 경로를 넣어
        // 두면, 방문지를 고치지 않는 한 그 루트는 OSRM 이 돌아온 뒤에도 영원히 직선을 준다.
        // 사용자는 이유를 알 길이 없다. 저장을 건너뛰면 장애 동안만 매번 다시 계산하고,
        // OSRM 이 살아나는 순간 저절로 낫는다.
        //
        // 온전하지 않은 세 가지를 모두 센다. 수단 이름이 STRAIGHT 인 구간(둘 다 실패),
        // 차단기가 걸린 요청(도보를 물어보지도 못했다), 그리고 **수단은 멀쩡한데 좌표만
        // 지어낸 구간**이다. 셋째가 가장 눈에 안 띈다 — 공급자가 200 과 요약은 주면서 좌표를
        // 비워 보내면 DRIVE 라고 적힌 직선이 캐시에 눌러앉는다.
        long straightLegs = legs.stream().filter(l -> STRAIGHT.equals(l.mode())).count();
        if (osrmDown || straightLegs > 0 || fakedPaths > 0) {
            log.info("경로가 온전하지 않아 저장하지 않는다 itinerary={} mode={}"
                            + " osrm다운={} 직선구간={}/{} 직선좌표={}",
                    id, wanted, osrmDown, straightLegs, legs.size(), fakedPaths);
        } else {
            save(id, wanted, hash, totalM, totalS, path, legs);
        }
        return new RouteResponse(totalM, totalS, wanted, stops, legs, path);
    }

    /** 직선으로 내려갔을 때의 수단 이름. 거리·시간이 어림값이라는 표시이기도 하다. */
    static final String STRAIGHT = "STRAIGHT";

    /** 걷는 속도(m/s). 직선으로 내려갈 때 시간을 어림하는 데만 쓴다. 약 5km/h. */
    private static final double WALK_MPS = 1.4;

    /** 시내 차 속도(m/s). 약 30km/h — 신호와 정체를 포함한 어림값이다. */
    private static final double DRIVE_MPS = 8.3;

    /**
     * 한 구간을 어떻게든 채운다.
     *
     * OSRM 이 죽어도, 카카오가 실패해도 루트 화면은 떠야 한다. 마지막에는 직선이라도
     * 준다 — 방문 순서와 대략의 거리는 그것만으로도 쓸모가 있다.
     *
     * 직선으로 내려갈 때는 **그 구간에 뭘 하려 했는지**에 맞춰 시간을 어림한다. 6km 짜리
     * 차 구간을 걷는 속도로 적으면 "71분"이 되는데, 숫자가 빈 화면보다 나쁘다.
     */
    static RouteLeg legOrFallback(OsrmRoute walk, int[] driveSummary, double straightM,
                                  String legWanted) {
        if (walk != null) return new RouteLeg(walk.distance(), walk.duration(), RouteModePlanner.WALK, 0);
        if (driveSummary != null) {
            return new RouteLeg(driveSummary[0], driveSummary[1], RouteModePlanner.DRIVE, 0);
        }
        double mps = RouteModePlanner.DRIVE.equals(legWanted) ? DRIVE_MPS : WALK_MPS;
        return new RouteLeg((int) straightM, (int) (straightM / mps), STRAIGHT, 0);
    }

    /**
     * 계산한 경로를 저장한다. 저장에 실패해도 경로는 돌려준다 — 다음 요청에 다시 계산될 뿐이다.
     *
     * 쓰기는 ItineraryRouteSaver 의 별도 트랜잭션에서 한다. 동시 저장 충돌이든 교착이든,
     * 실패가 이 요청의 트랜잭션을 오염시키지 않아야 손에 든 경로를 그대로 돌려줄 수 있다.
     */
    private void save(Long id, String mode, String hash, int m, int s,
                      List<double[]> path, List<RouteLeg> legs) {
        try {
            String pj = om.writeValueAsString(path), lj = om.writeValueAsString(legs);
            routeSaver.store(id, mode, hash, m, s, pj, lj);
        } catch (DataIntegrityViolationException e) {
            // 경쟁에서 졌다 — 이긴 쪽이 같은 내용을 넣었으니 잃을 것이 없다.
            log.debug("경로 저장이 경쟁에서 졌다(같은 결과가 이미 있다) itinerary={} mode={}", id, mode);
        } catch (Exception e) {
            log.warn("경로 저장 실패 itinerary={} mode={}: {}", id, mode, e.getMessage());
        }
    }

    /** 저장해 둔 것을 응답 모양으로 되돌린다. 못 읽으면 null 을 주어 다시 계산하게 한다. */
    private RouteResponse fromSaved(ItineraryRoute r, String mode, List<RouteStop> stops) {
        try {
            List<double[]> path = om.readValue(r.getPath(), new TypeReference<List<double[]>>() {});
            List<RouteLeg> legs = om.readValue(r.getLegs(), new TypeReference<List<RouteLeg>>() {});
            return new RouteResponse(r.getDistanceM(), r.getDurationS(), mode, stops, legs, path);
        } catch (Exception e) {
            log.warn("저장된 경로를 읽지 못했다 itinerary={} mode={}: {}",
                    r.getItineraryId(), mode, e.getMessage());
            return null;
        }
    }

    @Transactional
    public Detail create(Long userId, CreateRequest req) {
        // 필수 값 검증. 없으면 DB의 NOT NULL 제약에서 터져 SQL문이 그대로 응답에 실린다.
        if (req == null || req.title() == null || req.title().isBlank()) {
            throw new BadRequestException("ITINERARY_INVALID", "일정 제목은 필수입니다.");
        }
        if (req.startDate() == null || req.endDate() == null) {
            throw new BadRequestException("ITINERARY_INVALID", "여행 시작일과 종료일은 필수입니다.");
        }
        if (req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("ITINERARY_INVALID", "종료일은 시작일보다 앞설 수 없습니다.");
        }
        String sourceType = switch (req.sourceType() == null ? "" : req.sourceType()) {
            case "CURATED" -> "CURATED";
            case "AI" -> "AI";
            default -> "CUSTOM";
        };
        Itinerary saved = repo.save(new Itinerary(userId, req.title(), req.startDate(), req.endDate(),
                sourceType, sourceType.equals("CURATED") ? req.sourceCourseId() : null));

        // 추천 코스 복사 (CURATED): 코스 항목을 일지 항목으로 독립 복사
        if (sourceType.equals("CURATED") && req.sourceCourseId() != null) {
            List<CuratedCourseItem> courseItems = courseItemRepo.findByCourseIdOrderBySortOrderAsc(req.sourceCourseId());
            for (CuratedCourseItem ci : courseItems) {
                itemRepo.save(new ItineraryItem(saved.getId(), ci.getPerformanceId(), ci.getVenueId(),
                        req.startDate(), ci.getSortOrder(), ci.getRecommendedTime(), ci.getNote()));
            }
        }

        // AI 루트 저장: 미리보기의 방문지를 순서대로 담는다. 여러 날 코스는 항목마다 날짜가 다르다.
        if (sourceType.equals("AI") && req.items() != null) {
            if (req.items().size() > 20) {
                throw new BadRequestException("ITINERARY_INVALID", "한 번에 담을 수 있는 장소는 20곳까지예요.");
            }
            int order = 0;
            for (ItemRequest it : req.items()) {
                ContentTarget t = new ContentTarget(ContentType.from(it.targetType()), it.targetId());
                LocalDate visit = it.visitDate() == null ? req.startDate() : it.visitDate();
                if (visit.isBefore(req.startDate()) || visit.isAfter(req.endDate())) {
                    throw new BadRequestException("ITINERARY_INVALID", "방문 날짜가 여행 기간을 벗어났어요.");
                }
                itemRepo.save(new ItineraryItem(saved.getId(), t.performanceId(), t.venueId(),
                        visit, order++, it.plannedTime(), it.memo()));
            }
        }
        return get(userId, saved.getId());
    }

    @Transactional
    public Detail update(Long userId, Long id, UpdateRequest req) {
        Itinerary i = own(userId, id);
        i.update(req.title(), req.startDate(), req.endDate());
        return get(userId, id);
    }

    /**
     * 하루치 순서를 받은 차례대로 다시 매긴다.
     *
     * 보낸 목록이 그날 항목 **전체**와 정확히 같을 때만 받는다. 일부만 받으면 빠진 항목을
     * 몇 번에 둬야 할지 알 수 없고, 아무 데나 끼우면 사용자가 보던 순서와 어긋난다.
     * 화면이 낡은 목록을 들고 있을 때(다른 기기에서 장소를 지웠다거나) 여기서 걸린다.
     */
    @Transactional
    public List<ItemResponse> reorderItems(Long userId, Long id, ReorderRequest req) {
        own(userId, id);
        if (req == null || req.visitDate() == null || req.itemIds() == null || req.itemIds().isEmpty()) {
            throw new BadRequestException("ORDER_INVALID", "순서를 바꿀 장소가 없습니다.");
        }
        List<ItineraryItem> sameDay = itemRepo.findByItineraryIdOrderByVisitDateAscSortOrderAsc(id).stream()
                .filter(it -> req.visitDate().equals(it.getVisitDate()))
                .toList();
        Set<Long> given = new HashSet<>(req.itemIds());
        Set<Long> actual = new HashSet<>(sameDay.stream().map(ItineraryItem::getId).toList());
        if (given.size() != req.itemIds().size() || !given.equals(actual)) {
            throw new BadRequestException("ORDER_MISMATCH", "그날의 장소 목록과 맞지 않아요. 새로고침 후 다시 시도해주세요.");
        }
        Map<Long, ItineraryItem> byId = new HashMap<>();
        sameDay.forEach(it -> byId.put(it.getId(), it));
        int order = 0;
        List<ItemResponse> out = new ArrayList<>();
        for (Long itemId : req.itemIds()) {
            ItineraryItem item = byId.get(itemId);
            item.update(null, order++, null, null);
            out.add(toItemResponse(item));
        }
        return out;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        own(userId, id);
        itemRepo.deleteByItineraryId(id);
        repo.deleteById(id);
    }

    @Transactional
    public ItemResponse addItem(Long userId, Long id, ItemRequest req) {
        own(userId, id);
        if (req == null || req.visitDate() == null) {
            throw new BadRequestException("ITEM_INVALID", "방문 날짜는 필수입니다.");
        }
        ContentTarget t = new ContentTarget(ContentType.from(req.targetType()), req.targetId());
        ItineraryItem saved = itemRepo.save(new ItineraryItem(id, t.performanceId(), t.venueId(),
                req.visitDate(), req.sortOrder(), req.plannedTime(), req.memo()));
        return toItemResponse(saved);
    }

    /**
     * 카카오에서 고른 장소를 루트에 넣는다. 같은 사용자가 같은 곳을 이미 넣은 적이 있으면 그 장소를
     * 재사용하고, 없으면 그 사용자만 보는 장소로 만든다.
     */
    @Transactional
    public ItemResponse addPlaceItem(Long userId, Long id, PlaceItemRequest req) {
        own(userId, id);
        if (req == null || req.visitDate() == null) {
            throw new BadRequestException("ITEM_INVALID", "방문 날짜는 필수입니다.");
        }
        Venue venue = resolvePlaceVenue(userId, req);
        ItineraryItem saved = itemRepo.save(new ItineraryItem(id, null, venue.getId(),
                req.visitDate(), req.sortOrder(), null, null));
        return toItemResponse(saved);
    }

    private static String clip(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    @Transactional
    public ItemResponse updateItem(Long userId, Long id, Long itemId, ItemRequest req) {
        own(userId, id);
        ItineraryItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new NotFoundException("ITEM_NOT_FOUND", "일정 항목을 찾을 수 없습니다."));
        if (!item.getItineraryId().equals(id)) throw new BadRequestException("ITEM_MISMATCH", "해당 일지의 항목이 아닙니다.");
        // 장소 교체. 둘 다 와야 바꾼다 — 하나만 오면 무엇으로 바꾸라는 건지 알 수 없다.
        if (req.targetType() != null && req.targetId() != null) {
            ContentTarget t = new ContentTarget(ContentType.from(req.targetType()), req.targetId());
            requireExists(t);
            item.changeTarget(t.performanceId(), t.venueId());
        }
        item.update(req.visitDate(), req.sortOrder(), req.plannedTime(), req.memo());
        return toItemResponse(item);
    }

    @Transactional
    public void deleteItem(Long userId, Long id, Long itemId) {
        own(userId, id);
        itemRepo.findById(itemId).ifPresent(item -> {
            if (!item.getItineraryId().equals(id)) throw new BadRequestException("ITEM_MISMATCH", "해당 일지의 항목이 아닙니다.");
            itemRepo.delete(item);
        });
    }

    private Itinerary own(Long userId, Long id) {
        Itinerary i = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("ITINERARY_NOT_FOUND", "여행 일지를 찾을 수 없습니다."));
        if (!i.getUserId().equals(userId)) throw new BadRequestException("ITINERARY_FORBIDDEN", "본인 일지만 접근할 수 있습니다.");
        return i;
    }

    /** 카카오 장소를 내 장소로 바꿔 준다. 넣기와 교체가 같은 규칙을 쓰도록 한 군데에 둔다. */
    private Venue resolvePlaceVenue(Long userId, PlaceItemRequest req) {
        String kakaoId = req.kakaoPlaceId() == null ? "" : req.kakaoPlaceId().strip();
        String name = req.name() == null ? "" : req.name().strip();
        if (!kakaoId.matches("\\d{1,20}") || name.isEmpty() || name.length() > 100) {
            throw new BadRequestException("PLACE_INVALID", "장소 정보가 올바르지 않아요.");
        }
        if (req.lat() == null || req.lng() == null
                || req.lat() < 33 || req.lat() > 39 || req.lng() < 124 || req.lng() > 132) {
            throw new BadRequestException("PLACE_INVALID", "국내 장소만 추가할 수 있어요.");
        }
        return venueRepo.findFirstByCreatedByUserIdAndKakaoPlaceId(userId, kakaoId)
                .orElseGet(() -> venueRepo.save(Venue.privatePlace(userId, kakaoId, name,
                        clip(req.address(), 300), BigDecimal.valueOf(req.lat()), BigDecimal.valueOf(req.lng()),
                        KakaoLocalClient.toByeoriCategory(req.category()), clip(req.phone(), 30))));
    }

    /** 없는 곳으로 바꾸면 그 줄은 이름도 좌표도 없는 빈 칸이 된다. 미리 막는다. */
    private void requireExists(ContentTarget t) {
        boolean ok = t.targetType() == ContentType.VENUE
                ? venueRepo.existsById(t.targetId())
                : performanceRepo.existsById(t.targetId());
        if (!ok) throw new NotFoundException("TARGET_NOT_FOUND", "그 장소를 찾을 수 없어요.");
    }

    /**
     * 카카오에서 고른 장소를 이 자리로 바꾼다.
     *
     * 넣기(addPlaceItem)와 같은 규칙을 쓴다 — 같은 사용자가 전에 넣은 곳이면 그 장소를
     * 다시 쓰고, 처음이면 그 사용자만 보는 장소로 만든다.
     */
    @Transactional
    public ItemResponse replaceItemPlace(Long userId, Long id, Long itemId, PlaceItemRequest req) {
        own(userId, id);
        ItineraryItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new NotFoundException("ITEM_NOT_FOUND", "일정 항목을 찾을 수 없습니다."));
        if (!item.getItineraryId().equals(id)) throw new BadRequestException("ITEM_MISMATCH", "해당 일지의 항목이 아닙니다.");
        Venue venue = resolvePlaceVenue(userId, req);
        item.changeTarget(null, venue.getId());
        return toItemResponse(item);
    }

    private ItemResponse toItemResponse(ItineraryItem item) {
        ContentTarget t = ContentTarget.of(item.getPerformanceId(), item.getVenueId());
        String name = null, image = null;
        BigDecimal lat = null, lng = null;
        if (t.targetType() == ContentType.VENUE) {
            Venue v = venueRepo.findById(t.targetId()).orElse(null);
            if (v != null) { name = v.getName(); image = v.getImageUrl(); lat = v.getLat(); lng = v.getLng(); }
        } else {
            Performance p = performanceRepo.findById(t.targetId()).orElse(null);
            if (p != null) { name = p.getTitle(); image = p.getPosterImageUrl(); lat = p.getLat(); lng = p.getLng(); }
        }
        return new ItemResponse(item.getId(), t.targetType().name(), t.targetId(), name, image,
                item.getVisitDate(), item.getSortOrder(), item.getPlannedTime(), item.getMemo(),
                lat == null ? null : lat.doubleValue(), lng == null ? null : lng.doubleValue());
    }
}
