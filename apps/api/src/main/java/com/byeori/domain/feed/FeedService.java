package com.byeori.domain.feed;

import com.byeori.domain.activity.ViewLog;
import com.byeori.domain.activity.ViewLogRepository;
import com.byeori.domain.itinerary.ItineraryItem;
import com.byeori.domain.itinerary.ItineraryItemRepository;
import com.byeori.domain.itinerary.ItineraryRepository;
import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.user.InterestCatalog;
import com.byeori.domain.user.UserInterest;
import com.byeori.domain.user.UserInterestRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.domain.wishlist.Wishlist;
import com.byeori.domain.wishlist.WishlistRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * '당신을 위한 추천'.
 *
 * X 의 for-you 파이프라인과 같은 네 단계를 쓴다 — 후보 모으기 → 걸러내기 → 점수 →
 * 다시 섞기. 다른 점은 3단계가 학습 랭커가 아니라 **설명 가능한 규칙**이라는 것뿐이다.
 * 사용자 17명, 상호작용 92건으로는 학습할 것이 없고, 규칙은 지금 바로 돈다.
 *
 * 점수 네 항을 곱하지 않고 더하는 이유는 FeedProperties 에 적었다.
 */
@Service
public class FeedService {

    /** 곧 시작할 행사로 볼 기간. 반년 뒤 공연을 오늘 권해도 할 수 있는 게 없다. */
    private static final int UPCOMING_DAYS = 30;
    /** 거리 점수가 절반이 되는 거리(km). 5km 면 같은 동네, 20km 면 다른 구. */
    private static final double HALF_DISTANCE_KM = 5.0;

    private final FeedProperties props;
    private final UserInterestRepository interestRepo;
    private final ViewLogRepository viewRepo;
    private final WishlistRepository wishRepo;
    private final ItineraryRepository itineraryRepo;
    private final ItineraryItemRepository itemRepo;
    private final VenueRepository venueRepo;
    private final PerformanceRepository performanceRepo;

    public FeedService(FeedProperties props, UserInterestRepository interestRepo, ViewLogRepository viewRepo,
                       WishlistRepository wishRepo, ItineraryRepository itineraryRepo,
                       ItineraryItemRepository itemRepo, VenueRepository venueRepo,
                       PerformanceRepository performanceRepo) {
        this.props = props;
        this.interestRepo = interestRepo;
        this.viewRepo = viewRepo;
        this.wishRepo = wishRepo;
        this.itineraryRepo = itineraryRepo;
        this.itemRepo = itemRepo;
        this.venueRepo = venueRepo;
        this.performanceRepo = performanceRepo;
    }

    @Transactional(readOnly = true)
    public List<FeedItem> forUser(Long userId, Double lat, Double lng, int size) {
        TasteProfile taste = profileOf(userId);
        Set<Long> seenVenues = venuesAlreadyInRoutes(userId);

        List<FeedItem> scored = new ArrayList<>();
        scored.addAll(scoreVenues(taste, seenVenues, lat, lng));
        scored.addAll(scorePerformances(taste, lat, lng));

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return spread(scored, size);
    }

    // ── 1. 취향 요약 ─────────────────────────────

    private TasteProfile profileOf(Long userId) {
        List<UserInterest> interests = interestRepo.findByUserId(userId);
        List<String> topics = pick(interests, InterestCatalog.TOPIC);
        Set<String> regions = new HashSet<>(pick(interests, InterestCatalog.REGION));
        TasteProfile taste = new TasteProfile(
                InterestCatalog.categoriesOf(topics), InterestCatalog.prefersPerformances(topics), regions);

        // 본 것·찜한 것·루트에 담은 것의 분류를 끌어모은다.
        taste.addViews(categoriesOfViews(viewRepo.findTop100ByUserIdOrderByViewedAtDesc(userId)));
        taste.addWishes(categoriesOfWishes(wishRepo.findByUserIdOrderByCreatedAtDesc(userId)));
        taste.addItinerary(categoriesOfVenues(venuesAlreadyInRoutes(userId)));
        return taste;
    }

    private List<String> categoriesOfViews(List<ViewLog> logs) {
        List<Long> venueIds = logs.stream().filter(v -> "VENUE".equals(v.getTargetType()))
                .map(ViewLog::getTargetId).distinct().toList();
        List<Long> perfIds = logs.stream().filter(v -> "PERFORMANCE".equals(v.getTargetType()))
                .map(ViewLog::getTargetId).distinct().toList();
        List<String> out = new ArrayList<>(categoriesOfVenues(new HashSet<>(venueIds)));
        performanceRepo.findAllById(perfIds).forEach(p -> out.add(p.getGenre()));
        return out;
    }

