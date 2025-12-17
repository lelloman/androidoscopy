import type { AlertConfig } from '../types/protocol';
import { evaluateCondition } from '../jsonpath';

export interface AlertState {
    isTriggered: boolean;
    severity: 'info' | 'warning' | 'critical';
    message: string;
}

/**
 * Evaluates an alert configuration against data.
 * Returns the alert state if triggered, null otherwise.
 */
export function evaluateAlert(
    alert: AlertConfig | undefined,
    data: unknown
): AlertState | null {
    if (!alert) return null;

    const triggered = evaluateCondition(data, alert.condition);

    if (triggered) {
        return {
            isTriggered: true,
            severity: alert.severity,
            message: alert.message
        };
    }

    return null;
}

/**
 * Returns CSS class names for alert styling.
 */
export function getAlertClass(alertState: AlertState | null): string {
    if (!alertState?.isTriggered) return '';
    return `alert-${alertState.severity}`;
}
