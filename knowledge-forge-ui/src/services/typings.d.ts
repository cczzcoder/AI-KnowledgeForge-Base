declare namespace API {
  type AssistantMessage = {
    messageType?: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL';
    metadata?: Record<string, unknown>;
    toolCalls?: ToolCall[];
    media?: Media[];
    text?: string;
  };

  type AuthVO = {
    username?: string;
    token?: string;
    roles?: string[];
  };

  type BaseResponseAuthVO = {
    code?: number;
    data?: AuthVO;
    message?: string;
  };

  type BaseResponseBoolean = {
    code?: number;
    data?: boolean;
    message?: string;
  };

  type BaseResponseInteger = {
    code?: number;
    data?: number;
    message?: string;
  };

  type BaseResponseLong = {
    code?: number;
    data?: number;
    message?: string;
  };

  type BaseResponseString = {
    code?: number;
    data?: string;
    message?: string;
  };

  type BaseResponseListKnowledgeBaseVO = {
    code?: number;
    data?: KnowledgeBaseVO[];
    message?: string;
  };

  type BaseResponseListSimpleBaseVO = {
    code?: number;
    data?: SimpleBaseVO[];
    message?: string;
  };

  type BaseResponsePageDocumentVO = {
    code?: number;
    data?: PageDocumentVO;
    message?: string;
  };

  type BaseResponseListChatConversationVO = {
    code?: number;
    data?: ChatConversationVO[];
    message?: string;
  };

  type BaseResponseChatConversationVO = {
    code?: number;
    data?: ChatConversationVO;
    message?: string;
  };

  type ChatConversationVO = {
    id?: string;
    title?: string;
    createTime?: string;
    messages?: ChatMessageVO[];
  };

  type ChatGenerationMetadata = {
    empty?: boolean;
    contentFilters?: string[];
    finishReason?: string;
  };

  type ChatMessageVO = {
    id?: string;
    conversationId?: string;
    messageNo?: number;
    content?: string;
    role?: string;
    resourceIds?: string[];
    resources?: ResourceVO[];
  };

  type ChatRequestVO = {
    conversationId: string;
    content: string;
    resourceIds?: string[];
    knowledgeIds?: string[];
    chatType: string;
  };

  type DocumentVO = {
    pageNo?: number;
    pageSize?: number;
    knowledgeBaseId?: string;
    id?: number;
    fileName?: string;
    path?: string;
    isEmbedding?: boolean;
    baseId?: string;
    knowledgeBaseName?: string;
    fileType?: string;
    uploadTime?: string;
  };

  type Generation = {
    metadata?: ChatGenerationMetadata;
    output?: AssistantMessage;
  };

  type KnowledgeBaseVO = {
    id?: string;
    name: string;
    description?: string;
    author?: number;
    authorName?: string;
    createTime?: string;
  };

  type Media = {
    id?: string;
    mimeType?: MimeType;
    data?: Record<string, unknown>;
    name?: string;
    dataAsByteArray?: string;
  };

  type MimeType = {
    type?: string;
    subtype?: string;
    parameters?: Record<string, unknown>;
    wildcardType?: boolean;
    wildcardSubtype?: boolean;
    subtypeSuffix?: string;
    charset?: string;
    concrete?: boolean;
  };

  type OrderItem = {
    column?: string;
    asc?: boolean;
  };

  type PageDocumentVO = {
    records?: DocumentVO[];
    total?: number;
    size?: number;
    current?: number;
    orders?: OrderItem[];
    pages?: number;
  };

  type ResourceVO = {
    resourceId?: string;
    fileName?: string;
    fileType?: string;
    path?: string;
  };

  type SimpleBaseVO = {
    id?: string;
    name?: string;
  };

  type ToolCall = {
    id?: string;
    type?: string;
    name?: string;
    arguments?: string;
  };

  type UserLoginVO = {
    username: string;
    password: string;
  };

  type uploadKnowledgeFileParams = {
    knowledgeId: string;
  };

  type detailChatConversationParams = {
    id: string;
  };

  type listDocumentParams = {
    arg0: DocumentVO;
  };
}

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface KnowledgeBase {
  id: string;
  name: string;
  description: string;
  icon: string;
  deleted: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeBaseDTO {
  name: string;
  description: string;
  icon: string;
}

export interface DocumentDTO {
  id: string;
  kbId: string;
  title: string;
  fileType: string;
  fileSize: number;
  chunkCount: number;
  status: string;
  errorMessage: string;
  createdAt: string;
}

export interface ChatRequest {
  message: string;
  kbId?: string;
  conversationId?: string;
  kbIds?: string[];
}

export interface SourceDTO {
  chunkId: string;
  documentId: string;
  documentTitle: string;
  snippet: string;
  similarityScore: number;
}

export interface CredibilityBreakdownDTO {
  similarityScore: number;
  freshnessScore: number;
  diversityScore: number;
  historyScore: number;
  overallScore: number;
}

export interface ChatResponse {
  messageId: string;
  conversationId: string;
  answer: string;
  sources: SourceDTO[];
  credibility?: CredibilityBreakdownDTO;
  totalTokens: number;
  latencyMs: number;
}

export interface ConversationDTO {
  id: string;
  title: string;
  messageCount: number;
  createdAt: string;
  updatedAt: string;
  messages: ChatMessageDTO[];
}

export interface ChatMessageDTO {
  id: string;
  role: string;
  content: string;
  metadata: string;
  feedback?: string;
  createdAt: string;
}

export interface ConversationCreateDTO {
  title: string;
}

export interface DocumentChunkDTO {
  id: string;
  documentId: string;
  content: string;
  chunkIndex: number;
  tokenCount: number;
  embeddingReady: boolean;
}