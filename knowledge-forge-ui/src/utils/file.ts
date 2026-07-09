export function getFileType(fileName: string): string {
  const ext = fileName.split('.').pop()?.toLowerCase() || '';
  const typeMap: Record<string, string> = {
    txt: '文本',
    csv: '表格',
    json: 'JSON',
    pdf: 'PDF',
    doc: 'Word',
    docx: 'Word',
    md: 'Markdown',
    png: '图片',
    jpg: '图片',
    jpeg: '图片',
  };
  return typeMap[ext] || ext.toUpperCase();
}

export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}
