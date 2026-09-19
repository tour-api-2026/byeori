package com.byeori.global.external.dto;

/**
 * areaBasedSyncList2 항목. 공사가 로컬 저장용으로 제공하는 동기화 오퍼레이션이다.
 *
 * areaBasedList2 와 달리 {@code modifiedtime} 이후 변경분만 받을 수 있고, 응답에
 * {@code showflag}(노출 여부)가 실려 온다. 공사에서 내린 콘텐츠를 우리 쪽에서도
 * 내리려면 이 값이 필요하다 — 목록 조회만으로는 "사라졌다"를 알 수 없다.
 */
public record TourSyncItem(
        TourItem item,
        /** 1 = 노출, 0 = 미노출(공사에서 내림). null 이면 판단 불가라 노출로 본다. */
        String showFlag,
        /** yyyyMMddHHmmss. 다음 동기화 커서를 잡는 데 쓴다. */
        String modifiedTime
) {
    public boolean visible() {
        return !"0".equals(showFlag);
    }
}
