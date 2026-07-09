import request from './request';
import type { ApiResponse, PageResult } from './typings.d';

export interface GraphNode {
  id: string;
  name: string;
  entityType: string;
  description: string;
  sourceDocumentId: string;
  relationCount: number;
}

export interface GraphEdge {
  id: string;
  sourceId: string;
  targetId: string;
  relationType: string;
  description: string;
}

export interface GraphData {
  nodes: GraphNode[];
  edges: GraphEdge[];
  nodeCount: number;
  edgeCount: number;
}

export interface RelatedEntity {
  id: string;
  name: string;
  entityType: string;
  relationType: string;
  relationDescription: string;
}

export interface EntityDetail {
  id: string;
  name: string;
  entityType: string;
  description: string;
  aliases: string;
  sourceDocumentId: string;
  sourceChunkId: string;
  relatedEntities: RelatedEntity[];
  relatedEdges: GraphEdge[];
  relationCount: number;
}

export async function getGraph(kbId: string): Promise<ApiResponse<GraphData>> {
  const response = await request.get<ApiResponse<GraphData>>(`/graph/${kbId}`);
  return response.data;
}

export async function expandQuery(kbId: string, query: string): Promise<ApiResponse<string[]>> {
  const response = await request.get<ApiResponse<string[]>>(`/graph/${kbId}/expand`, { params: { query } });
  return response.data;
}

export async function getEntities(kbId: string, page: number = 0, size: number = 20): Promise<ApiResponse<PageResult<GraphNode>>> {
  const response = await request.get<ApiResponse<PageResult<GraphNode>>>('/graph/entities', { params: { kbId, page, size } });
  return response.data;
}

export async function getEntityDetail(name: string, kbId: string): Promise<ApiResponse<EntityDetail>> {
  const response = await request.get<ApiResponse<EntityDetail>>(`/graph/entities/${encodeURIComponent(name)}`, { params: { kbId } });
  return response.data;
}

export async function getSubgraph(kbId: string, entityName: string): Promise<ApiResponse<GraphData>> {
  const response = await request.get<ApiResponse<GraphData>>('/graph/subgraph', { params: { kbId, entityName } });
  return response.data;
}

export async function searchGraph(kbId: string, keyword: string): Promise<ApiResponse<GraphNode[]>> {
  const response = await request.get<ApiResponse<GraphNode[]>>('/graph/search', { params: { kbId, keyword } });
  return response.data;
}