<script lang="ts">
    import type { SharedPreferencesViewerWidget, SharedPreferencesFile, SharedPreferencesEntry } from '../types/protocol';
    import { evaluatePath } from '../jsonpath';
    import { sendAction } from '../stores/connection';
    import { openFullscreen } from '../stores/fullscreen';

    interface Props {
        widget: SharedPreferencesViewerWidget;
        data: unknown;
        sessionId: string;
        showExpandButton?: boolean;
    }

    let { widget, data, sessionId, showExpandButton = true }: Props = $props();

    let selectedFile = $state<string | null>(null);
    let editingEntry = $state<SharedPreferencesEntry | null>(null);
    let editValue = $state('');
    let addingEntry = $state(false);
    let newEntry = $state({ key: '', value: '', type: 'String' as const });
    let loadingAction = $state<string | null>(null);

    const TYPES = ['String', 'Int', 'Long', 'Float', 'Boolean', 'StringSet'];

    let prefsData = $derived.by(() => {
        const raw = evaluatePath(data, widget.data_path);
        if (!raw || typeof raw !== 'object') return null;
        return raw as { files: SharedPreferencesFile[], file_count: number, entry_count: number };
    });

    let files = $derived(prefsData?.files ?? []);

    // Auto-select first file if none selected
    $effect(() => {
        if (files.length > 0 && !selectedFile) {
            selectedFile = files[0].name;
        }
    });

    let currentFile = $derived(files.find(f => f.name === selectedFile));
    let entries = $derived(currentFile?.entries ?? []);

    function handleExpand() {
        openFullscreen('shared_preferences_viewer', { widget, data, sessionId });
    }

    async function handleRefresh() {
        if (loadingAction) return;
        loadingAction = 'refresh';
        try {
            await sendAction(sessionId, 'prefs_refresh', {});
        } finally {
            loadingAction = null;
        }
    }

    function startEdit(entry: SharedPreferencesEntry) {
        editingEntry = entry;
        editValue = entry.value;
    }

    function cancelEdit() {
        editingEntry = null;
        editValue = '';
    }

    async function saveEdit() {
        if (!editingEntry || loadingAction) return;
        loadingAction = `edit-${editingEntry.key}`;
        try {
            await sendAction(sessionId, 'prefs_set', {
                prefs_file: editingEntry.prefs_file,
                key: editingEntry.key,
                value: editValue,
                type: editingEntry.type
            });
            cancelEdit();
        } finally {
            loadingAction = null;
        }
    }

    async function handleDelete(entry: SharedPreferencesEntry) {
        if (loadingAction) return;
        loadingAction = `delete-${entry.key}`;
        try {
            await sendAction(sessionId, 'prefs_delete', {
                prefs_file: entry.prefs_file,
                key: entry.key
            });
        } finally {
            loadingAction = null;
        }
    }

    function startAdd() {
        addingEntry = true;
        newEntry = { key: '', value: '', type: 'String' };
    }

    function cancelAdd() {
        addingEntry = false;
        newEntry = { key: '', value: '', type: 'String' };
    }

    async function saveAdd() {
        if (!selectedFile || loadingAction || !newEntry.key) return;
        loadingAction = 'add';
        try {
            await sendAction(sessionId, 'prefs_add', {
                prefs_file: selectedFile,
                key: newEntry.key,
                value: newEntry.value,
                type: newEntry.type
            });
            cancelAdd();
        } finally {
            loadingAction = null;
        }
    }

    function getTypeColor(type: string): string {
        switch (type) {
            case 'String': return 'type-string';
            case 'Int':
            case 'Long': return 'type-number';
            case 'Float': return 'type-float';
            case 'Boolean': return 'type-boolean';
            case 'StringSet': return 'type-set';
            default: return 'type-other';
        }
    }
</script>

