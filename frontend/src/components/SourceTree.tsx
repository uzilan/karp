import { useState, useEffect, useRef } from 'react'
import type { Selection } from '../App'

interface FolderNode { id: string; type: 'folder'; name: string; children: string[]; collapsed: boolean }
interface SourceNode { id: string; type: 'source'; fileName: string }
type TreeNode = FolderNode | SourceNode
interface Tree { nodes: Record<string, TreeNode>; root: string[] }

const STORAGE_KEY = 'karp-source-tree'
const genId = () => Math.random().toString(36).slice(2, 9)

function loadTree(): Tree {
  try { const s = localStorage.getItem(STORAGE_KEY); if (s) return JSON.parse(s) } catch {}
  return { nodes: {}, root: [] }
}

function findParent(tree: Tree, id: string): string | null {
  for (const [pid, n] of Object.entries(tree.nodes))
    if (n.type === 'folder' && (n as FolderNode).children.includes(id)) return pid
  return null
}

function detach(tree: Tree, id: string): Tree {
  const pid = findParent(tree, id)
  if (pid === null) return { ...tree, root: tree.root.filter(x => x !== id) }
  const p = tree.nodes[pid] as FolderNode
  return { ...tree, nodes: { ...tree.nodes, [pid]: { ...p, children: p.children.filter(x => x !== id) } } }
}

interface Props {
  serverSources: string[]
  refreshKey: number
  selection: Selection
  onSelect: (s: Selection) => void
  selectedFolderId: string | null
  onSelectFolder: (id: string | null) => void
  addRootFolderKey: number
}

