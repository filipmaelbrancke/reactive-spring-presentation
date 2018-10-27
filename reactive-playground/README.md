
#Test commands

start server
```
mvn spring-boot:run
```
(reactive) endpoint
```
http :8080/rents/schaarlift
```
load test endpoint to show difference between spring-mvc and spring-webflux version (show threads with VisualVM)
```
siege -c 100 -r 5 http://localhost:8080/rents/schaarlift
siege -c 200 -r 5 http://localhost:8080/rents/schaarlift
```
bookings endpoint
```
http :8080/bookings
```
add new bookings
```
http POST :8080/bookings refNo=testBooking
```
bookings server sent events

```
curl http://localhost:8080/bookingstream -H Accept:text/event-stream
```

