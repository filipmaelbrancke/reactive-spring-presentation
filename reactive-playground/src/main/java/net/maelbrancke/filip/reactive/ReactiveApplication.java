package net.maelbrancke.filip.reactive;

import java.time.Duration;
import java.util.List;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class ReactiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveApplication.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(MongoOperations mongoOperations) {
        return args -> {
            mongoOperations.dropCollection(Booking.class);
            mongoOperations.createCollection(Booking.class, CollectionOptions.empty().capped().size(1000000).maxDocuments(100));
        };
    }


}

/*@Slf4j
@RestController
class RentController {

    @GetMapping("/rents/{machine}")
    Boolean canWeRent(@PathVariable String machine) throws InterruptedException {
        log.info("can we rent " + machine);
        TimeUnit.SECONDS.sleep(1);
        return false;
    }
}*/

@Slf4j
@RestController
class ReactiveRentController {

    @GetMapping("/rents/{machine}")
    Mono<Boolean> canWeRent(@PathVariable String machine) throws InterruptedException {
        log.info("can we rent " + machine);
        return Mono.delay(Duration.ofSeconds(1)).thenReturn(true);
    }
}

@RestController
class BookingController {

    private final BookingRepository bookingRepository;

    BookingController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /*@GetMapping("bookings")
    List<Booking> bookings() {
        return bookingRepository.findAll().collectList().block();
    }*/

    @GetMapping("bookings")
    Flux<Booking> bookings() {
        return bookingRepository.findAll().log();
    }

    //@GetMapping(value = "bookingstream", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
    @GetMapping(value = "bookingstream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<Booking> bookingsStream() {
        return bookingRepository.findBookingsBy().log();
    }

    @PostMapping("bookings")
    Flux<Booking> bookings(@RequestBody Flux<Booking> bookings) {
        return bookingRepository.insert(bookings).log();
    }


//    public static void main(String[] args) {
//        WebClient client = WebClient.create("http://localhost:8080");
//
//        Flux<Booking> bookings
//                = Flux.interval(Duration.ofSeconds(1)).map(i -> new Booking());
//
//        client
//                .post()
//                .uri("/bookings")
//                .contentType(MediaType.APPLICATION_STREAM_JSON)
//                .body(bookings, Booking.class)
//                .retrieve()
//                .bodyToFlux(Booking.class)
//                .blockLast();
//
//    }


}

@Slf4j
@Component
class Booker {

    private final BookingRepository bookingRepository;

    public Booker(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        log.info("started");

        Flux.interval(Duration.ofSeconds(10), Duration.ofSeconds(1))
            .map(i -> new Booking("test" + i))
            .flatMap(bookingRepository::save)
            .subscribe();
    }

}
