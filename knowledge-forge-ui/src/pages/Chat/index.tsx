import { useState, useEffect, useCallback, useRef } from 'react';
import { App } from 'antd';
import ChatWindow from '@/component/ChatWindow';
import ChatBottombar from '@/component/ChatBottombar';
import ChatConversation from '@/component/ChatConversation';
import {
  chatController,
  conversationController,
  knowledgeBaseController,
} from '@/services';
import type {
  ConversationDTO,
  KnowledgeBase,
  SourceDTO,
  CredibilityBreakdownDTO,
} from '@/services/typings.d';
import './index.css';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: SourceDTO[];
  credibility?: CredibilityBreakdownDTO;
  messageId?: string;
}

interface ChatPageProps {
  themeMode: 'light' | 'dark';
  sidebarCollapsed: boolean;
  onToggleSidebar: () => void;
}

function ChatPage({
  themeMode,
  sidebarCollapsed,
  onToggleSidebar,
}: ChatPageProps) {
  const { message } = App.useApp();
  const [messagesState, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);
  const [conversations, setConversations] = useState<ConversationDTO[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<
    string | null
  >(null);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [chatMode, setChatMode] = useState<'rag' | 'simple'>('rag');

  const isDark = themeMode === 'dark';

  const mountedRef = useRef(true);
  const abortRef = useRef<AbortController | null>(null);

  const reportBackgroundError = useCallback(
    (context: string, error: unknown) => {
      console.warn(`[ChatPage] ${context}`, error);
    },
    [],
  );

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const loadConversations = useCallback(async () => {
    try {
      const res = await conversationController.listConversations(0, 50);
      if (mountedRef.current) {
        setConversations(res.data.data.items);
      }
    } catch (error) {
      reportBackgroundError('加载对话列表失败', error);
    }
  }, [reportBackgroundError]);

  const loadKnowledgeBases = useCallback(async () => {
    try {
      const res = await knowledgeBaseController.listAllKnowledgeBases();
      if (mountedRef.current) {
        setKnowledgeBases(res.data.data);
      }
    } catch (error) {
      reportBackgroundError('加载知识库列表失败', error);
    }
  }, [reportBackgroundError]);

  useEffect(() => {
    mountedRef.current = true;
    loadConversations();
    loadKnowledgeBases();
    return () => {
      mountedRef.current = false;
    };
  }, [loadConversations, loadKnowledgeBases]);

  const handleSend = async (content: string, kbId?: string) => {
    const userMsg: Message = {
      id: Date.now().toString(),
      role: 'user',
      content,
    };

    const assistantMsgId = 'loading-' + Date.now().toString();
    const loadingMsg: Message = {
      id: assistantMsgId,
      role: 'assistant',
      content: '',
    };

    setMessages((prev) => [...prev, userMsg, loadingMsg]);
    setLoading(true);

    if (chatMode === 'simple') {
      try {
        const res = await chatController.simpleChat({
          message: content,
          conversationId: activeConversationId || undefined,
        });
        const { answer, conversationId, messageId } = res.data.data;
        if (conversationId && !activeConversationId) {
          setActiveConversationId(conversationId);
        }
        setMessages((prev) =>
          prev.map((m) =>
            m.id === assistantMsgId
              ? {
                  id: Date.now().toString(),
                  role: 'assistant' as const,
                  content: answer,
                  messageId,
                }
              : m,
          ),
        );
        loadConversations();
      } catch (err: any) {
        setMessages((prev) => prev.filter((m) => m.id !== assistantMsgId));
        const errorMsg =
          err?.response?.data?.message || err?.message || '请求失败，请重试';
        message.error(errorMsg);
      } finally {
        setLoading(false);
      }
      return;
    }

    let fullContent = '';

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    try {
      await chatController.ragChatStream(
        {
          message: content,
          kbId: kbId || undefined,
          conversationId: activeConversationId || undefined,
        },
        (text) => {
          fullContent += text;
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantMsgId ? { ...m, content: fullContent } : m,
            ),
          );
        },
        controller.signal,
        (metadata) => {
          if (metadata.conversationId && !activeConversationId) {
            setActiveConversationId(metadata.conversationId);
          }
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantMsgId
                ? {
                    ...m,
                    sources: metadata.sources,
                    credibility: metadata.credibility,
                  }
                : m,
            ),
          );
        },
      );

      setMessages((prev) =>
        prev.map((m) =>
          m.id === assistantMsgId
            ? {
                id: Date.now().toString(),
                role: 'assistant' as const,
                content: fullContent,
                sources: m.sources,
                credibility: m.credibility,
              }
            : m,
        ),
      );

      loadConversations();
    } catch (err: any) {
      if (err?.name !== 'AbortError') {
        setMessages((prev) => prev.filter((m) => m.id !== assistantMsgId));
        const errorMsg =
          err?.response?.data?.message || err?.message || '请求失败，请重试';
        message.error(errorMsg);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleClear = () => {
    setMessages([]);
    setActiveConversationId(null);
  };

  const handleSelectConversation = async (id: string) => {
    setActiveConversationId(id);
    try {
      const res = await conversationController.getMessages(id);
      const msgs: Message[] = res.data.data.map((m) => ({
        id: m.id,
        messageId: m.id,
        role: m.role as 'user' | 'assistant',
        content: m.content,
        sources: [],
      }));
      setMessages(msgs);
    } catch {
      message.error('加载对话消息失败');
    }
  };

  const handleDeleteConversation = async (id: string) => {
    try {
      await conversationController.deleteConversation(id);
      message.success('对话已删除');
      if (activeConversationId === id) {
        setActiveConversationId(null);
        setMessages([]);
      }
      loadConversations();
    } catch {
      message.error('删除对话失败');
    }
  };

  const handleNewConversation = () => {
    setActiveConversationId(null);
    setMessages([]);
  };

  return (
    <div className={`chat-page ${isDark ? 'dark' : ''}`}>
      <ChatConversation
        conversations={conversations}
        activeId={activeConversationId}
        onSelect={handleSelectConversation}
        onDelete={handleDeleteConversation}
        onNew={handleNewConversation}
        collapsed={sidebarCollapsed}
        onToggleCollapse={onToggleSidebar}
        isDark={isDark}
      />
      <div className={`chat-page-main ${isDark ? 'dark' : ''}`}>
        <ChatWindow
          messages={messagesState}
          loading={loading}
          isDark={isDark}
        />
        <ChatBottombar
          onSend={handleSend}
          onClear={handleClear}
          loading={loading}
          knowledgeBases={knowledgeBases}
          isDark={isDark}
          chatMode={chatMode}
          onChatModeChange={setChatMode}
        />
      </div>
    </div>
  );
}

export default ChatPage;
