import { describe, it, expect } from 'vitest';
import {
    getLayoutClass,
    isWidgetVisible,
    getInitialCollapsedState,
    isSectionCollapsible,
} from './layoutLogic';
import type { Section, NumberWidget, Widget } from '../types/protocol';

describe('getLayoutClass', () => {
    it('returns layout-row for undefined layout', () => {
        expect(getLayoutClass(undefined)).toBe('layout-row');
    });

    it('returns layout-row for row layout', () => {
        expect(getLayoutClass('row')).toBe('layout-row');
    });

    it('returns layout-grid for grid layout', () => {
        expect(getLayoutClass('grid')).toBe('layout-grid');
    });

    it('returns layout-stack for stack layout', () => {
        expect(getLayoutClass('stack')).toBe('layout-stack');
    });
});

describe('isWidgetVisible', () => {
    const testData = {
        status: 'active',
        count: 10,
        enabled: true,
    };

    function createNumberWidget(overrides: Partial<NumberWidget> = {}): NumberWidget {
        return {
            type: 'number',
            label: 'Test',
            data_path: '$.count',
            format: 'number',
            ...overrides,
        };
    }

    describe('without visible_when', () => {
        it('returns true when visible_when is undefined', () => {
            const widget = createNumberWidget();
            expect(isWidgetVisible(widget, testData)).toBe(true);
        });
    });

    describe('with visible_when', () => {
        it('returns true when condition is met (eq)', () => {
            const widget = createNumberWidget({
                visible_when: { path: '$.status', operator: 'eq', value: 'active' },
            });
            expect(isWidgetVisible(widget, testData)).toBe(true);
        });

        it('returns false when condition is not met (eq)', () => {
            const widget = createNumberWidget({
                visible_when: { path: '$.status', operator: 'eq', value: 'inactive' },
            });
            expect(isWidgetVisible(widget, testData)).toBe(false);
        });

        it('returns true when count > 5 (gt)', () => {
            const widget = createNumberWidget({
                visible_when: { path: '$.count', operator: 'gt', value: 5 },
            });
            expect(isWidgetVisible(widget, testData)).toBe(true);
        });

        it('returns false when count > 15 (gt)', () => {
            const widget = createNumberWidget({
                visible_when: { path: '$.count', operator: 'gt', value: 15 },
            });
            expect(isWidgetVisible(widget, testData)).toBe(false);
        });

        it('returns true when value exists (exists)', () => {
            const widget = createNumberWidget({
                visible_when: { path: '$.enabled', operator: 'exists' },
            });
            expect(isWidgetVisible(widget, testData)).toBe(true);
        });

        it('returns false when value does not exist (exists)', () => {
            const widget = createNumberWidget({
                visible_when: { path: '$.nonexistent', operator: 'exists' },
            });
            expect(isWidgetVisible(widget, testData)).toBe(false);
        });
    });
});

describe('getInitialCollapsedState', () => {
    function createSection(overrides: Partial<Section> = {}): Section {
        return {
            id: 'test',
            title: 'Test Section',
            ...overrides,
        };
    }

    it('returns false when collapsed_default is undefined', () => {
        const section = createSection();
        expect(getInitialCollapsedState(section)).toBe(false);
    });

    it('returns false when collapsed_default is false', () => {
        const section = createSection({ collapsed_default: false });
        expect(getInitialCollapsedState(section)).toBe(false);
    });

    it('returns true when collapsed_default is true', () => {
        const section = createSection({ collapsed_default: true });
        expect(getInitialCollapsedState(section)).toBe(true);
    });
});

describe('isSectionCollapsible', () => {
    function createSection(overrides: Partial<Section> = {}): Section {
        return {
            id: 'test',
            title: 'Test Section',
            ...overrides,
        };
    }

    it('returns false when collapsible is undefined', () => {
        const section = createSection();
        expect(isSectionCollapsible(section)).toBe(false);
    });

    it('returns false when collapsible is false', () => {
        const section = createSection({ collapsible: false });
        expect(isSectionCollapsible(section)).toBe(false);
    });

    it('returns true when collapsible is true', () => {
        const section = createSection({ collapsible: true });
        expect(isSectionCollapsible(section)).toBe(true);
    });
});
