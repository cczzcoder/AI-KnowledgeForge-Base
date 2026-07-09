/**
 * 图布局计算 Worker — 在独立线程中执行，不阻塞 UI 渲染。
 * 支持三种布局：力导向 (force)、水平分层 (hierarchical)、环形 (circular)。
 */

// ---- 力导向布局（简化版 FR 算法） ----
interface ForceNode {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  id: string;
}

interface ForceEdge {
  source: number;
  target: number;
}

function forceLayout(
  nodeCount: number,
  edges: { sourceIndex: number; targetIndex: number }[],
  nodeRadiuses: number[],
): { x: number; y: number }[] {
  const nodes: ForceNode[] = [];
  const W = 1400,
    H = 1050;
  for (let i = 0; i < nodeCount; i++) {
    nodes.push({
      x: W / 2 + (Math.random() - 0.5) * 300,
      y: H / 2 + (Math.random() - 0.5) * 300,
      vx: 0,
      vy: 0,
      radius: nodeRadiuses[i] || 20,
      id: String(i),
    });
  }

  const linkEdges: ForceEdge[] = edges.map((e) => ({
    source: e.sourceIndex,
    target: e.targetIndex,
  }));
  const iterations = 400;
  const alphaMin = 0.001;
  let alpha = 1;
  const alphaDecay = 1 - Math.pow(alphaMin, 1 / iterations);

  for (let iter = 0; iter < iterations; iter++) {
    alpha *= alphaDecay;
    // 斥力 (增大推开力度)
    for (let i = 0; i < nodeCount; i++) {
      for (let j = i + 1; j < nodeCount; j++) {
        const dx = nodes[j].x - nodes[i].x;
        const dy = nodes[j].y - nodes[i].y;
        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
        const force = (alpha * 2000) / (dist * dist);
        const fx = (dx / dist) * force;
        const fy = (dy / dist) * force;
        nodes[i].vx -= fx * 0.5 * alpha;
        nodes[i].vy -= fy * 0.5 * alpha;
        nodes[j].vx += fx * 0.5 * alpha;
        nodes[j].vy += fy * 0.5 * alpha;
      }
    }
    // 引力 (弹簧，增大理想边长)
    for (const e of linkEdges) {
      const s = nodes[e.source];
      const t = nodes[e.target];
      const dx = t.x - s.x;
      const dy = t.y - s.y;
      const dist = Math.sqrt(dx * dx + dy * dy) || 1;
      const ideal = 200;
      const force = (dist - ideal) * alpha * 0.05;
      const fx = (dx / dist) * force;
      const fy = (dy / dist) * force;
      s.vx += fx;
      s.vy += fy;
      t.vx -= fx;
      t.vy -= fy;
    }
    // 向心力 (减弱，让节点更分散)
    for (const n of nodes) {
      const dx = W / 2 - n.x;
      const dy = H / 2 - n.y;
      n.vx += dx * alpha * 0.008;
      n.vy += dy * alpha * 0.008;
    }
    // 碰撞检测 (增大最小间距)
    for (let i = 0; i < nodeCount; i++) {
      for (let j = i + 1; j < nodeCount; j++) {
        const dx = nodes[j].x - nodes[i].x;
        const dy = nodes[j].y - nodes[i].y;
        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
        const minDist = nodes[i].radius + nodes[j].radius + 20;
        if (dist < minDist) {
          const fx = (dx / dist) * (minDist - dist) * 0.5;
          const fy = (dy / dist) * (minDist - dist) * 0.5;
          nodes[i].x -= fx;
          nodes[i].y -= fy;
          nodes[j].x += fx;
          nodes[j].y += fy;
        }
      }
    }
    // 更新位置
    for (const n of nodes) {
      n.vx *= 0.6;
      n.vy *= 0.6;
      n.x += n.vx;
      n.y += n.vy;
      n.x = Math.max(n.radius, Math.min(W - n.radius, n.x));
      n.y = Math.max(n.radius, Math.min(H - n.radius, n.y));
    }
  }

  return nodes.map((n) => ({ x: n.x, y: n.y }));
}