export default function SourceTree({ serverSources, refreshKey, selection, onSelect, selectedFolderId, onSelectFolder, addRootFolderKey }: Props) {
  const [tree, setTree] = useState<Tree>(loadTree)
  const [dragId, setDragId] = useState<string | null>(null)
  const [dropTarget, setDropTarget] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editName, setEditName] = useState('')
  const [hoveredId, setHoveredId] = useState<string | null>(null)
  const folderRef = useRef(selectedFolderId)

  useEffect(() => { folderRef.current = selectedFolderId }, [selectedFolderId])
  useEffect(() => { localStorage.setItem(STORAGE_KEY, JSON.stringify(tree)) }, [tree])
  useEffect(() => {
    if (addRootFolderKey === 0) return
    const id = genId()
    const parentId = folderRef.current
    const node: FolderNode = { id, type: 'folder', name: 'New Folder', children: [], collapsed: false }
    setTree(prev => {
      if (parentId && prev.nodes[parentId]?.type === 'folder') {
        const p = prev.nodes[parentId] as FolderNode
        return { ...prev, nodes: { ...prev.nodes, [id]: node, [parentId]: { ...p, children: [...p.children, id] } } }
      }
      return { ...prev, nodes: { ...prev.nodes, [id]: node }, root: [...prev.root, id] }
    })
    setEditingId(id)
    setEditName('New Folder')
  }, [addRootFolderKey])

  useEffect(() => {
    setTree(prev => {
      const inTree = new Set(
        Object.values(prev.nodes).filter(n => n.type === 'source').map(n => (n as SourceNode).fileName)
      )
      let next = { nodes: { ...prev.nodes }, root: [...prev.root] }

      for (const fileName of serverSources) {
        if (!inTree.has(fileName)) {
          const id = genId()
          next.nodes[id] = { id, type: 'source', fileName }
          const fid = folderRef.current
          if (fid && next.nodes[fid]?.type === 'folder') {
            const f = next.nodes[fid] as FolderNode
            next.nodes[fid] = { ...f, children: [...f.children, id] }
          } else {
            next.root = [...next.root, id]
          }
        }
      }

      const serverSet = new Set(serverSources)
      for (const [id, n] of Object.entries(next.nodes)) {
        if (n.type === 'source' && !serverSet.has((n as SourceNode).fileName)) {
          const detached = detach({ nodes: next.nodes, root: next.root }, id)
          const { [id]: _, ...rest } = detached.nodes
          next = { nodes: rest, root: detached.root }
        }
      }
      return next
    })
  }, [serverSources, refreshKey])

  const addFolder = (parentId: string | null) => {
    const id = genId()
    const node: FolderNode = { id, type: 'folder', name: 'New Folder', children: [], collapsed: false }
    setTree(prev => {
      if (parentId && prev.nodes[parentId]?.type === 'folder') {
        const p = prev.nodes[parentId] as FolderNode
        return { ...prev, nodes: { ...prev.nodes, [id]: node, [parentId]: { ...p, children: [...p.children, id] } } }
      }
      return { ...prev, nodes: { ...prev.nodes, [id]: node }, root: [...prev.root, id] }
    })
    setEditingId(id)
    setEditName('New Folder')
  }

  const commitRename = (id: string) => {
    const name = editName.trim() || 'Folder'
    setTree(prev => ({ ...prev, nodes: { ...prev.nodes, [id]: { ...prev.nodes[id], name } as FolderNode } }))
    setEditingId(null)
  }

  const toggle = (id: string) =>
    setTree(prev => {
      const f = prev.nodes[id] as FolderNode
      return { ...prev, nodes: { ...prev.nodes, [id]: { ...f, collapsed: !f.collapsed } } }
    })

  const drop = (e: React.DragEvent, targetId: string | null) => {
    e.preventDefault(); e.stopPropagation()
    setDropTarget(null)
    if (!dragId || dragId === targetId) return
    setTree(prev => {
      let next = detach(prev, dragId)
      if (targetId === null) {
        next = { ...next, root: [...next.root, dragId] }
      } else {
        const t = next.nodes[targetId] as FolderNode
        next = { ...next, nodes: { ...next.nodes, [targetId]: { ...t, children: [...t.children, dragId] } } }
      }
      return next
    })
    setDragId(null)
  }

  const renderNode = (id: string, depth: number): React.ReactNode => {
    const node = tree.nodes[id]
    if (!node) return null
    const indent = depth * 14 + 8

    if (node.type === 'source') {
      const sn = node as SourceNode
      const active = selection?.type === 'source' && selection.name === sn.fileName
      return (
        <div
          key={id}
          draggable
          onDragStart={e => { e.stopPropagation(); setDragId(id) }}
          onDragEnd={() => setDragId(null)}
          onClick={() => onSelect({ type: 'source', name: sn.fileName })}
          style={{
            paddingLeft: indent + 16, paddingRight: 8, paddingTop: 3, paddingBottom: 3,
            cursor: 'pointer', userSelect: 'none',
            background: active ? 'var(--color-selected-bg)' : 'transparent',
            color: active ? 'var(--color-selected-text)' : 'var(--color-text)',
            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', fontSize: 13,
          }}
        >
          📎 {sn.fileName}
        </div>
      )
    }

    const fn = node as FolderNode
    const selected = selectedFolderId === id
    const dragOver = dropTarget === id
    const hovered = hoveredId === id

    return (
      <div key={id}>
        <div
          draggable
          onDragStart={e => { e.stopPropagation(); setDragId(id) }}
          onDragEnd={() => setDragId(null)}
          onDragOver={e => { e.preventDefault(); e.stopPropagation(); setDropTarget(id) }}
          onDragLeave={e => { e.stopPropagation(); if (!e.currentTarget.contains(e.relatedTarget as Node)) setDropTarget(null) }}
          onDrop={e => drop(e, id)}
          onMouseEnter={() => setHoveredId(id)}
          onMouseLeave={() => setHoveredId(null)}
          onClick={() => onSelectFolder(selected ? null : id)}
          onDoubleClick={() => { setEditingId(id); setEditName(fn.name) }}
          style={{
            paddingLeft: indent, paddingRight: 8, paddingTop: 3, paddingBottom: 3,
            cursor: 'pointer', userSelect: 'none',
            background: selected ? 'var(--color-selected-bg)' : dragOver ? 'var(--color-drag-bg)' : 'transparent',
            color: selected ? 'var(--color-selected-text)' : 'var(--color-text)',
            display: 'flex', alignItems: 'center', gap: 4, fontSize: 13,
          }}
        >
          <span
            onClick={e => { e.stopPropagation(); toggle(id) }}
            style={{ fontSize: 9, color: 'var(--color-text-faint)', minWidth: 10, lineHeight: 1 }}
          >{fn.collapsed ? '▶' : '▼'}</span>
          <span>📁</span>
          {editingId === id ? (
            <input
              autoFocus
              value={editName}
              onChange={e => setEditName(e.target.value)}
              onBlur={() => commitRename(id)}
              onKeyDown={e => { if (e.key === 'Enter') commitRename(id); if (e.key === 'Escape') setEditingId(null) }}
              onClick={e => e.stopPropagation()}
              style={{ fontSize: 12, border: '1px solid var(--color-border)', borderRadius: 3, padding: '1px 4px', width: 90, outline: 'none', background: 'var(--color-surface)', color: 'var(--color-text)' }}
            />
          ) : (
            <span style={{ flex: 1 }}>{fn.name}</span>
          )}
          {hovered && (
            <span
              title="Add subfolder"
              onClick={e => { e.stopPropagation(); addFolder(id) }}
              style={{ fontSize: 11, color: 'var(--color-text-faint)', padding: '0 2px', borderRadius: 3 }}
            >＋</span>
          )}
        </div>
        {!fn.collapsed && fn.children.map(cid => renderNode(cid, depth + 1))}
      </div>
    )
  }

  return (
    <div
      onDragOver={e => { if (!dragId) return; e.preventDefault(); setDropTarget('__root__') }}
      onDragLeave={() => setDropTarget(null)}
      onDrop={e => drop(e, null)}
      style={{ minHeight: 8, background: dropTarget === '__root__' ? 'var(--color-drag-bg)' : 'transparent' }}
    >
      {tree.root.map(id => renderNode(id, 0))}
    </div>
  )
}
