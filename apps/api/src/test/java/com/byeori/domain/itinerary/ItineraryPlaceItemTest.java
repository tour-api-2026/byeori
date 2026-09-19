package com.byeori.domain.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.byeori.domain.itinerary.dto.ItineraryDtos.ItemResponse;
import com.byeori.domain.itinerary.dto.ItineraryDtos.PlaceItemRequest;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 카카오에서 고른 장소를 루트에 넣기 — 개인 장소로 저장하고, 다시 고르면 재사용한다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItineraryPlaceItemTest {

    static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Mock ItineraryRepository repo;
    @Mock ItineraryItemRepository itemRepo;
    @Mock VenueRepository venueRepo;

    ItineraryService service;

    @BeforeEach
    void setUp() {
        service = new ItineraryService(repo, itemRepo, null, venueRepo, null, null);
        Itinerary mine = new Itinerary(7L, "내 루트", DAY, DAY, "CUSTOM", null);
        ReflectionTestUtils.setField(mine, "id", 1L);
        when(repo.findById(1L)).thenReturn(Optional.of(mine));
        when(venueRepo.save(any(Venue.class))).thenAnswer(i -> {
            Venue v = i.getArgument(0);
            ReflectionTestUtils.setField(v, "id", 500L);
            return v;
        });
        when(venueRepo.findById(500L)).thenAnswer(i -> Optional.ofNullable(saved));
        when(itemRepo.save(any(ItineraryItem.class))).thenAnswer(i -> i.getArgument(0));
    }

    Venue saved;

    static PlaceItemRequest req(String kakaoId, double lat) {
        return new PlaceItemRequest(kakaoId, "명동교자 본점", "서울 중구 명동10길 29", "FD6", "02-776-5348",
                lat, 126.985, DAY, 3);
    }

    @Test
    void 처음_고른_곳은_나만_보는_장소로_저장한다() {
        when(venueRepo.findFirstByCreatedByUserIdAndKakaoPlaceId(7L, "10332413")).thenReturn(Optional.empty());
        ArgumentCaptor<Venue> cap = ArgumentCaptor.forClass(Venue.class);

        service.addPlaceItem(7L, 1L, req("10332413", 37.5625));

        verify(venueRepo).save(cap.capture());
        saved = cap.getValue();
        assertThat(saved.getVisibility()).isEqualTo("PRIVATE");   // 지도·검색·목록에 나오지 않는다
        assertThat(saved.getSource()).isEqualTo("KAKAO");
        assertThat(saved.getCategory()).isEqualTo("맛집");         // FD6 → 맛집
        assertThat(saved.getCreatedByUserId()).isEqualTo(7L);
    }

    @Test
    void 같은_곳을_다시_고르면_재사용한다() {
        Venue existing = Venue.privatePlace(7L, "10332413", "명동교자 본점", null,
                BigDecimal.valueOf(37.56), BigDecimal.valueOf(126.98), "맛집", null);
        ReflectionTestUtils.setField(existing, "id", 500L);
        saved = existing;
        when(venueRepo.findFirstByCreatedByUserIdAndKakaoPlaceId(7L, "10332413")).thenReturn(Optional.of(existing));

        ItemResponse res = service.addPlaceItem(7L, 1L, req("10332413", 37.5625));

        verify(venueRepo, never()).save(any());
        assertThat(res.targetId()).isEqualTo(500L);
        assertThat(res.lat()).isEqualTo(37.56);
    }

    @Test
    void 해외_좌표나_이상한_ID는_거절한다() {
        assertThatThrownBy(() -> service.addPlaceItem(7L, 1L, req("10332413", 48.85)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.addPlaceItem(7L, 1L, req("abc'--", 37.56)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 남의_루트에는_넣을_수_없다() {
        assertThatThrownBy(() -> service.addPlaceItem(8L, 1L, req("10332413", 37.56)))
                .isInstanceOf(BadRequestException.class);
        verify(venueRepo, never()).save(any());
    }
}
