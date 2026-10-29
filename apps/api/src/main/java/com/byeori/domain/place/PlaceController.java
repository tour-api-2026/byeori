package com.byeori.domain.place;

import com.byeori.global.external.KakaoLocalClient;
import com.byeori.global.external.KakaoLocalClient.Place;
import com.byeori.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 벼리 DB 밖의 장소 찾기(카카오 로컬 검색 대행). 루트에 식당·카페를 직접 넣을 때 쓴다.
 * 카카오 키를 앱에 두지 않으려고 서버가 대신 부른다. 로그인 필수(검색 할당량 보호).
 */
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private final KakaoLocalClient kakao;

    /** category 는 벼리 분류로 옮긴 값(맛집·카페·문화), categoryName 은 카카오 원래 분류(표시용). */
    public record PlaceResult(String kakaoPlaceId, String name, String category, String categoryName,
                              String address, String phone, double lat, double lng) {
        static PlaceResult from(Place p) {
            return new PlaceResult(p.id(), p.name(), KakaoLocalClient.toByeoriCategory(p.categoryGroup()), lastSegment(p.categoryName()),
                    p.address(), p.phone(), p.lat(), p.lng());
        }
    }

    /**
     * 장소 검색. 한 쪽에 15건이고, 카카오는 전체 45건까지만 내준다.
     *
     * **page 를 주느냐에 따라 응답 모양이 다르다.**
     * <ul>
     *   <li>안 주면(기존) 그냥 배열 — 이미 깔려 있는 앱이 unwrap&lt;KakaoPlace[]&gt; 로 읽는다.
     *       OTA 를 안 받은 버전이 남아 있으므로 이 모양을 깨면 그쪽에서 장소 검색이 죽는다.</li>
     *   <li>주면 {items, end} — 더 받을 것이 있는지 함께 준다.</li>
     * </ul>
     *
     * 끝 판정은 우리가 세지 않고 카카오가 준 is_end 를 그대로 전한다. 45 라는 상한을 코드에
     * 적어 두면 카카오가 바꿨을 때 따라가지 못한다.
     */
    @GetMapping("/search")
    public ApiResponse<?> search(@RequestParam("query") String query,
                                 @RequestParam(name = "lat", required = false) Double lat,
                                 @RequestParam(name = "lng", required = false) Double lng,
                                 @RequestParam(name = "page", required = false) Integer page) {
        KakaoLocalClient.Page p = kakao.searchPage(query, lat, lng, page == null ? 1 : page);
        List<PlaceResult> items = p.places().stream().map(PlaceResult::from).toList();
        return ApiResponse.ok(page == null ? items : new SearchPage(items, p.end()));
    }

    /** 한 쪽의 결과. end 가 true 면 더 받을 것이 없다. */
    public record SearchPage(List<PlaceResult> items, boolean end) {}

    /** "음식점 > 한식 > 국밥" → "국밥" */
    private static String lastSegment(String s) {
        if (s == null || s.isBlank()) return null;
        String[] parts = s.split(">");
        return parts[parts.length - 1].strip();
    }
}
