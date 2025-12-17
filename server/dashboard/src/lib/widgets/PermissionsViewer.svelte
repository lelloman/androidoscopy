<script lang="ts">
    import type { PermissionsViewerWidget, PermissionEntry } from '../types/protocol';
    import { evaluatePath } from '../jsonpath';
    import { sendAction } from '../stores/connection';

    interface Props {
        widget: PermissionsViewerWidget;
        data: unknown;
        sessionId: string;
    }

    let { widget, data, sessionId }: Props = $props();

    let loadingRefresh = $state(false);

    let permissionsData = $derived.by(() => {
        const raw = evaluatePath(data, widget.data_path);
        if (!raw || typeof raw !== 'object') return null;
        return raw as {
            permissions: PermissionEntry[];
            total_count: number;
            granted_count: number;
            denied_count: number;
        };
    });

    let permissions = $derived(permissionsData?.permissions ?? []);
    let granted = $derived(permissions.filter(p => p.status === 'GRANTED'));
    let denied = $derived(permissions.filter(p => p.status === 'DENIED'));

    async function handleRefresh() {
        if (loadingRefresh) return;
        loadingRefresh = true;
        try {
            await sendAction(sessionId, 'permissions_refresh', {});
        } finally {
            loadingRefresh = false;
        }
    }

    function getShortName(permission: PermissionEntry): string {
        // Extract the last part of the permission name (e.g., INTERNET from android.permission.INTERNET)
        return permission.name.split('.').pop() ?? permission.name;
    }
</script>

<div class="permissions-viewer">
    <div class="header">
        <div class="stats">
            <span class="stat granted">{granted.length} granted</span>
            <span class="stat denied">{denied.length} denied</span>
        </div>
        <button class="refresh-btn" onclick={handleRefresh} disabled={loadingRefresh}>
            {loadingRefresh ? '...' : 'Refresh'}
        </button>
    </div>

    {#if granted.length > 0}
        <div class="group">
            <div class="group-header granted">Granted</div>
            <div class="chips">
                {#each granted as perm (perm.name)}
                    <span class="chip granted" title={perm.name}>{getShortName(perm)}</span>
                {/each}
            </div>
        </div>
    {/if}

    {#if denied.length > 0}
        <div class="group">
            <div class="group-header denied">Denied</div>
            <div class="chips">
                {#each denied as perm (perm.name)}
                    <span class="chip denied" title={perm.name}>{getShortName(perm)}</span>
                {/each}
            </div>
        </div>
    {/if}

    {#if permissions.length === 0}
        <div class="empty">No permissions declared</div>
    {/if}
</div>

<style>
    .permissions-viewer {
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        padding: 1rem;
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 1rem;
    }

    .stats {
        display: flex;
        gap: 1rem;
    }

    .stat {
        font-size: 0.85rem;
        font-weight: 600;
    }

    .stat.granted {
        color: #22c55e;
    }

    .stat.denied {
        color: #ef4444;
    }

    .refresh-btn {
        padding: 0.4rem 0.75rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--input-bg, #252525);
        color: var(--text-color, #fff);
        font-size: 0.8rem;
        cursor: pointer;
    }

    .refresh-btn:hover:not(:disabled) {
        background: var(--surface-hover, #333);
    }

    .refresh-btn:disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }

    .group {
        margin-bottom: 1rem;
    }

    .group:last-child {
        margin-bottom: 0;
    }

    .group-header {
        font-size: 0.75rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        margin-bottom: 0.5rem;
        padding-bottom: 0.25rem;
        border-bottom: 1px solid var(--border-color, #333);
    }

    .group-header.granted {
        color: #22c55e;
    }

    .group-header.denied {
        color: #ef4444;
    }

    .chips {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
    }

    .chip {
        display: inline-block;
        padding: 0.35rem 0.6rem;
        border-radius: 16px;
        font-size: 0.75rem;
        font-weight: 500;
        cursor: default;
    }

    .chip.granted {
        background: rgba(34, 197, 94, 0.15);
        color: #22c55e;
    }

    .chip.denied {
        background: rgba(239, 68, 68, 0.15);
        color: #ef4444;
    }

    .empty {
        text-align: center;
        padding: 2rem;
        color: var(--text-muted, #666);
        font-style: italic;
    }
</style>
