import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Spin, Empty, Card, Tag, Typography, Space, Select, List, Progress, Alert, Button, Steps, message, Divider, Collapse } from 'antd';
import { ArrowLeftOutlined, ReloadOutlined, SearchOutlined, BulbOutlined, CompassOutlined, RocketOutlined, BookOutlined, CheckCircleOutlined, AimOutlined, TrophyOutlined } from '@ant-design/icons';
import { listAllKnowledgeBases } from '@/services/knowledgeBaseController';
import { getKnowledgeGaps, getRecommendations, getLearningPath, type KnowledgeGap, type Recommendation, type LearningPath } from '@/services/discoveryController';
import './index.css';

const { Text, Title } = Typography;

function DiscoveryPage() {
  const { knowledgeBaseId } = useParams<{ knowledgeBaseId?: string }>();
  const navigate = useNavigate();

  const [kbs, setKbs] = useState<{ id: string; name: string }[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<string | undefined>(knowledgeBaseId);
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState<'gaps' | 'recommendations' | 'learning'>('gaps');

  const [gaps, setGaps] = useState<KnowledgeGap[]>([]);
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [learningPath, setLearningPath] = useState<LearningPath | null>(null);
  const [learningPathTopic, setLearningPathTopic] = useState('');

  const loadKnowledgeBases = useCallback(async () => {
    try {
      const res = await listAllKnowledgeBases();
      setKbs(res.data.data || []);
    } catch {
      // silent
    }
  }, []);

  const loadGaps = useCallback(async () => {
    const kbId = selectedKbId || knowledgeBaseId;
    if (!kbId) return;
    setLoading(true);
    try {
      const res = await getKnowledgeGaps(kbId);
      setGaps(res.data.data || []);
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
      setRecommendations(res.data.data || []);
    } catch {
      message.error('获取推荐失败');
    } finally {
      setLoading(false);
    }
  }, [selectedKbId, knowledgeBaseId]);

  const loadLearningPath = useCallback(async (topic: string) => {
    const kbId = selectedKbId || knowledgeBaseId;
    if (!kbId || !topic) return;
    setLearningPathTopic(topic);
    setLoading(true);
    try {
      const res = await getLearningPath(kbId, topic);
      setLearningPath(res.data.data || null);
    } catch {
      message.error('获取学习路径失败');
    } finally {
      setLoading(false);
    }
  }, [selectedKbId, knowledgeBaseId]);

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
            onClick={() => navigate(`/knowledge-base${knowledgeBaseId ? `/${knowledgeBaseId}` : ''}`)}
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
          { key: 'recommendations' as const, icon: <BulbOutlined />, label: '智能推荐' },
          { key: 'learning' as const, icon: <RocketOutlined />, label: '学习路径' },
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
            <div style={{ marginTop: 12, color: '#999', fontSize: 13 }}>正在分析知识库...</div>
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
                              <Tag color={getCoverageColor(gap.coverageLevel)}>{gap.coverageLevel}</Tag>
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
                                  <Tag key={`${gap.topic}-sub-${i}`} color="orange">{sub}</Tag>
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
                                  <Tag key={`${rec.topic}-res-${i}`} color="green">{res}</Tag>
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
                <Alert
                  message="学习路径"
                  description="基于知识图谱分析，为指定主题生成结构化的学习路径，包含核心知识点、推荐资源和预期成果"
                  type="info"
                  showIcon
                  style={{ marginBottom: 24 }}
                />
                {!learningPath ? (
                  <Empty description="点击知识盲区或推荐中的「查看学习路径」按钮来生成学习路径">
                    <div style={{ marginTop: 16 }}>
                      <Text type="secondary">输入主题关键词:</Text>
                      <div style={{ marginTop: 8, display: 'flex', gap: 8, justifyContent: 'center' }}>
                        <Select
                          showSearch
                          style={{ width: 300 }}
                          placeholder="选择或输入主题"
                          value={learningPathTopic || undefined}
                          onChange={(val) => setLearningPathTopic(val)}
                          options={(() => {
                            const seen = new Set<string>();
                            return [...gaps.map((g) => ({ label: g.topic, value: g.topic })), ...recommendations.map((r) => ({ label: r.topic, value: r.topic }))]
                              .filter((opt) => {
                                if (seen.has(opt.value)) return false;
                                seen.add(opt.value);
                                return true;
                              });
                          })()}
                        />
                        <Button
                          type="primary"
                          onClick={() => loadLearningPath(learningPathTopic)}
                          disabled={!learningPathTopic}
                        >
                          生成路径
                        </Button>
                      </div>
                    </div>
                  </Empty>
                ) : (
                  <div>
                    <Title level={5} style={{ marginBottom: 8 }}>
                      <BookOutlined /> 主题: {learningPath.topic}
                    </Title>
                    {learningPath.summary && (
                      <Alert
                        message="路径概览"
                        description={learningPath.summary}
                        type="success"
                        showIcon
                        style={{ marginBottom: 24 }}
                      />
                    )}
                    <Steps
                      direction="vertical"
                      current={-1}
                      items={learningPath.steps.map((step) => ({
                        title: step.title,
                        description: (
                          <div style={{ marginTop: 8 }}>
                            <Text>{step.description}</Text>
                            <br />
                            <Tag
                              style={{ marginTop: 4 }}
                              color={
                                step.status === '已覆盖' ? 'success'
                                  : step.status === '可探索' || step.status === '可实践' ? 'processing'
                                  : 'default'
                              }
                            >
                              {step.status}
                            </Tag>
                            {step.knowledgePoints && step.knowledgePoints.length > 0 && (
                              <div style={{ marginTop: 8 }}>
                                <Text type="secondary" strong>📚 核心知识点：</Text>
                                <ul style={{ margin: '4px 0 0 16px', padding: 0 }}>
                                  {step.knowledgePoints.filter(Boolean).map((kp, i) => (
                                    <li key={`${step.title}-kp-${i}`}><Text type="secondary">{kp}</Text></li>
                                  ))}
                                </ul>
                              </div>
                            )}
                            {step.recommendedResources && step.recommendedResources.length > 0 && (
                              <div style={{ marginTop: 4 }}>
                                <Text type="secondary" strong>🔗 推荐资源：</Text>
                                <Space wrap size={[4, 4]} style={{ marginLeft: 4 }}>
                                  {step.recommendedResources.filter(Boolean).map((res, i) => (
                                    <Tag key={`${step.title}-rec-${i}`} color="blue">{res}</Tag>
                                  ))}
                                </Space>
                              </div>
                            )}
                            {step.expectedOutcomes && step.expectedOutcomes.length > 0 && (
                              <div style={{ marginTop: 4 }}>
                                <Text type="secondary" strong>🎯 预期成果：</Text>
                                <ul style={{ margin: '4px 0 0 16px', padding: 0 }}>
                                  {step.expectedOutcomes.filter(Boolean).map((outcome, i) => (
                                    <li key={`${step.title}-out-${i}`}><Text type="secondary">{outcome}</Text></li>
                                  ))}
                                </ul>
                              </div>
                            )}
                          </div>
                        ),
                      }))}
                    />

                    {learningPath.example && (
                      <>
                        <Divider />
                        <div style={{ marginTop: 24 }}>
                          <Title level={5}>
                            <TrophyOutlined /> 学习路径示例
                          </Title>
                          <Alert
                            message={learningPath.example.topic}
                            description={
                              <Space direction="vertical" size={4}>
                                <Text strong>总体目标：</Text>
                                <Text>{learningPath.example.overallGoal}</Text>
                              </Space>
                            }
                            type="warning"
                            showIcon
                            style={{ marginBottom: 16 }}
                          />
                          <Collapse
                            items={learningPath.example.exampleSteps.map((exStep) => ({
                              key: exStep.stage.toString(),
                              label: (
                                <Space>
                                  <Tag color="volcano">阶段 {exStep.stage}</Tag>
                                  <Text strong>{exStep.stageName}</Text>
                                  <Tag color="blue">{exStep.duration}</Tag>
                                </Space>
                              ),
                              children: (
                                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                                  <div>
                                    <Text strong><AimOutlined /> 目标：</Text>
                                    <Text>{exStep.objective}</Text>
                                  </div>
                                  <div>
                                    <Text strong><BookOutlined /> 核心内容：</Text>
                                    <ul style={{ margin: '4px 0 0 16px', padding: 0 }}>
                                      {exStep.coreContents.map((c, i) => (
                                        <li key={`${exStep.stageName}-core-${i}`}><Text>{c}</Text></li>
                                      ))}
                                    </ul>
                                  </div>
                                  <div>
                                    <Text strong><CheckCircleOutlined /> 成果：</Text>
                                    <Tag color="green">{exStep.outcome}</Tag>
                                  </div>
                                </Space>
                              ),
                            }))}
                          />
                        </div>
                      </>
                    )}
                  </div>
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