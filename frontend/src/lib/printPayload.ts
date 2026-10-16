/**
 * Helpers that turn raw printer wire payloads (as produced by lims-printing's formatters) into
 * something a human can inspect in the spooler monitor.
 */

export interface ZplLabel {
  /** Text fields (^FD...^FS) in print order, excluding the barcode data. */
  textFields: string[];
  /** Data of the first Code 128 barcode (^BC), if any. */
  barcode?: string;
}

export function parseZplLabel(payload: string): ZplLabel {
  const textFields: string[] = [];
  let barcode: string | undefined;
  let nextFieldIsBarcode = false;

  // Walk the commands in order: a ^BC barcode command applies to the next ^FD field
  const commands = payload.split('^').slice(1);
  for (const command of commands) {
    if (command.startsWith('BC')) {
      nextFieldIsBarcode = true;
    } else if (command.startsWith('FD')) {
      const data = command.slice(2).trim();
      if (nextFieldIsBarcode && barcode === undefined) {
        barcode = data;
      } else {
        textFields.push(data);
      }
      nextFieldIsBarcode = false;
    }
  }
  return { textFields, barcode };
}

/** Removes ESC/POS control sequences (init, alignment, print mode, paper cut) leaving the receipt text. */
export function stripEscPos(payload: string): string {
  return (
    payload
      // ESC @ (initialise)
      .replace(/\x1B@/g, '')
      // ESC a n / ESC ! n / ESC E n / ESC - n / ESC d n (one parameter byte)
      .replace(/\x1B[a!E\-d][\s\S]/g, '')
      // GS V A n / GS V B n (feed and cut), then GS V m
      .replace(/\x1DV[AB][\s\S]/g, '')
      .replace(/\x1DV[\x00\x01\x30\x31]/g, '')
      // Any remaining non-printing control characters except tab and newline
      .replace(/[\x00-\x08\x0B-\x1F\x7F]/g, '')
  );
}

/** Approximate decoded size of a base64 payload in KB. */
export function base64SizeKb(payload: string): number {
  return Math.max(1, Math.round((payload.length * 3) / 4 / 1024));
}
