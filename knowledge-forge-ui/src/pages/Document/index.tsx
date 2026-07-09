import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Button,
  Table,
  Upload,
  Space,
  Popconfirm,
  App,
  Typography,
  Tag,
  Drawer,
  List,
  Spin,
  Progress,
} from 'antd';
import {
  UploadOutlined,
  DeleteOutlined,
  ArrowLeftOutlined,
  ReloadOutlined,
  DownloadOutlined,
  RedoOutlined,
  EyeOutlined,
  NodeIndexOutlined,
} from '@ant-design/icons';
import { documentController } from '@/services';
import type { DocumentDTO, DocumentChunkDTO } from '@/services/typings.d';
import './index.css';

const { Title, Paragraph, Text } = Typography;

function DocumentPage() {
  const { message } = App.useApp();
  const { knowledgeBaseId } = useParams<{ knowledgeBaseId: string }>();
  const navigate = useNavigate();
  const [documents, setDocuments] = useState<DocumentDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [chunksDrawerOpen, setChunksDrawerOpen] = useState(false);
  const [chunks, setChunks] = useState<DocumentChunkDTO[]>([]);
  const [chunksLoading, setChunksLoading] = useState(false);
  const [selectedDocTitle, setSelectedDocTitle] = useState('');

  const mountedRef = useRef(true);

  const loadDocuments = useCallback(async () => {
    if (!knowledgeBaseId) return;
    setLoading(true);
    try {
      const res = await documentController.listDocuments(
        knowledgeBaseId,
        0,
        50,
      );
      if (mountedRef.current) {
        setDocuments(res.data.data.items);
      }
    } catch {
      if (mountedRef.current) {
        message.error('加载文档列表失败');
      }
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, [knowledgeBaseId, message]);

  useEffect(() => {
    mountedRef.current = true;
    loadDocuments();
    return () => {
      mountedRef.current = false;
    };
  }, [loadDocuments]);

  // 当有文档正在处理时，每3秒自动刷新状态
  useEffect(() => {
    const hasProcessing = documents.some(
      (d) => d.status === 'PROCESSING' || d.status === 'PENDING',
    );
    if (!hasProcessing) return;
    const timer = setInterval(() => {
      if (mountedRef.current) loadDocuments();
    }, 3000);
    return () => clearInterval(timer);
  }, [documents, loadDocuments]);

  const handleUpload = async (file: File) => {
    if (!knowledgeBaseId) return;
    setUploading(true);
    setUploadProgress(0);
    try {
      await documentController.uploadDocument(
        knowledgeBaseId,
        file,
        (percent) => setUploadProgress(percent),
      );
      message.success(`文档 "${file.name}" 上传成功，正在后台处理...`);
      loadDocuments();
    } catch (err: any) {
      message.error(err?.response?.data?.message || '上传失败');
    } finally {
      setUploading(false);
      setUploadProgress(0);
    }
    return false;
  };

  const handleDelete = async (id: string) => {
    try {
      await documentController.deleteDocument(id);
      message.success('文档已删除');
      loadDocuments();
    } catch {
      message.error('删除失败');
    }
  };

  const handleDownload = async (record: DocumentDTO) => {
    try {
      await documentController.downloadDocument(record.id, record.title);
    } catch {
      message.error('下载失败');
    }
  };

  const handleReprocess = async (id: string) => {
    try {
      await documentController.reprocessDocument(id);
      message.success('文档重新处理已开始');
      loadDocuments();
    } catch (err: any) {
      message.error(err?.response?.data?.message || '重新处理失败');
    }
  };

  const handleViewChunks = async (record: DocumentDTO) => {
    setSelectedDocTitle(record.title);
    setChunksDrawerOpen(true);
    setChunksLoading(true);
    try {
      const res = await documentController.getChunks(record.id);
      setChunks(res.data.data);
    } catch {
      message.error('加载分块失败');
    } finally {
      setChunksLoading(false);
    }
  };

  const statusColorMap: Record<string, string> = {
    PENDING: 'processing',
    PROCESSING: 'processing',
    READY: 'success',
    FAILED: 'error',
  };

  const statusLabelMap: Record<string, string> = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    READY: '就绪',
    FAILED: '失败',
  };

  const columns = [
    {
      title: '文档名称',
      dataIndex: 'title',
      key: 'title',
      ellipsis: true,
    },
    {
      title: '类型',
      dataIndex: 'fileType',
      key: 'fileType',
      width: 80,
      render: (text: string) => <Tag>{text}</Tag>,
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 100,
      render: (size: number) => {
        if (!size) return '-';
        if (size < 1024) return `${size} B`;
        if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
        return `${(size / (1024 * 1024)).toFixed(1)} MB`;
      },
    },
    {
      title: '分块数',
      dataIndex: 'chunkCount',
      key: 'chunkCount',
      width: 80,
      render: (count: number, record: DocumentDTO) =>
        count > 0 ? (
          <a onClick={() => handleViewChunks(record)}>{count}</a>
        ) : (
          <span>{count || 0}</span>
        ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string, record: DocumentDTO) => {
        if (status === 'FAILED') {
          return (
            <Tag color="error" title={record.errorMessage}>
              {statusLabelMap[status] || status}
            </Tag>
          );
        }
        return (
          <Tag color={statusColorMap[status] || 'default'}>
            {statusLabelMap[status] || status}
          </Tag>
        );
      },
    },
    {
      title: '上传时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (text: string) =>
        text ? new Date(text).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_: unknown, record: DocumentDTO) => (
        <Space>
          {record.status === 'FAILED' && (
            <Button
              type="link"
              icon={<RedoOutlined />}
              onClick={() => handleReprocess(record.id)}
              title="重新处理"
            />
          )}
          {record.chunkCount > 0 && (
            <Button
              type="link"
              icon={<EyeOutlined />}
              onClick={() => handleViewChunks(record)}
              title="查看分块"
            />
          )}
          <Button
            type="link"
            icon={<DownloadOutlined />}
            onClick={() => handleDownload(record)}
            title="下载"
          />
          <Popconfirm
            title="确定删除此文档？"
            onConfirm={() => handleDelete(record.id)}
          >
            <Button type="link" danger icon={<DeleteOutlined />} title="删除" />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="document-page">
      <div className="page-header">
        <div>
          <Space align="center">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/knowledge-base')}
            />
            <Title level={3} style={{ margin: 0 }}>
              文档管理
            </Title>
          </Space>
          <Paragraph type="secondary" style={{ margin: '4px 0 0 48px' }}>
            知识库ID: {knowledgeBaseId}
          </Paragraph>
        </div>
        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={loadDocuments}
            loading={loading}
          >
            刷新
          </Button>
          <Button
            icon={<NodeIndexOutlined />}
            onClick={() => navigate(`/knowledge-base/${knowledgeBaseId}/graph`)}
          >
            查看图谱
          </Button>
          <Upload
            beforeUpload={(file) => {
              handleUpload(file);
              return false;
            }}
            showUploadList={false}
            accept=".txt,.md,.markdown,.pdf,.doc,.docx"
          >
            <Button
              type="primary"
              icon={<UploadOutlined />}
              loading={uploading && uploadProgress === 0}
            >
              上传文档
            </Button>
          </Upload>
        </Space>
      </div>

      {uploading && uploadProgress > 0 && (
        <div style={{ marginBottom: 16 }}>
          <Progress
            percent={uploadProgress}
            status="active"
            strokeColor="#1677ff"
          />
        </div>
      )}

      <Card>
        <Table
          columns={columns}
          dataSource={documents}
          rowKey="id"
          loading={loading}
          pagination={false}
          locale={{ emptyText: '暂无文档，点击上方按钮上传' }}
        />
      </Card>

      <Drawer
        title={`文档分块 - ${selectedDocTitle}`}
        placement="right"
        width={480}
        onClose={() => setChunksDrawerOpen(false)}
        open={chunksDrawerOpen}
      >
        {chunksLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin />
          </div>
        ) : (
          <List
            dataSource={chunks}
            locale={{ emptyText: '暂无分块数据' }}
            renderItem={(item, index) => (
              <List.Item>
                <div style={{ width: '100%' }}>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      marginBottom: 8,
                    }}
                  >
                    <Tag color="blue">分块 #{index + 1}</Tag>
                    <Space size="small">
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {item.tokenCount} tokens
                      </Text>
                      <Tag
                        color={item.embeddingReady ? 'success' : 'warning'}
                        style={{ fontSize: 11 }}
                      >
                        {item.embeddingReady ? '已向量化' : '未向量化'}
                      </Tag>
                    </Space>
                  </div>
                  <Paragraph
                    style={{
                      background: '#f5f5f5',
                      padding: 12,
                      borderRadius: 6,
                      whiteSpace: 'pre-wrap',
                      margin: 0,
                      fontSize: 13,
                      maxHeight: 200,
                      overflow: 'auto',
                    }}
                    ellipsis={{ rows: 4, expandable: true, symbol: '展开' }}
                  >
                    {item.content}
                  </Paragraph>
                </div>
              </List.Item>
            )}
          />
        )}
      </Drawer>
    </div>
  );
}

export default DocumentPage;