    private List<String> categoriesOfWishes(List<Wishlist> wishes) {
        Set<Long> venueIds = wishes.stream().map(Wishlist::getVenueId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> perfIds = wishes.stream().map(Wishlist::getPerformanceId).filter(java.util.Objects::nonNull).toList();
        List<String> out = new ArrayList<>(categoriesOfVenues(venueIds));
        performanceRepo.findAllById(perfIds).forEach(p -> out.add(p.getGenre()));
        return out;
    }

    private List<String> categoriesOfVenues(Set<Long> venueIds) {
        if (venueIds.isEmpty()) return List.of();
        return venueRepo.findAllById(venueIds).stream().map(Venue::getCategory).toList();
    }

    /** 이미 루트에 담은 장소. 취향 신호이면서 동시에 '다시 권하지 않을 것'이다. */
    private Set<Long> venuesAlreadyInRoutes(Long userId) {
        Set<Long> out = new HashSet<>();
        itineraryRepo.findByUserIdOrderByCreatedAtDesc(userId).forEach(i ->
                itemRepo.findByItineraryIdOrderByVisitDateAscSortOrderAsc(i.getId()).stream()
                        .map(ItineraryItem::getVenueId).filter(java.util.Objects::nonNull).forEach(out::add));
        return out;
    }

    // ── 2~3. 후보와 점수 ─────────────────────────────

    private List<FeedItem> scoreVenues(TasteProfile taste, Set<Long> exclude, Double lat, Double lng) {
        List<Venue> candidates = lat != null && lng != null
                ? venueRepo.searchNear(null, null, null, BigDecimal.valueOf(lat), BigDecimal.valueOf(lng),
                        PageRequest.of(0, props.getCandidatePerKind())).getContent()
                : venueRepo.search(null, null, null, PageRequest.of(0, props.getCandidatePerKind())).getContent();

        List<FeedItem> out = new ArrayList<>();
        for (Venue v : candidates) {
            if (exclude.contains(v.getId())) continue;   // 이미 루트에 담은 곳은 권하지 않는다
            double match = taste.match(v.getCategory());
            if (taste.inFavoriteRegion(v.getAddress())) match = Math.min(1, match + 0.3);
            double dist = distanceScore(lat, lng, v.getLat(), v.getLng());
            double pop = popularity(v.getReviewCount());
            // 장소는 임박도를 1로 본다 — 언제 가도 열려 있다. 이 항을 빼면 행사만 0.20 을
            // 더 받아 장소가 구조적으로 밀린다(실제로 추천 상위 10칸이 전부 공연이었다).
            double score = props.getTaste() * match + props.getDistance() * dist
                    + props.getImminence() + props.getPopularity() * pop;
            out.add(new FeedItem("VENUE", v.getId(), v.getName(), v.getImageUrl(), v.getCategory(),
                    num(v.getLat()), num(v.getLng()),
                    venueReason(taste, v, match, dist, lat, lng), score));
        }
        return out;
    }

    private List<FeedItem> scorePerformances(TasteProfile taste, Double lat, Double lng) {
        LocalDate today = LocalDate.now();
        List<Performance> candidates = performanceRepo.findUpcomingCandidates(
                today, today.plusDays(UPCOMING_DAYS), PageRequest.of(0, props.getCandidatePerKind()));

        List<FeedItem> out = new ArrayList<>();
        for (Performance p : candidates) {
            double match = Math.min(1, taste.match(p.getGenre()) + taste.performanceBonus());
            double dist = distanceScore(lat, lng, p.getLat(), p.getLng());
            double imminence = imminence(p, today);
            double pop = popularity(p.getReviewCount());
            double score = props.getTaste() * match + props.getDistance() * dist
                    + props.getImminence() * imminence + props.getPopularity() * pop;
            out.add(new FeedItem("PERFORMANCE", p.getId(), p.getTitle(), p.getPosterImageUrl(), p.getGenre(),
                    num(p.getLat()), num(p.getLng()), performanceReason(p, today, match), score));
        }
        return out;
    }

    /**
     * 가까울수록 1에 가깝다. 5km 에서 0.5, 20km 에서 0.2.
     *
     * 좌표가 없으면 0 이 아니라 0.5 를 준다. 0 을 주면 좌표 없는 항목이 영영 안 뜨는데,
     * 좌표가 없다는 건 '먼 곳'이 아니라 '모르는 곳'이다.
     */
    private double distanceScore(Double fromLat, Double fromLng, BigDecimal toLat, BigDecimal toLng) {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return 0.5;
        double km = haversineKm(fromLat, fromLng, toLat.doubleValue(), toLng.doubleValue());
        return HALF_DISTANCE_KM / (HALF_DISTANCE_KM + km);
    }

    /** 지금 하는 것이 가장 높고, 멀수록 낮다. */
    private double imminence(Performance p, LocalDate today) {
        LocalDate start = p.getStartDate();
        if (start == null) return 0.4;
        if (!start.isAfter(today)) return 1.0;                       // 이미 진행 중
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, start);
        if (days <= 7) return 0.8;
        if (days <= 30) return 0.5;
        return 0.2;
    }

