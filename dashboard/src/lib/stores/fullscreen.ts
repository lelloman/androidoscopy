import { writable } from 'svelte/store';

export interface FullscreenState {
    isOpen: boolean;
    widgetType: 'logviewer' | 'chart' | 'table' | 'network_request_viewer' | null;
    props: Record<string, unknown>;
}

const initialState: FullscreenState = {
    isOpen: false,
    widgetType: null,
    props: {}
};

export const fullscreenWidget = writable<FullscreenState>(initialState);

export function openFullscreen(widgetType: FullscreenState['widgetType'], props: Record<string, unknown>) {
    fullscreenWidget.set({
        isOpen: true,
        widgetType,
        props
    });
}

export function closeFullscreen() {
    fullscreenWidget.set(initialState);
}
