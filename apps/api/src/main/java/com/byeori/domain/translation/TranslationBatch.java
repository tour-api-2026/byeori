package com.byeori.domain.translation;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.i18n.Lang;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 번역이 없거나 이름이 바뀐 행을 찾아 번역해 저장한다.
 *
 * 매일 동기화 뒤에 한 번 돈다. **사용자 요청 경로에서는 절대 부르지 않는다** — 응답
 * 시간이 AI 에 묶인다.
 *
 * 처음 한 번은 장소 34,728 + 행사 12,633 을 전부 번역한다(언어당 약 $1). 그 뒤로는
 * 새로 들어온 행사만 주 670건쯤이라 월 몇십 원이다.
 */
@Component
@Slf4j
public class TranslationBatch {

    /**
     * 한 번에 AI 에 보내는 이름 수. 너무 크면 응답이 잘리고, 너무 작으면 호출이 잦다.
     * 100개는 응답에 15~20초가 걸려 OpenAiClient 의 읽기 제한(20초)에 걸렸다(행사 제목은
     * 더 길어 세 번 모두 시간 초과). 40개면 7초 안팎이라 여유가 있다.
     */
    private static final int CHUNK = 40;
    /** 정기 작업이 언어·종류별로 한 번에 처리할 최대 행 수. 하룻밤에 안 끝나도 다음 날 이어간다. */
    public static final int MAX_PER_RUN = 5_000;
    /**
     * 한 행이 한 언어에서 이만큼 번역 못 받으면 그 언어 큐에서 뺀다(V30 의 tr_fail_*).
     * 못 하는 행이 큐 맨 앞에 영원히 남아 새 행을 굶기는 것을 막는다. 3 이면 일시적 오답은
     * 사흘 밤 다시 시도해 보고, 구조적으로 못 하는 행만 걸러진다. 다시 시도하게 하려면
     * 해당 행의 tr_fail_* 를 0 으로 되돌린다.
     */
    public static final int MAX_ATTEMPTS = 3;

    private final VenueRepository venueRepo;
    private final PerformanceRepository perfRepo;
    private final TranslationClient client;
    private final TransactionTemplate tx;

    public TranslationBatch(VenueRepository venueRepo, PerformanceRepository perfRepo,
                            TranslationClient client, PlatformTransactionManager txm) {
        this.venueRepo = venueRepo;
        this.perfRepo = perfRepo;
        this.client = client;
        this.tx = new TransactionTemplate(txm);
    }

    /** 언어·종류별로 limit 행까지. 돌려주는 값은 실제로 저장한 번역 수. */
    public int run(int limit) {
        int total = 0;
        for (Lang lang : List.of(Lang.EN, Lang.JA, Lang.ZH)) {
            total += translateVenues(lang, limit);
            total += translatePerformances(lang, limit);
        }
        return total;
    }

    // 메서드 전체를 한 트랜잭션으로 묶지 않는다. AI 호출 내내 DB 연결을 잡고 있게 되고,
    // 묶음 하나가 터지면 앞에서 끝낸 번역까지 되돌아간다. 묶음마다 저장하고 커밋한다.
    int translateVenues(Lang lang, int limit) {
        List<Venue> todo = venueRepo.findNeedingTranslation(lang.name(), limit, MAX_ATTEMPTS);
        int done = 0;
        int unavailable = 0;
        List<Venue> failed = new ArrayList<>();
        for (List<Venue> chunk : partition(todo, CHUNK)) {
            Map<String, String> got;
            try {
                got = client.translate(lang, chunk.stream().map(Venue::getName).distinct().toList());
            } catch (TranslationClient.UnavailableException e) {
                // AI 가 안 닿은 것이지 행의 잘못이 아니다 — 실패로 세지 않고 다음 배치가 다시 집는다.
                unavailable += chunk.size();
                log.warn("장소 번역 호출 실패 lang={} 건수={} : {}", lang, chunk.size(), e.getMessage());
                continue;
            }
            List<Venue> changed = new ArrayList<>();
            for (Venue x : chunk) {
                String tr = got.get(x.getName());
                if (tr == null) { x.recordTranslationFailure(lang); failed.add(x); continue; }   // 해시를 안 찍어 다음 배치가 다시 집되, 실패 횟수가 쌓인다
                String h = hashOf(x.getName());
                // 해시가 어긋났다면 이름이 바뀐 것이다. 다른 언어의 옛 번역이 남아 있으면
                // 해시만 새로 찍힌 뒤 "끝난 행" 으로 보이므로 여기서 함께 비운다.
                if (!h.equals(x.getNameHash())) {
                    if (x.isHumanTranslated())
                        log.warn("사람 번역을 비운다(이름이 바뀜) 장소 id={} 번역 시점 해시={} 새 이름={}", x.getId(), x.getNameHash(), x.getName());
                    x.clearTranslations();
                }
                x.applyTranslation(lang, tr);
                x.markHash(h);
                changed.add(x);
            }
            if (!changed.isEmpty()) tx.executeWithoutResult(s -> venueRepo.saveAll(changed));
            done += changed.size();
        }
        if (!failed.isEmpty()) tx.executeWithoutResult(s -> venueRepo.saveAll(failed));
        reportRun("장소", lang, todo.size(), done, failed.size(), unavailable, venueRepo.countGivenUp(lang.name(), MAX_ATTEMPTS));
        return done;
    }

