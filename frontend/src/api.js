const base = import.meta.env.VITE_API_BASE_URL || ''
const friendlyErrors = [
  ['AI_PROVIDER_NOT_CONFIGURED', 'AI 服务尚未配置，请添加 API Key 后重试。'],
  ['VECTOR_STORE_UNAVAILABLE', '课程知识库暂不可用。'],
  ['WEB_SEARCH_NOT_CONFIGURED', '尚未配置网络搜索 API Key，网络搜索暂不可用。'],
  ['NO_READY_DOCUMENTS', '课程资料仍在处理中，请上传资料或等待文档就绪。'],
  ['COURSE_NOT_FOUND', '找不到所选课程。'],
  ['TOOL_EXECUTION_FAILED', 'Agent 未能完成该操作，系统没有声称已产生任何修改。'],
  ['AI_GENERATION_FAILED', '模型未能生成有效结果，请重试。'],
]
function friendly(message, status) { const text = String(message || ''); const match = friendlyErrors.find(([code]) => text.includes(code)); if (match) return match[1]; if (text && !text.includes('java.') && !text.includes('Exception')) return text; return status >= 500 ? '服务器暂时无法完成请求，请稍后重试。' : `请求失败（${status}）` }
async function request(path, options = {}) {
  const { timeoutMs = 15000, ...fetchOptions } = options
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(`${base}${path}`, { ...fetchOptions, signal: fetchOptions.signal || controller.signal })
    const body = response.headers.get('content-type')?.includes('application/json') ? await response.json() : null
    if (!response.ok) throw new Error(friendly(body?.message, response.status))
    return body
  } catch (error) {
    if (error?.name === 'AbortError') throw new Error('请求超时，请确认后端正在运行后重试。')
    if (error instanceof TypeError) throw new Error('无法连接 LearnAgent 后端，请确认后端已经启动。')
    throw error
  } finally { clearTimeout(timeout) }
}
export const api = {
  courses: () => request('/api/courses'), course: id => request(`/api/courses/${id}`),
  createCourse: payload => request('/api/courses', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }),
  deleteCourse: id => request(`/api/courses/${id}`, { method: 'DELETE' }), documents: id => request(`/api/courses/${id}/documents`),
  upload: (id, file) => { const form = new FormData(); form.append('file', file); return request(`/api/courses/${id}/documents`, { method: 'POST', body: form, timeoutMs: 300000 }) },
  deleteDocument: (courseId, documentId) => request(`/api/courses/${courseId}/documents/${documentId}`, { method: 'DELETE' }),
  chat: payload => request('/api/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), timeoutMs: 180000 }),
  agentChat: payload => request('/api/agent/chat', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), timeoutMs: 180000 }),
  skills: () => request('/api/skills'),
  tasks: status => request(`/api/tasks${status ? `?status=${status}` : ''}`), createTask: payload => request('/api/tasks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }), completeTask: id => request(`/api/tasks/${id}/complete`, { method: 'POST' }), deleteTask: id => request(`/api/tasks/${id}`, { method: 'DELETE' }),
  updateTask: (id, payload) => request(`/api/tasks/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }),
  quizzes: courseId => request(`/api/quizzes${courseId ? `?courseId=${courseId}` : ''}`),
  generateQuiz: payload => request('/api/quizzes/generate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), timeoutMs: 180000 }), submitQuiz: (id, answers) => request(`/api/quizzes/${id}/submit`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ answers }) }), statistics: () => request('/api/analysis/statistics'), weakPoints: () => request('/api/analysis/weak-points'),
  learningSessions: courseId => request(`/api/learning-sessions${courseId ? `?courseId=${courseId}` : ''}`),
  learningSession: id => request(`/api/learning-sessions/${id}`),
  startLearningSession: payload => request('/api/learning-sessions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), timeoutMs: 240000 }),
  submitLearningRound: (sessionId, roundId, answers) => request(`/api/learning-sessions/${sessionId}/rounds/${roundId}/submit`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ answers }), timeoutMs: 180000 }),
  nextLearningAction: (sessionId, action) => request(`/api/learning-sessions/${sessionId}/next-action`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ action }), timeoutMs: 240000 }),
  stopLearningSession: id => request(`/api/learning-sessions/${id}/stop`, { method: 'POST' }),
  flashcards: (status = 'CANDIDATE', courseId = '') => request(`/api/flashcards?status=${status}${courseId ? `&courseId=${courseId}` : ''}`),
  generateFlashcards: payload => request('/api/flashcards/candidates/generate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), timeoutMs: 180000 }),
  updateFlashcard: (id, payload) => request(`/api/flashcards/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }),
  approveFlashcard: id => request(`/api/flashcards/${id}/approve`, { method: 'POST' }),
  approveFlashcards: cardIds => request('/api/flashcards/approve-batch', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ cardIds }) }),
  suspendFlashcard: id => request(`/api/flashcards/${id}/suspend`, { method: 'POST' }),
  rejectFlashcard: id => request(`/api/flashcards/${id}/reject`, { method: 'POST' }),
  dueFlashcards: courseId => request(`/api/memory-reviews/due${courseId ? `?courseId=${courseId}` : ''}`),
  startMemoryReview: courseId => request('/api/memory-reviews/sessions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ courseId: courseId || null }) }),
  reviewFlashcard: (sessionId, cardId, rating, durationSeconds) => request(`/api/memory-reviews/sessions/${sessionId}/cards/${cardId}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ rating, durationSeconds }) }),
  memoryStatistics: () => request('/api/memory-reviews/statistics'),
  knowledgeGraph: courseId => request(`/api/knowledge-graph?courseId=${courseId}`),
  weeklyReviews: () => request('/api/weekly-reviews'), latestWeeklyReview: () => request('/api/weekly-reviews/latest'), generateWeeklyReview: () => request('/api/weekly-reviews/generate', { method: 'POST', timeoutMs: 180000 }),
  notifications: unreadOnly => request(`/api/notifications${unreadOnly ? '?unreadOnly=true' : ''}`),
  unreadNotificationCount: () => request('/api/notifications/unread-count'),
  readNotification: id => request(`/api/notifications/${id}/read`, { method: 'POST' }),
  readAllNotifications: () => request('/api/notifications/read-all', { method: 'POST' }),
  latestDigest: () => request('/api/daily-digests/latest'),
  generateDigest: () => request('/api/daily-digests/generate', { method: 'POST', timeoutMs: 180000 }),
  externalResources: courseId => request(`/api/external-resources${courseId ? `?courseId=${courseId}` : ''}`),
  hiddenResourceSources: () => request('/api/external-resources/hidden-sources'),
  hideResourceSource: id => request(`/api/external-resources/${id}/hide-source`, { method: 'PUT' }),
  restoreResourceSource: host => request(`/api/external-resources/hidden-sources/${encodeURIComponent(host)}`, { method: 'DELETE' }),
  externalResourceCollections: () => request('/api/external-resources/collections'),
  workspaces: () => request('/api/workspaces'),
  createWorkspace: payload => request('/api/workspaces', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }),
  generateWorkspace: id => request(`/api/workspaces/${id}/generate`, { method: 'POST', timeoutMs: 300000 }),
  workspaceTree: id => request(`/api/workspaces/${id}/tree`),
  workspaceFile: (id, path) => request(`/api/workspaces/${id}/files?path=${encodeURIComponent(path)}`),
  saveWorkspaceFile: (id, path, content) => request(`/api/workspaces/${id}/files?path=${encodeURIComponent(path)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content }) }),
  buildWorkspace: (id, goal = 'test') => request(`/api/workspaces/${id}/build?goal=${goal}`, { method: 'POST', timeoutMs: 300000 }),
  openWorkspaceFolder: id => request(`/api/workspaces/${id}/open-folder`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' }),
  workspaceArtifacts: id => request(`/api/workspaces/${id}/artifacts`),
  sandboxFile: path => request(`/api/workspace-files?path=${encodeURIComponent(path)}`),
  saveSandboxFile: (path, content) => request(`/api/workspace-files?path=${encodeURIComponent(path)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content }) }),
}
