import request from './request';
import type {
  ApiResponse,
  DocumentDTO,
  DocumentChunkDTO,
  PageResult,
} from './typings.d';

export async function uploadDocument(
  kbId: string,
  file: File,
  onProgress?: (percent: number) => void,
) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<ApiResponse<DocumentDTO>>(
    `/knowledge-bases/${kbId}/documents`,
    formData,
    {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const percent = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total,
          );
          onProgress(percent);
        }
      },
    },
  );
}

export async function listDocuments(kbId: string, page = 0, size = 20) {
  return request.get<ApiResponse<PageResult<DocumentDTO>>>(
    `/knowledge-bases/${kbId}/documents`,
    {
      params: { page, size },
    },
  );
}

export async function getDocument(id: string) {
  return request.get<ApiResponse<DocumentDTO>>(`/documents/${id}`);
}

export async function deleteDocument(id: string) {
  return request.delete<ApiResponse<null>>(`/documents/${id}`);
}

export async function reprocessDocument(id: string) {
  return request.post<ApiResponse<DocumentDTO>>(`/documents/${id}/reprocess`);
}

export async function getChunks(id: string) {
  return request.get<ApiResponse<DocumentChunkDTO[]>>(
    `/documents/${id}/chunks`,
  );
}

export async function downloadDocument(id: string, title: string) {
  const response = await request.get(`/documents/${id}/download`, {
    responseType: 'blob',
  });
  const url = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', title);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}
