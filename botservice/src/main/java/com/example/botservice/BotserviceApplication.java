package com.example.botservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.context.annotation.Import(com.example.sharedlib.messaging.KafkaRpcConfiguration.class)
@SpringBootApplication
public class BotserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BotserviceApplication.class, args);
    }

}
