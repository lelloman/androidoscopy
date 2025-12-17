import type { LogEntry, LogLevel } from '../types/protocol';

export const LOG_LEVELS: readonly LogLevel[] = ['VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR'];

export type ExportFormat = 'text' | 'json' | 'csv' | 'logcat';

/**
 * Formats a log entry as plain text.
 */
function formatAsText(log: LogEntry): string {
    const time = new Date(log.timestamp).toISOString();
    const throwable = log.throwable ? `\n${log.throwable}` : '';
    return `[${time}] ${log.level} ${log.tag || '-'}: ${log.message}${throwable}`;
}

/**
 * Formats a log entry in Android logcat format.
 * Format: MM-DD HH:MM:SS.mmm LEVEL/TAG: message
 */
function formatAsLogcat(log: LogEntry): string {
    const date = new Date(log.timestamp);
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    const millis = String(date.getMilliseconds()).padStart(3, '0');
    const levelChar = log.level.charAt(0);
    const throwable = log.throwable ? `\n${log.throwable}` : '';
    return `${month}-${day} ${hours}:${minutes}:${seconds}.${millis} ${levelChar}/${log.tag || 'unknown'}: ${log.message}${throwable}`;
}

/**
 * Escapes a value for CSV format.
 */
function escapeCsv(value: string): string {
    if (value.includes(',') || value.includes('"') || value.includes('\n')) {
        return `"${value.replace(/"/g, '""')}"`;
    }
    return value;
}

/**
 * Exports logs to the specified format.
 */
export function exportLogs(logs: LogEntry[], format: ExportFormat): string {
    switch (format) {
        case 'text':
            return logs.map(formatAsText).join('\n');
        case 'json':
            return JSON.stringify(logs, null, 2);
        case 'csv': {
            const header = 'timestamp,level,tag,message,throwable';
            const rows = logs.map(log => {
                const time = new Date(log.timestamp).toISOString();
                return [
                    time,
                    log.level,
                    escapeCsv(log.tag || ''),
                    escapeCsv(log.message),
                    escapeCsv(log.throwable || '')
                ].join(',');
            });
            return [header, ...rows].join('\n');
        }
        case 'logcat':
            return logs.map(formatAsLogcat).join('\n');
    }
}

/**
 * Returns the file extension for the given format.
 */
export function getExportExtension(format: ExportFormat): string {
    switch (format) {
        case 'text': return 'txt';
        case 'json': return 'json';
        case 'csv': return 'csv';
        case 'logcat': return 'log';
    }
}

/**
 * Returns the MIME type for the given format.
 */
export function getExportMimeType(format: ExportFormat): string {
    switch (format) {
        case 'text': return 'text/plain';
        case 'json': return 'application/json';
        case 'csv': return 'text/csv';
        case 'logcat': return 'text/plain';
    }
}

/**
 * Downloads logs as a file in the browser.
 */
export function downloadLogs(logs: LogEntry[], format: ExportFormat, filename?: string): void {
    const content = exportLogs(logs, format);
    const mimeType = getExportMimeType(format);
    const extension = getExportExtension(format);
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename || `logs-${Date.now()}.${extension}`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

export interface LogFilterOptions {
    levelFilter: LogLevel;
    tagFilter: string;
    searchFilter: string;
}

/**
 * Filters log entries based on level, tag, and search criteria.
 */
export function filterLogs(logs: LogEntry[], options: LogFilterOptions): LogEntry[] {
    const { levelFilter, tagFilter, searchFilter } = options;
    const filterIdx = LOG_LEVELS.indexOf(levelFilter);

    return logs.filter(log => {
        // Level filter: only show logs at or above the filter level
        const levelIdx = LOG_LEVELS.indexOf(log.level);
        if (levelIdx < filterIdx) return false;

        // Tag filter: case-insensitive substring match
        if (tagFilter && !log.tag?.toLowerCase().includes(tagFilter.toLowerCase())) {
            return false;
        }

        // Search filter: case-insensitive substring match on message
        if (searchFilter && !log.message.toLowerCase().includes(searchFilter.toLowerCase())) {
            return false;
        }

        return true;
    });
}

/**
 * Determines if scroll position is at the bottom of a container.
 */
export function isAtBottom(
    scrollHeight: number,
    scrollTop: number,
    clientHeight: number,
    threshold: number = 50
): boolean {
    return scrollHeight - scrollTop <= clientHeight + threshold;
}
