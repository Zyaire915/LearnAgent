const exact = {
  'Operating Systems': '操作系统',
  'Java Programming': 'Java 编程',
  'Computer Networks': '计算机网络',
  'Processes, memory, file systems, permissions, and concurrency.': '进程、内存、文件系统、权限与并发。',
  'Object-oriented programming, collections, concurrency, and modern Java.': '面向对象编程、集合、并发与现代 Java。',
  'Network layers, TCP/IP, routing, and reliable transport.': '网络分层、TCP/IP、路由与可靠传输。',
  'Review Linux file permissions': '复习 Linux 文件权限',
  'Practice owner, group, others, chmod and chown.': '练习 owner、group、others、chmod 和 chown。',
  'Summarize process scheduling': '总结进程调度',
  'Create a one-page comparison of common schedulers.': '制作一页常见调度算法对比。',
  'Complete file-system notes': '完成文件系统笔记',
  'Review inode and directory structures.': '复习 inode 与目录结构。',
  'Finish Java collections lab': '完成 Java 集合实验',
  'Complete the map and stream exercises.': '完成 Map 与 Stream 练习。',
  'Review inheritance': '复习继承',
  'Compare abstract classes and interfaces.': '比较抽象类与接口。',
  'Practice subnetting': '练习子网划分',
  'Solve five CIDR calculation questions.': '完成 5 道 CIDR 计算题。',
  'Review TCP reliability': '复习 TCP 可靠性',
  'Explain ACK, retransmission, and congestion control.': '解释 ACK、重传与拥塞控制。',
  'Read transport lecture': '阅读传输层讲义',
  'Mark important concepts for review.': '标记需要复习的重要概念。',
  'Linux file permissions': 'Linux 文件权限',
  Comprehensive: '综合练习',
}

export const statusText = {
  TODO: '待开始', IN_PROGRESS: '进行中', COMPLETED: '已完成',
  READY: '就绪', PROCESSING: '处理中', FAILED: '失败',
  COMPLETE: '完整', PARTIAL: '部分完成',
  AVAILABLE: '可用', DEGRADED: '部分可用', UNAVAILABLE: '不可用',
  CONFIGURED: '已配置', NOT_CONFIGURED: '未配置', ENABLED: '已启用', DISABLED: '已停用', OFFLINE: '离线',
}

export const priorityText = { LOW: '低', MEDIUM: '中', HIGH: '高' }

export function zh(value) {
  return exact[value] || value || ''
}

export function formatDateTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
}

export function formatDate(value) {
  return value ? new Date(value).toLocaleDateString('zh-CN') : ''
}

const skills = {
  knowledge: ['课程知识检索', '通过本地 RAG 检索课程 PDF 知识'],
  task: ['任务管理', '创建、查询、更新并完成真实学习任务'],
  quiz: ['测验生成', '生成练习题并查看错题'],
  analysis: ['学习分析', '读取薄弱知识点与学习统计'],
  planning: ['学习规划', '根据目标和薄弱点生成结构化学习计划'],
  'web-search': ['网络搜索', '搜索最新的公开外部信息'],
  'adaptive-test': ['自适应测试', '运行持久化的掌握度测试轮次'],
  workspace: ['项目工作台', '根据自由描述生成项目计划、任务、资料与初始文件'],
  'weekly-review': ['学习周报', '根据真实学习统计生成主动回顾'],
  memory: ['记忆巩固', '生成可追溯闪卡并使用 FSRS 安排长期复习'],
}

export function localizeSkill(skill) {
  const value = skills[skill.id]
  if (!value) return skill
  let detail = skill.status === 'AVAILABLE' ? '已就绪' : skill.detail
  if (skill.id === 'web-search') detail = skill.status === 'AVAILABLE' ? 'Tavily 已就绪' : '需要配置 TAVILY_API_KEY'
  if (skill.id === 'quiz' && skill.status !== 'AVAILABLE') detail = '生成题目需要 AI 与向量服务；历史记录仍可查看'
  return { ...skill, name: value[0], description: value[1], detail }
}

const skillNames = {
  'Knowledge Skill': '课程知识检索', 'Web Search Skill': '网络搜索', 'Task Skill': '任务管理',
  'Adaptive Test Skill': '自适应测试', 'Workspace Skill': '项目工作台',
  'Quiz Skill': '测验生成', 'Analysis Skill': '学习分析', 'Planning Skill': '学习规划',
  'Weekly Review Skill': '学习周报',
  'Memory Skill': '记忆巩固',
}

export function skillName(value) {
  return skillNames[value] || value
}

export function stepSummary(value) {
  if (String(value || '').startsWith('Created task: ')) return `已创建任务：${value.slice('Created task: '.length)}`
  const summaries = {
    Started: '开始执行', 'Loaded learning statistics': '已读取学习统计',
    'No weekly review exists': '还没有学习周报', 'Loaded latest weekly review': '已读取最新周报',
  }
  return summaries[value] || value
}

export function backendMessage(value) {
  const text = String(value || '')
  if (text.includes('Document processing is not configured')) return '文档处理服务当时尚未配置。现在已启用本地 Embedding 与 PGVector，请删除该失败记录后重新上传原 PDF。'
  if (text.includes('RAG chat is not configured')) return 'RAG 服务尚未配置完整。'
  if (text.includes('PDF text extraction failed')) return 'PDF 文本提取失败，请上传可读取且未加密的 PDF。'
  if (text.includes('Only PDF files are supported')) return '仅支持 PDF 文件。'
  if (text.includes('Choose a non-empty PDF file')) return '请选择非空 PDF 文件。'
  if (text.includes('Document processing failed')) return '文档处理失败。'
  return text
}
