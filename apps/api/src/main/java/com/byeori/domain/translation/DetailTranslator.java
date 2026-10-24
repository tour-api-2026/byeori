package com.byeori.domain.translation;

import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.i18n.Lang;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 장소 상세의 긴 설명을 **화면 뒤에서** 번역해 저장한다.
 *
 * 전에는 상세 요청 안에서 번역이 끝나기를 기다렸다. 그게 운영에서 이렇게 깨져 있었다 —
 * 긴 설명(경복궁 1,262자)은 8초 제한 안에 번역이 끝나지 않아 매번 끊겼고, 끊기면 저장할
 * 것이 없으니 **다음에 열 때도 똑같이 8.5초를 쓰고 또 실패했다.** 외국어로 열면 8.5초를
 * 기다린 끝에 한국어 설명이 나왔고, 34,729곳 중 번역이 저장된 곳은 1곳(짧아서 운 좋게
 * 끝난 것)뿐이었다. 하루 500건 상한도 전부 실패에 쓰였다.
 *
 * 그래서 기다리지 않는다. 지금 여는 사람에게는 한국어를 바로 주고, 번역은 뒤에서 돌려
 * 저장한다. 다음에 여는 사람부터 번역된 글을 본다. 기다리는 사람이 없으니 시간 제한을
 * 넉넉히 둘 수 있어(OpenAiClient 의 longTextHttp) 긴 글도 끝까지 번역된다.
 *
 * 첫 사람이 한국어를 보는 것은 손해가 아니다. 전에는 **모두가** 영원히 한국어를 봤다.
 */
@Component
@Slf4j
public class DetailTranslator {

    private final VenueRepository repo;
    private final TranslationClient client;
    private final DetailTranslationCap cap;
    private final Executor executor;
    private final TransactionTemplate tx;

    /**
     * 같은 장소·언어를 동시에 여러 번 번역하지 않게 막는다. 인기 있는 장소는 여러 사람이
     * 몇 초 안에 함께 열 수 있는데, 그때마다 번역을 걸면 같은 글을 여러 번 사고 상한도
     * 그만큼 빨리 닳는다. 끝나면 지운다.
     *
     * 서버가 한 대라 메모리로 충분하다(DetailTranslationCap 과 같은 가정).
     */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Executor 는 **이름으로** 받는다. 타입만 쓰면 모호하다 — @EnableScheduling 이 만드는
     * taskScheduler 도 Executor 라서 후보가 둘이 된다.
     *
     * 테스트는 같은 이름의 빈으로 같은 스레드에서 바로 실행하는 것을 끼워, 뒤에서 도는 일이
     * 끝나기를 기다리지 않게 한다(SameThreadExecutor).
     */
    public DetailTranslator(VenueRepository repo, TranslationClient client, DetailTranslationCap cap,
                            @Qualifier("applicationTaskExecutor") Executor executor,
                            PlatformTransactionManager txm) {
        this.repo = repo;
        this.client = client;
        this.cap = cap;
        this.executor = executor;
        this.tx = new TransactionTemplate(txm);
    }

    /**
     * 번역을 걸어 둔다. **곧바로 돌아온다 — 부르는 쪽은 결과를 받지 못한다.**
     *
     * 걸러내는 일(중복·상한)은 여기서 바로 한다. 큐에 넣고 나서 거르면 사람이 몰릴 때
     * 할 일 없는 작업이 큐에 쌓인다.
     */
    public void request(long venueId, Lang lang, String koText) {
        if (lang == Lang.KO || koText == null || koText.isBlank()) return;

        String key = venueId + ":" + lang;
        if (!inFlight.add(key)) return;                 // 이미 같은 번역이 돌고 있다
        if (!cap.tryAcquire()) {                        // 오늘 몫을 다 썼다 — 내일 다시 걸린다
            inFlight.remove(key);
            return;
        }

        executor.execute(() -> {
            try {
                translateAndSave(venueId, lang, koText);
            } catch (RuntimeException e) {
                // 삼킨다. 화면은 이미 한국어를 받아 갔고, 다음에 열 때 다시 걸린다.
                log.warn("설명 번역 실패 장소 id={} lang={} : {}", venueId, lang, e.toString());
            } finally {
                inFlight.remove(key);
            }
        });
    }

    private void translateAndSave(long venueId, Lang lang, String koText) {
        // 거는 사이에 다른 요청이 이미 저장했을 수 있다. 번역은 돈이 드니 한 번 더 본다.
        Venue before = repo.findById(venueId).orElse(null);
        if (before == null) return;
        String already = before.descIn(lang);
        if (already != null && !already.isBlank()) return;

        String tr = client.translateText(lang, koText);
        if (tr == null || tr.isBlank()) return;         // 실패 — 저장하지 않으면 다음에 다시 시도된다

        // 낡은 사본을 통째로 저장하면 그 사이 바뀐 평점 등을 덮으므로 다시 읽어 설명만 바꾼다.
        tx.executeWithoutResult(st -> repo.findById(venueId).ifPresent(fresh -> {
            fresh.applyDescription(lang, tr);
            repo.save(fresh);
        }));
    }
}