    /** 리뷰 수를 0~1 로. log 를 쓰는 이유: 100개와 110개의 차이는 0개와 10개만큼 크지 않다. */
    private double popularity(Integer reviewCount) {
        int n = reviewCount == null ? 0 : reviewCount;
        return Math.min(1, Math.log1p(n) / Math.log(51));            // 리뷰 50개면 1.0
    }

    // ── 4. 다시 섞기 ─────────────────────────────

    /**
     * 같은 분류가 줄줄이 나오지 않게 뒤로 민다.
     *
     * 이게 없으면 클래식 공연 3,451건이 상위를 덮는다. vm-ranker 의 DPP 를 한 줄로 줄인 것이다.
     *
     * 종류(장소/행사)에도 같은 제한을 건다. 주변 장소가 34,728건이라 위치를 주면 상위가
     * 전부 장소로 채워져 행사가 한 건도 안 올라왔다 — 전통 공연을 보여주려는 앱에서
     * 그건 추천이 아니라 지도다.
     */
    private List<FeedItem> spread(List<FeedItem> sorted, int size) {
        List<FeedItem> out = new ArrayList<>();
        List<FeedItem> held = new ArrayList<>();
        String lastCategory = null, lastType = null;
        int categoryRun = 0, typeRun = 0;

        for (FeedItem item : sorted) {
            if (out.size() >= size) break;
            String c = item.category() == null ? "" : item.category();
            int cRun = c.equals(lastCategory) ? categoryRun : 0;
            int tRun = item.targetType().equals(lastType) ? typeRun : 0;
            if (cRun >= props.getSameCategoryRun() || tRun >= props.getSameTypeRun()) {
                held.add(item);
                continue;
            }
            out.add(item);
            categoryRun = cRun + 1; lastCategory = c;
            typeRun = tRun + 1; lastType = item.targetType();
        }
        // 밀어 둔 것으로 모자란 자리를 채운다. 다양성 때문에 목록이 짧아지면 안 된다.
        for (FeedItem item : held) {
            if (out.size() >= size) break;
            out.add(item);
        }
        return out;
    }

    // ── 왜 떴는지 ─────────────────────────────

    private String venueReason(TasteProfile taste, Venue v, double match, double dist, Double lat, Double lng) {
        if (lat != null && lng != null && v.getLat() != null && v.getLng() != null) {
            double km = haversineKm(lat, lng, v.getLat().doubleValue(), v.getLng().doubleValue());
            if (km <= 3) return String.format("내 주변 %.1fkm", km);
        }
        if (taste.inFavoriteRegion(v.getAddress())) return "관심 지역";
        if (match > 0.5 && v.getCategory() != null) return "관심 주제 · " + v.getCategory();
        return v.getCategory() == null ? "추천" : v.getCategory();
    }

    private String performanceReason(Performance p, LocalDate today, double match) {
        LocalDate start = p.getStartDate();
        if (start != null && !start.isAfter(today)) return "지금 진행중";
        if (start != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, start);
            if (days <= 7) return days == 0 ? "오늘 시작" : days + "일 뒤 시작";
        }
        if (match > 0.5 && p.getGenre() != null) return "관심 주제 · " + p.getGenre();
        return p.getGenre() == null ? "곧 열리는 행사" : p.getGenre();
    }

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static Double num(BigDecimal v) { return v == null ? null : v.doubleValue(); }

    private static List<String> pick(List<UserInterest> rows, String kind) {
        return rows.stream().filter(i -> kind.equals(i.getKind())).map(UserInterest::getCategory).toList();
    }
}
