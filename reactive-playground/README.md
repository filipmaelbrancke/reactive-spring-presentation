

```
http :8080/rents/schaarlift
siege -c 100 -r 5 http://localhost:8080/rents/schaarlift
siege -c 200 -r 5 http://localhost:8080/rents/schaarlift
http :8080/bookings
curl http://localhost:8080/bookingstream -H Accept:text/event-stream
```

```
mvn spring-boot:run
```