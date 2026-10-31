package com.hackathon.podcast.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventContractTest {

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void withdrawalEventMatchesV1JsonSchema() throws Exception {
        GuestExcerptWithdrawnEvent event = new GuestExcerptWithdrawnEvent(
                UUID.fromString("1ac7c51a-5f7a-4b0a-9fc9-9ba4bef605f0"),
                GuestExcerptWithdrawnEvent.EVENT_TYPE,
                GuestExcerptWithdrawnEvent.EVENT_VERSION,
                Instant.parse("2026-10-27T09:30:00Z"),
                "demo-withdrawal-001",
                UUID.fromString("d76025ba-7021-47f0-ab83-4749a581fd61"),
                UUID.fromString("4ace15d0-2e39-488e-8bc1-4084a08d557a"),
                UUID.fromString("2a3f078d-471e-46bb-a4b7-23ad33300960"),
                UUID.fromString("f0f4e4bf-dff7-433c-91be-b8b151c47e42"),
                "22222222-2222-2222-2222-222222222222",
                "22222222-2222-2222-2222-222222222222",
                "I no longer consent to this excerpt being distributed.");

        JsonNode json = mapper.valueToTree(event);
        Set<ValidationMessage> errors = schema("guest-excerpt-withdrawn-v1.schema.json").validate(json);

        assertThat(errors).as(errors.toString()).isEmpty();
        assertThat(json.get("eventType").asText()).isEqualTo("GuestExcerptWithdrawn");
        assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
        for (String required : new String[]{"eventId", "occurredAt", "correlationId", "releaseId",
                "releaseScopeId", "episodeId", "excerptId", "guestId", "withdrawnBy", "reason"}) {
            assertThat(json.hasNonNull(required)).as("required field " + required).isTrue();
        }
        assertThat(json.has("transcript")).isFalse();
        assertThat(json.has("accessToken")).isFalse();
    }

    @Test
    void withdrawalEventWithoutReasonFailsSchema() throws Exception {
        Map<String, Object> incomplete = new LinkedHashMap<>();
        incomplete.put("eventId", UUID.randomUUID().toString());
        incomplete.put("eventType", "GuestExcerptWithdrawn");
        incomplete.put("eventVersion", 1);

        Set<ValidationMessage> errors = schema("guest-excerpt-withdrawn-v1.schema.json")
                .validate(mapper.valueToTree(incomplete));

        assertThat(errors).isNotEmpty();
    }

    @Test
    void withdrawalAcceptedResponseMatchesV1JsonSchema() throws Exception {
        Map<String, Object> response = Map.of(
                "eventId", "1ac7c51a-5f7a-4b0a-9fc9-9ba4bef605f0",
                "releaseId", "d76025ba-7021-47f0-ab83-4749a581fd61",
                "excerptId", "f0f4e4bf-dff7-433c-91be-b8b151c47e42",
                "status", "WITHDRAWN_ACCEPTED");

        Set<ValidationMessage> errors = schema("withdrawal-accepted-v1.schema.json")
                .validate(mapper.valueToTree(response));

        assertThat(errors).as(errors.toString()).isEmpty();
    }

    private JsonSchema schema(String fileName) throws Exception {
        JsonNode schemaNode = mapper.readTree(
                Path.of("..", "contracts", "kafka", fileName).toFile());
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);
    }
}
