<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { formatDateTime, priorityText, statusText, zh } from '../localize'

const tasks = ref([]), courses = ref([]), filter = ref(''), error = ref(''), loading = ref(true)
const saving = ref(false), busyId = ref(''), dialog = ref(false), editingId = ref(null)
const blank = () => ({ courseId: null, title: '', description: '', priority: 'MEDIUM', status: 'TODO', dueAt: '' })
const draft = ref(blank())
const visible = computed(() => filter.value ? tasks.value.filter(task => task.status === filter.value) : tasks.value)
const byDueAt = (left, right) => {
  if (!left.dueAt && !right.dueAt) return 0
  if (!left.dueAt) return 1
  if (!right.dueAt) return -1
  return new Date(left.dueAt) - new Date(right.dueAt)
}
const groups = computed(() => [
  { id: 'upcoming', title: '待开始', items: visible.value.filter(task => task.status === 'TODO').sort(byDueAt) },
  { id: 'progress', title: '进行中', items: visible.value.filter(task => task.status === 'IN_PROGRESS').sort(byDueAt) },
  { id: 'completed', title: '已完成', items: visible.value.filter(task => task.status === 'COMPLETED').sort(byDueAt) },
].filter(group => group.items.length))
const courseName = id => zh(courses.value.find(course => course.id === id)?.name || '通用任务')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [taskResult, courseResult] = await Promise.allSettled([api.tasks(), api.courses()])
    if (taskResult.status === 'rejected') throw taskResult.reason
    tasks.value = taskResult.value || []
    courses.value = courseResult.status === 'fulfilled' ? (courseResult.value || []) : []
    if (courseResult.status === 'rejected') error.value = `任务已加载，但课程名称加载失败：${courseResult.reason.message}`
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
function openCreate() { editingId.value = null; draft.value = blank(); dialog.value = true }
function openEdit(task) { editingId.value = task.id; draft.value = { courseId: task.courseId, title: task.title, description: task.description || '', priority: task.priority, status: task.status, dueAt: task.dueAt || '' }; dialog.value = true }
async function save() { if (!draft.value.title.trim()) return; saving.value = true; try { const payload = { ...draft.value, dueAt: draft.value.dueAt || null }; editingId.value ? await api.updateTask(editingId.value, payload) : await api.createTask(payload); dialog.value = false; ElMessage.success(editingId.value ? '任务已更新' : '任务已创建'); await load() } catch (e) { error.value = e.message } finally { saving.value = false } }
async function complete(task) { busyId.value = task.id; try { await api.completeTask(task.id); ElMessage.success('任务已完成'); await load() } catch (e) { error.value = e.message } finally { busyId.value = '' } }
async function restore(task) { busyId.value = task.id; try { await api.updateTask(task.id, { courseId: task.courseId, title: task.title, description: task.description, dueAt: task.dueAt, status: 'TODO', priority: task.priority }); ElMessage.success('任务已恢复为待开始'); await load() } catch (e) { error.value = e.message } finally { busyId.value = '' } }
async function remove(task) { try { await ElMessageBox.confirm(`确定删除“${zh(task.title)}”吗？`, '删除任务', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return } busyId.value = task.id; try { await api.deleteTask(task.id); ElMessage.success('任务已删除'); await load() } catch (e) { error.value = e.message } finally { busyId.value = '' } }
onMounted(load)
</script>

<template>
  <section>
    <div class="section-heading">
      <div><span class="eyebrow">学习计划</span><h2>学习任务</h2><p>把学习计划拆解为清晰、可跟踪的行动。</p></div>
      <div class="task-actions">
        <el-select v-model="filter" placeholder="全部状态" clearable><el-option label="待开始" value="TODO"/><el-option label="进行中" value="IN_PROGRESS"/><el-option label="已完成" value="COMPLETED"/></el-select>
        <el-button type="primary" @click="openCreate">新建任务</el-button>
      </div>
    </div>
    <el-alert v-if="error" class="page-alert" type="error" :title="error" show-icon @close="error=''"/>
    <el-skeleton v-if="loading" :rows="7" animated/>
    <template v-else>
      <div v-if="!visible.length" class="empty-card"><el-empty description="当前筛选下没有任务。"><el-button type="primary" @click="openCreate">创建任务</el-button></el-empty></div>
      <template v-else><section v-for="group in groups" :key="group.id" class="task-group">
        <div class="group-heading"><h3>{{group.title}}</h3><span>{{group.items.length}}</span></div>
        <div class="task-list"><article v-for="task in group.items" :key="task.id" class="task-card" :class="{done:task.status==='COMPLETED'}">
          <button class="task-check" :disabled="busyId===task.id" @click="task.status==='COMPLETED'?restore(task):complete(task)">{{task.status==='COMPLETED'?'✓':'○'}}</button>
          <div class="doc-main"><strong>{{zh(task.title)}}</strong><small>{{courseName(task.courseId)}}<template v-if="task.dueAt"> · {{formatDateTime(task.dueAt)}}</template></small><p v-if="task.description">{{zh(task.description)}}</p></div>
          <el-tag :type="task.priority==='HIGH'?'danger':task.priority==='LOW'?'info':'warning'">{{priorityText[task.priority]}}</el-tag>
          <el-button text @click="openEdit(task)">编辑</el-button><el-button text type="danger" :loading="busyId===task.id" @click="remove(task)">删除</el-button>
        </article></div>
      </section></template>
    </template>
    <el-dialog v-model="dialog" :title="editingId?'编辑任务':'新建学习任务'" width="460px"><el-form label-position="top">
      <el-form-item label="标题" required><el-input v-model="draft.title"/></el-form-item>
      <el-form-item label="课程"><el-select v-model="draft.courseId" clearable placeholder="通用任务"><el-option v-for="course in courses" :key="course.id" :label="zh(course.name)" :value="course.id"/></el-select></el-form-item>
      <el-form-item label="说明"><el-input v-model="draft.description" type="textarea" :rows="3"/></el-form-item>
      <div class="form-row"><el-form-item label="优先级"><el-select v-model="draft.priority"><el-option label="低" value="LOW"/><el-option label="中" value="MEDIUM"/><el-option label="高" value="HIGH"/></el-select></el-form-item><el-form-item v-if="editingId" label="状态"><el-select v-model="draft.status"><el-option v-for="value in ['TODO','IN_PROGRESS','COMPLETED']" :key="value" :label="statusText[value]" :value="value"/></el-select></el-form-item></div>
      <el-form-item label="截止时间（ISO-8601）"><el-input v-model="draft.dueAt" placeholder="2026-10-20T12:00:00Z"/></el-form-item>
    </el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!draft.title.trim()" @click="save">保存任务</el-button></template></el-dialog>
  </section>
</template>
