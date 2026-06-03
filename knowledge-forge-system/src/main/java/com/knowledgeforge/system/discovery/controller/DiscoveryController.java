package com.knowledgeforge.system.discovery.controller;

import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.core.shared.dto.ApiResponse;
import com.knowledgeforge.system.discovery.dto.KnowledgeGapDTO;
import com.knowledgeforge.system.discovery.dto.LearningPathDTO;
import com.knowledgeforge.system.discovery.dto.RecommendationDTO;
import com.knowledgeforge.system.discovery.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(SystemConstants.API_V1 + "/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @GetMapping("/gaps")
    public ApiResponse<List<KnowledgeGapDTO>> getKnowledgeGaps(@RequestParam UUID kbId) {
        return ApiResponse.success(discoveryService.detectKnowledgeGaps(kbId));
    }

    @GetMapping("/recommendations")
    public ApiResponse<List<RecommendationDTO>> getRecommendations(@RequestParam UUID kbId) {
        return ApiResponse.success(discoveryService.getRecommendations(kbId));
    }

    @GetMapping("/learning-path")
    public ApiResponse<LearningPathDTO> getLearningPath(@RequestParam UUID kbId,
                                                         @RequestParam String topic) {
        return ApiResponse.success(discoveryService.getLearningPath(kbId, topic));
    }
}