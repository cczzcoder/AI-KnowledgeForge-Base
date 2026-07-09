import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Spin,
  Empty,
  Card,
  Tag,
  Typography,
  Space,
  Select,
  List,
  Progress,
  Alert,
  Button,
  Steps,
  message,
  Divider,
  Collapse,
} from 'antd';
import {
  ArrowLeftOutlined,
  ReloadOutlined,
  SearchOutlined,
  BulbOutlined,
  CompassOutlined,
  RocketOutlined,
  CheckCircleOutlined,
  AimOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import { listAllKnowledgeBases } from '@/services/knowledgeBaseController';
import {
  getKnowledgeGaps,
  getRecommendations,
  getLearningPath,
  type KnowledgeGap,
  type Recommendation,
  type LearningPath,
} from '@/services/discoveryController';
import './index.css';

const { Text, Title } = Typography;

function DiscoveryPage() {
  const { knowledgeBaseId } = useParams<{ knowledgeBaseId?: string }>();
  const navigate = useNavigate();

  const [kbs, setKbs] = useState<{ id: string; name: string }[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<string | undefined>(
    knowledgeBaseId,
  );
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<
    'gaps' | 'recommendations' | 'learning'
  >('gaps');

  const [gaps, setGaps] = useState<KnowledgeGap[]>([]);
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [learningPath, setLearningPath] = useState<LearningPath | null>(null);
  const [learningPathTopic, setLearningPathTopic] = useState('');

  const reportBackgroundError = useCallback(
    (context: string, error: unknown) => {
      console.warn(`[DiscoveryPage] ${context}`, error);
    },
    [],
  );

  const loadKnowledgeBases = useCallback(async () => {
    try {
      const res = await listAllKnowledgeBases();
      setKbs(res.data.data || []);
    } catch (error) {
      reportBackgroundError('加载知识库列表失败', error);
    }
  }, [reportBackgroundError]);

  const loadGaps = useCallback(async () => {
    const kbId = selectedKbId || knowledgeBaseId;
    if (!kbId) return;
    setLoading(true);
    try {
      const res = await getKnowledgeGaps(kbId);
      setGaps(res.data || []);
    } catch {
      message.error('获取知识盲区失败');
    } finally {
      setLoading(false);
    }
  }, [selectedKbId, knowledgeBaseId]);

  const loadRecommendations = useCallback(async () => {
    const kbId = selectedKbId || knowledgeBaseId;
    if (!kbId) return;
    setLoading(true);
    try {
      const res = await getRecommendations(kbId);
      setRecommendations(res.data || []);
    } catch {
      message.error('获取推荐失败');
    } finally {
      setLoading(false);
    }
  }, [selectedKbId, knowledgeBaseId]);

  const loadLearningPath = useCallback(
    async (topic: string) => {
      const kbId = selectedKbId || knowledgeBaseId;
      if (!kbId || !topic) return;
      setLearningPathTopic(topic);
      setLoading(true);
      try {
        const res = await getLearningPath(kbId, topic);
        setLearningPath(res.data || null);
      } catch {
        message.error('获取学习路径失败');
      } finally {
        setLoading(false);
      }
    },
    [selectedKbId, knowledgeBaseId],
  );

  useEffect(() => {
    loadKnowledgeBases();
  }, [loadKnowledgeBases]);

  useEffect(() => {
    if (selectedKbId || knowledgeBaseId) {
      if (activeTab === 'gaps') loadGaps();
      if (activeTab === 'recommendations') loadRecommendations();
    }
  }, [selectedKbId, knowledgeBaseId, activeTab, loadGaps, loadRecommendations]);

  const getCoverageColor = (level: string) => {
    if (level === '严重不足') return '#ff4d4f';
    if (level === '不足') return '#faad14';
    return '#52c41a';
  };

  return (
    <div className="discovery-page">
      <div className="discovery-header">
        <Space>
          <ArrowLeftOutlined
            className="discovery-back-btn"
            onClick={() =>
              navigate(
                `/knowledge-base${knowledgeBaseId ? `/${knowledgeBaseId}` : ''}`,
              )
            }
          />
          <Title level={4} style={{ margin: 0 }}>
            <CompassOutlined /> 主动知识发现
          </Title>
        </Space>
        <Space>
          <Select
            placeholder="选择知识库"
            value={selectedKbId}
            onChange={(val) => setSelectedKbId(val)}
            style={{ width: 200 }}
            options={kbs.map((kb) => ({ label: kb.name, value: kb.id }))}
            allowClear={false}
          />
          <ReloadOutlined
            className="discovery-reload-btn"
            onClick={() => {
              if (activeTab === 'gaps') loadGaps();
              if (activeTab === 'recommendations') loadRecommendations();
            }}
          />
        </Space>
      </div>

      <div className="discovery-tabs">
        {[
          { key: 'gaps' as const, icon: <SearchOutlined />, label: '知识盲区' },
          {
            key: 'recommendations' as const,
            icon: <BulbOutlined />,
            label: '智能推荐',
          },
          {
            key: 'learning' as const,
            icon: <RocketOutlined />,
            label: '学习路径',
          },
        ].map((tab) => (
          <div
            key={tab.key}
            className={`discovery-tab ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {tab.icon} {tab.label}
          </div>
        ))}
      </div>

      <div className="discovery-content">
        {!selectedKbId && !knowledgeBaseId ? (
          <Empty description="请先选择知识库" />
        ) : loading ? (
          <div style={{ textAlign: 'center', padding: 60 }}>
            <Spin size="large" />
            <div style={{ marginTop: 12, color: '#999', fontSize: 13 }}>
              正在分析知识库...
            </div>
          </div>
        ) : (
          <>
            {activeTab === 'gaps' && (
              <div className="gaps-section">
                <Alert
                  message="知识盲区分析"
                  description="系统自动检测知识库中覆盖不足的领域，帮助您发现需要补充的知识"
                  type="info"
                  showIcon
                  style={{ marginBottom: 24 }}
                />
                {gaps.length === 0 ? (
                  <Empty description="未发现明显知识盲区，知识库覆盖良好" />
                ) : (
                  <List
                    dataSource={gaps}
                    renderItem={(gap) => (
                      <List.Item>
                        <Card
                          style={{ width: '100%' }}
                          size="small"
                          title={
                            <Space>
                              <Tag color={getCoverageColor(gap.coverageLevel)}>
                                {gap.coverageLevel}
                              </Tag>
                              <Text strong>{gap.topic}</Text>
                            </Space>
                          }
                          extra={
                            <Button
                              size="small"
                              type="link"
                              onClick={() => {
                                setActiveTab('learning');
                                loadLearningPath(gap.topic);
                              }}
                            >
                              查看学习路径
                            </Button>
                          }
                        >
                          <div style={{ marginBottom: 12 }}>
                            <Text type="secondary">覆盖密度: </Text>
                            <Progress
                              percent={Math.round(gap.coverageDensity * 100)}
                              size="small"
                              style={{ width: 200 }}
                              strokeColor={getCoverageColor(gap.coverageLevel)}
                            />
                          </div>
                          {gap.missingSubtopics.length > 0 && (
                            <div style={{ marginBottom: 8 }}>
                              <Text type="secondary">缺失子主题: </Text>
                              <Space wrap size={[4, 4]}>
                                {gap.missingSubtopics.map((sub, i) => (
                                  <Tag
                                    key={`${gap.topic}-sub-${i}`}
                                    color="orange"
                                  >
                                    {sub}
                                  </Tag>
                                ))}
                              </Space>
                            </div>
                          )}
                          <Text type="secondary">{gap.suggestion}</Text>
                        </Card>
                      </List.Item>
                    )}
                  />
                )}
              </div>
            )}

            {activeTab === 'recommendations' && (
              <div className="recommendations-section">
                <Alert
                  message="智能推荐"
                  description="根据知识库现状和用户行为，推荐值得探索的知识方向"
                  type="info"
                  showIcon
                  style={{ marginBottom: 24 }}
                />
                {recommendations.length === 0 ? (
                  <Empty description="暂无推荐内容" />
                ) : (
                  <List
                    dataSource={recommendations}
                    renderItem={(rec) => (
                      <List.Item>
                        <Card
                          style={{ width: '100%' }}
                          size="small"
                          title={
                            <Space>
                              <Tag color="blue">优先级 {rec.priority}</Tag>
                              <Text strong>{rec.topic}</Text>
                            </Space>
                          }
                          extra={
                            <Button
                              size="small"
                              type="link"
                              onClick={() => {
                                setActiveTab('learning');
                                loadLearningPath(rec.topic);
                              }}
                            >
                              查看学习路径
                            </Button>
                          }
                        >
                          <Text type="secondary">{rec.reason}</Text>
                          {rec.suggestedResources.length > 0 && (
                            <div style={{ marginTop: 8 }}>
                              <Space wrap size={[4, 4]}>
                                {rec.suggestedResources.map((res, i) => (
                                  <Tag
                                    key={`${rec.topic}-res-${i}`}
                                    color="green"
                                  >
                                    {res}
                                  </Tag>
                                ))}
                              </Space>
                            </div>
                          )}
                        </Card>
                      </List.Item>
                    )}
                  />
                )}
              </div>
            )}

            {activeTab === 'learning' && (
              <div className="learning-section">
                {!learningPath ? (
                  <Empty description="请从知识盲区或智能推荐中选择一个主题查看学习路径" />
                ) : (
                  <>
                    <Alert
                      message={`学习路径：${learningPathTopic}`}
                      description={
                        learningPath.description || learningPath.summary
                      }
                      type="success"
                      showIcon
                      style={{ marginBottom: 24 }}
                    />
                    <Card>
                      <Steps
                        direction="vertical"
                        items={learningPath.steps.map((step, index) => ({
                          title: (
                            <Space>
                              <span>{step.title}</span>
                              <Tag color="processing">第 {index + 1} 步</Tag>
                            </Space>
                          ),
                          description: (
                            <Space
                              direction="vertical"
                              size={4}
                              style={{ width: '100%' }}
                            >
                              <Text type="secondary">{step.description}</Text>
                              {(step.resources || step.recommendedResources)
                                .length > 0 && (
                                <div>
                                  <Text strong>推荐资源：</Text>
                                  <Space
                                    wrap
                                    size={[4, 4]}
                                    style={{ marginTop: 4 }}
                                  >
                                    {(
                                      step.resources ||
                                      step.recommendedResources
                                    ).map((res, i) => (
                                      <Tag
                                        key={`${step.title}-resource-${i}`}
                                        color="purple"
                                      >
                                        {res}
                                      </Tag>
                                    ))}
                                  </Space>
                                </div>
                              )}
                            </Space>
                          ),
                          icon:
                            index === 0 ? (
                              <AimOutlined />
                            ) : index === learningPath.steps.length - 1 ? (
                              <TrophyOutlined />
                            ) : (
                              <CheckCircleOutlined />
                            ),
                        }))}
                      />
                    </Card>
                    <Divider />
                    <Collapse
                      items={[
                        {
                          key: 'tips',
                          label: '学习建议',
                          children: (
                            <Space direction="vertical" size={8}>
                              <Text>• 按步骤顺序逐步学习，避免跳跃式理解</Text>
                              <Text>
                                • 结合当前知识库中的已有内容，优先补足缺失部分
                              </Text>
                              <Text>
                                • 学习完成后可回到知识盲区分析查看覆盖度变化
                              </Text>
                            </Space>
                          ),
                        },
                      ]}
                    />
                  </>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default DiscoveryPage;
