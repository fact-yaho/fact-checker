package com.yaho.factchecker.application.factcheck.controller;

import com.yaho.factchecker.application.factcheck.dto.request.FactCheckStartRequest;
import com.yaho.factchecker.application.factcheck.dto.response.FactCheckStartResponse;
import com.yaho.factchecker.application.factcheck.service.FactCheckOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/")
public class FactCheckController {

    private final FactCheckOrchestratorService factCheckOrchestratorService;

    @PostMapping("fact-checks")
    @ResponseStatus(HttpStatus.CREATED)
    public FactCheckStartResponse startFactCheck(
            @Valid @RequestBody FactCheckStartRequest request) {
        return factCheckOrchestratorService.start(request);
    }

}
