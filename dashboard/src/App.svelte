<script lang="ts">
    import { onMount } from 'svelte';
    import {
        connect,
        connected,
        connecting,
        error,
        sessionList,
        activeSessionCount
    } from './lib/stores/connection';
    import SessionCard from './lib/SessionCard.svelte';
    import ToastContainer from './lib/ToastContainer.svelte';
    import ErrorBoundary from './lib/ErrorBoundary.svelte';

    onMount(() => {
        connect();
    });
</script>

<main>
    <header>
        <h1>Androidoscopy</h1>
        <div class="status-bar">
            <span class="status-indicator" class:connected={$connected} class:connecting={$connecting}>
                {#if $connecting}
                    Connecting...
                {:else if $connected}
                    Connected
                {:else}
                    Disconnected
                {/if}
            </span>
            {#if $connected}
                <span class="session-count">
                    {$activeSessionCount} active session{$activeSessionCount !== 1 ? 's' : ''}
                </span>
            {/if}
        </div>
    </header>

    {#if $error}
        <div class="error-banner">
            {$error}
        </div>
    {/if}

    <div class="sessions">
        {#if $sessionList.length === 0}
            <div class="empty-state">
                {#if $connected}
                    <p>No connected apps</p>
                    <p class="hint">Connect an Android app to see its dashboard here</p>
                {:else if $connecting}
                    <p>Connecting to server...</p>
                {:else}
                    <p>Not connected</p>
                    <p class="hint">Make sure the Androidoscopy server is running</p>
                {/if}
            </div>
        {:else}
            {#each $sessionList as session (session.session_id)}
                <ErrorBoundary>
                    {#snippet children()}
                        <SessionCard {session} />
                    {/snippet}
                </ErrorBoundary>
            {/each}
        {/if}
    </div>
</main>

<ToastContainer />

<style>
    :global(:root) {
        --bg-color: #0a0a0a;
        --card-bg: #171717;
        --surface-color: #1e1e1e;
        --border-color: #333;
        --text-color: #fff;
        --text-muted: #888;
        --primary-color: #3b82f6;
        --success-color: #22c55e;
        --warning-color: #f59e0b;
        --danger-color: #ef4444;
        --info-color: #3b82f6;
    }

    :global(body) {
        margin: 0;
        padding: 0;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
        background: var(--bg-color);
        color: var(--text-color);
        min-height: 100vh;
    }

    :global(*) {
        box-sizing: border-box;
    }

    main {
        max-width: 1200px;
        margin: 0 auto;
        padding: 2rem;
    }

    header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 2rem;
        padding-bottom: 1rem;
        border-bottom: 1px solid var(--border-color);
    }

    header h1 {
        margin: 0;
        font-size: 1.5rem;
        font-weight: 700;
    }

    .status-bar {
        display: flex;
        align-items: center;
        gap: 1rem;
    }

    .status-indicator {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 1rem;
        border-radius: 9999px;
        font-size: 0.875rem;
        background: rgba(107, 114, 128, 0.2);
        color: #6b7280;
    }

    .status-indicator::before {
        content: '';
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: currentColor;
    }

    .status-indicator.connected {
        background: rgba(34, 197, 94, 0.2);
        color: #22c55e;
    }

    .status-indicator.connecting {
        background: rgba(245, 158, 11, 0.2);
        color: #f59e0b;
    }

    .session-count {
        color: var(--text-muted);
        font-size: 0.875rem;
    }

    .error-banner {
        background: rgba(239, 68, 68, 0.1);
        border: 1px solid rgba(239, 68, 68, 0.3);
        color: #ef4444;
        padding: 1rem;
        border-radius: 8px;
        margin-bottom: 1rem;
    }

    .sessions {
        display: flex;
        flex-direction: column;
        gap: 1.5rem;
    }

    .empty-state {
        text-align: center;
        padding: 4rem 2rem;
        color: var(--text-muted);
    }

    .empty-state p {
        margin: 0;
    }

    .empty-state .hint {
        margin-top: 0.5rem;
        font-size: 0.875rem;
        opacity: 0.7;
    }
</style>
