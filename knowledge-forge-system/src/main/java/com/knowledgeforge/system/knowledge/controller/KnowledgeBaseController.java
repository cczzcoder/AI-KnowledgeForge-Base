package com.knowledgeforge.system.knowledge.controller;

import com.knowledgeforge.core.entity.KnowledgeBase;
import com.knowledgeforge.core.shared.constant.SystemConstants;
import com.knowledgeforge.core.shared.dto.ApiResponse;
import com.knowledgeforge.core.shared.dto.PageResult;
import com.knowledgeforge.system.knowledge.dto.KnowledgeBaseDTO;
import com.knowledgeforge.system.knowledge.dto.KnowledgeBaseResetRequest;
import com.knowledgeforge.system.knowledge.service.KnowledgeBaseResetService;
import com.knowledgeforge.system.knowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(SystemConstants.API_V1 + "/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBaseResetService knowledgeBaseResetService;

    @PostMapping
    public ApiResponse<KnowledgeBase> create(@Valid @RequestBody KnowledgeBaseDTO dto) {
        return ApiResponse.success(knowledgeBaseService.create(dto));
    }

    @GetMapping
    public ApiResponse<PageResult<KnowledgeBase>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(knowledgeBaseService.list(page, Math.min(size, SystemConstants.MAX_PAGE_SIZE)));
    }

    @GetMapping("/all")
    public ApiResponse<List<KnowledgeBase>> listAll() {
        return ApiResponse.success(knowledgeBaseService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBase> getById(@PathVariable UUID id) {
        return ApiResponse.success(knowledgeBaseService.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBase> update(@PathVariable UUID id,
                                              @Valid @RequestBody KnowledgeBaseDTO dto) {
        return ApiResponse.success(knowledgeBaseService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        knowledgeBaseService.delete(id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/reset")
    public ApiResponse<KnowledgeBaseResetService.ResetCleanupStats> hardResetAll() {
        return ApiResponse.success(knowledgeBaseResetService.hardDeleteAllKnowledgeBases());
    }

    @PostMapping(value = "/reset-and-reseed", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeBaseResetService.ResetSeedResponse> resetAndReseed(
            @Valid @ModelAttribute KnowledgeBaseResetRequest request) {
        return ApiResponse.success(knowledgeBaseResetService.resetAndReseed(request));
    }
}
