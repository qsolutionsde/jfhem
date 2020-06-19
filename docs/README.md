# jFHEM

This is a Java interface to [FHEM Home Automation system](https://fhem.de).

It provides access to FHEM via two protocols:

- Telnet (event-based)
- HTTP(S) (polling)

These classes could be used separately.

Furthermore, this SpringBoot micro service represents also a gateway

- an AMQP-based interface (both events and commands)
- a REST-based interface 

## Configuration

You need to place `application.yml`:

````yaml
fhemgateway:
  telnet:
    hosts:
      - host: fully.qualified.host
      - host: second.qualified.host
  http:
    hosts:
      - url: http://myfhemserver:8083/fhem
  amqp:
    queue: fhem-commandqueue
    exchange: amq.topic

spring:
  rabbitmq:
    host: rabbitmq.server
    port: 5672
    username: jfhem
    password: 1234567890
    template:
      exchange: amq.topic
      retry:
        enabled: true
        initial-interval: 5000
        max-attempts: 100
        max-interval: 60000
        multiplier: 1.5
````
