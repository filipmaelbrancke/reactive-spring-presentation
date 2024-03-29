package net.maelbrancke.filip.reactive;


import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class ReactiveApplicationTests {

    Flux<String> randomClubs() {
        return Flux
            .just("FCB", "Roma", "Chelsea", "Manchester", "Bayern");
    }

    Flux<String> randomPlayers() {
        return Flux
            .just("Nainggolan", "Hazard", "Lukaku");
    }

    @Test
    public void footballTest() throws Exception {
            Flux<String> newPlayersInNewClubs =
                    randomClubs()
                    .skipLast(1)
                    .takeLast(3)
                    .zipWith(randomPlayers(), (club, player) -> player + " in " + club);


            StepVerifier.create(newPlayersInNewClubs)
                    .expectNext("Nainggolan in Roma")
                    .expectNext("Hazard in Chelsea")
                    .expectNext("Lukaku in Manchester")
                    .verifyComplete();
    }




    Mono<String> canWeRent(String decision, long days) {
        return Mono.delay(Duration.ofDays(days)).map(i -> decision);
    }

    @Disabled
    @Test
    public void longRunningOperationTest() throws Exception {

        Mono<String> client1 = canWeRent("no!", 1000);
        Mono<String> client2 = canWeRent("no *f* way!!", 500);
        Mono<String> client3 = canWeRent("sure :D", 300);

        Flux<String> theQuickest =
            client1
                .mergeWith(client2)
                .mergeWith(client3)
                .filter(decision -> !decision.contains("no"));

        StepVerifier.create(theQuickest)
            .expectNext("sure :D")
            .verifyComplete();

    }

    @Test
    public void fastLongRunningTest() throws Exception {

        StepVerifier.withVirtualTime(() -> canWeRent("no", 10))
            .expectSubscription()
            .expectNoEvent(Duration.ofDays(10))
            .expectNext("no")
            .verifyComplete();
    }

    @Disabled
    @Test
    public void justShowingSomething() throws InterruptedException {
        Mono.just("test")
                .delayElement(Duration.of(10, ChronoUnit.SECONDS))
                .doOnNext(s -> System.out.println("first output"))
                .subscribe();

        System.out.println("second output");

        Thread.sleep(10000);
    }

}
