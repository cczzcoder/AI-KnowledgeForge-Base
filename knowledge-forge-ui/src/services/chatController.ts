import request from './request';
import type { ApiResponse, ChatRequest, ChatResponse } from './typings.d';

const API_BASE = '/api/v1';

export async function ragChat(body: ChatRequest) {
  return request.post<ApiResponse<ChatResponse>>('/chat/rag', body);
}

export async function simpleChat(body: ChatRequest) {
  return request.post<ApiResponse<ChatResponse>>('/chat/simple', body);
}

export async function ragChatStream(
  body: ChatRequest,
  onChunk: (text: string) => void,
  signal?: AbortSignal,
  onMetadata?: (metadata: { conversationId: string; sources: import('./typings.d').SourceDTO[] }) => void,
): Promise<void> {
  const response = await fetch(`${API_BASE}/chat/rag/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  });

  if (!response.ok) {
    throw new Error(`SSE 请求失败: ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('浏览器不支持流式读取');
  }

  const decoder = new TextDecoder();
  let buffer = '';
  let currentEvent = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';
    for (const line of lines) {
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        const data = line.slice(5).trim();
        if (currentEvent === 'metadata') {
          try {
            const parsed = JSON.parse(data);
            onMetadata?.(parsed);
          } catch {
            // ignore parse errors
          }
        } else {
          onChunk(data);
        }
      }
    }
  }
}

export async function submitFeedback(messageId: string, feedback: string) {
  return request.patch<ApiResponse<null>>(`/chat/messages/${messageId}/feedback`, { feedback });
}