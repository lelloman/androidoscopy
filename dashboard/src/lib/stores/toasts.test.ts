import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { get } from 'svelte/store';
import { toasts, showToast, removeToast, clearAllToasts, type Toast } from './toasts';

describe('toasts store', () => {
    beforeEach(() => {
        clearAllToasts();
        vi.useFakeTimers();
        // Mock crypto.randomUUID
        vi.stubGlobal('crypto', {
            randomUUID: () => 'test-uuid-' + Math.random().toString(36).substr(2, 9)
        });
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
    });

    describe('showToast', () => {
        it('adds a toast to the store', () => {
            showToast('Test message');

            const currentToasts = get(toasts);
            expect(currentToasts).toHaveLength(1);
            expect(currentToasts[0].message).toBe('Test message');
        });

        it('uses info type by default', () => {
            showToast('Test message');

            const currentToasts = get(toasts);
            expect(currentToasts[0].type).toBe('info');
        });

        it('allows setting custom type', () => {
            showToast('Error message', 'error');

            const currentToasts = get(toasts);
            expect(currentToasts[0].type).toBe('error');
        });

        it('uses 5000ms duration for error toasts by default', () => {
            showToast('Error message', 'error');

            const currentToasts = get(toasts);
            expect(currentToasts[0].duration).toBe(5000);
        });

        it('uses 3000ms duration for non-error toasts by default', () => {
            showToast('Info message', 'info');

            const currentToasts = get(toasts);
            expect(currentToasts[0].duration).toBe(3000);
        });

        it('allows setting custom duration', () => {
            showToast('Custom duration', 'info', 10000);

            const currentToasts = get(toasts);
            expect(currentToasts[0].duration).toBe(10000);
        });

        it('returns the toast id', () => {
            const id = showToast('Test message');

            expect(id).toBeTruthy();
            const currentToasts = get(toasts);
            expect(currentToasts[0].id).toBe(id);
        });

        it('auto-removes toast after duration', () => {
            showToast('Test message', 'info', 3000);

            expect(get(toasts)).toHaveLength(1);

            vi.advanceTimersByTime(3000);

            expect(get(toasts)).toHaveLength(0);
        });

        it('can add multiple toasts', () => {
            showToast('First');
            showToast('Second');
            showToast('Third');

            expect(get(toasts)).toHaveLength(3);
        });
    });

    describe('removeToast', () => {
        it('removes toast by id', () => {
            const id = showToast('Test message');
            expect(get(toasts)).toHaveLength(1);

            removeToast(id);

            expect(get(toasts)).toHaveLength(0);
        });

        it('only removes the specified toast', () => {
            showToast('First');
            const id = showToast('Second');
            showToast('Third');

            expect(get(toasts)).toHaveLength(3);

            removeToast(id);

            const remaining = get(toasts);
            expect(remaining).toHaveLength(2);
            expect(remaining.find(t => t.message === 'Second')).toBeUndefined();
        });

        it('does nothing if id not found', () => {
            showToast('Test message');

            removeToast('nonexistent-id');

            expect(get(toasts)).toHaveLength(1);
        });
    });

    describe('clearAllToasts', () => {
        it('removes all toasts', () => {
            showToast('First');
            showToast('Second');
            showToast('Third');

            expect(get(toasts)).toHaveLength(3);

            clearAllToasts();

            expect(get(toasts)).toHaveLength(0);
        });

        it('works when already empty', () => {
            clearAllToasts();

            expect(get(toasts)).toHaveLength(0);
        });
    });

    describe('toast types', () => {
        it('supports success type', () => {
            showToast('Success!', 'success');
            expect(get(toasts)[0].type).toBe('success');
        });

        it('supports warning type', () => {
            showToast('Warning!', 'warning');
            expect(get(toasts)[0].type).toBe('warning');
        });

        it('supports error type', () => {
            showToast('Error!', 'error');
            expect(get(toasts)[0].type).toBe('error');
        });

        it('supports info type', () => {
            showToast('Info!', 'info');
            expect(get(toasts)[0].type).toBe('info');
        });
    });
});
