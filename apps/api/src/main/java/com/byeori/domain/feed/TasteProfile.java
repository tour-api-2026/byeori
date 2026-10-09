package com.byeori.domain.feed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 이 사람이 무엇을 좋아하는지, 분류별 무게로 요약한 것.
 *
 * 두 군데서 모은다.
 *
 * ① **고른 관심사** — 기록이 하나도 없어도 바로 쓸 수 있다. 사용자가 17명이고 조회 기록을
 *    이제 막 켠 지금, 사실상 이쪽이 전부다. 그래서 무게를 크게 준다.
 * ② **실제 행동** — 조회·찜·루트에 담기. 말보다 행동이 정확하지만 쌓이는 데 시간이 걸린다.
 *
 * 둘을 더해 분류별 점수를 만들고 최댓값으로 나눠 0~1 로 맞춘다. 정규화하지 않으면 기록이
 * 많은 사람일수록 취향 항만 커져 거리·임박도가 묻힌다.
 */
public class TasteProfile {

    /** 고른 관심사 한 건의 무게. 행동 한 건보다 크게 본다 — 지금은 이것밖에 없다. */
    private static final double INTEREST = 3.0;
    private static final double VIEW = 1.0;
    private static final double WISH = 2.0;
    private static final double ITINERARY = 2.5;

    private final Map<String, Double> weights = new HashMap<>();
    private final boolean likesPerformances;
    private final Set<String> regions;

    public TasteProfile(Set<String> interestCategories, boolean likesPerformances, Set<String> regions) {
        this.likesPerformances = likesPerformances;
        this.regions = regions;
        interestCategories.forEach(c -> add(c, INTEREST));
    }

    public void addViews(List<String> categories) { categories.forEach(c -> add(c, VIEW)); }
    public void addWishes(List<String> categories) { categories.forEach(c -> add(c, WISH)); }
    public void addItinerary(List<String> categories) { categories.forEach(c -> add(c, ITINERARY)); }

    private void add(String category, double w) {
        if (category == null || category.isBlank()) return;
        weights.merge(category, w, Double::sum);
    }

    public boolean isEmpty() { return weights.isEmpty(); }

    /** 이 분류가 내 취향에 얼마나 맞는지, 0~1. */
    public double match(String category) {
        if (category == null || weights.isEmpty()) return 0;
        double max = weights.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        return weights.getOrDefault(category, 0.0) / max;
    }

    /** '공연·관람'을 고른 사람에게는 행사를 조금 더 민다. */
    public double performanceBonus() { return likesPerformances ? 0.15 : 0; }

    /** 관심 지역에 있는 곳인지. 주소 앞마디로 본다 — '서울 종로구 …' 의 '서울'. */
    public boolean inFavoriteRegion(String address) {
        if (address == null || regions.isEmpty()) return false;
        return regions.stream().anyMatch(address::contains);
    }

    public Set<String> topCategories() { return weights.keySet(); }
}
