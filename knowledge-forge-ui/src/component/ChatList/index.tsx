import { List, Typography, Space } from 'antd';
import { MessageOutlined } from '@ant-design/icons';
import type { ConversationDTO } from '@/services/typings.d';
import './index.css';

const { Text } = Typography;

interface ChatListProps {
  conversations: ConversationDTO[];
  activeId: string | null;
  onSelect: (id: string) => void;
}

function ChatList({ conversations, activeId, onSelect }: ChatListProps) {
  return (
    <div className="chat-list">
      <List
        dataSource={conversations}
        locale={{ emptyText: '暂无对话历史' }}
        renderItem={(item) => (
          <List.Item
            className={`chat-list-item ${activeId === item.id ? 'active' : ''}`}
            onClick={() => onSelect(item.id)}
          >
            <Space>
              <MessageOutlined style={{ color: '#1677ff' }} />
              <Text ellipsis style={{ maxWidth: 120 }}>
                {item.title}
              </Text>
            </Space>
          </List.Item>
        )}
      />
    </div>
  );
}

export default ChatList;