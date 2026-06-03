import request from './request';
import type { ApiResponse, ChatMessageDTO, ConversationCreateDTO, ConversationDTO, PageResult } from './typings.d';

export async function createConversation(body: ConversationCreateDTO) {
  return request.post<ApiResponse<ConversationDTO>>('/conversations', body);
}

export async function listConversations(page = 0, size = 20) {
  return request.get<ApiResponse<PageResult<ConversationDTO>>>('/conversations', {
    params: { page, size },
  });
}

export async function getConversation(id: string) {
  return request.get<ApiResponse<ConversationDTO>>(`/conversations/${id}`);
}

export async function getMessages(conversationId: string) {
  return request.get<ApiResponse<ChatMessageDTO[]>>(`/conversations/${conversationId}/messages`);
}

export async function deleteConversation(id: string) {
  return request.delete<ApiResponse<null>>(`/conversations/${id}`);
}