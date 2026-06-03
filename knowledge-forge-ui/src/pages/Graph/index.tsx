import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Spin, Empty, Card, Tag, Typography, Space, Select, Input, Drawer, Button, List, message, Divider, AutoComplete, Segmented } from 'antd';
import { ArrowLeftOutlined, ReloadOutlined, NodeIndexOutlined, ZoomInOutlined, ZoomOutOutlined, ExpandOutlined, SearchOutlined, LinkOutlined, PartitionOutlined, ApartmentOutlined, RadarChartOutlined } from '@ant-design/icons';
import { getGraph, getEntities, getEntityDetail, getSubgraph, searchGraph, type GraphNode, type GraphEdge, type GraphData, type EntityDetail, type RelatedEntity } from '@/services/graphController';
import { listAllKnowledgeBases } from '@/services/knowledgeBaseController';
import './index.css';

const { Text, Title } = Typography;

/* ================================================================
 * 类型定义
 * ================================================================ */
interface Position {
  x: number;
  y: number;
}

type LayoutType = 'force' | 'hierarchical' | 'circular';

/* ================================================================
 * 常量配置
 * ================================================================ */
const ENTITY_COLORS: Record<string, string> = {
  '人物': '#ff6b6b',
  '地点': '#4ecdc4',
  '组织': '#45b7d1',
  '概念': '#96ceb4',
  '事件': '#ffeaa7',
  '时间': '#dfe6e9',
  '作品': '#a29bfe',
  '默认': '#74b9ff',
};

const LAYOUT_OPTIONS: { label: string; value: LayoutType; icon: React.ReactNode }[] = [
  { label: '力导向', value: 'force', icon: <PartitionOutlined /> },
  { label: '水平分层', value: 'hierarchical', icon: <ApartmentOutlined /> },
  { label: '环形', value: 'circular', icon: <RadarChartOutlined /> },
];

const DEFAULT_SVG_SIZE = { width: 1400, height: 1050 };

function getEntityColor(type: string): string {
  return ENTITY_COLORS[type] || ENTITY_COLORS['默认'];
}

/** 根据度计算节点半径 (对数缩放) */
function calcNodeRadius(degree: number): number {
  return Math.min(14 + Math.log2(degree + 1) * 4, 32);
}

/* ================================================================
 * 主组件
 * ================================================================ */
