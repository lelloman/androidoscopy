import type { Section, Widget } from '../types/protocol';
import { evaluateCondition } from '../jsonpath';

/**
 * Determines the CSS class name for a section's layout.
 */
export function getLayoutClass(layout?: 'row' | 'grid' | 'stack'): string {
    return `layout-${layout || 'row'}`;
}

/**
 * Determines if a widget should be visible based on its visible_when condition.
 */
export function isWidgetVisible(widget: Widget, data: unknown): boolean {
    if (!widget.visible_when) {
        return true;
    }
    return evaluateCondition(data, widget.visible_when);
}

/**
 * Gets the initial collapsed state of a section.
 */
export function getInitialCollapsedState(section: Section): boolean {
    return section.collapsed_default ?? false;
}

/**
 * Determines if a section can be collapsed.
 */
export function isSectionCollapsible(section: Section): boolean {
    return section.collapsible ?? false;
}
