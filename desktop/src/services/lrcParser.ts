import { LrcLine } from '../types';

/**
 * Parses standard LRC format strings into sorted LrcLine array.
 */
export function parseLrc(lrcText: string): LrcLine[] {
  if (!lrcText || typeof lrcText !== 'string') return [];

  const lines = lrcText.split(/\r?\n/);
  const timeRegex = /\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?\]/g;
  const parsedLines: LrcLine[] = [];

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    // Skip metadata headers like [ti:Title], [ar:Artist], etc.
    if (/^\[(ti|ar|al|by|offset|length):.*\]$/i.test(trimmed)) {
      continue;
    }

    const matches = [...trimmed.matchAll(timeRegex)];
    if (matches.length === 0) continue;

    const text = trimmed.replace(timeRegex, '').trim();

    for (const match of matches) {
      const minutes = parseInt(match[1], 10);
      const seconds = parseInt(match[2], 10);
      const millisecondsStr = match[3] || '0';
      const milliseconds = parseFloat(`0.${millisecondsStr}`);
      const totalSeconds = minutes * 60 + seconds + milliseconds;

      parsedLines.push({
        time: totalSeconds,
        text: text,
      });
    }
  }

  // Sort by time ascending
  parsedLines.sort((a, b) => a.time - b.time);

  // If there are duplicate timestamps with different text, treat the 2nd one as translation
  const deduped: LrcLine[] = [];
  for (let i = 0; i < parsedLines.length; i++) {
    const cur = parsedLines[i];
    const prev = deduped[deduped.length - 1];

    if (prev && Math.abs(prev.time - cur.time) < 0.1 && cur.text) {
      if (!prev.translation) {
        prev.translation = cur.text;
      }
    } else {
      deduped.push(cur);
    }
  }

  return deduped;
}

/**
 * Finds the index of the currently active lyric line given playback current time.
 */
export function findActiveLyricIndex(lyrics: LrcLine[], currentTime: number): number {
  if (!lyrics || lyrics.length === 0) return -1;
  
  let low = 0;
  let high = lyrics.length - 1;
  let best = -1;

  while (low <= high) {
    const mid = Math.floor((low + high) / 2);
    if (lyrics[mid].time <= currentTime) {
      best = mid;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }

  return best;
}
