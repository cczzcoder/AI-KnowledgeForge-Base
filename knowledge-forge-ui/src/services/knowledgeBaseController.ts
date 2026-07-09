import request from './request';
import type {
  ApiResponse,
  KnowledgeBase,
  KnowledgeBaseDTO,
  PageResult,
} from './typings.d';

export async function createKnowledgeBase(body: KnowledgeBaseDTO) {
  return request.post<ApiResponse<KnowledgeBase>>('/knowledge-bases', body);
}

export async function listKnowledgeBases(page = 0, size = 20) {
  return request.get<ApiResponse<PageResult<KnowledgeBase>>>(
    '/knowledge-bases',
    {
      params: { page, size },
    },
  );
}

export async function listAllKnowledgeBases() {
  return request.get<ApiResponse<KnowledgeBase[]>>('/knowledge-bases/all');
}

export async function getKnowledgeBase(id: string) {
  return request.get<ApiResponse<KnowledgeBase>>(`/knowledge-bases/${id}`);
}

export async function updateKnowledgeBase(id: string, body: KnowledgeBaseDTO) {
  return request.put<ApiResponse<KnowledgeBase>>(
    `/knowledge-bases/${id}`,
    body,
  );
}

export async function deleteKnowledgeBase(id: string) {
  return request.delete<ApiResponse<null>>(`/knowledge-bases/${id}`);
}
