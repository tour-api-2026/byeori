package com.byeori.domain.ai;

import java.time.LocalDate;
import java.util.List;

/** AI 루트 생성 요청/응답 DTO 모음 */
public final class AiRouteDtos {
    private AiRouteDtos() {}

    /**
     * 생성 조건. 중심 좌표는 앱의 지역 칩(또는 내 위치)에서 온다.
     * regenerate=true 면 같은 조건의 저장된 결과를 쓰지 않고 새로 만든다.
     */
    public record GenerateRequest(
            Double lat, Double lng, String areaName,
            List<String> categories, LocalDate date, Boolean regenerate) {}

    /** 방문지 한 곳. targetType/targetId 는 일정 항목 저장 형식과 같다. */
    public record Stop(
            String targetType, Long targetId, String name, String category,
            String imageUrl, Double lat, Double lng, String time, String reason) {}

    /** 저장 전 미리보기. remainingToday 는 오늘 남은 생성 횟수. */
    public record Preview(
            String title, String summary, LocalDate date, List<Stop> stops, int remainingToday) {}

    /** 앱이 버튼을 보여줄지, 오늘 몇 번 남았는지. */
    public record Status(boolean enabled, Integer remainingToday) {}
}
