<script lang="ts">
    import type { ButtonWidget } from '../types/protocol';
    import { sendAction } from '../stores/connection';

    interface Props {
        widget: ButtonWidget;
        sessionId: string;
        onShowDialog?: (widget: ButtonWidget) => void;
        onShowToast?: (message: string, type: 'success' | 'error') => void;
    }

    let { widget, sessionId, onShowDialog, onShowToast }: Props = $props();

    type ButtonState = 'idle' | 'loading' | 'success' | 'error';
    let buttonState: ButtonState = $state('idle');
    let resultMessage: string | null = $state(null);

    async function handleClick() {
        if (buttonState === 'loading') return;

        // If has dialog, show it instead of executing directly
        if (widget.args_dialog && onShowDialog) {
            onShowDialog(widget);
            return;
        }

        await executeAction({});
    }

    export async function executeAction(args: Record<string, unknown>) {
        buttonState = 'loading';
        resultMessage = null;

        try {
            const result = await sendAction(sessionId, widget.action, args);
            buttonState = result.success ? 'success' : 'error';
            resultMessage = result.message || null;

            // Handle result display
            const displayType = widget.result_display?.type || 'toast';
            if (displayType === 'toast' && onShowToast && resultMessage) {
                onShowToast(resultMessage, result.success ? 'success' : 'error');
            }

            // Reset state after delay
            setTimeout(() => {
                buttonState = 'idle';
                if (displayType !== 'inline') {
                    resultMessage = null;
                }
            }, 2000);
        } catch (e) {
            buttonState = 'error';
            resultMessage = e instanceof Error ? e.message : 'Action failed';

            if (onShowToast) {
                onShowToast(resultMessage, 'error');
            }

            setTimeout(() => {
                buttonState = 'idle';
            }, 2000);
        }
    }

    let buttonClass = $derived(
        `button-widget ${widget.style || 'primary'} ${buttonState}`
    );
</script>

<div class="button-container">
    <button
        class={buttonClass}
        onclick={handleClick}
        disabled={buttonState === 'loading'}
        aria-label={buttonState === 'loading' ? `${widget.label} - loading` : buttonState === 'success' ? `${widget.label} - success` : buttonState === 'error' ? `${widget.label} - error` : widget.label}
        aria-busy={buttonState === 'loading'}
    >
        {#if buttonState === 'loading'}
            <span class="spinner" aria-hidden="true"></span>
        {:else if buttonState === 'success'}
            <span aria-hidden="true">✓</span>
        {:else if buttonState === 'error'}
            <span aria-hidden="true">✕</span>
        {:else}
            {widget.label}
        {/if}
    </button>

    {#if widget.result_display?.type === 'inline' && resultMessage}
        <span class="inline-result {buttonState}">{resultMessage}</span>
    {/if}
</div>

<style>
    .button-container {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
    }

    .button-widget {
        padding: 0.5rem 1rem;
        border: none;
        border-radius: 6px;
        font-size: 0.875rem;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.2s;
        min-width: 80px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
    }

    .button-widget.primary {
        background: var(--primary-color, #3b82f6);
        color: white;
    }

    .button-widget.primary:hover:not(:disabled) {
        background: #2563eb;
    }

    .button-widget.secondary {
        background: var(--surface-color, #333);
        color: var(--text-color, #fff);
        border: 1px solid var(--border-color, #444);
    }

    .button-widget.secondary:hover:not(:disabled) {
        background: #404040;
    }

    .button-widget.danger {
        background: var(--danger-color, #ef4444);
        color: white;
    }

    .button-widget.danger:hover:not(:disabled) {
        background: #dc2626;
    }

    .button-widget:disabled {
        opacity: 0.7;
        cursor: not-allowed;
    }

    .button-widget.success {
        background: var(--success-color, #22c55e) !important;
    }

    .button-widget.error {
        background: var(--danger-color, #ef4444) !important;
    }

    .spinner {
        width: 14px;
        height: 14px;
        border: 2px solid rgba(255, 255, 255, 0.3);
        border-top-color: white;
        border-radius: 50%;
        animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
        to {
            transform: rotate(360deg);
        }
    }

    .inline-result {
        font-size: 0.875rem;
    }

    .inline-result.success {
        color: var(--success-color, #22c55e);
    }

    .inline-result.error {
        color: var(--danger-color, #ef4444);
    }
</style>
