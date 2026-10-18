<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { markdown } from '@codemirror/lang-markdown'
import { java } from '@codemirror/lang-java'

const props = defineProps({ modelValue: { type: String, default: '' }, language: { type: String, default: 'markdown' } })
const emit = defineEmits(['update:modelValue'])
const host = ref(null)
let editor
onMounted(() => {
  editor = new EditorView({
    parent: host.value,
    state: EditorState.create({ doc: props.modelValue, extensions: [basicSetup, props.language === 'java' ? java() : markdown(), EditorView.lineWrapping, EditorView.updateListener.of(update => { if (update.docChanged) emit('update:modelValue', update.state.doc.toString()) })] }),
  })
})
watch(() => props.modelValue, value => {
  if (!editor || value === editor.state.doc.toString()) return
  editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: value || '' } })
})
onBeforeUnmount(() => editor?.destroy())
</script>
<template><div ref="host" class="code-editor"/></template>
