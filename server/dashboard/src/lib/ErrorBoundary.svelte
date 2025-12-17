<script lang="ts">
    import type { Snippet } from 'svelte';

    interface Props {
        children: Snippet;
        fallback?: Snippet<[unknown, () => void]>;
    }

    let { children, fallback }: Props = $props();

    let capturedError: unknown = $state(null);

    function handleError(e: unknown, reset: () => void) {
        capturedError = e;
        console.error('Component error:', e);
    }

    function retry() {
        capturedError = null;
    }

    function getErrorMessage(e: unknown): string {
        if (e instanceof Error) return e.message;
        return String(e);
    }
</script>

<svelte:boundary onerror={handleError}>
    {#if capturedError}
        {#if fallback}
            {@render fallback(capturedError, retry)}
        {:else}
            <div class="error-boundary">
                <h3>Something went wrong</h3>
                <p>{getErrorMessage(capturedError)}</p>
                <button onclick={retry}>Try again</button>
            </div>
        {/if}
    {:else}
        {@render children()}
    {/if}
</svelte:boundary>

<style>
    .error-boundary {
        padding: 1.5rem;
        background: rgba(239, 68, 68, 0.1);
        border: 1px solid rgba(239, 68, 68, 0.3);
        border-radius: 8px;
        text-align: center;
    }

    h3 {
        margin: 0 0 0.5rem;
        color: var(--danger-color, #ef4444);
        font-size: 1rem;
    }

    p {
        margin: 0 0 1rem;
        color: var(--text-muted, #888);
        font-size: 0.875rem;
    }

    button {
        padding: 0.5rem 1rem;
        background: var(--primary-color, #3b82f6);
        color: white;
        border: none;
        border-radius: 6px;
        cursor: pointer;
        font-size: 0.875rem;
    }

    button:hover {
        opacity: 0.9;
    }
</style>
