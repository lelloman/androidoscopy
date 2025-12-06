<script lang="ts">
    import type { Session } from './types/protocol';
    import Section from './layout/Section.svelte';
    import { getRelativeTime } from './format';

    interface Props {
        session: Session;
    }

    let { session }: Props = $props();

    let isEnded = $derived(!!session.ended_at);
</script>

<article class="session-card" class:ended={isEnded}>
    <header class="session-header">
        <div class="app-info">
            <h2>{session.app_name}</h2>
            <span class="version">v{session.version_name}</span>
            {#if isEnded}
                <span class="status ended">Ended</span>
            {:else}
                <span class="status active">Active</span>
            {/if}
        </div>
        <div class="device-info">
            <span class="device">{session.device.manufacturer} {session.device.model}</span>
            <span class="android">Android {session.device.android_version}</span>
            {#if session.device.is_emulator}
                <span class="emulator">Emulator</span>
            {/if}
        </div>
        <div class="time-info">
            Started {getRelativeTime(session.started_at)}
        </div>
    </header>

    <div class="sections">
        {#if session.dashboard?.sections}
            {#each session.dashboard.sections as section}
                <Section
                    {section}
                    data={session.latest_data}
                    logs={session.recent_logs}
                    sessionId={session.session_id}
                />
            {/each}
        {/if}
    </div>
</article>

<style>
    .session-card {
        background: var(--card-bg, #171717);
        border-radius: 16px;
        overflow: hidden;
        border: 1px solid var(--border-color, #333);
    }

    .session-card.ended {
        opacity: 0.6;
    }

    .session-header {
        padding: 1.5rem;
        border-bottom: 1px solid var(--border-color, #333);
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        flex-wrap: wrap;
        gap: 1rem;
    }

    .app-info {
        display: flex;
        align-items: center;
        gap: 0.75rem;
    }

    .app-info h2 {
        margin: 0;
        font-size: 1.25rem;
        font-weight: 600;
    }

    .version {
        color: var(--text-muted, #888);
        font-size: 0.875rem;
    }

    .status {
        padding: 0.25rem 0.5rem;
        border-radius: 4px;
        font-size: 0.75rem;
        font-weight: 500;
        text-transform: uppercase;
    }

    .status.active {
        background: rgba(34, 197, 94, 0.2);
        color: #22c55e;
    }

    .status.ended {
        background: rgba(107, 114, 128, 0.2);
        color: #6b7280;
    }

    .device-info {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        color: var(--text-muted, #888);
        font-size: 0.875rem;
    }

    .emulator {
        background: rgba(59, 130, 246, 0.2);
        color: #3b82f6;
        padding: 0.125rem 0.375rem;
        border-radius: 4px;
        font-size: 0.75rem;
    }

    .time-info {
        color: var(--text-muted, #666);
        font-size: 0.75rem;
    }

    .sections {
        padding: 1rem;
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
        gap: 1rem;
    }

    /* Let full-width sections like logs span all columns */
    .sections :global(.section-full-width) {
        grid-column: 1 / -1;
    }
</style>
