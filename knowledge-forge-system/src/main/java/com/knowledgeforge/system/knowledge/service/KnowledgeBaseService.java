package com.knowledgeforge.system.knowledge.service;

import com.knowledgeforge.core.entity.KnowledgeBase;
import com.knowledgeforge.core.shared.dto.PageResult;
import com.knowledgeforge.system.knowledge.dto.KnowledgeBaseDTO;
import com.knowledgeforge.system.knowledge.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Transactional
    public KnowledgeBase create(KnowledgeBaseDTO dto) {
        KnowledgeBase kb = KnowledgeBase.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .icon(dto.getIcon())
                .build();
        return knowledgeBaseRepository.save(kb);
    }

    public PageResult<KnowledgeBase> list(int page, int size) {
        Page<KnowledgeBase> result = knowledgeBaseRepository
                .findByDeletedFalse(PageRequest.of(page, size));
        return PageResult.of(result.getContent(), result.getTotalElements(), page, size);
    }

    public List<KnowledgeBase> listAll() {
        return knowledgeBaseRepository.findByDeletedFalse();
    }

    public KnowledgeBase getById(UUID id) {
        return knowledgeBaseRepository.findById(id)
                .filter(kb -> !kb.getDeleted())
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + id));
    }

    @Transactional
    public KnowledgeBase update(UUID id, KnowledgeBaseDTO dto) {
        KnowledgeBase kb = getById(id);
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setIcon(dto.getIcon());
        kb.setUpdatedAt(LocalDateTime.now());
        return knowledgeBaseRepository.save(kb);
    }

    @Transactional
    public void delete(UUID id) {
        KnowledgeBase kb = getById(id);
        kb.setDeleted(true);
        kb.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseRepository.save(kb);
    }
}
