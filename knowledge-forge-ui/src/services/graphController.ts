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
  return request.get(`/graph/${kbId}`);
}

export async function expandQuery(kbId: string, query: string): Promise<ApiResponse<string[]>> {
  return request.get(`/graph/${kbId}/expand`, { params: { query } });
}

export async function getEntities(kbId: string, page: number = 0, size: number = 20): Promise<ApiResponse<PageResult<GraphNode>>> {
  return request.get('/graph/entities', { params: { kbId, page, size } });
}

export async function getEntityDetail(name: string, kbId: string): Promise<ApiResponse<EntityDetail>> {
  return request.get(`/graph/entities/${encodeURIComponent(name)}`, { params: { kbId } });
}

export async function getSubgraph(kbId: string, entityName: string): Promise<ApiResponse<GraphData>> {
  return request.get('/graph/subgraph', { params: { kbId, entityName } });
}

export async function searchGraph(kbId: string, keyword: string): Promise<ApiResponse<GraphNode[]>> {
  return request.get('/graph/search', { params: { kbId, keyword } });
}