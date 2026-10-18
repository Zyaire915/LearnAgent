import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from './views/DashboardView.vue'
import PlaceholderView from './views/PlaceholderView.vue'
import CoursesView from './views/CoursesView.vue'
import ChatView from './views/ChatView.vue'
import TasksView from './views/TasksView.vue'
import QuizView from './views/QuizView.vue'
import SkillsView from './views/SkillsView.vue'
import WeeklyReviewView from './views/WeeklyReviewView.vue'
import WorkbenchView from './views/WorkbenchView.vue'
import MemoryView from './views/MemoryView.vue'
import ResourcesView from './views/ResourcesView.vue'

const pages = []
const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'dashboard', component: DashboardView, meta: { title: '学习概览' } },
  { path: '/courses/:id?', name: 'courses', component: CoursesView, meta: { title: '课程资料' } },
  { path: '/chat', name: 'chat', component: ChatView, meta: { title: 'AI 对话' } },
  { path: '/tasks', name: 'tasks', component: TasksView, meta: { title: '学习任务' } },
  { path: '/quiz', name: 'quiz', component: QuizView, meta: { title: '练习测验' } },
  { path: '/skills', name: 'skills', component: SkillsView, meta: { title: 'Agent 技能' } },
  { path: '/weekly-review', name: 'weekly-review', component: WeeklyReviewView, meta: { title: '学习周报' } },
  { path: '/workbench', name: 'workbench', component: WorkbenchView, meta: { title: '项目工作台' } },
  { path: '/memory', name: 'memory', component: MemoryView, meta: { title: '记忆巩固' } },
  { path: '/resources', name: 'resources', component: ResourcesView, meta: { title: '外部资料' } },
  ...pages.map(([path, title, description]) => ({ path: `/${path}`, name: path, component: PlaceholderView, meta: { title, description } })),
]
export default createRouter({ history: createWebHistory(), routes })
