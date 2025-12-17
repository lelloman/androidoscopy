<script lang="ts">
    import { toasts, removeToast } from './stores/toasts';
</script>

<div class="toast-container">
    {#each $toasts as toast (toast.id)}
        <div class="toast {toast.type}">
            <span class="message">{toast.message}</span>
            <button class="close" onclick={() => removeToast(toast.id)}>×</button>
        </div>
    {/each}
</div>

<style>
    .toast-container {
        position: fixed;
        bottom: 1.5rem;
        right: 1.5rem;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        z-index: 1000;
        max-width: 400px;
    }

    .toast {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        padding: 0.75rem 1rem;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
        animation: slideIn 0.3s ease;
    }

    @keyframes slideIn {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }

    .toast.success {
        background: var(--success-color, #22c55e);
        color: white;
    }

    .toast.error {
        background: var(--danger-color, #ef4444);
        color: white;
    }

    .toast.warning {
        background: var(--warning-color, #f59e0b);
        color: white;
    }

    .toast.info {
        background: var(--primary-color, #3b82f6);
        color: white;
    }

    .message {
        font-size: 0.875rem;
        line-height: 1.4;
    }

    .close {
        background: none;
        border: none;
        color: inherit;
        font-size: 1.25rem;
        cursor: pointer;
        padding: 0;
        opacity: 0.7;
        line-height: 1;
    }

    .close:hover {
        opacity: 1;
    }
</style>
