package com.n4d3sh1k4.solution_archive_service.controller;

import com.n4d3sh1k4.solution_archive_service.service.RecognitionUserDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.autoconfigure.web.DataWebAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(excludeAutoConfiguration = DataWebAutoConfiguration.class, properties = "app.test.webmvc-config=enabled")
@Import(RecognitionUserDataController.class)
@AutoConfigureMockMvc(addFilters = true)
class RecognitionUserDataControllerTest {

    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TASK_ID = "650e8400-e29b-41d4-a716-446655440000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecognitionUserDataService recognitionService;

    @Test
    void history_missingHeader_returns400() throws Exception {
        mockMvc.perform(get("/equation/user/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.error.message", containsString("X-User-Id")));

        verify(recognitionService, never()).getUserHistory(any());
    }

    @Test
    void history_withHeader_returns200() throws Exception {
        when(recognitionService.getUserHistory(UUID.fromString(USER_ID))).thenReturn(List.of());

        mockMvc.perform(get("/equation/user/history").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void statistic_missingHeader_returns400() throws Exception {
        mockMvc.perform(get("/equation/user/statistic"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void delete_missingHeader_returns400() throws Exception {
        mockMvc.perform(delete("/equation/user/" + TASK_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

        verify(recognitionService, never()).deleteTask(any(), any());
    }

    @Test
    void errorResponseBody_isLoggedWithCodeAndMessage() throws Exception {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(com.n4d3sh1k4.solution_archive_service.advice.ErrorResponseLogger.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get("/equation/user/history"))
                    .andExpect(status().isBadRequest());
        } finally {
            logger.detachAppender(appender);
        }

        org.junit.jupiter.api.Assertions.assertFalse(appender.list.isEmpty());
        String msg = appender.list.get(0).getFormattedMessage();
        org.junit.jupiter.api.Assertions.assertTrue(
                msg.contains("/equation/user/history") && msg.contains("400") && msg.contains("BAD_REQUEST"),
                "unexpected log line: " + msg);
    }
}
