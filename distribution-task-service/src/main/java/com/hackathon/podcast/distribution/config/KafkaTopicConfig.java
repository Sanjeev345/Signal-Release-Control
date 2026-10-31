package com.hackathon.podcast.distribution.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic withdrawalEvents(@Value("${app.kafka.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic withdrawalDeadLetter(@Value("${app.kafka.topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic taskNotifications(
            @Value("${app.kafka.notification-topic:soa.s14.t04.notifications}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
