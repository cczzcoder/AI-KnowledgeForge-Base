import { useRef, useEffect } from 'react';
import ChatMessageComponent from '@/component/ChatMessage';
import { Spin } from 'antd';
import type { SourceDTO, CredibilityBreakdownDTO } from '@/services/typings.d';
import logoWhite from '@/assets/images/logo-white.jpg';
import logoBlack from '@/assets/images/logo-black.jpg';
import './index.css';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources?: SourceDTO[];
  credibility?: CredibilityBreakdownDTO;
  messageId?: string;
}

interface ChatWindowProps {
  messages: Message[];
  loading: boolean;
  isDark: boolean;
}

function ChatWindow({ messages, loading, isDark }: ChatWindowProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <div className={`chat-window ${isDark ? 'dark' : ''}`}>
      {messages.length === 0 && !loading ? (
        <div className="chat-window-empty">
          <div className="empty-icon">
            <img
              src={isDark ? logoWhite : logoBlack}
              alt="知否"
              style={{ height: 80, width: 'auto', objectFit: 'contain' }}
            />
          </div>
          <h2 style={{ color: isDark ? '#e8e8e8' : '#333' }}>知否</h2>
          <p style={{ color: isDark ? '#8b8b9e' : '#999' }}>
            你的个人知识库AI助手
          </p>
          <div
            className="empty-tips"
            style={{
              background: isDark ? '#1a1a2e' : '#f5f5f5',
              color: isDark ? '#8b8b9e' : '#666',
            }}
          >
            <span>在上方选择知识库，然后输入您的问题开始对话</span>
          </div>
        </div>
      ) : (
        <div className="chat-window-messages">
          {messages.map((msg) => (
            <ChatMessageComponent
              key={msg.id}
              role={msg.role}
              content={msg.content}
              sources={msg.sources}
              credibility={msg.credibility}
              messageId={msg.messageId}
            />
          ))}
        </div>
      )}
      {loading && (
        <div className="chat-window-loading">
          <Spin size="small" />
          <span style={{ marginLeft: 8, color: '#999' }}>知否 正在思考...</span>
        </div>
      )}
      <div ref={bottomRef} />
    </div>
  );
}

export default ChatWindow;
