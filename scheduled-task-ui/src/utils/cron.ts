/**
 * Simple cron expression parser for 5-field and 6-field cron expressions.
 *
 * Supported formats:
 *   - 5-field:   minute hour dayOfMonth month dayOfWeek
 *   - 6-field:   second minute hour dayOfMonth month dayOfWeek (Quartz/Spring Scheduler)
 *
 * Returns the next `n` execution times after `from` (defaults to now).
 */

interface CronField {
  /** all values in this field */
  values: number[];
  /** total range for this field */
  min: number;
  max: number;
  /** field name for error messages */
  name: string;
}

const FIELD_NAMES = ["second", "minute", "hour", "dayOfMonth", "month", "dayOfWeek"];
const FIELD_MAXES = [59, 59, 23, 31, 12, 7];

/**
 * Parse a single cron field expression into a sorted set of allowed values.
 * Supports: star, star/n, n-m, n, n-m/s, comma-separated, L, question mark.
 */
function parseCronField(expr: string, min: number, max: number, fieldIndex: number): number[] {
  const values = new Set<number>();

  for (const part of expr.split(",")) {
    // L = last day / last weekday (approximate)
    if (part.trim() === "L") {
      if (fieldIndex === 3) {
        // last day of month: use 28-31 to cover all
        for (let d = 28; d <= 31; d++) values.add(d);
      } else if (fieldIndex === 5) {
        values.add(7); // last day of week = Sunday
      }
      continue;
    }

    // ? = no specific value (used in 6-field cron for dayOfMonth or dayOfWeek)
    if (part.trim() === "?") {
      continue;
    }

    let step: number | null = null;
    let range = part;

    if (part.includes("/")) {
      const [rangePart, stepPart] = part.split("/");
      range = rangePart;
      step = parseInt(stepPart, 10);
      if (isNaN(step) || step < 1) {
        return [];
      }
    }

    if (range === "*") {
      // Every nth starting from min
      for (let i = min; i <= max; i += step ?? 1) {
        values.add(i);
      }
    } else if (range.includes("-")) {
      const [startStr, endStr] = range.split("-");
      const start = parseInt(startStr, 10);
      const end = parseInt(endStr, 10);
      if (isNaN(start) || isNaN(end)) {
        return [];
      }
      const interval = step ?? 1;
      for (let i = start; i <= end; i += interval) {
        values.add(i);
      }
    } else {
      const val = parseInt(range, 10);
      if (isNaN(val)) {
        return [];
      }
      if (step !== null) {
        for (let i = val; i <= max; i += step) {
          values.add(i);
        }
      } else {
        values.add(val);
      }
    }
  }

  return Array.from(values).sort((a, b) => a - b);
}

/**
 * Parse a cron expression (5-field or 6-field).
 * Returns parsed fields or null if invalid.
 */
function parseCron(cron: string): CronField[] | null {
  const parts = cron.trim().split(/\s+/);
  if (parts.length < 5 || parts.length > 6) {
    return null;
  }

  // Determine if 6-field (second at index 0) or 5-field
  const isSixField = parts.length === 6;
  const fieldIndices = isSixField
    ? [0, 1, 2, 3, 4, 5]
    : [null, 0, 1, 2, 3, 4];

  const fields: CronField[] = [];
  for (let i = 0; i < 6; i++) {
    if (fieldIndices[i] === null) {
      // 5-field: second = * (every second)
      fields.push({ values: [], min: 0, max: 59, name: "second" });
      continue;
    }
    const idx = fieldIndices[i]!;
    const expr = parts[idx];
    const min = 0;
    const max = FIELD_MAXES[i];
    const values = parseCronField(expr, min, max, i);
    if (values.length === 0 && expr !== "?" && expr !== "L") {
      return null;
    }
    fields.push({
      values: values.length === 0 ? [1] : values,
      min,
      max,
      name: FIELD_NAMES[i],
    });
  }

  // For "?": dayOfMonth or dayOfWeek should be skipped in evaluation
  // We handle this in next execution logic
  return fields;
}

/**
 * Check if a date matches the cron expression.
 */
