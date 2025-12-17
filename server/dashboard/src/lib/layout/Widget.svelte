<script lang="ts">
    import type { Widget } from '../types/protocol';
    import { evaluateCondition } from '../jsonpath';
    import { evaluateAlert, getAlertClass } from '../widgets/alertLogic';
    import { showToast } from '../stores/toasts';
    import NumberWidget from '../widgets/Number.svelte';
    import TextWidget from '../widgets/Text.svelte';
    import GaugeWidget from '../widgets/Gauge.svelte';
    import BadgeWidget from '../widgets/Badge.svelte';
    import ButtonWidget from '../widgets/Button.svelte';
    import TableWidget from '../widgets/Table.svelte';
    import ChartWidget from '../widgets/Chart.svelte';

    interface Props {
        widget: Widget;
        data: unknown;
        sessionId: string;
    }

    let { widget, data, sessionId }: Props = $props();

    let visible = $derived(
        !widget.visible_when || evaluateCondition(data, widget.visible_when)
    );

    let alertState = $derived(evaluateAlert(widget.alert, data));
    let alertClass = $derived(getAlertClass(alertState));

    // Track previous alert state to only show toast on transition to triggered
    let wasTriggered = $state(false);

    $effect(() => {
        if (alertState?.isTriggered && !wasTriggered) {
            // Alert just became triggered - show toast
            const toastType = alertState.severity === 'critical' ? 'error'
                : alertState.severity === 'warning' ? 'warning'
                : 'info';
            showToast(alertState.message, toastType);
        }
        wasTriggered = alertState?.isTriggered ?? false;
    });
</script>

{#if visible}
    <div class="widget-wrapper {alertClass}">
        {#if widget.type === 'number'}
            <NumberWidget {widget} {data} />
        {:else if widget.type === 'text'}
            <TextWidget {widget} {data} />
        {:else if widget.type === 'gauge'}
            <GaugeWidget {widget} {data} />
        {:else if widget.type === 'badge'}
            <BadgeWidget {widget} {data} />
        {:else if widget.type === 'button'}
            <ButtonWidget {widget} {sessionId} />
        {:else if widget.type === 'table'}
            <TableWidget {widget} {data} {sessionId} />
        {:else if widget.type === 'chart'}
            <ChartWidget {widget} {data} />
        {:else}
            <div class="unknown-widget">
                Unknown widget type: {widget.type}
            </div>
        {/if}
    </div>
{/if}

<style>
    .widget-wrapper {
        transition: all 0.3s ease;
        border-radius: 8px;
    }

    .widget-wrapper.alert-critical {
        border: 2px solid #ef4444;
        box-shadow: 0 0 15px rgba(239, 68, 68, 0.5), inset 0 0 10px rgba(239, 68, 68, 0.1);
        animation: alertPulse 2s infinite;
    }

    .widget-wrapper.alert-warning {
        border: 2px solid #f59e0b;
        box-shadow: 0 0 10px rgba(245, 158, 11, 0.4);
    }

    .widget-wrapper.alert-info {
        border: 2px solid #3b82f6;
        box-shadow: 0 0 8px rgba(59, 130, 246, 0.3);
    }

    @keyframes alertPulse {
        0%, 100% { opacity: 1; }
        50% { opacity: 0.85; }
    }

    .unknown-widget {
        padding: 1rem;
        background: var(--surface-color, #1e1e1e);
        border-radius: 8px;
        color: var(--text-muted, #888);
        font-style: italic;
    }
</style>
