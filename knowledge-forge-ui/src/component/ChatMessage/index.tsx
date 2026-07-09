import { useState } from 'react';
import { Avatar, Button, Space, App, Tooltip, Progress, Tag } from 'antd';
import {
  UserOutlined,
  RobotOutlined,
  LikeOutlined,
  DislikeOutlined,
  LikeFilled,
  DislikeFilled,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import MarkdownContent from '@/component/MarkdownContent';
import { chatController } from '@/services';
import type { SourceDTO, CredibilityBreakdownDTO } from '@/services/typings.d';
import './index.css';

interface ChatMessageProps {
  role: 'user' | 'assistant';
  content: string;
  sources?: SourceDTO[];
  credibility?: CredibilityBreakdownDTO;
  messageId?: string;
}

function ChatMessageComponent({
  role,
  content,
  sources,
  credibility,
  messageId,
}: ChatMessageProps) {
  const { message } = App.useApp();
  const isUser = role === 'user';
  const [feedback, setFeedback] = useState<string | null>(null);

  const handleFeedback = async (type: string) => {
    if (!messageId) return;
    const newFeedback = feedback === type ? null : type;
    setFeedback(newFeedback);
    try {
      await chatController.submitFeedback(messageId, newFeedback || '');
    } catch {
      setFeedback(feedback);
      message.error('反馈提交失败');
    }
  };

  return (
    <div
      className={`chat-message ${isUser ? 'chat-message-user' : 'chat-message-assistant'}`}
    >
      <div className="chat-message-avatar">
        <Avatar
          size={36}
          icon={isUser ? <UserOutlined /> : <RobotOutlined />}
          style={{ background: isUser ? '#1677ff' : '#52c41a' }}
        />
      </div>
      <div className="chat-message-body">
        <div className="chat-message-role">{isUser ? '我' : '知否'}</div>
        <div className="chat-message-content">
          <MarkdownContent content={content} />
        </div>
        {sources && sources.length > 0 && (
          <div className="chat-message-sources">
            <div className="sources-title">参考来源：</div>
            {sources.map((source, index) => (
              <div key={index} className="source-item">
                <span className="source-doc">{source.documentTitle}</span>
                <span className="source-snippet">{source.snippet}</span>
              </div>
            ))}
          </div>
        )}
        {!isUser && credibility && (
          <Tooltip
            title={
              <div style={{ fontSize: 12 }}>
                <div>
                  来源匹配度: {(credibility.similarityScore * 100).toFixed(0)}%
                </div>
                <div>
                  文档新鲜度: {(credibility.freshnessScore * 100).toFixed(0)}%
                </div>
                <div>
                  来源多样性: {(credibility.diversityScore * 100).toFixed(0)}%
                </div>
                <div>
                  历史验证度: {(credibility.historyScore * 100).toFixed(0)}%
                </div>
              </div>
            }
          >
            <div className="chat-message-credibility">
              <SafetyCertificateOutlined style={{ marginRight: 4 }} />
              <span>可信度</span>
              <Progress
                percent={Math.round(credibility.overallScore * 100)}
                size="small"
                style={{ width: 100, marginLeft: 8, marginBottom: 0 }}
                strokeColor={
                  credibility.overallScore >= 0.7
                    ? '#52c41a'
                    : credibility.overallScore >= 0.4
                      ? '#faad14'
                      : '#ff4d4f'
                }
              />
              <Tag
                color={
                  credibility.overallScore >= 0.7
                    ? 'success'
                    : credibility.overallScore >= 0.4
                      ? 'warning'
                      : 'error'
                }
                style={{ marginLeft: 8, fontSize: 11 }}
              >
                {credibility.overallScore >= 0.7
                  ? '高'
                  : credibility.overallScore >= 0.4
                    ? '中'
                    : '低'}
              </Tag>
            </div>
          </Tooltip>
        )}
        {!isUser && messageId && (
          <div className="chat-message-feedback">
            <Space size="small">
              <Button
                type="text"
                size="small"
                icon={
                  feedback === 'like' ? (
                    <LikeFilled style={{ color: '#1677ff' }} />
                  ) : (
                    <LikeOutlined />
                  )
                }
                onClick={() => handleFeedback('like')}
              />
              <Button
                type="text"
                size="small"
                icon={
                  feedback === 'dislike' ? (
                    <DislikeFilled style={{ color: '#ff4d4f' }} />
                  ) : (
                    <DislikeOutlined />
                  )
                }
                onClick={() => handleFeedback('dislike')}
              />
            </Space>
          </div>
        )}
      </div>
    </div>
  );
}

export default ChatMessageComponent;