function matchesCron(date: Date, parsed: CronField[], originalCron: string): boolean {
  const parts = originalCron.trim().split(/\s+/);
  const isSixField = parts.length === 6;

  const sec = date.getSeconds();
  const min = date.getMinutes();
  const hour = date.getHours();
  const day = date.getDate();
  const month = date.getMonth() + 1; // 1-based
  const dow = date.getDay(); // 0=Sunday, 1=Monday ... 6=Saturday

  // Map: cron uses 0=Sunday(7), 1=Monday ... 6=Saturday
  // JS: 0=Sunday ... 6=Saturday
  const cronDow = dow === 0 ? 7 : dow;

  // Second (6-field only)
  if (isSixField) {
    if (parsed[0].values.length > 0 && !parsed[0].values.includes(sec)) return false;
  } else {
    // 5-field: only match at second=0
    if (sec !== 0) return false;
  }

  // Minute
  if (parsed[1].values.length > 0 && !parsed[1].values.includes(min)) return false;

  // Hour
  if (parsed[2].values.length > 0 && !parsed[2].values.includes(hour)) return false;

  // Day of month (handle ? and L)
  const domIdx = isSixField ? 3 : 2;
  const domExpr = parts[domIdx].trim();
  if (domExpr === "?" || domExpr === "L") {
    // skip
  } else if (parsed[3].values.length > 0 && !parsed[3].values.includes(day)) {
    return false;
  }

  // Month
  if (parsed[4].values.length > 0 && !parsed[4].values.includes(month)) return false;

  // Day of week (handle ? and L)
  const dowIdx = isSixField ? 5 : 4;
  const dowExpr = parts[dowIdx].trim();
  if (dowExpr === "?" || dowExpr === "L") {
    // skip
  } else if (parsed[5].values.length > 0 && !parsed[5].values.includes(cronDow)) {
    return false;
  }

  return true;
}

/**
 * Calculate the next n execution times from a cron expression.
 */
export function getNextExecutions(cron: string, count: number = 10): string[] | null {
  const parsed = parseCron(cron);
  if (!parsed) return null;

  const parts = cron.trim().split(/\s+/);
  const isSixField = parts.length === 6;

  // Determine seconds step: use minute-level iteration if seconds field includes 0,
  // otherwise fall back to second-level iteration for the first few minutes.
  const secsWithZero = parsed[0].values.includes(0);

  const results: string[] = [];
  let date = new Date();

  // Move to the start of the next minute
  date.setSeconds(0);
  date.setMilliseconds(0);
  date.setMinutes(date.getMinutes() + 1);

  const maxIterations = 876000; // ~10 years of minutes
  let iterations = 0;

  while (results.length < count && iterations < maxIterations) {
    if (isSixField && !secsWithZero) {
      // Iterate by second within this minute
      for (let s = 0; s < 60; s++) {
        date.setSeconds(s);
        if (matchesCron(date, parsed, cron)) {
          results.push(formatDateTime(date));
          if (results.length >= count) break;
        }
      }
    } else {
      // Iterate by minute (check at :00)
      date.setSeconds(0);
      if (matchesCron(date, parsed, cron)) {
        results.push(formatDateTime(date));
      }
    }

    date.setMinutes(date.getMinutes() + 1);
    iterations++;

    if (results.length >= count) break;
  }

  return results;
}

/**
 * Format a Date as yyyy-MM-dd HH:mm:ss.
 */
function formatDateTime(date: Date): string {
  const yyyy = date.getFullYear();
  const MM = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  const HH = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");
  return `${yyyy}-${MM}-${dd} ${HH}:${mm}:${ss}`;
}

/**
 * Validate a cron expression.
 */
export function validateCron(cron: string): { valid: boolean; message: string } {
  if (!cron || cron.trim() === "") {
    return { valid: false, message: "请输入 Cron 表达式" };
  }

  const parts = cron.trim().split(/\s+/);
  if (parts.length < 5 || parts.length > 6) {
    return { valid: false, message: "Cron 表达式应为 5 或 6 个字段" };
  }

  const parsed = parseCron(cron);
  if (!parsed) {
    return { valid: false, message: "Cron 表达式格式无效" };
  }

  return { valid: true, message: "格式正确" };
}