import request from './request';
import type { ApiResponse } from './typings.d';

// ==================== 类型定义 ====================

export interface KnowledgeCardDTO {
  id: string;
  kbId: string;
  conversationId?: string;
  messageId?: string;
  title: string;
  content: string;
  category: 'CONCEPT' | 'FACT' | 'RULE' | 'INSIGHT';
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  entityType?: string;
  sourceContext?: string;
  reviewerNote?: string;
  vectorized: boolean;
  graphEntityId?: string;
  createdAt: string;
}

export interface ExtractionRequest {
  kbId: string;
  conversationId: string;
  maxCards: number;
}

export interface ExtractionResult {
  cards: KnowledgeCardDTO[];
  messagesAnalyzed: number;
  summary: string;
}

export interface CardPageResult {
  items: KnowledgeCardDTO[];
  total: number;
  page: number;
  size: number;
}

export interface ReviewRequest {
  action: 'APPROVED' | 'REJECTED';
  note?: string;
  category?: string;
  entityType?: string;
}

export interface BatchReviewRequest {
  kbId: string;
  cardIds: string[];
  review: ReviewRequest;
}

// ==================== API 方法 ====================

/** 从对话中提取知识卡片 */
export async function extractFromConversation(
  data: ExtractionRequest,
): Promise<ApiResponse<ExtractionResult>> {
  return request.post('/knowledge-cards/extract', data);
}

/** 分页查询知识卡片 */
export async function listCards(params: {
  kbId: string;
  status?: string;
  page?: number;
  size?: number;
}): Promise<ApiResponse<CardPageResult>> {
  return request.get('/knowledge-cards', { params });
}

/** 获取待审核数量 */
export async function pendingCount(kbId: string): Promise<ApiResponse<{ count: number }>> {
  return request.get('/knowledge-cards/pending-count', { params: { kbId } });
}

/** 审核单张卡片 */
export async function reviewCard(
  id: string,
  data: ReviewRequest,
): Promise<ApiResponse<KnowledgeCardDTO>> {
  return request.put(`/knowledge-cards/${id}/review`, data);
}

/** 批量审核 */
export async function batchReview(data: BatchReviewRequest): Promise<ApiResponse<{ reviewed: number }>> {
  return request.post('/knowledge-cards/batch-review', data);
}

/** 删除卡片 */
export async function deleteCard(id: string): Promise<ApiResponse<null>> {
  return request.delete(`/knowledge-cards/${id}`);
}