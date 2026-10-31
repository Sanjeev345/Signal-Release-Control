package com.hackathon.podcast.distribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hackathon.podcast.contracts.GuestExcerptWithdrawnEvent;
import com.hackathon.podcast.distribution.domain.DistributedEdit;
import com.hackathon.podcast.distribution.domain.DistributionTask;
import com.hackathon.podcast.distribution.domain.EditExcerpt;
import com.hackathon.podcast.distribution.domain.ExcerptGuest;
import com.hackathon.podcast.distribution.repository.DistributedEditRepository;
import com.hackathon.podcast.distribution.repository.DistributionTaskRepository;
import com.hackathon.podcast.distribution.repository.EditExcerptRepository;
import com.hackathon.podcast.distribution.repository.ExcerptGuestRepository;
import com.hackathon.podcast.distribution.repository.ProcessedEventRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.flyway.locations=classpath:db/migration",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class WithdrawalKafkaIntegrationTest {

    private static final UUID EPISODE_ID = UUID.fromString("2a3f078d-471e-46bb-a4b7-23ad33300960");
    private static final UUID TARGET_EXCERPT = UUID.fromString("f0f4e4bf-dff7-433c-91be-b8b151c47e42");
    private static final UUID OTHER_EXCERPT = UUID.fromString("6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410");
    private static final UUID EVENT_ID = UUID.fromString("1ac7c51a-5f7a-4b0a-9fc9-9ba4bef605f0");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.2"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    DistributionTaskRepository taskRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Autowired
    DistributedEditRepository editRepository;

    @Autowired
    EditExcerptRepository editExcerptRepository;

    @Autowired
    ExcerptGuestRepository excerptGuestRepository;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${app.kafka.topic}")
    String topic;

    DistributedEdit editA;
    DistributedEdit editB;
    DistributedEdit editC;
    DistributedEdit editD;
    DistributedEdit editE;

    @BeforeEach
    void seedLocalDistributionIndex() {
        taskRepository.deleteAll();
        processedEventRepository.deleteAll();
        editExcerptRepository.deleteAll();
        editRepository.deleteAll();
        excerptGuestRepository.deleteAll();

        excerptGuestRepository.save(reference(TARGET_EXCERPT));
        excerptGuestRepository.save(reference(OTHER_EXCERPT));

        editA = editRepository.save(DistributedEdit.create(
                "edit-youtube-001", EPISODE_ID, "YouTube", "yt-full", Instant.parse("2026-10-21T12:00:00Z")));
        editB = editRepository.save(DistributedEdit.create(
                "edit-spotify-001", EPISODE_ID, "Spotify", "spotify-full", Instant.parse("2026-10-21T12:05:00Z")));
        editC = editRepository.save(DistributedEdit.create(
                "edit-social-001", EPISODE_ID, "Social", "social-teaser", Instant.parse("2026-10-21T12:10:00Z")));
        editD = editRepository.save(DistributedEdit.create(
                "edit-tiktok-001", EPISODE_ID, "TikTok", "tiktok-clip", Instant.parse("2026-10-21T12:15:00Z")));
        editE = editRepository.save(DistributedEdit.create(
                "edit-website-001", EPISODE_ID, "Website", "site-embed", Instant.parse("2026-10-21T12:20:00Z")));

        editExcerptRepository.save(EditExcerpt.link(editA.getId(), TARGET_EXCERPT));
        editExcerptRepository.save(EditExcerpt.link(editA.getId(), OTHER_EXCERPT));
        editExcerptRepository.save(EditExcerpt.link(editB.getId(), TARGET_EXCERPT));
        editExcerptRepository.save(EditExcerpt.link(editC.getId(), OTHER_EXCERPT));
        editExcerptRepository.save(EditExcerpt.link(editD.getId(), TARGET_EXCERPT));
        editExcerptRepository.save(EditExcerpt.link(editD.getId(), OTHER_EXCERPT));
        editExcerptRepository.save(EditExcerpt.link(editE.getId(), OTHER_EXCERPT));
    }

    @Test
    void consumesGuestExcerptWithdrawnAndPersistsThreeTasksIdempotently() throws Exception {
        ContainerTestUtils.waitForAssignment(
                listenerRegistry.getListenerContainer("withdrawal-consumer"), 3);

        GuestExcerptWithdrawnEvent event = new GuestExcerptWithdrawnEvent(
                EVENT_ID, "GuestExcerptWithdrawn", 1, Instant.parse("2026-10-27T09:30:00Z"),
                "demo-withdrawal-001",
                UUID.fromString("d76025ba-7021-47f0-ab83-4749a581fd61"),
                UUID.fromString("4ace15d0-2e39-488e-8bc1-4084a08d557a"),
                EPISODE_ID, TARGET_EXCERPT,
                "22222222-2222-2222-2222-222222222222",
                "22222222-2222-2222-2222-222222222222",
                "I no longer consent to this excerpt.");
        String payload = objectMapper.writeValueAsString(event);

        kafkaTemplate.send(topic, TARGET_EXCERPT.toString(), payload).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(taskRepository.countByExcerptId(TARGET_EXCERPT)).isEqualTo(3));

        List<DistributionTask> tasks = taskRepository.findByExcerptIdOrderByCreatedAtDesc(TARGET_EXCERPT);
        assertThat(tasks).extracting(DistributionTask::getEditId)
                .containsExactlyInAnyOrder(editA.getId(), editB.getId(), editD.getId())
                .doesNotContain(editC.getId(), editE.getId());
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.getStatus().name()).isEqualTo("OPEN");
            assertThat(task.getPriority().name()).isEqualTo("HIGH");
            assertThat(task.getTaskType().name()).isEqualTo("EXCERPT_REMOVAL");
        });
        assertThat(processedEventRepository.existsById(EVENT_ID)).isTrue();

        kafkaTemplate.send(topic, TARGET_EXCERPT.toString(), payload).get(10, TimeUnit.SECONDS);
        Thread.sleep(2000);

        assertThat(taskRepository.countByExcerptId(TARGET_EXCERPT)).isEqualTo(3);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    void malformedEventIsRetriedAndDeadLetteredWithoutTaskWrites() throws Exception {
        String malformed = "{\"eventType\":\"GuestExcerptWithdrawn\"}";
        kafkaTemplate.send(topic, TARGET_EXCERPT.toString(), malformed).get(10, TimeUnit.SECONDS);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic + ".DLT"));
            String received = null;
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
            while (received == null && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (malformed.equals(record.value())) {
                        received = record.value();
                    }
                }
            }
            assertThat(received).as("malformed event should be routed to the DLT").isEqualTo(malformed);
        }

        assertThat(taskRepository.count()).isZero();
        assertThat(processedEventRepository.count()).isZero();
    }

    private ExcerptGuest reference(UUID excerptId) {
        ExcerptGuest reference = new ExcerptGuest();
        reference.setExcerptId(excerptId);
        reference.setEpisodeId(EPISODE_ID);
        reference.setGuestId("22222222-2222-2222-2222-222222222222");
        return reference;
    }
}
