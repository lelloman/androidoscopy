<script lang="ts">
    import type { NetworkRequestViewerWidget, NetworkRequest } from '../types/protocol';
    import { evaluateArrayPath } from '../jsonpath';
    import { formatDuration } from '../format';
    import { openFullscreen } from '../stores/fullscreen';
    import { sendAction } from '../stores/connection';

    interface Props {
        widget: NetworkRequestViewerWidget;
        data: unknown;
        sessionId: string;
        showExpandButton?: boolean;
    }

    let { widget, data, sessionId, showExpandButton = true }: Props = $props();

    let methodFilter = $state<string>('ALL');
    let statusFilter = $state<string>('ALL');
    let searchFilter = $state('');
    let selectedRequest = $state<NetworkRequest | null>(null);
    let clearing = $state(false);

    const HTTP_METHODS = ['ALL', 'GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'];
    const STATUS_FILTERS = ['ALL', 'Success', 'Error'];

    let requests = $derived.by(() => {
        const raw = evaluateArrayPath(data, widget.data_path);
        return raw as NetworkRequest[];
    });

    let filteredRequests = $derived.by(() => {
        return requests.filter((req) => {
            if (methodFilter !== 'ALL' && req.method !== methodFilter) return false;
            if (statusFilter === 'Success' && !req.is_success) return false;
            if (statusFilter === 'Error' && !req.is_error) return false;
            if (searchFilter && !req.url.toLowerCase().includes(searchFilter.toLowerCase())) return false;
            return true;
        });
    });

    function handleExpand() {
        openFullscreen('network_request_viewer', { widget, data, sessionId });
    }

    function selectRequest(request: NetworkRequest) {
        selectedRequest = selectedRequest?.id === request.id ? null : request;
    }

    function getStatusColor(code: number, isError: boolean): string {
        if (isError) return 'error';
        if (code >= 200 && code < 300) return 'success';
        if (code >= 300 && code < 400) return 'info';
        if (code >= 400 && code < 500) return 'warning';
        return 'error';
    }

    function getMethodColor(method: string): string {
        switch (method) {
            case 'GET': return 'method-get';
            case 'POST': return 'method-post';
            case 'PUT': return 'method-put';
            case 'DELETE': return 'method-delete';
            case 'PATCH': return 'method-patch';
            default: return 'method-other';
        }
    }

    function formatTimestamp(ts: string): string {
        try {
            const date = new Date(ts);
            return date.toLocaleTimeString();
        } catch {
            return ts;
        }
    }

    async function handleClear() {
        if (clearing) return;
        clearing = true;
        try {
            await sendAction(sessionId, 'network_clear', {});
        } catch (e) {
            console.error('Failed to clear network history:', e);
        } finally {
            clearing = false;
        }
    }
</script>