// ---- 水平分层布局 ----
function hierarchicalLayout(
  nodeCount: number,
  edges: { sourceIndex: number; targetIndex: number }[],
  _nodeRadiuses: number[],
): { x: number; y: number }[] {
  // 构建邻接表
  const adj = new Map<number, Set<number>>();
  const undirected = new Map<number, Set<number>>();
  for (let i = 0; i < nodeCount; i++) {
    adj.set(i, new Set());
    undirected.set(i, new Set());
  }
  for (const e of edges) {
    adj.get(e.sourceIndex)?.add(e.targetIndex);
    undirected.get(e.sourceIndex)?.add(e.targetIndex);
    undirected.get(e.targetIndex)?.add(e.sourceIndex);
  }

  // 连通分量
  const visited = new Set<number>();
  const components: number[][] = [];
  for (let i = 0; i < nodeCount; i++) {
    if (!visited.has(i)) {
      const comp: number[] = [];
      const q = [i];
      visited.add(i);
      while (q.length) {
        const cur = q.shift()!;
        comp.push(cur);
        for (const nb of undirected.get(cur) || []) {
          if (!visited.has(nb)) {
            visited.add(nb);
            q.push(nb);
          }
        }
      }
      components.push(comp);
    }
  }

  const NodeGapY = 100;
  const LayerGapX = 160;
  const MarginY = 55;
  const MarginLeft = 55;
  const MarginRight = 40;
  const CompGapX = 80;
  const result: { x: number; y: number }[] = new Array(nodeCount);
  let globalOffsetX = MarginLeft;
  let maxCompHeight = 0;

  for (const compIds of components) {
    const degree = new Map<number, number>();
    for (const id of compIds) degree.set(id, undirected.get(id)?.size ?? 0);
    const sorted = [...compIds].sort(
      (a, b) => (degree.get(b) ?? 0) - (degree.get(a) ?? 0),
    );
    const centerId = sorted[0];

    const compLayerMap = new Map<number, number>();
    compLayerMap.set(centerId, 0);
    const queue = [centerId];
    while (queue.length) {
      const cur = queue.shift()!;
      const curDist = compLayerMap.get(cur)!;
      for (const nb of adj.get(cur) || []) {
        if (!compLayerMap.has(nb) && compIds.includes(nb)) {
          compLayerMap.set(nb, curDist + 1);
          queue.push(nb);
        }
      }
    }
    let nextLayer =
      compLayerMap.size > 0 ? Math.max(...compLayerMap.values()) + 1 : 0;
    for (const id of compIds) {
      if (!compLayerMap.has(id)) compLayerMap.set(id, nextLayer++);
    }

    const maxDist = Math.max(...compLayerMap.values());
    const layers: number[][] = [];
    for (let d = 0; d <= maxDist; d++) {
      const layerIds = compIds.filter((id) => compLayerMap.get(id) === d);
      if (layerIds.length) layers.push(layerIds);
    }

    // Barycenter 启发式减少交叉
    const nodeOrder = new Map<number, number>();
    for (const id of layers[0]) nodeOrder.set(id, layers[0].indexOf(id));
    for (let pass = 0; pass < 4; pass++) {
      const forward = pass % 2 === 0;
      const range = forward
        ? Array.from({ length: layers.length - 1 }, (_, i) => i + 1)
        : Array.from(
            { length: layers.length - 1 },
            (_, i) => layers.length - 2 - i,
          );
      for (const li of range) {
        const curLayer = layers[li];
        const barycenters = curLayer.map((id) => {
          const neighbors = undirected.get(id) || new Set();
          let sum = 0,
            count = 0;
          for (const nb of neighbors) {
            const ord = nodeOrder.get(nb);
            if (ord !== undefined) {
              sum += ord;
              count++;
            }
          }
          return {
            id,
            barycenter: count > 0 ? sum / count : Number.MAX_SAFE_INTEGER,
          };
        });
        barycenters.sort((a, b) => a.barycenter - b.barycenter);
        barycenters.forEach((item, i) => nodeOrder.set(item.id, i));
      }
    }

    const maxLayerHeight = Math.max(...layers.map((l) => l.length));
    const compWidth = layers.length * LayerGapX;
    const compHeight = MarginY * 2 + maxLayerHeight * NodeGapY;

    for (let li = 0; li < layers.length; li++) {
      const curLayer = layers[li];
      const n = curLayer.length;
      const layerHeight = n * NodeGapY;
      const offsetY = (compHeight - layerHeight) / 2;
      const x = globalOffsetX + li * LayerGapX;
      for (const id of curLayer) {
        const ord = nodeOrder.get(id) ?? 0;
        const y = offsetY + NodeGapY * (ord + 0.5);
        result[id] = { x, y };
      }
    }
    globalOffsetX += compWidth + CompGapX;
    maxCompHeight = Math.max(maxCompHeight, compHeight);
  }

  const svgWidth = Math.max(800, globalOffsetX + MarginRight);
  const svgHeight = Math.max(600, maxCompHeight);
  return result.map((p) => p || { x: svgWidth / 2, y: svgHeight / 2 });
}

// ---- 环形布局 ----
function circularLayout(
  nodeCount: number,
  _nodeRadiuses: number[],
): { x: number; y: number }[] {
  const cx = 600,
    cy = 450,
    r = Math.min(cx, cy) - 80;
  const result: { x: number; y: number }[] = [];
  for (let i = 0; i < nodeCount; i++) {
    const angle = (2 * Math.PI * i) / nodeCount - Math.PI / 2;
    result.push({ x: cx + r * Math.cos(angle), y: cy + r * Math.sin(angle) });
  }
  return result;
}

// ---- Worker 消息处理 ----
self.onmessage = (
  e: MessageEvent<{
    type: 'force' | 'hierarchical' | 'circular';
    nodeCount: number;
    edges: { sourceIndex: number; targetIndex: number }[];
    nodeRadiuses: number[];
  }>,
) => {
  const { type, nodeCount, edges, nodeRadiuses } = e.data;
  let positions: { x: number; y: number }[];

  switch (type) {
    case 'force':
      positions = forceLayout(nodeCount, edges, nodeRadiuses);
      break;
    case 'hierarchical':
      positions = hierarchicalLayout(nodeCount, edges, nodeRadiuses);
      break;
    case 'circular':
      positions = circularLayout(nodeCount, nodeRadiuses);
      break;
    default:
      positions = forceLayout(nodeCount, edges, nodeRadiuses);
  }

  self.postMessage(positions);
};
