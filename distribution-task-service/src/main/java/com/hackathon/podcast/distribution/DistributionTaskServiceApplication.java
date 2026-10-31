package com.hackathon.podcast.distribution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication
public class DistributionTaskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributionTaskServiceApplication.class, args);
    }
}
