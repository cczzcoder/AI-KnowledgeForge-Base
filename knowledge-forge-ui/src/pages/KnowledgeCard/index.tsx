import {
  Card,
  Tag,
  Button,
  Space,
  Select,
  Modal,
  Input,
  message,
  Badge,
  Checkbox,
  Typography,
  Spin,
  Empty,
} from 'antd';
import {
  CheckOutlined,
  CloseOutlined,
  DeleteOutlined,
  BookOutlined,
} from '@ant-design/icons';
import { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  listCards,
  reviewCard,
  batchReview,
  deleteCard,
  pendingCount,
  type KnowledgeCardDTO,
  type ReviewRequest,
} from '@/services/knowledgeCardController';
import { listAllKnowledgeBases } from '@/services/knowledgeBaseController';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

const STATUS_MAP: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'orange', label: '待审核' },
  APPROVED: { color: 'green', label: '已通过' },
  REJECTED: { color: 'red', label: '已驳回' },
};

const CATEGORY_MAP: Record<string, { color: string; label: string }> = {
  CONCEPT: { color: 'blue', label: '概念' },
  FACT: { color: 'cyan', label: '事实' },
  RULE: { color: 'purple', label: '规则' },
  INSIGHT: { color: 'gold', label: '见解' },
};

export default function KnowledgeCardPage() {
  const { knowledgeBaseId: paramKbId } = useParams<{ knowledgeBaseId?: string }>();
  const navigate = useNavigate();

  const [knowledgeBases, setKnowledgeBases] = useState<{ id: string; name: string }[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<string | undefined>(paramKbId);
  const kbId = selectedKbId || '';

  const [cards, setCards] = useState<KnowledgeCardDTO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(10);
  const [statusFilter, setStatusFilter] = useState<string>('PENDING');
  const [loading, setLoading] = useState(false);
  const [pendingNum, setPendingNum] = useState(0);

  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [reviewingCard, setReviewingCard] = useState<KnowledgeCardDTO | null>(null);
  const [reviewAction, setReviewAction] = useState<'APPROVED' | 'REJECTED'>('APPROVED');
  const [reviewNote, setReviewNote] = useState('');
  const [reviewCategory, setReviewCategory] = useState('');
  const [reviewEntityType, setReviewEntityType] = useState('');

  const [batchModalOpen, setBatchModalOpen] = useState(false);

  const reportBackgroundError = useCallback((context: string, error: unknown) => {
    console.warn(`[KnowledgeCardPage] ${context}`, error);
  }, []);

  const loadKnowledgeBases = useCallback(async () => {
    try {
      const res = await listAllKnowledgeBases();
      const kbs = res.data?.data;
      setKnowledgeBases(Array.isArray(kbs) ? kbs : []);
    } catch (error) {
      reportBackgroundError('加载知识库列表失败', error);
    }
  }, [reportBackgroundError]);

  useEffect(() => {
    if (paramKbId && paramKbId !== selectedKbId) {
      setSelectedKbId(paramKbId);
    }
  }, [paramKbId, selectedKbId]);

  const loadCards = useCallback(async () => {
    if (!kbId) return;
    setLoading(true);
    try {
      const res = await listCards({
        kbId,
        status: statusFilter || undefined,
        page,
        size: pageSize,
      });
      if (res.code === 200 && res.data) {
        setCards(res.data.items || []);
        setTotal(res.data.total || 0);
      }
    } catch {
      message.error('加载知识卡片失败');
    } finally {
      setLoading(false);
    }
  }, [kbId, statusFilter, page, pageSize]);

  const loadPendingCount = useCallback(async () => {
    if (!kbId) return;
    try {
      const res = await pendingCount(kbId);
      if (res.code === 200 && res.data) {
        setPendingNum(res.data.count);
      }
    } catch (error) {
      reportBackgroundError('加载待审核数量失败', error);
    }
  }, [kbId, reportBackgroundError]);

  useEffect(() => {
    loadKnowledgeBases();
  }, [loadKnowledgeBases]);

  useEffect(() => {
    loadCards();
    loadPendingCount();
  }, [loadCards, loadPendingCount]);

  const handleKbChange = (val: string) => {
    setSelectedKbId(val);
    setPage(0);
    setSelectedIds([]);
    navigate(`/knowledge-cards/${val}`, { replace: true });
  };

  const allSelected = cards.length > 0 && selectedIds.length === cards.length;
  const toggleSelectAll = () => {
    if (allSelected) {
      setSelectedIds([]);
    } else {
      setSelectedIds(cards.map((c) => c.id));
    }
  };

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((i) => i !== id) : [...prev, id],
    );
  };

  const openReviewModal = (card: KnowledgeCardDTO, action: 'APPROVED' | 'REJECTED') => {
    setReviewingCard(card);
    setReviewAction(action);
    setReviewNote('');
    setReviewCategory(card.category);
    setReviewEntityType(card.entityType || '');
    setReviewModalOpen(true);
  };

  const submitReview = async () => {
    if (!reviewingCard) return;
    try {
      const req: ReviewRequest = {
        action: reviewAction,
        note: reviewNote || undefined,
        category: reviewCategory || undefined,
        entityType: reviewEntityType || undefined,
      };
      const res = await reviewCard(reviewingCard.id, req);
      if (res.code === 200) {
        message.success(reviewAction === 'APPROVED' ? '已通过审核' : '已驳回');
        setReviewModalOpen(false);
        loadCards();
        loadPendingCount();
      }
    } catch {
      message.error('审核操作失败');
    }
  };

  const submitBatchReview = async (action: 'APPROVED' | 'REJECTED') => {
    if (selectedIds.length === 0) {
      message.warning('请先选择卡片');
      return;
    }
    try {
      const res = await batchReview({
        kbId,
        cardIds: selectedIds,
        review: { action },
      });
      if (res.code === 200) {
        message.success(`批量审核完成: ${res.data?.reviewed ?? 0} 张`);
        setBatchModalOpen(false);
        setSelectedIds([]);
        loadCards();
        loadPendingCount();
      }
    } catch {
      message.error('批量审核失败');
    }
  };

  const handleDelete = (cardId: string) => {
    Modal.confirm({
      title: '确认删除',
      content: '删除后无法恢复，确定要删除此知识卡片吗？',
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res = await deleteCard(cardId);
          if (res.code === 200) {
            message.success('已删除');
            loadCards();
            loadPendingCount();
          }
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  if (!kbId) {
    return (
      <div style={{ padding: 40, textAlign: 'center', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center' }}>
        <BookOutlined style={{ fontSize: 48, color: '#1677ff', marginBottom: 16 }} />
        <Typography.Title level={4} style={{ marginBottom: 8 }}>
          知识卡片审核
        </Typography.Title>
        <Typography.Text type="secondary" style={{ marginBottom: 24, maxWidth: 360 }}>
          选择一个知识库，查看和管理从对话中自动提取的知识卡片，支持审核通过、驳回、批量处理等操作。
        </Typography.Text>
        <Select
          showSearch
          placeholder="请选择知识库"
          value={undefined}
          onChange={handleKbChange}
          style={{ width: 280 }}
          size="large"
          options={knowledgeBases.map((kb) => ({ label: kb.name, value: kb.id }))}
          filterOption={(input, option) =>
            (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
          }
          notFoundContent={
            knowledgeBases.length === 0 ? <Empty description="暂无知识库，请先创建" /> : null
          }
        />
      </div>
    );
  }

  return (
    <div style={{ padding: 24, height: '100%', overflow: 'auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, flexWrap: 'wrap', gap: 8 }}>
        <Space wrap>
          <Select
            showSearch
            placeholder="选择知识库"
            value={selectedKbId}
            onChange={handleKbChange}
            style={{ width: 200 }}
            options={knowledgeBases.map((kb) => ({ label: kb.name, value: kb.id }))}
            filterOption={(input, option) =>
              (option?.label as string)?.toLowerCase().includes(input.toLowerCase())
            }
            prefix={<BookOutlined />}
          />
          <Typography.Title level={4} style={{ margin: 0 }}>
            知识卡片审核
          </Typography.Title>
          <Badge count={pendingNum} overflowCount={99}>
            <Tag color="orange">待审核</Tag>
          </Badge>
        </Space>
        <Space>
          <Select
            value={statusFilter}
            onChange={(val) => {
              setStatusFilter(val);
              setPage(0);
              setSelectedIds([]);
            }}
            style={{ width: 120 }}
            options={[
              { value: 'PENDING', label: '待审核' },
              { value: 'APPROVED', label: '已通过' },
              { value: 'REJECTED', label: '已驳回' },
              { value: '', label: '全部' },
            ]}
          />
          {selectedIds.length > 0 && (
            <Button onClick={() => setBatchModalOpen(true)}>批量审核 ({selectedIds.length})</Button>
          )}
        </Space>
      </div>

      <Spin spinning={loading}>
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space>
            <Checkbox checked={allSelected} onChange={toggleSelectAll}>
              全选当前页
            </Checkbox>
            <Text type="secondary">共 {total} 张卡片</Text>
          </Space>

          {cards.length === 0 ? (
            <Empty description="暂无知识卡片" />
          ) : (
            cards.map((card) => (
              <Card
                key={card.id}
                size="small"
                style={{ width: '100%' }}
                title={
                  <Space wrap>
                    <Checkbox checked={selectedIds.includes(card.id)} onChange={() => toggleSelect(card.id)} />
                    <Tag color={STATUS_MAP[card.status]?.color}>{STATUS_MAP[card.status]?.label || card.status}</Tag>
                    <Tag color={CATEGORY_MAP[card.category]?.color}>{CATEGORY_MAP[card.category]?.label || card.category}</Tag>
                    <Text strong>{card.title}</Text>
                  </Space>
                }
                extra={
                  <Space>
                    {card.status === 'PENDING' && (
                      <>
                        <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => openReviewModal(card, 'APPROVED')}>
                          通过
                        </Button>
                        <Button size="small" danger icon={<CloseOutlined />} onClick={() => openReviewModal(card, 'REJECTED')}>
                          驳回
                        </Button>
                      </>
                    )}
                    <Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(card.id)}>
                      删除
                    </Button>
                  </Space>
                }
              >
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  <Paragraph style={{ marginBottom: 0 }}>{card.content}</Paragraph>
                  {card.summary && <Text type="secondary">摘要：{card.summary}</Text>}
                  <Space wrap size={[4, 4]}>
                    {card.tags?.map((tag) => (
                      <Tag key={tag}>{tag}</Tag>
                    ))}
                  </Space>
                  <Text type="secondary">
                    来源知识库：{card.knowledgeBaseName || '-'}
                    {card.entityType ? ` · 实体类型：${card.entityType}` : ''}
                  </Text>
                </Space>
              </Card>
            ))
          )}
        </Space>
      </Spin>

      <Modal
        title={reviewAction === 'APPROVED' ? '审核通过' : '审核驳回'}
        open={reviewModalOpen}
        onOk={submitReview}
        onCancel={() => setReviewModalOpen(false)}
        okText="提交"
        cancelText="取消"
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <div>
            <Text strong>分类</Text>
            <Select
              value={reviewCategory}
              onChange={setReviewCategory}
              style={{ width: '100%', marginTop: 8 }}
              options={Object.entries(CATEGORY_MAP).map(([value, meta]) => ({
                value,
                label: meta.label,
              }))}
            />
          </div>
          <div>
            <Text strong>实体类型</Text>
            <Input
              value={reviewEntityType}
              onChange={(e) => setReviewEntityType(e.target.value)}
              placeholder="可选"
              style={{ marginTop: 8 }}
            />
          </div>
          <div>
            <Text strong>审核备注</Text>
            <TextArea
              value={reviewNote}
              onChange={(e) => setReviewNote(e.target.value)}
              rows={4}
              placeholder="可选"
              style={{ marginTop: 8 }}
            />
          </div>
        </Space>
      </Modal>

      <Modal
        title="批量审核"
        open={batchModalOpen}
        onCancel={() => setBatchModalOpen(false)}
        footer={[
          <Button key="cancel" onClick={() => setBatchModalOpen(false)}>
            取消
          </Button>,
          <Button key="reject" danger onClick={() => submitBatchReview('REJECTED')}>
            批量驳回
          </Button>,
          <Button key="approve" type="primary" onClick={() => submitBatchReview('APPROVED')}>
            批量通过
          </Button>,
        ]}
      >
        <Text>已选择 {selectedIds.length} 张卡片，请确认批量审核操作。</Text>
      </Modal>
    </div>
  );
}
