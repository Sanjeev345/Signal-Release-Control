package com.hackathon.podcast.excerpt;

import com.hackathon.podcast.excerpt.config.ApiProblemWriter;
import com.hackathon.podcast.excerpt.config.SecurityConfig;
import com.hackathon.podcast.excerpt.service.ExcerptService;
import com.hackathon.podcast.excerpt.web.EpisodeController;
import com.hackathon.podcast.excerpt.web.dto.EpisodeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EpisodeController.class)
@Import({SecurityConfig.class, ApiProblemWriter.class})
@TestPropertySource(properties = {
        "app.security.issuer=http://localhost:8180/realms/podcast-demo",
        "app.security.jwk-set-uri=http://localhost:8180/realms/podcast-demo/protocol/openid-connect/certs"
})
class ExcerptSecurityTest {

    private static final String PRODUCER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String GUEST_ID = "22222222-2222-2222-2222-222222222222";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ExcerptService excerptService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void guestCannotCreateEpisode() throws Exception {
        mockMvc.perform(post("/api/excerpts/episodes")
                        .with(jwt().jwt(j -> j.subject(GUEST_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ava tries producer-only\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void producerCanCreateEpisode() throws Exception {
        UUID id = UUID.randomUUID();
        when(excerptService.createEpisode(any(), eq(PRODUCER_ID)))
                .thenReturn(new EpisodeResponse(id, "ep-test", "Designing for Trust", PRODUCER_ID,
                        null, Instant.now()));

        mockMvc.perform(post("/api/excerpts/episodes")
                        .with(jwt().jwt(j -> j.subject(PRODUCER_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_podcastProducer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Designing for Trust\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.producerId").value(PRODUCER_ID));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/excerpts/episodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No token\"}"))
                .andExpect(status().isUnauthorized());
    }
}
