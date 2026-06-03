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

  // 知识库列表与选中
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

  // 选中状态
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  // 审核弹窗
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [reviewingCard, setReviewingCard] = useState<KnowledgeCardDTO | null>(null);
  const [reviewAction, setReviewAction] = useState<'APPROVED' | 'REJECTED'>('APPROVED');
  const [reviewNote, setReviewNote] = useState('');
  const [reviewCategory, setReviewCategory] = useState('');
  const [reviewEntityType, setReviewEntityType] = useState('');

  // 批量审核弹窗
  const [batchModalOpen, setBatchModalOpen] = useState(false);

  // 加载知识库列表
  const loadKnowledgeBases = useCallback(async () => {
    try {
      const res = await listAllKnowledgeBases();
      const kbs = res.data?.data;
      setKnowledgeBases(Array.isArray(kbs) ? kbs : []);
    } catch {
      // silent
    }
  }, []);

  // 同步URL参数到选中状态
  useEffect(() => {
    if (paramKbId && paramKbId !== selectedKbId) {
      setSelectedKbId(paramKbId);
    }
  }, [paramKbId]);

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
    } catch {
      // ignore
    }
  }, [kbId]);

  useEffect(() => {
    loadKnowledgeBases();
  }, [loadKnowledgeBases]);

  useEffect(() => {
    loadCards();
    loadPendingCount();
  }, [loadCards, loadPendingCount]);

  // 切换知识库
  const handleKbChange = (val: string) => {
    setSelectedKbId(val);
    setPage(0);
    setSelectedIds([]);
    navigate(`/knowledge-cards/${val}`, { replace: true });
  };

  // 全选/取消
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

  // 打开审核弹窗
  const openReviewModal = (card: KnowledgeCardDTO, action: 'APPROVED' | 'REJECTED') => {
    setReviewingCard(card);
    setReviewAction(action);
    setReviewNote('');
    setReviewCategory(card.category);
    setReviewEntityType(card.entityType || '');
    setReviewModalOpen(true);
  };

  // 提交审核
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

  // 批量审核
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

  // 删除卡片
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
      {/* 页头 */}
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

      {/* 卡片列表 */}
      <Spin spinning={loading}>
        {cards.length === 0 ? (
          <Empty description="暂无知识卡片" style={{ marginTop: 80 }} />
        ) : (
          <>
            {/* 全选栏 */}
            {statusFilter === 'PENDING' && (
              <Checkbox
                checked={allSelected}
                indeterminate={selectedIds.length > 0 && !allSelected}
                onChange={toggleSelectAll}
                style={{ marginBottom: 12 }}
              >
                全选
              </Checkbox>
            )}

            {/* 卡片列表 */}
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              {cards.map((card) => (
                <Card
                  key={card.id}
                  size="small"
                  title={
                    <Space>
                      {statusFilter === 'PENDING' && (
                        <Checkbox
                          checked={selectedIds.includes(card.id)}
                          onChange={() => toggleSelect(card.id)}
                        />
                      )}
                      <Text strong>{card.title}</Text>
                      {card.category && CATEGORY_MAP[card.category] && (
                        <Tag color={CATEGORY_MAP[card.category].color}>
                          {CATEGORY_MAP[card.category].label}
                        </Tag>
                      )}
                      {card.entityType && <Tag>{card.entityType}</Tag>}
                    </Space>
                  }
                  extra={
                    <Tag color={STATUS_MAP[card.status]?.color}>
                      {STATUS_MAP[card.status]?.label}
                    </Tag>
                  }
                  actions={
                    card.status === 'PENDING'
                      ? [
                          <Button
                            type="primary"
                            size="small"
                            icon={<CheckOutlined />}
                            onClick={() => openReviewModal(card, 'APPROVED')}
                          >
                            通过
                          </Button>,
                          <Button
                            danger
                            size="small"
                            icon={<CloseOutlined />}
                            onClick={() => openReviewModal(card, 'REJECTED')}
                          >
                            驳回
                          </Button>,
                          <Button
                            size="small"
                            icon={<DeleteOutlined />}
                            onClick={() => handleDelete(card.id)}
                          >
                            删除
                          </Button>,
                        ]
                      : [
                          <Button
                            size="small"
                            icon={<DeleteOutlined />}
                            onClick={() => handleDelete(card.id)}
                          >
                            删除
                          </Button>,
                        ]
                  }
                >
                  <Paragraph ellipsis={{ rows: 2, expandable: true, symbol: '展开' }}>
                    {card.content}
                  </Paragraph>
                  {card.reviewerNote && (
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      审核备注: {card.reviewerNote}
                    </Text>
                  )}
                  <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>
                    {card.createdAt && new Date(card.createdAt).toLocaleString('zh-CN')}
                    {card.vectorized && <Tag color="green" style={{ marginLeft: 8 }}>已向量化</Tag>}
                  </div>
                </Card>
              ))}
            </Space>

            {/* 分页 */}
            {total > pageSize && (
              <div style={{ textAlign: 'center', marginTop: 20 }}>
                <Button
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                  style={{ marginRight: 8 }}
                >
                  上一页
                </Button>
                <Text>
                  第 {page + 1} / {Math.ceil(total / pageSize)} 页，共 {total} 条
                </Text>
                <Button
                  disabled={(page + 1) * pageSize >= total}
                  onClick={() => setPage((p) => p + 1)}
                  style={{ marginLeft: 8 }}
                >
                  下一页
                </Button>
              </div>
            )}
          </>
        )}
      </Spin>

      {/* 审核弹窗 */}
      <Modal
        title={`${reviewAction === 'APPROVED' ? '通过' : '驳回'}知识卡片`}
        open={reviewModalOpen}
        onOk={submitReview}
        onCancel={() => setReviewModalOpen(false)}
        okText={reviewAction === 'APPROVED' ? '确认通过' : '确认驳回'}
        okType={reviewAction === 'APPROVED' ? 'primary' : 'danger'}
        width={560}
      >
        <div style={{ marginBottom: 16 }}>
          <Text strong>标题: </Text>
          <Text>{reviewingCard?.title}</Text>
        </div>
        <div style={{ marginBottom: 16 }}>
          <Text strong>内容: </Text>
          <Paragraph style={{ background: '#fafafa', padding: 12, borderRadius: 6, marginTop: 4 }}>
            {reviewingCard?.content}
          </Paragraph>
        </div>
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>
            <Text strong>分类: </Text>
            <Select
              value={reviewCategory}
              onChange={setReviewCategory}
              style={{ width: 200, marginLeft: 8 }}
              options={[
                { value: 'CONCEPT', label: '概念' },
                { value: 'FACT', label: '事实' },
                { value: 'RULE', label: '规则' },
                { value: 'INSIGHT', label: '见解' },
              ]}
            />
          </div>
          <div>
            <Text strong>实体类型: </Text>
            <Select
              value={reviewEntityType}
              onChange={setReviewEntityType}
              style={{ width: 200, marginLeft: 8 }}
              allowClear
              placeholder="可选：人物/地点/概念/事件..."
              options={[
                { value: '人物', label: '人物' },
                { value: '地点', label: '地点' },
                { value: '概念', label: '概念' },
                { value: '事件', label: '事件' },
                { value: '作品', label: '作品' },
                { value: '组织', label: '组织' },
              ]}
            />
          </div>
          <div>
            <Text strong>审核备注: </Text>
            <TextArea
              value={reviewNote}
              onChange={(e) => setReviewNote(e.target.value)}
              placeholder="可选：填写审核意见"
              rows={3}
              style={{ marginTop: 4 }}
            />
          </div>
        </Space>
      </Modal>

      {/* 批量审核弹窗 */}
      <Modal
        title="批量审核"
        open={batchModalOpen}
        onCancel={() => setBatchModalOpen(false)}
        footer={[
          <Button key="cancel" onClick={() => setBatchModalOpen(false)}>
            取消
          </Button>,
          <Button
            key="reject"
            danger
            onClick={() => submitBatchReview('REJECTED')}
          >
            全部驳回
          </Button>,
          <Button
            key="approve"
            type="primary"
            onClick={() => submitBatchReview('APPROVED')}
          >
            全部通过
          </Button>,
        ]}
      >
        <p>已选择 {selectedIds.length} 张卡片，请选择批量操作：</p>
      </Modal>
    </div>
  );
}