<div class="network-viewer">
    <div class="filters">
        <select bind:value={methodFilter} aria-label="Filter by HTTP method">
            {#each HTTP_METHODS as method}
                <option value={method}>{method}</option>
            {/each}
        </select>
        <select bind:value={statusFilter} aria-label="Filter by status">
            {#each STATUS_FILTERS as status}
                <option value={status}>{status}</option>
            {/each}
        </select>
        <input
            type="text"
            placeholder="Search URL..."
            bind:value={searchFilter}
            aria-label="Search requests"
        />
        <button class="clear-button" onclick={handleClear} disabled={clearing}>
            {clearing ? '...' : 'Clear'}
        </button>
        {#if showExpandButton}
            <button
                class="expand-button"
                onclick={handleExpand}
                aria-label="Open in fullscreen"
                title="Open in fullscreen"
            >
                ⛶
            </button>
        {/if}
    </div>

    <div class="stats-bar">
        <span class="stat">Total: {requests.length}</span>
        <span class="stat success">2xx: {requests.filter(r => r.response_code >= 200 && r.response_code < 300).length}</span>
        <span class="stat warning">4xx: {requests.filter(r => r.response_code >= 400 && r.response_code < 500).length}</span>
        <span class="stat error">5xx/Err: {requests.filter(r => r.is_error || r.response_code >= 500).length}</span>
    </div>

    <div class="request-list">
        {#if filteredRequests.length === 0}
            <div class="empty">No requests captured</div>
        {:else}
            {#each filteredRequests as request}
                <button
                    class="request-row"
                    class:selected={selectedRequest?.id === request.id}
                    class:error={request.is_error}
                    onclick={() => selectRequest(request)}
                >
                    <span class="method {getMethodColor(request.method)}">{request.method}</span>
                    <span class="status {getStatusColor(request.response_code, request.is_error)}">
                        {request.is_error ? 'ERR' : request.response_code}
                    </span>
                    <span class="url" title={request.url}>{request.path || request.url}</span>
                    <span class="host">{request.host}</span>
                    <span class="duration">{formatDuration(request.duration_ms)}</span>
                    <span class="time">{formatTimestamp(request.timestamp)}</span>
                </button>
            {/each}
        {/if}
    </div>

    {#if selectedRequest}
        <div class="request-detail">
            <div class="detail-header">
                <span class="method {getMethodColor(selectedRequest.method)}">{selectedRequest.method}</span>
                <span class="detail-url">{selectedRequest.url}</span>
                <button class="close-detail" onclick={() => selectedRequest = null}>×</button>
            </div>

            <div class="detail-tabs">
                <div class="detail-section">
                    <h4>Response</h4>
                    <div class="detail-row">
                        <span class="label">Status:</span>
                        <span class="status {getStatusColor(selectedRequest.response_code, selectedRequest.is_error)}">
                            {selectedRequest.is_error ? `Error: ${selectedRequest.error}` : selectedRequest.response_code}
                        </span>
                    </div>
                    <div class="detail-row">
                        <span class="label">Duration:</span>
                        <span>{formatDuration(selectedRequest.duration_ms)}</span>
                    </div>
                </div>

                {#if selectedRequest.request_headers}
                    <div class="detail-section">
                        <h4>Request Headers</h4>
                        <pre class="headers">{selectedRequest.request_headers}</pre>
                    </div>
                {/if}

                {#if selectedRequest.response_headers}
                    <div class="detail-section">
                        <h4>Response Headers</h4>
                        <pre class="headers">{selectedRequest.response_headers}</pre>
                    </div>
                {/if}

                {#if selectedRequest.request_body}
                    <div class="detail-section">
                        <h4>Request Body</h4>
                        <pre class="body">{selectedRequest.request_body}</pre>
                    </div>
                {/if}

                {#if selectedRequest.response_body}
                    <div class="detail-section">
                        <h4>Response Body</h4>
                        <pre class="body">{selectedRequest.response_body}</pre>
                    </div>
                {/if}
            </div>
        </div>
    {/if}
</div>

<style>
    .network-viewer {
        display: flex;
        flex-direction: column;
        height: 450px;
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        overflow: hidden;
    }

    .filters {
        display: flex;
        gap: 0.5rem;
        padding: 0.75rem;
        border-bottom: 1px solid var(--border-color, #333);
    }

    .filters select,
    .filters input {
        padding: 0.5rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--input-bg, #252525);
        color: var(--text-color, #fff);
        font-size: 0.875rem;
    }

    .filters input {
        flex: 1;
    }

    .clear-button,
    .expand-button {
        padding: 0.5rem 0.75rem;
        border: 1px solid var(--border-color, #333);
        border-radius: 4px;
        background: var(--input-bg, #252525);
        color: var(--text-color, #fff);
        font-size: 0.875rem;
        cursor: pointer;
    }

    .clear-button:hover,
    .expand-button:hover {
        background: var(--surface-hover, #333);
    }

    .clear-button:disabled {
        opacity: 0.5;
        cursor: not-allowed;
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

    .stat.success { color: #22c55e; }
    .stat.warning { color: #f59e0b; }
    .stat.error { color: #ef4444; }

    .request-list {
        flex: 1;
        overflow-y: auto;
    }

    .empty {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
        color: var(--text-muted, #666);
        font-style: italic;
    }

    .request-row {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.5rem 0.75rem;
        border: none;
        border-bottom: 1px solid var(--border-color, #222);
        background: transparent;
        color: var(--text-color, #fff);
        font-size: 0.8rem;
        cursor: pointer;
        text-align: left;
        width: 100%;
    }

    .request-row:hover {
        background: rgba(255, 255, 255, 0.02);
    }

    .request-row.selected {
        background: rgba(59, 130, 246, 0.1);
    }

    .request-row.error {
        background: rgba(239, 68, 68, 0.05);
    }

    .method {
        font-weight: 600;
        font-size: 0.7rem;
        padding: 0.15rem 0.4rem;
        border-radius: 3px;
        text-transform: uppercase;
        min-width: 50px;
        text-align: center;
    }

    .method-get { background: #22c55e20; color: #22c55e; }
    .method-post { background: #3b82f620; color: #3b82f6; }
    .method-put { background: #f59e0b20; color: #f59e0b; }
    .method-delete { background: #ef444420; color: #ef4444; }
    .method-patch { background: #8b5cf620; color: #8b5cf6; }
    .method-other { background: #6b728020; color: #6b7280; }

    .status {
        font-weight: 600;
        font-size: 0.75rem;
        min-width: 35px;
        text-align: center;
    }

    .status.success { color: #22c55e; }
    .status.info { color: #3b82f6; }
    .status.warning { color: #f59e0b; }
    .status.error { color: #ef4444; }

    .url {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
    }

    .host {
        color: var(--text-muted, #666);
        font-size: 0.7rem;
        max-width: 120px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .duration {
        color: var(--text-muted, #888);
        font-size: 0.75rem;
        min-width: 60px;
        text-align: right;
        font-variant-numeric: tabular-nums;
    }

    .time {
        color: var(--text-muted, #666);
        font-size: 0.7rem;
        min-width: 70px;
        text-align: right;
    }

    .request-detail {
        border-top: 1px solid var(--border-color, #333);
        max-height: 250px;
        overflow-y: auto;
        background: var(--input-bg, #252525);
    }

    .detail-header {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 0.75rem;
        border-bottom: 1px solid var(--border-color, #333);
        position: sticky;
        top: 0;
        background: var(--input-bg, #252525);
    }

    .detail-url {
        flex: 1;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 0.8rem;
        word-break: break-all;
    }

    .close-detail {
        background: none;
        border: none;
        color: var(--text-muted, #666);
        font-size: 1.25rem;
        cursor: pointer;
        padding: 0.25rem;
        line-height: 1;
    }

    .close-detail:hover {
        color: var(--text-color, #fff);
    }

    .detail-tabs {
        padding: 0.75rem;
    }

    .detail-section {
        margin-bottom: 1rem;
    }

    .detail-section h4 {
        margin: 0 0 0.5rem;
        font-size: 0.75rem;
        color: var(--text-muted, #888);
        text-transform: uppercase;
        letter-spacing: 0.05em;
    }

    .detail-row {
        display: flex;
        gap: 0.5rem;
        font-size: 0.85rem;
        margin-bottom: 0.25rem;
    }

    .label {
        color: var(--text-muted, #888);
    }

    .headers,
    .body {
        background: var(--surface-color, #1e1e1e);
        padding: 0.5rem;
        border-radius: 4px;
        font-size: 0.75rem;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        overflow-x: auto;
        white-space: pre-wrap;
        word-break: break-all;
        max-height: 150px;
        overflow-y: auto;
        margin: 0;
    }
</style>
