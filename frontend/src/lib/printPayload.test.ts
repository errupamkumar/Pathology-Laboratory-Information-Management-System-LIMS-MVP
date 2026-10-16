import { describe, expect, it } from 'vitest';
import { printAgentUrl } from '../hooks/usePrintAgentSocket';
import { parseZplLabel, stripEscPos } from './printPayload';

describe('parseZplLabel', () => {
  // Same layout as lims-printing ZplBarcodeFormatter
  const zpl = [
    '^XA',
    '^PW420',
    '^FO10,10^A0N,22,22^FDAPEX PATH LAB - EDTA^FS',
    '^FO10,38^A0N,20,20^FDUHID-2026-0001  Rahul Verma^FS',
    '^BY2,2.5,65',
    '^FO15,65^BCN,65,Y,N,N',
    '^FDACC-2026-5001-S01^FS',
    '^FO10,165^A0N,20,20^FDACC: ACC-2026-5001 (Whole Blood)^FS',
    '^XZ',
  ].join('\n');

  it('separates the barcode data from the text fields', () => {
    const label = parseZplLabel(zpl);
    expect(label.barcode).toBe('ACC-2026-5001-S01');
    expect(label.textFields).toEqual(['APEX PATH LAB - EDTA', 'UHID-2026-0001  Rahul Verma', 'ACC: ACC-2026-5001 (Whole Blood)']);
  });
});

describe('stripEscPos', () => {
  it('removes initialise, alignment, print-mode and cut commands', () => {
    const payload = '\x1B@\x1Ba\x01\x1B!\x38APEX LAB\n\x1B!\x00Total: 450.00\n\x1DVA\x03';
    expect(stripEscPos(payload)).toBe('APEX LAB\nTotal: 450.00\n');
  });
});

describe('printAgentUrl', () => {
  it('appends the encoded access token', () => {
    expect(printAgentUrl('a b+c', 'ws://host/ws/print-agent')).toBe('ws://host/ws/print-agent?access_token=a%20b%2Bc');
    expect(printAgentUrl('t', 'ws://host/ws?x=1')).toBe('ws://host/ws?x=1&access_token=t');
  });
});
