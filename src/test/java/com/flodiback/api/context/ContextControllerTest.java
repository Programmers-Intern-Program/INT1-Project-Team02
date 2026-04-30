package com.flodiback.api.context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.flodiback.domain.meeting.meetinglog.dto.ContextResponse;
import com.flodiback.domain.meeting.meetinglog.service.ContextService;

@WebMvcTest(ContextController.class)
class ContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContextService contextService;

    private ContextResponse stubResponse;

    @BeforeEach
    void setUp() {
        stubResponse = ContextResponse.noProject(Collections.emptyList());
        given(contextService.assemble(anyLong(), any())).willReturn(stubResponse);
    }

    @Test
    void getContext_정상_요청_200() throws Exception {
        mockMvc.perform(get("/internal/v1/meetings/1/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.shortTerm").exists());
    }

    @Test
    void getContext_question_파라미터_없어도_200() throws Exception {
        mockMvc.perform(get("/internal/v1/meetings/1/context")).andExpect(status().isOk());
    }

    @Test
    void getContext_question_파라미터_있어도_200() throws Exception {
        mockMvc.perform(get("/internal/v1/meetings/1/context").param("question", "이번 회의 결론이 뭐야?"))
                .andExpect(status().isOk());
    }
}
