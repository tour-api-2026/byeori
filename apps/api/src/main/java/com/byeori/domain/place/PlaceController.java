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

    @GetMapping("/search")
    public ApiResponse<List<PlaceResult>> search(@RequestParam("query") String query,
                                                 @RequestParam(name = "lat", required = false) Double lat,
                                                 @RequestParam(name = "lng", required = false) Double lng) {
        return ApiResponse.ok(kakao.search(query, lat, lng).stream().map(PlaceResult::from).toList());
    }

    /** "음식점 > 한식 > 국밥" → "국밥" */
    private static String lastSegment(String s) {
        if (s == null || s.isBlank()) return null;
        String[] parts = s.split(">");
        return parts[parts.length - 1].strip();
    }
}
