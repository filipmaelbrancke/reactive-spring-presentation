package net.maelbrancke.filip.reactive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.embedded.EmbeddedMongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@SpringBootApplication(exclude={EmbeddedMongoAutoConfiguration.class})
public class ReactiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveApplication.class, args);
    }



    @Bean
    CommandLineRunner commandLineRunner(MongoTemplate mongoTemplate) {
        return args -> {
            mongoTemplate.dropCollection(Booking.class);
            mongoTemplate.createCollection(Booking.class, CollectionOptions.empty().capped().size(1000000).maxDocuments(100));
        };
    }


}


/*@Slf4j
@RestController
class RentController {

    @GetMapping("/rents/{machine}")
    Boolean canWeRent(@PathVariable String machine) throws InterruptedException {
        log.info("can we rent " + machine);
        TimeUnit.SECONDS.sleep(1);     //  == Thread.sleep(1 second);
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

    @GetMapping("bookings")
     Flux<Booking> bookings() {

            return bookingRepository.findAll();
    }












    @GetMapping(value = "bookingstream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<Booking> bookingsStream() {
        return bookingRepository.findBookingsBy();
    }











    @PostMapping("bookings")
    Flux<Booking> bookings(@RequestBody Flux<Booking> bookings) {
        return bookingRepository.insert(bookings).log();
    }




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

        Flux.interval(Duration.ofSeconds(10), Duration.ofSeconds(5))
            .map(i -> new Booking("test" + i))
            .flatMap(bookingRepository::save)
            .subscribe();
    }

}
