export interface ChatState {
  messages: ChatMessage[];
  loading: boolean;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  resourceIds?: string[];
}