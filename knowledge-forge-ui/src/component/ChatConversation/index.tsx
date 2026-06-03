import { List, Button, Popconfirm, Typography } from 'antd';
import { DeleteOutlined, MessageOutlined, PlusOutlined, MenuFoldOutlined } from '@ant-design/icons';
import type { ConversationDTO } from '@/services/typings.d';
import './index.css';

const { Text } = Typography;

interface ChatConversationProps {
  conversations: ConversationDTO[];
  activeId: string | null;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  onNew: () => void;
  collapsed: boolean;
  onToggleCollapse: () => void;
  isDark: boolean;
}

function ChatConversation({
  conversations,
  activeId,
  onSelect,
  onDelete,
  onNew,
  collapsed,
  onToggleCollapse,
  isDark,
}: ChatConversationProps) {
  return (
    <div className={`chat-conversation ${isDark ? 'dark' : ''} ${collapsed ? 'collapsed' : ''}`}>
      <div className="chat-conversation-header">
        <div className="chat-conversation-header-top">
          <Button type="primary" icon={<PlusOutlined />} onClick={onNew} className="new-chat-btn">
            {!collapsed && '新建对话'}
          </Button>
          <Button
            type="text"
            size="small"
            icon={<MenuFoldOutlined />}
            onClick={onToggleCollapse}
            className="collapse-btn"
          />
        </div>
      </div>
      <div className="chat-conversation-list">
        <List
          dataSource={conversations}
          locale={{ emptyText: !collapsed ? '暂无对话' : '' }}
          renderItem={(item) => (
            <List.Item
              className={`conversation-item ${activeId === item.id ? 'active' : ''} ${isDark ? 'dark' : ''}`}
              onClick={() => onSelect(item.id)}
              actions={
                !collapsed
                  ? [
                      <Popconfirm
                        key="delete"
                        title="确定删除此对话？"
                        onConfirm={(e) => {
                          e?.stopPropagation();
                          onDelete(item.id);
                        }}
                        onCancel={(e) => e?.stopPropagation()}
                      >
                        <Button
                          type="text"
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={(e) => e.stopPropagation()}
                        />
                      </Popconfirm>,
                    ]
                  : undefined
              }
            >
              <List.Item.Meta
                avatar={<MessageOutlined style={{ fontSize: 18, color: '#1677ff' }} />}
                title={
                  !collapsed ? (
                    <Text ellipsis style={{ maxWidth: 140, color: isDark ? '#e8e8e8' : undefined }}>
                      {item.title}
                    </Text>
                  ) : null
                }
                description={!collapsed ? `${item.messageCount} 条消息` : null}
              />
            </List.Item>
          )}
        />
      </div>
    </div>
  );
}

export default ChatConversation;