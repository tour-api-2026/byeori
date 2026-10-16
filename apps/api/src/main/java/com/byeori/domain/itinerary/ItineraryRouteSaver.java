package com.byeori.domain.itinerary;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계산해 둔 경로를 쓰는 자리. 저장만 한다.
 *
 * 왜 서비스 밖에 따로 두는가. 저장은 **별도 트랜잭션**이어야 한다.
 * 같은 루트를 동시에 열면 둘 다 저장본을 못 찾고 둘 다 INSERT 해서 한쪽이 기본키 충돌을
 * 본다. 이때 실패한 문장 하나가 트랜잭션 전체를 못 쓰게 만드는데, 그 트랜잭션이 경로를
 * 돌려주려는 요청의 것이면 커밋에서 다시 터져 500 이 된다. 충돌뿐 아니라 교착(deadlock),
 * 락 대기 초과처럼 미리 다 헤아릴 수 없는 실패도 똑같이 트랜잭션을 오염시킨다.
 *
 * REQUIRES_NEW 로 끊으면 그 오염이 이 안에서 끝난다. 부르는 쪽은 예외를 삼키고 이미
 * 손에 든 경로를 돌려주면 된다 — 트랜잭션 상태를 따져 볼 필요가 없다.
 *
 * 자기 클래스 안에서 부르면 프록시를 지나지 않아 새 트랜잭션이 열리지 않는다. 그래서
 * 빈을 나눠 둔다.
 */
@Component
public class ItineraryRouteSaver {

    private final ItineraryRouteRepository repo;

    public ItineraryRouteSaver(ItineraryRouteRepository repo) {
        this.repo = repo;
    }

    /**
     * (루트, 모드) 한 줄을 쓴다. 있으면 덮어쓰고 없으면 넣는다.
     *
     * 바로 flush 한다. 커밋에서 터지면 예외가 DataIntegrityViolationException 이 아니라
     * UnexpectedRollbackException 으로 바뀌어, 부르는 쪽이 "경쟁에서 졌다"를 알아볼 수 없다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(Long itineraryId, String mode, String stopsHash,
                      int distanceM, int durationS, String pathJson, String legsJson) {
        ItineraryRoute saved = repo.findByItineraryIdAndMode(itineraryId, mode).orElse(null);
        if (saved != null) {
            saved.update(stopsHash, distanceM, durationS, pathJson, legsJson);
            return;
        }
        repo.saveAndFlush(new ItineraryRoute(itineraryId, mode, stopsHash,
                distanceM, durationS, pathJson, legsJson));
    }
}
