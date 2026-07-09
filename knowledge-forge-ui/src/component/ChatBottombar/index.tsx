import { Input, Button, Select, Space, Radio } from 'antd';
import { SendOutlined, ClearOutlined } from '@ant-design/icons';
import { useState, useRef, useEffect } from 'react';
import type { KnowledgeBase } from '@/services/typings.d';
import './index.css';

const { TextArea } = Input;

interface ChatBottombarProps {
  onSend: (message: string, kbId?: string) => void;
  onClear: () => void;
  loading: boolean;
  knowledgeBases: KnowledgeBase[];
  isDark: boolean;
  chatMode: 'rag' | 'simple';
  onChatModeChange: (mode: 'rag' | 'simple') => void;
}

function ChatBottombar({
  onSend,
  onClear,
  loading,
  knowledgeBases,
  isDark,
  chatMode,
  onChatModeChange,
}: ChatBottombarProps) {
  const [inputValue, setInputValue] = useState('');
  const [selectedKbId, setSelectedKbId] = useState<string | undefined>(
    undefined,
  );
  const textAreaRef = useRef<any>(null);

  useEffect(() => {
    if (!loading && textAreaRef.current) {
      textAreaRef.current.focus();
    }
  }, [loading]);

  const handleSend = () => {
    const trimmed = inputValue.trim();
    if (!trimmed || loading) return;
    onSend(trimmed, selectedKbId);
    setInputValue('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className={`chat-bottombar ${isDark ? 'dark' : ''}`}>
      <div className="chat-bottombar-options">
        <Space>
          <Radio.Group
            value={chatMode}
            onChange={(e) => onChatModeChange(e.target.value)}
            size="small"
            optionType="button"
            buttonStyle="solid"
          >
            <Radio.Button value="rag">RAG 检索</Radio.Button>
            <Radio.Button value="simple">简单对话</Radio.Button>
          </Radio.Group>
          {chatMode === 'rag' && (
            <Select
              placeholder="选择知识库 (可选)"
              allowClear
              style={{ width: 200 }}
              value={selectedKbId}
              onChange={setSelectedKbId}
              options={knowledgeBases.map((kb) => ({
                label: kb.name,
                value: kb.id,
              }))}
              size="small"
            />
          )}
        </Space>
        <Button
          size="small"
          icon={<ClearOutlined />}
          onClick={onClear}
          disabled={loading}
          type="text"
        >
          清空对话
        </Button>
      </div>
      <div className="chat-bottombar-input">
        <TextArea
          ref={textAreaRef}
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入您的问题，按 Enter 发送，Shift+Enter 换行..."
          autoSize={{ minRows: 1, maxRows: 5 }}
          disabled={loading}
          className="chat-textarea"
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={handleSend}
          loading={loading}
          disabled={!inputValue.trim()}
          className="chat-send-btn"
        >
          发送
        </Button>
      </div>
    </div>
  );
}

export default ChatBottombar;
