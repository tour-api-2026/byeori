package com.byeori.domain.translation;

import java.util.concurrent.Executor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 뒤에서 도는 일을 **부른 스레드에서 바로** 실행한다.
 *
 * DetailTranslator 는 번역을 Executor 에 던지고 곧바로 돌아온다. 테스트가 결과를 보려면
 * 끝나기를 기다려야 하는데, 잠깐 자거나 폴링하면 느리거나 가끔 깨진다. 같은 스레드에서
 * 실행하면 request() 가 돌아온 시점에 이미 끝나 있어 기다릴 것이 없다.
 *
 * 스프링 부트의 TaskExecutionAutoConfiguration 은 Executor 빈이 이미 있으면 물러나므로,
 * 이 설정을 import 한 테스트에서는 이것이 그 자리를 대신한다.
 */
@TestConfiguration
public class SameThreadExecutor {

    @Bean
    Executor applicationTaskExecutor() {
        return Runnable::run;
    }
}
