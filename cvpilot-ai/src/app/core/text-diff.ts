export interface DiffPart {
  text: string;
  kind: 'same' | 'added' | 'removed';
}

const MAX_TOKENS = 1500;

/**
 * Word-level diff (longest common subsequence) for the original-vs-updated view. Whitespace is kept
 * as its own token so the rendered text reads naturally. Very long texts fall back to replace-all.
 */
export function diffWords(original: string, updated: string): DiffPart[] {
  const a = tokenize(original);
  const b = tokenize(updated);
  if (a.length === 0 && b.length === 0) return [];
  if (a.length > MAX_TOKENS || b.length > MAX_TOKENS) {
    return [
      ...(original ? [{ text: original, kind: 'removed' as const }] : []),
      ...(updated ? [{ text: updated, kind: 'added' as const }] : []),
    ];
  }

  // lcs[i][j] = LCS length of a[i..] and b[j..]
  const lcs: Uint16Array[] = Array.from({ length: a.length + 1 }, () => new Uint16Array(b.length + 1));
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }

  const parts: DiffPart[] = [];
  const push = (text: string, kind: DiffPart['kind']) => {
    const last = parts[parts.length - 1];
    if (last && last.kind === kind) last.text += text;
    else parts.push({ text, kind });
  };
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      push(a[i], 'same');
      i++;
      j++;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      push(a[i++], 'removed');
    } else {
      push(b[j++], 'added');
    }
  }
  while (i < a.length) push(a[i++], 'removed');
  while (j < b.length) push(b[j++], 'added');
  return parts;
}

function tokenize(text: string): string[] {
  return (text ?? '').match(/\s+|[^\s]+/g) ?? [];
}
