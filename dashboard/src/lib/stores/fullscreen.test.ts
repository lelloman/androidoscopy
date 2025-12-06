import { describe, it, expect, beforeEach } from 'vitest';
import { get } from 'svelte/store';
import { fullscreenWidget, openFullscreen, closeFullscreen } from './fullscreen';

describe('fullscreen store', () => {
    beforeEach(() => {
        closeFullscreen();
    });

    describe('initial state', () => {
        it('starts closed', () => {
            const state = get(fullscreenWidget);
            expect(state.isOpen).toBe(false);
            expect(state.widgetType).toBeNull();
            expect(state.props).toEqual({});
        });
    });

    describe('openFullscreen', () => {
        it('opens with logviewer type', () => {
            const logs = [{ level: 'INFO', message: 'test', timestamp: '2024-01-01' }];
            openFullscreen('logviewer', { logs, defaultLevel: 'DEBUG' });

            const state = get(fullscreenWidget);
            expect(state.isOpen).toBe(true);
            expect(state.widgetType).toBe('logviewer');
            expect(state.props.logs).toEqual(logs);
            expect(state.props.defaultLevel).toBe('DEBUG');
        });

        it('opens with chart type', () => {
            const widget = { type: 'chart', label: 'Test', data_path: '$.value' };
            const data = { value: 42 };
            openFullscreen('chart', { widget, data });

            const state = get(fullscreenWidget);
            expect(state.isOpen).toBe(true);
            expect(state.widgetType).toBe('chart');
            expect(state.props.widget).toEqual(widget);
            expect(state.props.data).toEqual(data);
        });

        it('opens with table type', () => {
            const widget = { type: 'table', columns: [], data_path: '$.items' };
            const data = { items: [] };
            openFullscreen('table', { widget, data, sessionId: 'session-123' });

            const state = get(fullscreenWidget);
            expect(state.isOpen).toBe(true);
            expect(state.widgetType).toBe('table');
            expect(state.props.sessionId).toBe('session-123');
        });
    });

    describe('closeFullscreen', () => {
        it('resets to initial state', () => {
            openFullscreen('logviewer', { logs: [] });
            closeFullscreen();

            const state = get(fullscreenWidget);
            expect(state.isOpen).toBe(false);
            expect(state.widgetType).toBeNull();
            expect(state.props).toEqual({});
        });
    });
});
