import request from './request';
import type { ApiResponse } from './typings.d';

export interface KnowledgeGap {
  topic: string;
  coverageDensity: number;
  coverageLevel: string;
  missingSubtopics: string[];
  suggestion: string;
}

export interface Recommendation {
  topic: string;
  reason: string;
  suggestedResources: string[];
  priority: number;
}

export interface LearningStep {
  order: number;
  title: string;
  description: string;
  status: string;
  knowledgePoints: string[];
  recommendedResources: string[];
  expectedOutcomes: string[];
}

export interface LearningExampleStep {
  stage: number;
  stageName: string;
  objective: string;
  coreContents: string[];
  duration: string;
  outcome: string;
}

export interface LearningExample {
  topic: string;
  overallGoal: string;
  exampleSteps: LearningExampleStep[];
}

export interface LearningPath {
  topic: string;
  summary: string;
  steps: LearningStep[];
  example: LearningExample;
}

export async function getKnowledgeGaps(kbId: string): Promise<ApiResponse<KnowledgeGap[]>> {
  return request.get('/discovery/gaps', { params: { kbId } });
}

export async function getRecommendations(kbId: string): Promise<ApiResponse<Recommendation[]>> {
  return request.get('/discovery/recommendations', { params: { kbId } });
}

export async function getLearningPath(kbId: string, topic: string): Promise<ApiResponse<LearningPath>> {
  return request.get('/discovery/learning-path', { params: { kbId, topic } });
}