    int translatePerformances(Lang lang, int limit) {
        List<Performance> todo = perfRepo.findNeedingTranslation(lang.name(), limit, MAX_ATTEMPTS);
        int done = 0;
        int unavailable = 0;
        List<Performance> failed = new ArrayList<>();
        for (List<Performance> chunk : partition(todo, CHUNK)) {
            Map<String, String> got;
            try {
                got = client.translate(lang, chunk.stream().map(Performance::getTitle).distinct().toList());
            } catch (TranslationClient.UnavailableException e) {
                // AI 가 안 닿은 것이지 행의 잘못이 아니다 — 실패로 세지 않고 다음 배치가 다시 집는다.
                unavailable += chunk.size();
                log.warn("행사 번역 호출 실패 lang={} 건수={} : {}", lang, chunk.size(), e.getMessage());
                continue;
            }
            List<Performance> changed = new ArrayList<>();
            for (Performance x : chunk) {
                String tr = got.get(x.getTitle());
                if (tr == null) { x.recordTranslationFailure(lang); failed.add(x); continue; }   // 해시를 안 찍어 다음 배치가 다시 집되, 실패 횟수가 쌓인다
                String h = hashOf(x.getTitle());
                // 해시가 어긋났다면 이름이 바뀐 것이다. 다른 언어의 옛 번역이 남아 있으면
                // 해시만 새로 찍힌 뒤 "끝난 행" 으로 보이므로 여기서 함께 비운다.
                if (!h.equals(x.getNameHash())) {
                    if (x.isHumanTranslated())
                        log.warn("사람 번역을 비운다(제목이 바뀜) 행사 id={} 번역 시점 해시={} 새 제목={}", x.getId(), x.getNameHash(), x.getTitle());
                    x.clearTranslations();
                }
                x.applyTranslation(lang, tr);
                x.markHash(h);
                changed.add(x);
            }
            if (!changed.isEmpty()) tx.executeWithoutResult(s -> perfRepo.saveAll(changed));
            done += changed.size();
        }
        if (!failed.isEmpty()) tx.executeWithoutResult(s -> perfRepo.saveAll(failed));
        reportRun("행사", lang, todo.size(), done, failed.size(), unavailable, perfRepo.countGivenUp(lang.name(), MAX_ATTEMPTS));
        return done;
    }

    /**
     * 한 번 돈 결과를 남긴다. 예전에는 저장한 게 있을 때만 로그가 있어서, 아무것도 못 한 밤과
     * 할 일이 없던 밤이 똑같이 조용했다.
     */
    private void reportRun(String label, Lang lang, int picked, int done, int failedRows, int unavailable, long givenUp) {
        if (picked == 0 && givenUp == 0) return;   // 정말 할 일이 없던 밤
        if (done == 0) {
            log.warn("{} 번역 0건 lang={} 대상={} 모델이 못 한 행={} 호출 실패로 건너뜀={}", label, lang, picked, failedRows, unavailable);
        } else {
            log.info("{} 번역 lang={} {}건 (대상 {} 모델이 못 한 행 {} 호출 실패로 건너뜀 {})", label, lang, done, picked, failedRows, unavailable);
        }
        if (givenUp > 0) {
            log.warn("{} 번역 포기 상태 lang={} {}건 — {}번 연속 못 받아 큐에서 뺐다(tr_fail_* 를 0 으로 돌리면 다시 시도)",
                    label, lang, givenUp, MAX_ATTEMPTS);
        }
    }

    /** 이름의 sha256(16진수 64자). 이름이 바뀐 행만 다시 번역하려고 쓴다. */
    public static String hashOf(String name) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(name.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }

    private static <T> List<List<T>> partition(List<T> src, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < src.size(); i += size) out.add(src.subList(i, Math.min(i + size, src.size())));
        return out;
    }
}