export default function GraphPage() {
  const { knowledgeBaseId } = useParams<{ knowledgeBaseId?: string }>();
  const navigate = useNavigate();
  const svgRef = useRef<SVGSVGElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const workerRef = useRef<Worker | null>(null);

  /* ---------- 数据状态 ---------- */
  const [loading, setLoading] = useState(false);
  const [layoutComputing, setLayoutComputing] = useState(false);
  const [graphData, setGraphData] = useState<GraphData | null>(null);
  const [baseNodes, setBaseNodes] = useState<GraphNode[]>([]);
  const [edges, setEdges] = useState<GraphEdge[]>([]);
  const [positions, setPositions] = useState<Map<string, Position>>(new Map());
  const [svgSize, setSvgSize] = useState(DEFAULT_SVG_SIZE);
  const [layoutType, setLayoutType] = useState<LayoutType>('force');

  /* ---------- 交互状态 ---------- */
  const [hoveredNode, setHoveredNode] = useState<GraphNode | null>(null);
  const [hoveredEdge, setHoveredEdge] = useState<GraphEdge | null>(null);
  const mousePosRef = useRef<Position>({ x: 0, y: 0 });
  const hoveredNodeRef = useRef<GraphNode | null>(null);
  const [transform, setTransform] = useState({ x: 0, y: 0, scale: 1 });
  const isDraggingRef = useRef(false);
  const dragStartRef = useRef<Position>({ x: 0, y: 0 });
  const transformRef = useRef(transform);
  transformRef.current = transform;

  /* ---------- 搜索 / 详情 / 子图 ---------- */
  const [kbs, setKbs] = useState<{ id: string; name: string }[]>([]);
  const [selectedKbId, setSelectedKbId] = useState<string | undefined>(knowledgeBaseId);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState<GraphNode[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedEntity, setSelectedEntity] = useState<GraphNode | null>(null);
  const [entityDetail, setEntityDetail] = useState<EntityDetail | null>(null);
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [subgraphView, setSubgraphView] = useState(false);
  const [subgraphCenterName, setSubgraphCenterName] = useState<string | null>(null);

  /* ---------- 预计算节点度 ---------- */
  const nodeDegrees = useMemo(() => {
    const degree = new Map<string, number>();
    for (const n of baseNodes) degree.set(n.id, 0);
    for (const e of edges) {
      degree.set(e.sourceId, (degree.get(e.sourceId) || 0) + 1);
      degree.set(e.targetId, (degree.get(e.targetId) || 0) + 1);
    }
    return degree;
  }, [baseNodes, edges]);

  /* ---------- 视口裁剪：只渲染可见区域内的节点和边 ---------- */
  const containerSize = useRef({ width: 800, height: 600 });
  // 监听容器尺寸变化
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const observer = new ResizeObserver(() => {
      containerSize.current = { width: el.clientWidth, height: el.clientHeight };
    });
    observer.observe(el);
    containerSize.current = { width: el.clientWidth, height: el.clientHeight };
    return () => observer.disconnect();
  }, []);
  const visibleNodeIds = useMemo(() => {
    const ids = new Set<string>();
    const { x: tx, y: ty, scale } = transform;
    const cw = containerSize.current.width;
    const ch = containerSize.current.height;
    // SVG viewBox 坐标 → 屏幕像素的转换比例
    const svgToScreenX = cw / svgSize.width;
    const svgToScreenY = ch / svgSize.height;
    const margin = 150 / Math.max(scale * Math.max(svgToScreenX, svgToScreenY), 0.1);

    for (const node of baseNodes) {
      const pos = positions.get(node.id);
      if (!pos) continue;
      // SVG transform: translate(tx, ty) scale(s), 再映射到屏幕像素
      const sx = (pos.x * scale + tx) * svgToScreenX;
      const sy = (pos.y * scale + ty) * svgToScreenY;
      if (sx > -margin && sx < cw + margin && sy > -margin && sy < ch + margin) {
        ids.add(node.id);
      }
    }
    return ids;
  }, [baseNodes, positions, transform, svgSize]);
  const parallelEdgeGroups = useMemo(() => {
    const groups = new Map<string, GraphEdge[]>();
    for (const e of edges) {
      const key = [e.sourceId, e.targetId].sort().join('::');
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(e);
    }
    return groups;
  }, [edges]);

  /* ---- 加载知识库列表 ---- */
  useEffect(() => {
    listAllKnowledgeBases().then((res) => {
      setKbs(res.data.data || []);
    }).catch(() => {});
  }, []);

  /* ---- 加载图谱数据 ---- */
  const loadGraph = useCallback(async (kbId: string) => {
    setLoading(true);
    try {
      const res = await getGraph(kbId);
      const data = res.data.data;
      if (data) {
        setGraphData(data);
        setEdges(data.edges || []);
        setBaseNodes(data.nodes || []);
      }
    } catch {
      message.error('加载知识图谱失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const kbId = selectedKbId || knowledgeBaseId;
    if (kbId) loadGraph(kbId);
  }, [selectedKbId, knowledgeBaseId, loadGraph]);

  /* ---- 布局计算（Web Worker 异步） ---- */
  useEffect(() => {
    if (baseNodes.length === 0) {
      setPositions(new Map());
      setSvgSize(DEFAULT_SVG_SIZE);
      return;
    }
    setLayoutComputing(true);

    // 创建 Worker
    if (workerRef.current) workerRef.current.terminate();
    const worker = new Worker(new URL('./layout.worker.ts', import.meta.url));
    workerRef.current = worker;

    const nodeRadiuses = baseNodes.map((n) => calcNodeRadius(nodeDegrees.get(n.id) || 0));
    const edgeIndices = edges.map((e) => ({
      sourceIndex: baseNodes.findIndex((n) => n.id === e.sourceId),
      targetIndex: baseNodes.findIndex((n) => n.id === e.targetId),
    })).filter((e) => e.sourceIndex !== -1 && e.targetIndex !== -1);

    worker.onmessage = (e: MessageEvent<{ x: number; y: number }[]>) => {
      const newPositions = new Map<string, Position>();
      baseNodes.forEach((n, i) => {
        newPositions.set(n.id, e.data[i] || { x: 700, y: 525 });
      });
      setPositions(newPositions);
      setSvgSize(layoutType === 'circular' ? { width: 1200, height: 900 } : DEFAULT_SVG_SIZE);
      setLayoutComputing(false);
      worker.terminate();
      workerRef.current = null;
    };

    worker.onerror = () => {
      setLayoutComputing(false);
      worker.terminate();
      workerRef.current = null;
    };

    worker.postMessage({
      type: layoutType,
      nodeCount: baseNodes.length,
      edges: edgeIndices,
      nodeRadiuses,
    });

    return () => {
      if (workerRef.current) {
        workerRef.current.terminate();
        workerRef.current = null;
      }
    };
  }, [baseNodes, edges, layoutType, nodeDegrees]);

  /* ---------- 交互处理 ---------- */
  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault();
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const cursorX = e.clientX - rect.left;
    const cursorY = e.clientY - rect.top;
    const delta = e.deltaY > 0 ? -0.1 : 0.1;
    const newScale = Math.max(0.15, Math.min(3, transformRef.current.scale + delta));
    const ratio = newScale / transformRef.current.scale;
    setTransform({
      x: cursorX - (cursorX - transformRef.current.x) * ratio,
      y: cursorY - (cursorY - transformRef.current.y) * ratio,
      scale: newScale,
    });
  }, []);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (e.button !== 0) return;
    isDraggingRef.current = true;
    dragStartRef.current = { x: e.clientX - transformRef.current.x, y: e.clientY - transformRef.current.y };
  }, []);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    mousePosRef.current = { x: e.clientX - rect.left, y: e.clientY - rect.top };
    if (!isDraggingRef.current) return;
    setTransform((prev) => ({
      ...prev,
      x: e.clientX - dragStartRef.current.x,
      y: e.clientY - dragStartRef.current.y,
    }));
  }, []);

  const handleMouseUp = useCallback(() => { isDraggingRef.current = false; }, []);

  const handleZoomIn = useCallback(() => {
    setTransform((prev) => {
      const newScale = Math.min(3, prev.scale + 0.2);
      const ratio = newScale / prev.scale;
      const cx = (containerRef.current?.clientWidth ?? 800) / 2;
      const cy = (containerRef.current?.clientHeight ?? 600) / 2;
      return { x: cx - (cx - prev.x) * ratio, y: cy - (cy - prev.y) * ratio, scale: newScale };
    });
  }, []);

  const handleZoomOut = useCallback(() => {
    setTransform((prev) => {
      const newScale = Math.max(0.15, prev.scale - 0.2);
      const ratio = newScale / prev.scale;
      const cx = (containerRef.current?.clientWidth ?? 800) / 2;
      const cy = (containerRef.current?.clientHeight ?? 600) / 2;
      return { x: cx - (cx - prev.x) * ratio, y: cy - (cy - prev.y) * ratio, scale: newScale };
    });
  }, []);

  const handleResetZoom = useCallback(() => {
    setTransform({ x: 0, y: 0, scale: 1 });
  }, []);

  /* ---- 搜索 ---- */
  const handleSearch = useCallback(async (keyword: string) => {
    setSearchKeyword(keyword);
    if (!keyword.trim()) { setSearchResults([]); return; }
    const kbId = selectedKbId || knowledgeBaseId;
    if (!kbId) return;
    setSearching(true);
    try {
      const res = await searchGraph(kbId, keyword.trim());
      setSearchResults(res.data.data || []);
    } catch { message.error('搜索失败'); }
    finally { setSearching(false); }
  }, [selectedKbId, knowledgeBaseId]);

  const handleSearchSelect = useCallback(async (value: string) => {
    const node = searchResults.find((n) => n.name === value);
    if (node) await handleNodeClick(node);
  }, [searchResults]);

  /* ---- 节点点击 / 子图 ---- */
  const handleNodeClick = useCallback(async (node: GraphNode) => {
    setSelectedEntity(node);
    setDetailVisible(true);
    setDetailLoading(true);
    const kbId = selectedKbId || knowledgeBaseId;
    if (!kbId) { setDetailLoading(false); return; }
    try {
      const res = await getEntityDetail(node.name, kbId);
      setEntityDetail(res.data.data || null);
    } catch { message.error('获取实体详情失败'); }
    finally { setDetailLoading(false); }
  }, [selectedKbId, knowledgeBaseId]);

  const handleViewSubgraph = useCallback(async () => {
    if (!selectedEntity) return;
    const kbId = selectedKbId || knowledgeBaseId;
    if (!kbId) return;
    setLoading(true);
    setDetailVisible(false);
    setSubgraphView(true);
    setSubgraphCenterName(selectedEntity.name);
    try {
      const res = await getSubgraph(kbId, selectedEntity.name);
      const data = res.data.data;
      if (data) {
        setGraphData(data);
        setEdges(data.edges || []);
        setBaseNodes(data.nodes || []);
      }
    } catch { message.error('获取子图失败'); }
    finally { setLoading(false); }
  }, [selectedEntity, selectedKbId, knowledgeBaseId]);

  const handleBackToFullGraph = useCallback(async () => {
    const kbId = selectedKbId || knowledgeBaseId;
    if (!kbId) return;
    setSubgraphView(false);
    setSubgraphCenterName(null);
    setSelectedEntity(null);
    await loadGraph(kbId);
  }, [selectedKbId, knowledgeBaseId, loadGraph]);

  /* ================================================================
   * 渲染：边线（贝塞尔曲线 + 平行边弧线偏移）
   * ================================================================ */
  const renderEdges = useMemo(() => {
    const edgeOffsetMap = new Map<string, number>();
    for (const [key, group] of parallelEdgeGroups) {
      group.forEach((e, i) => {
        const total = group.length;
        const offset = total === 1 ? 0 : (i - (total - 1) / 2) * 22;
        edgeOffsetMap.set(e.id, offset);
      });
    }

    return edges.filter((e) => visibleNodeIds.has(e.sourceId) || visibleNodeIds.has(e.targetId))
      .map((edge) => {
      const source = positions.get(edge.sourceId);
      const target = positions.get(edge.targetId);
      if (!source || !target) return null;
      const isHovered = hoveredEdge?.id === edge.id;
      const isSamePair = edge.sourceId === edge.targetId;
      const offset = edgeOffsetMap.get(edge.id) || 0;

      const stroke = isHovered ? '#1677ff' : '#c0c0c0';
      const strokeWidth = isHovered ? 2.5 : 1.2;
      const midX = (source.x + target.x) / 2;
      const midY = (source.y + target.y) / 2;
      const dx = target.x - source.x;
      const dy = target.y - source.y;
      const dist = Math.sqrt(dx * dx + dy * dy) || 1;
      // 法线方向（在块外声明，供下方 hover 文本使用）
      const nx = -dy / dist;
      const ny = dx / dist;

      let pathD: string;
      if (isSamePair) {
        // 自环
        const r = 30;
        pathD = `M ${source.x} ${source.y - r} A ${r} ${r} 0 1 1 ${source.x + r} ${source.y}`;
      } else {
        // 三次贝塞尔曲线，垂线偏移
        const cx = midX + nx * offset;
        const cy = midY + ny * offset;
        const cpDist = dist * 0.3;
        const cp1x = source.x + (dx / dist) * cpDist + nx * offset * 0.5;
        const cp1y = source.y + (dy / dist) * cpDist + ny * offset * 0.5;
        const cp2x = target.x - (dx / dist) * cpDist + nx * offset * 0.5;
        const cp2y = target.y - (dy / dist) * cpDist + ny * offset * 0.5;
        pathD = `M ${source.x} ${source.y} C ${cp1x} ${cp1y} ${cp2x} ${cp2y} ${target.x} ${target.y}`;
      }

      return (
        <g key={edge.id}>
          <path
            d={pathD}
            fill="none"
            stroke={stroke}
            strokeWidth={strokeWidth}
            strokeDasharray={undefined}
            markerEnd={!isSamePair ? `url(#arrow)` : undefined}
            style={{ cursor: 'pointer', transition: 'stroke 0.2s' }}
            onMouseEnter={() => setHoveredEdge(edge)}
            onMouseLeave={() => setHoveredEdge(null)}
          />
          {isHovered && (
            <text
              x={midX + (offset ? nx * offset * 1.5 : 0)}
              y={midY + (offset ? ny * offset * 1.5 : -8)}
              textAnchor="middle"
              fontSize="11"
              fill="#1677ff"
              fontWeight="bold"
              style={{ pointerEvents: 'none' }}
            >
              {edge.relationType}
            </text>
          )}
        </g>
      );
    });
  }, [edges, positions, hoveredEdge, parallelEdgeGroups, visibleNodeIds]);

  /* ================================================================
   * 渲染：节点
   * ================================================================ */
  const renderNodes = useMemo(() => {
    return baseNodes.filter((n) => visibleNodeIds.has(n.id)).map((node) => {
      const pos = positions.get(node.id);
      if (!pos) return null;
      const isHovered = hoveredNode?.id === node.id;
      const isSelected = selectedEntity?.id === node.id;
      const color = getEntityColor(node.entityType);
      const degree = nodeDegrees.get(node.id) || 0;
      const radius = calcNodeRadius(degree);
      const label = node.name.length > 10 ? node.name.slice(0, 10) + '…' : node.name;

      return (
        <g
          key={node.id}
          style={{ cursor: 'pointer' }}
          onMouseEnter={() => { setHoveredNode(node); hoveredNodeRef.current = node; }}
          onMouseLeave={() => { setHoveredNode(null); hoveredNodeRef.current = null; }}
          onClick={() => handleNodeClick(node)}
        >
          {/* 选中高亮环 */}
          {isSelected && (
            <circle cx={pos.x} cy={pos.y} r={radius + 6} fill="none" stroke="#1677ff" strokeWidth={2.5} opacity={0.5} />
          )}
          {/* 节点主体 */}
          <circle
            cx={pos.x} cy={pos.y} r={radius}
            fill={color}
            stroke={isHovered ? '#fff' : 'none'}
            strokeWidth={isHovered ? 2.5 : 0}
            opacity={isHovered ? 1 : 0.9}
            style={{ transition: 'all 0.2s' }}
          />
          {/* 标签 */}
          <text
            x={pos.x} y={pos.y + radius + 13}
            textAnchor="middle"
            fontSize={isHovered ? 12 : 10}
            fill={isHovered || isSelected ? '#1677ff' : '#555'}
            fontWeight={isHovered || isSelected ? 'bold' : 'normal'}
            style={{ transition: 'all 0.2s', pointerEvents: 'none', userSelect: 'none' }}
          >
            {label}
          </text>
        </g>
      );
    });
  }, [baseNodes, positions, hoveredNode, selectedEntity, nodeDegrees, handleNodeClick, visibleNodeIds]);

  /* ================================================================
   * 渲染
   * ================================================================ */
  return (
    <div className="graph-page">
      <div className="graph-header">
        <Space>
          <ArrowLeftOutlined
            className="graph-back-btn"
            onClick={() => navigate(`/knowledge-base${knowledgeBaseId ? `/${knowledgeBaseId}` : ''}`)}
          />
          <Title level={4} style={{ margin: 0 }}>
            <NodeIndexOutlined /> 知识图谱
            {subgraphView && subgraphCenterName && (
              <Tag color="purple" style={{ marginLeft: 8, fontSize: 12 }}>
                子图: {subgraphCenterName}
              </Tag>
            )}
          </Title>
        </Space>
        <Space>
          {subgraphView && (
            <Button size="small" onClick={handleBackToFullGraph}>返回完整图谱</Button>
          )}
          <AutoComplete
            style={{ width: 240 }}
            value={searchKeyword}
            options={searchResults.map((n) => ({
              label: (
                <Space>
                  <Tag color={getEntityColor(n.entityType)} style={{ fontSize: 10, lineHeight: '16px' }}>
                    {n.entityType}
                  </Tag>
                  <span>{n.name}</span>
                </Space>
              ),
              value: n.name,
            }))}
            onSearch={handleSearch}
            onSelect={handleSearchSelect}
            notFoundContent={searching ? <Spin size="small" /> : (searchKeyword ? '未找到匹配实体' : null)}
          >
            <Input prefix={<SearchOutlined />} placeholder="搜索实体..." allowClear onClear={() => setSearchResults([])} />
          </AutoComplete>
          <Select
            placeholder="选择知识库"
            value={selectedKbId}
            onChange={(val) => setSelectedKbId(val)}
            style={{ width: 200 }}
            options={kbs.map((kb) => ({ label: kb.name, value: kb.id }))}
            allowClear={false}
          />
          <ReloadOutlined
            className="graph-reload-btn"
            onClick={() => {
              const kbId = selectedKbId || knowledgeBaseId;
              if (kbId) {
                if (subgraphView && selectedEntity) handleViewSubgraph();
                else loadGraph(kbId);
              }
            }}
          />
        </Space>
      </div>

      <Card className="graph-card">
        {loading ? (
          <div className="graph-loading">
            <Spin size="large" />
            <div style={{ marginTop: 12, color: '#999', fontSize: 13 }}>加载知识图谱...</div>
          </div>
        ) : !graphData || graphData.nodeCount === 0 ? (
          <Empty
            description={
              <span>
                暂无图谱数据
                <br />
                <Text type="secondary">上传文档后，系统会自动抽取实体关系构建知识图谱</Text>
              </span>
            }
          />
        ) : (
          <div
            className="graph-container"
            ref={containerRef}
            onWheel={handleWheel}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
          >
            {/* 布局切换 + 统计 */}
            <div className="graph-toolbar">
              <Segmented
                size="small"
                value={layoutType}
                options={LAYOUT_OPTIONS.map((o) => ({
                  label: o.label,
                  value: o.value,
                  icon: o.icon,
                }))}
                onChange={(val) => setLayoutType(val as LayoutType)}
              />
              <Space size={4}>
                <Tag color="blue">实体: {graphData.nodeCount}</Tag>
                <Tag color="green">关系: {graphData.edgeCount}</Tag>
              </Space>
            </div>

            {/* 布局计算中遮罩 */}
            {layoutComputing && (
              <div className="graph-layout-computing">
                <Spin size="small" />
                <span style={{ marginLeft: 8, fontSize: 13, color: '#999' }}>计算布局中...</span>
              </div>
            )}

            <svg
              ref={svgRef}
              className="graph-svg"
              viewBox={`0 0 ${svgSize.width} ${svgSize.height}`}
              width="100%"
              height="100%"
            >
              <defs>
                <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse">
                  <path d="M 0 0 L 10 5 L 0 10 z" fill="#999" />
                </marker>
                <marker id="arrow-hover" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse">
                  <path d="M 0 0 L 10 5 L 0 10 z" fill="#1677ff" />
                </marker>
              </defs>
              <g transform={`translate(${transform.x},${transform.y}) scale(${transform.scale})`}>
                {renderEdges}
                {renderNodes}
              </g>
            </svg>

            {/* 悬浮卡片 */}
            {hoveredNode && (() => {
              const cardWidth = 260, cardHeight = 140;
              const mp = mousePosRef.current;
              let left = mp.x + 12, top = mp.y - 10;
              if (containerRef.current) {
                const cw = containerRef.current.clientWidth, ch = containerRef.current.clientHeight;
                if (left + cardWidth > cw - 8) left = mp.x - cardWidth - 12;
                if (top + cardHeight > ch - 8) top = ch - cardHeight - 8;
                if (left < 4) left = 4;
                if (top < 4) top = 4;
              }
              return (
                <div
                  className="entity-card"
                  style={{ left, top }}
                  onMouseEnter={() => { setHoveredNode(hoveredNode); hoveredNodeRef.current = hoveredNode; }}
                  onMouseLeave={() => { setHoveredNode(null); hoveredNodeRef.current = null; }}
                >
                  <div className="entity-card-name">{hoveredNode.name}</div>
                  <div className="entity-card-type">
                    <Tag color={getEntityColor(hoveredNode.entityType)}>{hoveredNode.entityType}</Tag>
                  </div>
                  {hoveredNode.description && (
                    <div className="entity-card-desc">{hoveredNode.description}</div>
                  )}
                  <div className="entity-card-rel">关联: {hoveredNode.relationCount} 条</div>
                </div>
              );
            })()}

            {/* 缩放控件 */}
            <div className="graph-zoom-controls">
              <ZoomInOutlined className="graph-zoom-btn" onClick={handleZoomIn} title="放大" />
              <ZoomOutOutlined className="graph-zoom-btn" onClick={handleZoomOut} title="缩小" />
              <ExpandOutlined className="graph-zoom-btn" onClick={handleResetZoom} title="重置" />
            </div>
          </div>
        )}
      </Card>

      {/* 实体详情抽屉 */}
      <Drawer
        title={
          <Space>
            <NodeIndexOutlined />
            <span>实体详情</span>
            {selectedEntity && <Tag color={getEntityColor(selectedEntity.entityType)}>{selectedEntity.entityType}</Tag>}
          </Space>
        }
        placement="right"
        width={420}
        open={detailVisible}
        onClose={() => { setDetailVisible(false); setEntityDetail(null); }}
        extra={
          <Button type="primary" icon={<LinkOutlined />} onClick={handleViewSubgraph}>查看子图</Button>
        }
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin />
            <div style={{ marginTop: 12, color: '#999', fontSize: 13 }}>加载实体详情...</div>
          </div>
        ) : entityDetail ? (
          <div className="entity-detail-content">
            <div className="entity-detail-section">
              <Text type="secondary">实体名称</Text>
              <div className="entity-detail-value" style={{ fontSize: 16, fontWeight: 600 }}>{entityDetail.name}</div>
            </div>
            {entityDetail.aliases && (
              <div className="entity-detail-section">
                <Text type="secondary">别名</Text>
                <div className="entity-detail-value">{entityDetail.aliases}</div>
              </div>
            )}
            <div className="entity-detail-section">
              <Text type="secondary">类型</Text>
              <div className="entity-detail-value">
                <Tag color={getEntityColor(entityDetail.entityType)}>{entityDetail.entityType}</Tag>
              </div>
            </div>
            {entityDetail.description && (
              <div className="entity-detail-section">
                <Text type="secondary">描述</Text>
                <div className="entity-detail-value">{entityDetail.description}</div>
              </div>
            )}
            <div className="entity-detail-section">
              <Text type="secondary">关联关系</Text>
              <Tag color="blue">{entityDetail.relationCount} 条</Tag>
            </div>
            {entityDetail.relatedEntities && entityDetail.relatedEntities.length > 0 && (
              <>
                <Divider />
                <Text strong style={{ fontSize: 14 }}><LinkOutlined /> 关联实体 ({entityDetail.relatedEntities.length})</Text>
                <List
                  style={{ marginTop: 12 }}
                  size="small"
                  dataSource={entityDetail.relatedEntities}
                  renderItem={(item: RelatedEntity) => (
                    <List.Item>
                      <List.Item.Meta
                        title={
                          <Space>
                            <Tag color={getEntityColor(item.entityType)} style={{ fontSize: 10 }}>{item.entityType}</Tag>
                            <Text
                              style={{ cursor: 'pointer', color: '#1677ff' }}
                              onClick={async () => {
                                setDetailVisible(false);
                                const kbId = selectedKbId || knowledgeBaseId;
                                if (kbId) {
                                  try {
                                    const res = await getEntityDetail(item.name, kbId);
                                    if (res.data.data) {
                                      setSelectedEntity({
                                        id: item.id, name: item.name, entityType: item.entityType,
                                        description: '', sourceDocumentId: '', relationCount: 0,
                                      });
                                      setEntityDetail(res.data.data);
                                      setDetailVisible(true);
                                    }
                                  } catch { message.error('获取实体详情失败'); }
                                }
                              }}
                            >
                              {item.name}
                            </Text>
                          </Space>
                        }
                        description={
                          <Space size={4}>
                            <Tag style={{ fontSize: 10 }}>{item.relationType}</Tag>
                            {item.relationDescription && (
                              <Text type="secondary" style={{ fontSize: 12 }}>{item.relationDescription}</Text>
                            )}
                          </Space>
                        }
                      />
                    </List.Item>
                  )}
                />
              </>
            )}
          </div>
        ) : (
          <Empty description="暂无详情数据" />
        )}
      </Drawer>
    </div>
  );
}