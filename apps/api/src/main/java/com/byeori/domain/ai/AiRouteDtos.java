package com.byeori.domain.ai;

import java.time.LocalDate;
import java.util.List;

/** AI 루트 생성 요청/응답 DTO 모음 */
public final class AiRouteDtos {
    private AiRouteDtos() {}

    /**
     * 생성 조건. 중심 좌표는 앱의 지역 칩(또는 내 위치)에서 온다.
     * regenerate=true 면 같은 조건의 저장된 결과를 쓰지 않고 새로 만든다.
     *
     * note      - 칩으로 못 고르는 요청("아이와 함께", "많이 걷지 않게"). 100자까지.
     * previous  - 앞서 만든 코스. 이게 있으면 "이렇게 바꿔 주세요"(다듬기) 요청으로 본다.
     */
    public record GenerateRequest(
            Double lat, Double lng, String areaName,
            List<String> categories, LocalDate date, Boolean regenerate,
            String note, List<PreviousStop> previous) {}

    /** 다듬기 요청에서 넘어오는 현재 코스의 방문지. 그대로 두는 칸은 추천 이유도 유지한다. */
    public record PreviousStop(int slot, String targetType, Long targetId, String reason) {}

    /**
     * 방문지 한 곳. targetType/targetId 는 일정 항목 저장 형식과 같다.
     * slot 은 하루 틀의 칸 번호 — 다듬기 때 이 번호로 칸을 짝지어야 순서가 어긋나지 않는다.
     */
    public record Stop(
            int slot, String targetType, Long targetId, String name, String category,
            String imageUrl, Double lat, Double lng, String time, String reason) {}

    /** 저장 전 미리보기. remainingToday 는 오늘 남은 생성 횟수. */
    public record Preview(
            String title, String summary, LocalDate date, List<Stop> stops, int remainingToday) {}

    /** 앱이 버튼을 보여줄지, 오늘 몇 번 남았는지. */
    public record Status(boolean enabled, Integer remainingToday) {}
}
