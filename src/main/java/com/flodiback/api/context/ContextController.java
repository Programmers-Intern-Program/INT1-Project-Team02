package com.flodiback.api.context;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flodiback.domain.meeting.meetinglog.dto.ContextResponse;
import com.flodiback.domain.meeting.meetinglog.service.ContextService;
import com.flodiback.global.rsData.RsData;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/v1/meetings")
@RequiredArgsConstructor
public class ContextController {

    private final ContextService contextService;

    @GetMapping("/{meetingId}/context")
    public ResponseEntity<RsData<ContextResponse>> getContext(
            @PathVariable Long meetingId, @RequestParam(required = false) String question) {
        ContextResponse response = contextService.assemble(meetingId, question);
        return ResponseEntity.ok(RsData.of("200-1", "컨텍스트 조회 성공.", response));
    }
}
