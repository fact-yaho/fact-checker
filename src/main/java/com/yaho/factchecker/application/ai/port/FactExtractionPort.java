package com.yaho.factchecker.application.ai.port;

import com.yaho.factchecker.domain.ai.dto.request.FactExtractionRequest;
import com.yaho.factchecker.domain.ai.dto.response.FactExtractionResponse;

public interface FactExtractionPort {

    FactExtractionResponse extract(FactExtractionRequest request);
}
