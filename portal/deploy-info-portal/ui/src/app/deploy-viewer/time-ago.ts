/**
 * Timestamp helpers shared by the deploy navigator and the file viewer,
 * mirroring the mock's `timeAgo`/`fmtTime` utilities (ui-mocks/shared/app.js)
 * with UTC rendering (the collector stores epoch milliseconds).
 */

/** Relative label for an epoch-millisecond timestamp: "5m ago", "3h ago", "2d ago". */
export function timeAgo(ms: number): string {
    const m = Math.max(1, Math.round((Date.now() - ms) / 60000));
    if (m < 60) return `${m}m ago`;
    if (m < 1440) return `${Math.round(m / 60)}h ago`;
    return `${Math.round(m / 1440)}d ago`;
}

/** Absolute UTC timestamp in the mock's format: "2025-01-01 13:45 UTC". */
export function fmtTime(ms: number): string {
    const d = new Date(ms);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ` +
        `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())} UTC`;
}
