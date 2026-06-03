package com.knowledgeforge.system.graph.controller;

import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.core.shared.dto.ApiResponse;
import com.knowledgeforge.core.shared.dto.PageResult;
import com.knowledgeforge.system.graph.dto.AddRelationRequest;
import com.knowledgeforge.system.graph.dto.EntityDetailDTO;
import com.knowledgeforge.system.graph.dto.GraphDTO;
import com.knowledgeforge.system.graph.dto.GraphNodeDTO;
import com.knowledgeforge.system.graph.service.GraphService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(SystemConstants.API_V1 + "/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    @GetMapping("/{kbId}")
    public ApiResponse<GraphDTO> getGraph(@PathVariable UUID kbId) {
        return ApiResponse.success(graphService.getGraph(kbId));
    }

    @GetMapping("/{kbId}/expand")
    public ApiResponse<List<String>> expandQuery(@PathVariable UUID kbId, @RequestParam String query) {
        return ApiResponse.success(graphService.expandQuery(kbId, query));
    }

    @DeleteMapping("/{kbId}")
    public ApiResponse<Void> clearGraph(@PathVariable UUID kbId) {
        graphService.clearGraph(kbId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{kbId}/relations")
    public ApiResponse<Boolean> addRelation(@PathVariable UUID kbId,
                                         @Valid @RequestBody AddRelationRequest request) {
        boolean result = graphService.addRelation(kbId, request.getSourceEntityName(),
                request.getTargetEntityName(), request.getRelationType(), request.getDescription());
        if (!result) {
            return ApiResponse.error(400, "实体不存在，请检查实体名称");
        }
        return ApiResponse.success(true);
    }

    @GetMapping("/entities")
    public ApiResponse<PageResult<GraphNodeDTO>> getEntities(
            @RequestParam UUID kbId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<GraphNodeDTO> entityPage = graphService.getEntities(kbId, page, size);
        PageResult<GraphNodeDTO> result = PageResult.of(
                entityPage.getContent(),
                entityPage.getTotalElements(),
                entityPage.getNumber(),
                entityPage.getSize());
        return ApiResponse.success(result);
    }

    @GetMapping("/entities/{name}")
    public ApiResponse<EntityDetailDTO> getEntityDetail(
            @PathVariable String name,
            @RequestParam UUID kbId) {
        EntityDetailDTO detail = graphService.getEntityDetail(kbId, name);
        if (detail == null) {
            return ApiResponse.error(404, "实体不存在: " + name);
        }
        return ApiResponse.success(detail);
    }

    @GetMapping("/subgraph")
    public ApiResponse<GraphDTO> getSubgraph(
            @RequestParam UUID kbId,
            @RequestParam String entityName) {
        GraphDTO subgraph = graphService.getSubgraph(kbId, entityName);
        return ApiResponse.success(subgraph);
    }

    @GetMapping("/search")
    public ApiResponse<List<GraphNodeDTO>> searchGraph(
            @RequestParam UUID kbId,
            @RequestParam String keyword) {
        List<GraphNodeDTO> results = graphService.searchGraph(kbId, keyword);
        return ApiResponse.success(results);
    }
}