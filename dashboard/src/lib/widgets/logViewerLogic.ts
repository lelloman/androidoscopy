import type { LogEntry, LogLevel } from '../types/protocol';

export const LOG_LEVELS: readonly LogLevel[] = ['VERBOSE', 'DEBUG', 'INFO', 'WARN', 'ERROR'];

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
