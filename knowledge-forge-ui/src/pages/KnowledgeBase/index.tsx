import { useState, useEffect, useCallback, useRef } from 'react';
import { Card, Button, Table, Space, App, Typography, Dropdown } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, FolderOpenOutlined, MoreOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import KnowledgeForm from '@/component/KnowledgeForm';
import { knowledgeBaseController } from '@/services';
import type { KnowledgeBase, KnowledgeBaseDTO } from '@/services/typings.d';
import './index.css';

const { Title, Paragraph } = Typography;

function KnowledgeBasePage() {
  const { message } = App.useApp();
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingKb, setEditingKb] = useState<KnowledgeBase | null>(null);
  const navigate = useNavigate();

  const mountedRef = useRef(true);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await knowledgeBaseController.listKnowledgeBases(0, 50);
      if (mountedRef.current) {
        setKnowledgeBases(res.data.data.items);
      }
    } catch {
      if (mountedRef.current) {
        message.error('加载知识库列表失败');
      }
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, [message]);

  useEffect(() => {
    mountedRef.current = true;
    loadData();
    return () => {
      mountedRef.current = false;
    };
  }, [loadData]);

  const handleCreate = async (values: KnowledgeBaseDTO) => {
    try {
      await knowledgeBaseController.createKnowledgeBase(values);
      message.success('知识库创建成功');
      setModalOpen(false);
      loadData();
    } catch (err: any) {
      message.error(err?.response?.data?.message || '创建失败');
    }
  };

  const handleUpdate = async (values: KnowledgeBaseDTO) => {
    if (!editingKb) return;
    try {
      await knowledgeBaseController.updateKnowledgeBase(editingKb.id, values);
      message.success('知识库更新成功');
      setModalOpen(false);
      setEditingKb(null);
      loadData();
    } catch (err: any) {
      message.error(err?.response?.data?.message || '更新失败');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await knowledgeBaseController.deleteKnowledgeBase(id);
      message.success('知识库已删除');
      loadData();
    } catch {
      message.error('删除失败');
    }
  };

  const openCreateModal = () => {
    setEditingKb(null);
    setModalOpen(true);
  };

  const openEditModal = (kb: KnowledgeBase) => {
    setEditingKb(kb);
    setModalOpen(true);
  };

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: KnowledgeBase) => (
        <Space>
          <FolderOpenOutlined style={{ color: '#1677ff' }} />
          <a onClick={() => navigate(`/knowledge-base/${record.id}`)}>{text}</a>
        </Space>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
      render: (text: string) => text || '-',
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (text: string) => text ? new Date(text).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: unknown, record: KnowledgeBase) => {
        const menuItems = [
          {
            key: 'edit',
            icon: <EditOutlined />,
            label: '编辑',
            onClick: () => openEditModal(record),
          },
          {
            key: 'docs',
            icon: <FolderOpenOutlined />,
            label: '文档',
            onClick: () => navigate(`/knowledge-base/${record.id}`),
          },
          { type: 'divider' as const },
          {
            key: 'delete',
            icon: <DeleteOutlined />,
            label: '删除',
            danger: true,
            onClick: () => handleDelete(record.id),
          },
        ];
        return (
          <Dropdown menu={{ items: menuItems }} trigger={['click']}>
            <Button type="text" size="small" icon={<MoreOutlined />} />
          </Dropdown>
        );
      },
    },
  ];

  return (
    <div className="knowledge-base-page">
      <div className="page-header">
        <div>
          <Title level={3} style={{ margin: 0 }}>知识库管理</Title>
          <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
            管理您的知识库，上传文档以构建 RAG 检索能力
          </Paragraph>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
          新建知识库
        </Button>
      </div>

      <Card>
        <Table
          columns={columns}
          dataSource={knowledgeBases}
          rowKey="id"
          loading={loading}
          pagination={false}
          locale={{ emptyText: '暂无知识库，点击上方按钮创建' }}
        />
      </Card>

      <KnowledgeForm
        open={modalOpen}
        title={editingKb ? '编辑知识库' : '新建知识库'}
        initialValues={editingKb ? { name: editingKb.name, description: editingKb.description, icon: editingKb.icon } : undefined}
        onOk={editingKb ? handleUpdate : handleCreate}
        onCancel={() => {
          setModalOpen(false);
          setEditingKb(null);
        }}
      />
    </div>
  );
}

export default KnowledgeBasePage;