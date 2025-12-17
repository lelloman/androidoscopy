<script lang="ts">
    import type { SqliteViewerWidget, SqliteColumnSchema } from '../types/protocol';
    import { evaluatePath } from '../jsonpath';
    import { sendAction } from '../stores/connection';
    import { openFullscreen } from '../stores/fullscreen';

    interface Props {
        widget: SqliteViewerWidget;
        data: unknown;
        sessionId: string;
        showExpandButton?: boolean;
    }

    let { widget, data, sessionId, showExpandButton = true }: Props = $props();

    let showSchema = $state(false);
    let showQueryDialog = $state(false);
    let queryText = $state('SELECT * FROM ');
    let loadingAction = $state<string | null>(null);
    let queryResult = $state<{ rows: Record<string, unknown>[], message: string } | null>(null);

    let sqliteData = $derived.by(() => {
        const raw = evaluatePath(data, widget.data_path);
        if (!raw || typeof raw !== 'object') return null;
        return raw as {
            databases: string[];
            database_count: number;
            selected_database: string;
            tables: string[];
            table_count: number;
            selected_table: string;
            schema: SqliteColumnSchema[];
            column_count: number;
            data: Record<string, unknown>[];
            row_count: number;
            current_page: number;
            page_size: number;
            total_pages: number;
        };
    });

    let databases = $derived(sqliteData?.databases ?? []);
    let tables = $derived(sqliteData?.tables ?? []);
    let schema = $derived(sqliteData?.schema ?? []);
    let rows = $derived(sqliteData?.data ?? []);
    let selectedDatabase = $derived(sqliteData?.selected_database ?? '');
    let selectedTable = $derived(sqliteData?.selected_table ?? '');
    let currentPage = $derived(sqliteData?.current_page ?? 0);
    let totalPages = $derived(sqliteData?.total_pages ?? 0);
    let rowCount = $derived(sqliteData?.row_count ?? 0);

    // Get column names from first row or schema
    let columns = $derived.by(() => {
        if (rows.length > 0) {
            return Object.keys(rows[0]);
        }
        return schema.map(s => s.name);
    });

    function handleExpand() {
        openFullscreen('sqlite_viewer', { widget, data, sessionId });
    }

    async function handleSelectDatabase(event: Event) {
        const select = event.target as HTMLSelectElement;
        if (loadingAction) return;
        loadingAction = 'select_db';
        try {
            await sendAction(sessionId, 'sqlite_select_db', { database: select.value });
        } finally {
            loadingAction = null;
        }
    }

    async function handleSelectTable(event: Event) {
        const select = event.target as HTMLSelectElement;
        if (loadingAction) return;
        loadingAction = 'select_table';
        try {
            await sendAction(sessionId, 'sqlite_select_table', { table: select.value });
        } finally {
            loadingAction = null;
        }
    }

    async function handleRefresh() {
        if (loadingAction) return;
        loadingAction = 'refresh';
        try {
            await sendAction(sessionId, 'sqlite_refresh', {});
        } finally {
            loadingAction = null;
        }
    }

    async function handlePrevPage() {
        if (loadingAction || currentPage <= 0) return;
        loadingAction = 'prev';
        try {
            await sendAction(sessionId, 'sqlite_prev_page', {});
        } finally {
            loadingAction = null;
        }
    }

    async function handleNextPage() {
        if (loadingAction || currentPage >= totalPages - 1) return;
        loadingAction = 'next';
        try {
            await sendAction(sessionId, 'sqlite_next_page', {});
        } finally {
            loadingAction = null;
        }
    }

    async function handleRunQuery() {
        if (loadingAction || !queryText.trim()) return;
        loadingAction = 'query';
        queryResult = null;
        try {
            const result = await sendAction(sessionId, 'sqlite_query', {
                query: queryText,
                database: selectedDatabase
            });
            if (result.success && result.data) {
                queryResult = {
                    rows: (result.data as { rows?: Record<string, unknown>[] }).rows ?? [],
                    message: result.message ?? 'Query executed'
                };
            } else {
                queryResult = {
                    rows: [],
                    message: result.message ?? 'Query failed'
                };
            }
        } catch (e) {
            queryResult = {
                rows: [],
                message: `Error: ${e}`
            };
        } finally {
            loadingAction = null;
        }
    }

    function getTypeColor(type: string): string {
        const t = type.toUpperCase();
        if (t.includes('INT')) return 'type-int';
        if (t.includes('TEXT') || t.includes('CHAR') || t.includes('VARCHAR')) return 'type-text';
        if (t.includes('REAL') || t.includes('FLOAT') || t.includes('DOUBLE')) return 'type-real';
        if (t.includes('BLOB')) return 'type-blob';
        return 'type-other';
    }

    function formatCellValue(value: unknown): string {
        if (value === null || value === undefined) return 'NULL';
        if (typeof value === 'string' && value.length > 100) {
            return value.substring(0, 100) + '...';
        }
        return String(value);
    }
