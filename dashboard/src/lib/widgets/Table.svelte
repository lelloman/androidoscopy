<script lang="ts">
    import type { TableWidget, RowAction } from '../types/protocol';
    import { evaluateArrayPath } from '../jsonpath';
    import { formatValue } from '../format';
    import { sendAction } from '../stores/connection';
    import { openFullscreen } from '../stores/fullscreen';

    interface Props {
        widget: TableWidget;
        data: unknown;
        sessionId: string;
        showExpandButton?: boolean;
    }

    let { widget, data, sessionId, showExpandButton = true }: Props = $props();

    function handleExpand() {
        openFullscreen('table', { widget, data, sessionId });
    }

    let rows = $derived(evaluateArrayPath(data, widget.data_path));
    let loadingActions = $state(new Set<string>());

    async function handleRowAction(action: RowAction, row: Record<string, unknown>) {
        const actionKey = `${action.id}-${JSON.stringify(row)}`;
        if (loadingActions.has(actionKey)) return;

        loadingActions.add(actionKey);
        loadingActions = new Set(loadingActions);

        try {
            // Build args from action template and row data
            const args: Record<string, unknown> = { ...action.args };
            // Add row data to args
            for (const col of widget.columns) {
                args[col.key] = row[col.key];
            }

            await sendAction(sessionId, action.id, args);
        } catch (e) {
            console.error('Action failed:', e);
        } finally {
            loadingActions.delete(actionKey);
            loadingActions = new Set(loadingActions);
        }
    }

    function getCellValue(row: unknown, key: string): unknown {
        if (row && typeof row === 'object' && key in row) {
            return (row as Record<string, unknown>)[key];
        }
        return undefined;
    }
</script>

<div class="table-widget">
    {#if showExpandButton}
        <div class="table-header">
            <button
                class="expand-button"
                onclick={handleExpand}
                aria-label="Open in fullscreen"
                title="Open in fullscreen"
            >
                ⛶
            </button>
        </div>
    {/if}
    <div class="table-container">
        <table>
            <thead>
                <tr>
                    {#each widget.columns as col}
                        <th>{col.label}</th>
                    {/each}
                    {#if widget.row_actions && widget.row_actions.length > 0}
                        <th class="actions-header">Actions</th>
                    {/if}
                </tr>
            </thead>
            <tbody>
                {#if rows.length === 0}
                    <tr>
                        <td colspan={widget.columns.length + (widget.row_actions?.length ? 1 : 0)} class="empty">
                            No data
                        </td>
                    </tr>
                {:else}
                    {#each rows as row, idx}
                        <tr>
                            {#each widget.columns as col}
                                <td>{formatValue(getCellValue(row, col.key), col.format)}</td>
                            {/each}
                            {#if widget.row_actions && widget.row_actions.length > 0}
                                <td class="actions-cell">
                                    {#each widget.row_actions as action}
                                        {@const actionKey = `${action.id}-${JSON.stringify(row)}`}
                                        <button
                                            class="row-action"
                                            onclick={() => handleRowAction(action, row as Record<string, unknown>)}
                                            disabled={loadingActions.has(actionKey)}
                                        >
                                            {#if loadingActions.has(actionKey)}
                                                ...
                                            {:else}
                                                {action.label}
                                            {/if}
                                        </button>
                                    {/each}
                                </td>
                            {/if}
                        </tr>
                    {/each}
                {/if}
            </tbody>
        </table>
    </div>
</div>

<style>
    .table-widget {
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        overflow: hidden;
    }

    .table-header {
        display: flex;
        justify-content: flex-end;
        padding: 0.5rem 0.75rem;
        border-bottom: 1px solid var(--border-color, #333);
    }

    .expand-button {
        padding: 0.25rem 0.5rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--surface-color, #1e1e1e);
        color: var(--text-muted, #888);
        font-size: 0.875rem;
        cursor: pointer;
        line-height: 1;
        opacity: 0.6;
        transition: opacity 0.2s;
    }

    .expand-button:hover {
        opacity: 1;
        color: var(--text-color, #fff);
    }

    .table-container {
        overflow-x: auto;
    }

    table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.875rem;
    }

    th, td {
        padding: 0.75rem 1rem;
        text-align: left;
        border-bottom: 1px solid var(--border-color, #333);
    }

    th {
        font-weight: 600;
        color: var(--text-muted, #888);
        text-transform: uppercase;
        font-size: 0.75rem;
        letter-spacing: 0.05em;
        background: var(--surface-color, #1e1e1e);
        position: sticky;
        top: 0;
    }

    tbody tr:hover {
        background: rgba(255, 255, 255, 0.02);
    }

    td {
        color: var(--text-color, #fff);
        font-variant-numeric: tabular-nums;
    }

    .empty {
        text-align: center;
        color: var(--text-muted, #666);
        font-style: italic;
        padding: 2rem;
    }

    .actions-header {
        text-align: right;
    }

    .actions-cell {
        text-align: right;
        white-space: nowrap;
    }

    .row-action {
        padding: 0.25rem 0.5rem;
        margin-left: 0.25rem;
        background: var(--primary-color, #3b82f6);
        color: white;
        border: none;
        border-radius: 4px;
        font-size: 0.75rem;
        cursor: pointer;
        transition: opacity 0.2s;
    }

    .row-action:hover:not(:disabled) {
        opacity: 0.9;
    }

    .row-action:disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }
</style>