<div class="prefs-viewer">
    <div class="toolbar">
        <select bind:value={selectedFile} aria-label="Select preferences file" disabled={files.length === 0}>
            {#if files.length === 0}
                <option value={null}>No files found</option>
            {:else}
                {#each files as file}
                    <option value={file.name}>{file.name}</option>
                {/each}
            {/if}
        </select>
        <div class="toolbar-actions">
            <button class="action-btn" onclick={handleRefresh} disabled={loadingAction === 'refresh'}>
                {loadingAction === 'refresh' ? '...' : 'Refresh'}
            </button>
            <button class="action-btn primary" onclick={startAdd} disabled={!selectedFile || addingEntry}>
                Add
            </button>
            {#if showExpandButton}
                <button
                    class="expand-button"
                    onclick={handleExpand}
                    aria-label="Open in fullscreen"
                    title="Open in fullscreen"
                >
                    &#x26F6;
                </button>
            {/if}
        </div>
    </div>

    <div class="stats-bar">
        <span class="stat">Files: {prefsData?.file_count ?? 0}</span>
        <span class="stat">Total entries: {prefsData?.entry_count ?? 0}</span>
        {#if currentFile}
            <span class="stat highlight">Current: {entries.length} entries</span>
        {/if}
    </div>

    {#if addingEntry}
        <div class="add-form">
            <input type="text" bind:value={newEntry.key} placeholder="Key" class="input-key" />
            <input type="text" bind:value={newEntry.value} placeholder="Value" class="input-value" />
            <select bind:value={newEntry.type}>
                {#each TYPES as t}
                    <option value={t}>{t}</option>
                {/each}
            </select>
            <button class="action-btn primary" onclick={saveAdd} disabled={!newEntry.key || loadingAction === 'add'}>
                {loadingAction === 'add' ? '...' : 'Save'}
            </button>
            <button class="action-btn" onclick={cancelAdd}>Cancel</button>
        </div>
    {/if}

    <div class="entries-list">
        {#if entries.length === 0}
            <div class="empty">
                {#if !selectedFile}
                    No preferences file selected
                {:else}
                    No entries in this file
                {/if}
            </div>
        {:else}
            {#each entries as entry (entry.key)}
                <div class="entry-row">
                    <span class="entry-key" title={entry.key}>{entry.key}</span>
                    <span class="type-badge {getTypeColor(entry.type)}">{entry.type}</span>
                    {#if editingEntry?.key === entry.key}
                        <input
                            type="text"
                            bind:value={editValue}
                            class="edit-input"
                            onkeydown={(e) => e.key === 'Enter' && saveEdit()}
                        />
                        <div class="entry-actions">
                            <button
                                class="action-btn primary small"
                                onclick={saveEdit}
                                disabled={loadingAction === `edit-${entry.key}`}
                            >
                                {loadingAction === `edit-${entry.key}` ? '...' : 'Save'}
                            </button>
                            <button class="action-btn small" onclick={cancelEdit}>Cancel</button>
                        </div>
                    {:else}
                        <span class="entry-value" title={entry.value}>{entry.value}</span>
                        <div class="entry-actions">
                            <button class="action-btn small" onclick={() => startEdit(entry)}>Edit</button>
                            <button
                                class="action-btn small danger"
                                onclick={() => handleDelete(entry)}
                                disabled={loadingAction === `delete-${entry.key}`}
                            >
                                {loadingAction === `delete-${entry.key}` ? '...' : 'Delete'}
                            </button>
                        </div>
                    {/if}
                </div>
            {/each}
        {/if}
    </div>
</div>

<style>
    .prefs-viewer {
        display: flex;
        flex-direction: column;
        height: 500px;
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        overflow: hidden;
    }

    .toolbar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.5rem;
        padding: 0.75rem;
        border-bottom: 1px solid var(--border-color, #333);
    }

    .toolbar select {
        flex: 1;
        max-width: 300px;
        padding: 0.5rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--input-bg, #252525);
        color: var(--text-color, #fff);
        font-size: 0.875rem;
    }

    .toolbar-actions {
        display: flex;
        gap: 0.5rem;
    }

    .action-btn {
        padding: 0.5rem 0.75rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--input-bg, #252525);
        color: var(--text-color, #fff);
        font-size: 0.875rem;
        cursor: pointer;
    }

    .action-btn:hover:not(:disabled) {
        background: var(--surface-hover, #333);
    }

    .action-btn:disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }

    .action-btn.primary {
        background: var(--primary-color, #3b82f6);
        border-color: var(--primary-color, #3b82f6);
    }

    .action-btn.primary:hover:not(:disabled) {
        opacity: 0.9;
    }

    .action-btn.danger {
        color: #ef4444;
        border-color: #ef4444;
    }

    .action-btn.danger:hover:not(:disabled) {
        background: rgba(239, 68, 68, 0.1);
    }

    .action-btn.small {
        padding: 0.25rem 0.5rem;
        font-size: 0.75rem;
    }

    .expand-button {
        padding: 0.5rem 0.75rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--input-bg, #252525);
        color: var(--text-muted, #888);
        font-size: 0.875rem;
        cursor: pointer;
    }

    .expand-button:hover {
        background: var(--surface-hover, #333);
    }

    .stats-bar {
        display: flex;
        gap: 1rem;
        padding: 0.5rem 0.75rem;
        background: var(--input-bg, #252525);
        border-bottom: 1px solid var(--border-color, #333);
        font-size: 0.75rem;
    }

    .stat {
        color: var(--text-muted, #888);
    }

    .stat.highlight {
        color: var(--primary-color, #3b82f6);
    }

    .add-form {
        display: flex;
        gap: 0.5rem;
        padding: 0.75rem;
        background: var(--input-bg, #252525);
        border-bottom: 1px solid var(--border-color, #333);
        align-items: center;
    }

    .add-form input,
    .add-form select {
        padding: 0.5rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--surface-color, #1e1e1e);
        color: var(--text-color, #fff);
        font-size: 0.875rem;
    }

    .add-form .input-key {
        flex: 1;
    }

    .add-form .input-value {
        flex: 2;
    }

    .entries-list {
        flex: 1;
        overflow-y: auto;
        min-height: 0;
    }

    .empty {
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 3rem;
        color: var(--text-muted, #666);
        font-style: italic;
    }

    .entry-row {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.5rem 0.75rem;
        border-bottom: 1px solid var(--border-color, #222);
    }

    .entry-row:hover {
        background: rgba(255, 255, 255, 0.02);
    }

    .entry-key {
        flex: 1;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 0.85rem;
        color: var(--text-color, #fff);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .type-badge {
        font-size: 0.7rem;
        padding: 0.15rem 0.4rem;
        border-radius: 3px;
        font-weight: 600;
        min-width: 55px;
        text-align: center;
    }

    .type-string { background: #22c55e20; color: #22c55e; }
    .type-number { background: #3b82f620; color: #3b82f6; }
    .type-float { background: #f59e0b20; color: #f59e0b; }
    .type-boolean { background: #8b5cf620; color: #8b5cf6; }
    .type-set { background: #ec489920; color: #ec4899; }
    .type-other { background: #6b728020; color: #6b7280; }

    .entry-value {
        flex: 2;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 0.85rem;
        color: var(--text-muted, #888);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .edit-input {
        flex: 2;
        padding: 0.35rem 0.5rem;
        border: 1px solid var(--primary-color, #3b82f6);
        border-radius: 4px;
        background: var(--input-bg, #252525);
        color: var(--text-color, #fff);
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 0.85rem;
    }

    .entry-actions {
        display: flex;
        gap: 0.25rem;
    }
</style>
