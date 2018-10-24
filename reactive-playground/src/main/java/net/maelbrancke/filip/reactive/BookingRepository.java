package net.maelbrancke.filip.reactive;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Tailable;
import reactor.core.publisher.Flux;

public interface BookingRepository extends ReactiveMongoRepository<Booking, String> {
    @Tailable
    Flux<Booking> findBookingsBy();
}
