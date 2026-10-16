package com.byeori.domain.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.byeori.domain.itinerary.dto.ItineraryDtos.RouteLeg;
import com.byeori.domain.itinerary.dto.ItineraryDtos.RouteResponse;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.external.KakaoMobilityClient;
import com.byeori.global.external.OsrmClient;
import com.byeori.global.external.dto.KakaoRoute;
import com.byeori.global.external.dto.OsrmRoute;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 한 구간을 어떻게든 채운다.
 *
 * OSRM 컨테이너가 죽어도, 카카오가 쿼터를 다 써도, 루트 화면은 떠야 한다.
 * 지금보다 나빠지는 경우가 있으면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteFallbackTest {

    @Test
    @DisplayName("도보가 실패하면 자동차로 내려간다")
    void 도보_실패() {
        RouteLeg leg = ItineraryService.legOrFallback(
                null,                   // OSRM 실패
                new int[]{1500, 300},   // 카카오 성공
                1200, "WALK");
        assertThat(leg.mode()).isEqualTo("DRIVE");
        assertThat(leg.distance()).isEqualTo(1500);
    }

    @Test
    @DisplayName("둘 다 실패하면 직선이라도 준다 — 빈 화면보다 낫다")
    void 둘_다_실패() {
        RouteLeg leg = ItineraryService.legOrFallback(null, null, 1200, "WALK");
        assertThat(leg.mode()).isEqualTo("STRAIGHT");
        assertThat(leg.distance()).isEqualTo(1200);   // 직선거리
        assertThat(leg.duration()).isPositive();       // 걷는 속도로 어림한다
    }

    @Test
    @DisplayName("차로 가려던 구간은 차 속도로 어림한다 — 6km 를 71분으로 적지 않는다")
    void 직선은_의도한_속도로() {
        RouteLeg drive = ItineraryService.legOrFallback(null, null, 6000, "DRIVE");
        RouteLeg walk = ItineraryService.legOrFallback(null, null, 6000, "WALK");
        assertThat(drive.mode()).isEqualTo("STRAIGHT");
        assertThat(drive.duration()).isLessThan(walk.duration());
        // 시내 30km/h 어림 — 10~20분 사이에 들어와야 사람이 보고 납득한다.
        assertThat(drive.duration()).isBetween(600, 1200);
    }

    // ── 구간별 계산 전체 ─────────────────────────────

    static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Mock ItineraryRepository repo;
    @Mock ItineraryItemRepository itemRepo;
    @Mock VenueRepository venueRepo;
    @Mock KakaoMobilityClient mobilityClient;
    @Mock OsrmClient osrmClient;
    @Mock ItineraryRouteRepository routeRepo;
    @Mock ItineraryRouteSaver routeSaver;

    ItineraryService service;

    @BeforeEach
    void setUp() {
        service = new ItineraryService(repo, itemRepo, null, venueRepo, null,
                mobilityClient, osrmClient, routeRepo, routeSaver, new RouteProperties());

        Itinerary mine = new Itinerary(7L, "걷는 루트", DAY, DAY, "CUSTOM", null);
        ReflectionTestUtils.setField(mine, "id", 1L);
        when(repo.findById(1L)).thenReturn(Optional.of(mine));

        // 300m 쯤씩 떨어진 네 곳 — AUTO 면 모든 구간이 도보로 뽑힌다(기준 1000m).
        List<ItineraryItem> items = new ArrayList<>();
        double[] lats = {37.5600, 37.5630, 37.5660, 37.5690};
        for (int i = 0; i < lats.length; i++) {
            long venueId = 500L + i;
            Venue v = Venue.privatePlace(7L, String.valueOf(venueId), "장소" + i, null,
                    BigDecimal.valueOf(lats[i]), BigDecimal.valueOf(126.9800), "FOOD", null);
            ReflectionTestUtils.setField(v, "id", venueId);
            when(venueRepo.findById(venueId)).thenReturn(Optional.of(v));
            items.add(new ItineraryItem(1L, null, venueId, DAY, i, null, null));
        }
        when(itemRepo.findByItineraryIdOrderByVisitDateAscSortOrderAsc(1L)).thenReturn(items);
        when(routeRepo.findByItineraryIdAndMode(anyLong(), anyString())).thenReturn(Optional.empty());
    }

    private static OsrmRoute walkRoute() {
        return new OsrmRoute(320, 240,
                List.of(new double[]{37.5600, 126.9800}, new double[]{37.5630, 126.9800}));
    }

    private static KakaoRoute driveRoute(List<double[]> path) {
        return new KakaoRoute(400, 90, path, List.of());
    }

    @Test
    @DisplayName("OSRM 이 한 번 빗나가면 남은 구간은 묻지 않는다 — 멈춘 컨테이너가 요청을 몇 배로 늘리지 않게")
    void 요청_안_차단기() {
        // 멈춘 OSRM 은 구간마다 4초를 잡아먹는다. 세 구간이면 12초다. 그래서 한 번만 묻는다.
        when(osrmClient.walk(anyList())).thenReturn(null);
        when(mobilityClient.directions(anyList(), anyString())).thenReturn(driveRoute(
                List.of(new double[]{126.9800, 37.5600}, new double[]{126.9800, 37.5630})));

        RouteResponse res = service.route(7L, 1L, "AUTO");

        assertThat(res.legs()).hasSize(3);
        assertThat(res.legs()).allMatch(l -> "DRIVE".equals(l.mode()));
        verify(osrmClient, times(1)).walk(anyList());          // 세 구간인데 한 번만
        verify(mobilityClient, times(3)).directions(anyList(), anyString());
        assertThat(res.distance()).isEqualTo(1200);
        // 차단기가 걸린 결과는 저장하지 않는다 — OSRM 이 살아나면 저절로 나아야 한다.
        verify(routeSaver, never()).store(anyLong(), anyString(), anyString(),
                anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("OSRM 이 살아 있으면 가까운 구간은 모두 걷고, 그 결과만 저장한다")
    void 도보가_되면() {
        when(osrmClient.walk(anyList())).thenReturn(walkRoute());

        RouteResponse res = service.route(7L, 1L, "AUTO");

        assertThat(res.legs()).allMatch(l -> "WALK".equals(l.mode()));
        verify(osrmClient, times(3)).walk(anyList());
        verify(mobilityClient, never()).directions(anyList(), anyString());
        assertThat(res.mode()).isEqualTo("AUTO");
        // 이어지는 지점은 한 번만 들어간다 — 구간마다 2점이면 4점이 된다.
        assertThat(res.path()).hasSize(4);
        verify(routeSaver, times(1)).store(anyLong(), anyString(), anyString(),
                anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("직선으로 내려간 경로는 저장하지 않는다 — 장애가 캐시에 눌러앉으면 영원히 직선이 나온다")
    void 직선은_저장하지_않는다() {
        when(osrmClient.walk(anyList())).thenReturn(null);
        when(mobilityClient.directions(anyList(), anyString())).thenReturn(null);

        RouteResponse res = service.route(7L, 1L, "AUTO");

        assertThat(res.legs()).allMatch(l -> "STRAIGHT".equals(l.mode()));
        verify(routeSaver, never()).store(anyLong(), anyString(), anyString(),
                anyInt(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("카카오가 빈 경로를 주어도 터지지 않는다 — result_code 0 에 좌표가 없을 때가 있다")
    void 카카오_빈_경로() {
        // mode=DRIVE 라 OSRM 은 아예 묻지 않는다. 빈 좌표열만으로 터지는지를 본다.
        when(mobilityClient.directions(anyList(), anyString())).thenReturn(driveRoute(List.of()));

        RouteResponse res = service.route(7L, 1L, "DRIVE");

        assertThat(res.legs()).hasSize(3);
        assertThat(res.legs()).allMatch(l -> "DRIVE".equals(l.mode()));
        assertThat(res.path()).hasSize(4);   // 구간마다 직선 두 점, 이음점은 한 번만
    }

    @Test
    @DisplayName("저장이 경쟁에서 져도 경로는 돌려준다")
    void 저장_충돌() {
        when(osrmClient.walk(anyList())).thenReturn(walkRoute());
        // 다른 요청이 먼저 같은 줄을 넣은 상황. 같은 입력이면 같은 결과라 잃을 것이 없다.
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(routeSaver).store(anyLong(), anyString(), anyString(),
                        anyInt(), anyInt(), anyString(), anyString());

        RouteResponse res = service.route(7L, 1L, "AUTO");

        assertThat(res.legs()).hasSize(3);
        assertThat(res.legs()).allMatch(l -> "WALK".equals(l.mode()));
    }

    @Test
    @DisplayName("저장이 어떤 이유로 실패해도 경로는 돌려준다 — 교착·락 대기도 500 이 되면 안 된다")
    void 저장_실패_일반() {
        when(osrmClient.walk(anyList())).thenReturn(walkRoute());
        doThrow(new org.springframework.dao.CannotAcquireLockException("deadlock detected"))
                .when(routeSaver).store(anyLong(), anyString(), anyString(),
                        anyInt(), anyInt(), anyString(), anyString());

        assertThatCode(() -> service.route(7L, 1L, "AUTO")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("카카오 키가 없어도 걷는 루트는 나온다 — 미리 막지 않는다")
    void 카카오_키가_없어도() {
        when(mobilityClient.enabled()).thenReturn(false);
        when(mobilityClient.directions(anyList(), anyString())).thenReturn(null);
        when(osrmClient.walk(anyList())).thenReturn(walkRoute());

        RouteResponse res = service.route(7L, 1L, "WALK");

        assertThat(res.legs()).allMatch(l -> "WALK".equals(l.mode()));
        verify(mobilityClient, never()).directions(any(), anyString());
    }
}
