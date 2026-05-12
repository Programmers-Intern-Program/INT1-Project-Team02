package com.flodiback.api.project;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flodiback.domain.meeting.meetinglog.dto.ActionItemRequest;
import com.flodiback.domain.meeting.meetinglog.dto.UpdateContextRequest;
import com.flodiback.domain.meeting.meetinglog.service.ContextService;

@WebMvcTest(ProjectContextController.class)
class ProjectContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContextService contextService;

    @BeforeEach
    void setUp() {
        doNothing().when(contextService).updateContext(anyLong(), any());
    }

    @Test
    void updateContext_정상_요청_200() throws Exception {
        String body = objectMapper.writeValueAsString(new UpdateContextRequest(1L, "회의 요약입니다.", null, null, null));

        mockMvc.perform(put("/internal/v1/projects/1/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"));
    }

    @Test
    void updateContext_meetingId_null이면_400() throws Exception {
        String body = objectMapper.writeValueAsString(new UpdateContextRequest(null, "요약", null, null, null));

        mockMvc.perform(put("/internal/v1/projects/1/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateContext_summary_blank이면_400() throws Exception {
        String body = objectMapper.writeValueAsString(new UpdateContextRequest(1L, "  ", null, null, null));

        mockMvc.perform(put("/internal/v1/projects/1/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateContext_actionItem_assigneeName_blank이면_400() throws Exception {
        ActionItemRequest badItem = new ActionItemRequest("", "태스크 내용", null);
        String body = objectMapper.writeValueAsString(
                new UpdateContextRequest(1L, "요약", null, null, java.util.List.of(badItem)));

        mockMvc.perform(put("/internal/v1/projects/1/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateContext_actionItem_task_blank이면_400() throws Exception {
        ActionItemRequest badItem = new ActionItemRequest("담당자", "", null);
        String body = objectMapper.writeValueAsString(
                new UpdateContextRequest(1L, "요약", null, null, java.util.List.of(badItem)));

        mockMvc.perform(put("/internal/v1/projects/1/context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateContext_requestBody_없으면_400() throws Exception {
        mockMvc.perform(put("/internal/v1/projects/1/context").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