</script>

<div class="sqlite-viewer">
    <div class="toolbar">
        <div class="selectors">
            <select
                value={selectedDatabase}
                onchange={handleSelectDatabase}
                aria-label="Select database"
                disabled={databases.length === 0 || loadingAction !== null}
            >
                {#if databases.length === 0}
                    <option value="">No databases</option>
                {:else}
                    {#each databases as db}
                        <option value={db}>{db}</option>
                    {/each}
                {/if}
            </select>
            <select
                value={selectedTable}
                onchange={handleSelectTable}
                aria-label="Select table"
                disabled={tables.length === 0 || loadingAction !== null}
            >
                {#if tables.length === 0}
                    <option value="">No tables</option>
                {:else}
                    {#each tables as table}
                        <option value={table}>{table}</option>
                    {/each}
                {/if}
            </select>
        </div>
        <div class="toolbar-actions">
            <button class="action-btn" onclick={() => showSchema = !showSchema}>
                {showSchema ? 'Hide Schema' : 'Schema'}
            </button>
            <button class="action-btn" onclick={() => { showQueryDialog = true; queryResult = null; }}>
                Query
            </button>
            <button class="action-btn" onclick={handleRefresh} disabled={loadingAction === 'refresh'}>
                {loadingAction === 'refresh' ? '...' : 'Refresh'}
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
        <span class="stat">Databases: {sqliteData?.database_count ?? 0}</span>
        <span class="stat">Tables: {sqliteData?.table_count ?? 0}</span>
        <span class="stat">Columns: {sqliteData?.column_count ?? 0}</span>
        <span class="stat highlight">Rows: {rowCount}</span>
    </div>

    {#if showQueryDialog}
        <div class="query-panel">
            <div class="query-header">
                <span>SQL Query</span>
                <button class="close-btn" onclick={() => { showQueryDialog = false; queryResult = null; }}>x</button>
            </div>
            <textarea
                bind:value={queryText}
                placeholder="SELECT * FROM table_name"
                rows="3"
            ></textarea>
            <div class="query-actions">
                <button
                    class="action-btn primary"
                    onclick={handleRunQuery}
                    disabled={loadingAction === 'query' || !queryText.trim()}
                >
                    {loadingAction === 'query' ? 'Running...' : 'Run Query'}
                </button>
            </div>
            {#if queryResult}
                <div class="query-result">
                    <div class="query-message" class:error={queryResult.rows.length === 0 && queryResult.message.includes('Error')}>
                        {queryResult.message}
                    </div>
                    {#if queryResult.rows.length > 0}
                        <div class="query-table-container">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        {#each Object.keys(queryResult.rows[0]) as col}
                                            <th>{col}</th>
                                        {/each}
                                    </tr>
                                </thead>
                                <tbody>
                                    {#each queryResult.rows as row}
                                        <tr>
                                            {#each Object.values(row) as value}
                                                <td class:null-value={value === null}>{formatCellValue(value)}</td>
                                            {/each}
                                        </tr>
                                    {/each}
                                </tbody>
                            </table>
                        </div>
                    {/if}
                </div>
            {/if}
        </div>
    {/if}

    {#if showSchema && schema.length > 0}
        <div class="schema-panel">
            <div class="schema-header">Table Schema: {selectedTable}</div>
            <div class="schema-table-container">
                <table class="schema-table">
                    <thead>
                        <tr>
                            <th>Column</th>
                            <th>Type</th>
                            <th>Not Null</th>
                            <th>PK</th>
                            <th>Default</th>
                        </tr>
                    </thead>
                    <tbody>
                        {#each schema as col}
                            <tr>
                                <td class="col-name">{col.name}</td>
                                <td><span class="type-badge {getTypeColor(col.type)}">{col.type}</span></td>
                                <td class="center">{col.notnull ? 'Yes' : ''}</td>
                                <td class="center">{col.pk ? 'PK' : ''}</td>
                                <td class="default-val">{col.default_value}</td>
                            </tr>
                        {/each}
                    </tbody>
                </table>
            </div>
        </div>
    {/if}

    <div class="data-panel">
        {#if rows.length === 0}
            <div class="empty">
                {#if !selectedTable}
                    Select a table to view data
                {:else}
                    No data in this table
                {/if}
            </div>
        {:else}
            <div class="data-table-container">
                <table class="data-table">
                    <thead>
                        <tr>
                            {#each columns as col}
                                <th>{col}</th>
                            {/each}
                        </tr>
                    </thead>
                    <tbody>
                        {#each rows as row}
                            <tr>
                                {#each columns as col}
                                    <td class:null-value={row[col] === null}>{formatCellValue(row[col])}</td>
                                {/each}
                            </tr>
                        {/each}
                    </tbody>
                </table>
            </div>
        {/if}
    </div>

    {#if totalPages > 1}
        <div class="pagination">
            <button
                class="page-btn"
                onclick={handlePrevPage}
                disabled={currentPage <= 0 || loadingAction !== null}
            >
                Previous
            </button>
            <span class="page-info">Page {currentPage + 1} of {totalPages}</span>
            <button
                class="page-btn"
                onclick={handleNextPage}
                disabled={currentPage >= totalPages - 1 || loadingAction !== null}
            >
                Next
            </button>
        </div>
    {/if}
</div>

<style>
    .sqlite-viewer {
        display: flex;
        flex-direction: column;
        height: 600px;
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

    .selectors {
        display: flex;
        gap: 0.5rem;
        flex: 1;
    }

    .selectors select {
        flex: 1;
        max-width: 200px;
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

    .query-panel {
        padding: 0.75rem;
        background: var(--input-bg, #252525);
        border-bottom: 1px solid var(--border-color, #333);
    }

    .query-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 0.5rem;
        font-size: 0.8rem;
        font-weight: 600;
        color: var(--text-muted, #888);
    }

    .close-btn {
        background: none;
        border: none;
        color: var(--text-muted, #888);
        font-size: 1rem;
        cursor: pointer;
        padding: 0.25rem;
    }

    .close-btn:hover {
        color: var(--text-color, #fff);
    }

    .query-panel textarea {
        width: 100%;
        padding: 0.5rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--surface-color, #1e1e1e);
        color: var(--text-color, #fff);
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 0.85rem;
        resize: vertical;
    }

    .query-actions {
        margin-top: 0.5rem;
    }

    .query-result {
        margin-top: 0.75rem;
    }

    .query-message {
        font-size: 0.8rem;
        color: var(--text-muted, #888);
        margin-bottom: 0.5rem;
    }

    .query-message.error {
        color: #ef4444;
    }

    .query-table-container {
        max-height: 200px;
        overflow: auto;
    }

    .schema-panel {
        padding: 0.75rem;
        background: var(--input-bg, #252525);
        border-bottom: 1px solid var(--border-color, #333);
    }

    .schema-header {
        font-size: 0.8rem;
        font-weight: 600;
        color: var(--text-muted, #888);
        margin-bottom: 0.5rem;
    }

    .schema-table-container {
        max-height: 150px;
        overflow: auto;
    }

    .schema-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.8rem;
    }

    .schema-table th,
    .schema-table td {
        padding: 0.4rem 0.6rem;
        text-align: left;
        border-bottom: 1px solid var(--border-color, #333);
    }

    .schema-table th {
        font-weight: 600;
        color: var(--text-muted, #888);
        font-size: 0.7rem;
        text-transform: uppercase;
    }

    .schema-table .col-name {
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        color: var(--text-color, #fff);
    }

    .schema-table .center {
        text-align: center;
    }

    .schema-table .default-val {
        color: var(--text-muted, #666);
        font-size: 0.75rem;
    }

    .type-badge {
        font-size: 0.7rem;
        padding: 0.1rem 0.35rem;
        border-radius: 3px;
        font-weight: 600;
    }

    .type-int { background: #3b82f620; color: #3b82f6; }
    .type-text { background: #22c55e20; color: #22c55e; }
    .type-real { background: #f59e0b20; color: #f59e0b; }
    .type-blob { background: #8b5cf620; color: #8b5cf6; }
    .type-other { background: #6b728020; color: #6b7280; }

    .data-panel {
        flex: 1;
        overflow: hidden;
        display: flex;
        flex-direction: column;
        min-height: 0;
    }

    .empty {
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 3rem;
        color: var(--text-muted, #666);
        font-style: italic;
        height: 100%;
    }

    .data-table-container {
        flex: 1;
        overflow: auto;
    }

    .data-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.8rem;
    }

    .data-table th,
    .data-table td {
        padding: 0.5rem 0.75rem;
        text-align: left;
        border-bottom: 1px solid var(--border-color, #333);
        white-space: nowrap;
    }

    .data-table th {
        font-weight: 600;
        color: var(--text-muted, #888);
        font-size: 0.75rem;
        text-transform: uppercase;
        background: var(--surface-color, #1e1e1e);
        position: sticky;
        top: 0;
    }

    .data-table tbody tr:hover {
        background: rgba(255, 255, 255, 0.02);
    }

    .data-table td {
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        color: var(--text-color, #fff);
        max-width: 300px;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .data-table td.null-value {
        color: var(--text-muted, #666);
        font-style: italic;
    }

    .pagination {
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 1rem;
        padding: 0.75rem;
        border-top: 1px solid var(--border-color, #333);
        background: var(--input-bg, #252525);
    }

    .page-btn {
        padding: 0.4rem 0.75rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--surface-color, #1e1e1e);
        color: var(--text-color, #fff);
        font-size: 0.8rem;
        cursor: pointer;
    }

    .page-btn:hover:not(:disabled) {
        background: var(--surface-hover, #333);
    }

    .page-btn:disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }

    .page-info {
        font-size: 0.8rem;
        color: var(--text-muted, #888);
    }
</style>
