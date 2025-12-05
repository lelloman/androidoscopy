import { writable } from 'svelte/store';

export interface Toast {
    id: string;
    message: string;
    type: 'success' | 'error' | 'info';
    duration: number;
}

export const toasts = writable<Toast[]>([]);

export function showToast(
    message: string,
    type: 'success' | 'error' | 'info' = 'info',
    duration?: number
) {
    const id = crypto.randomUUID();
    const toastDuration = duration ?? (type === 'error' ? 5000 : 3000);

    const toast: Toast = {
        id,
        message,
        type,
        duration: toastDuration
    };

    toasts.update(t => [...t, toast]);

    setTimeout(() => {
        removeToast(id);
    }, toastDuration);

    return id;
}

export function removeToast(id: string) {
    toasts.update(t => t.filter(toast => toast.id !== id));
}

export function clearAllToasts() {
    toasts.set([]);
}
