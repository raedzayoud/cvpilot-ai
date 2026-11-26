import { diffWords } from './text-diff';

describe('diffWords', () => {
  it('marks inserted and removed words and keeps common text', () => {
    const parts = diffWords('Built APIs with Java', 'Built REST APIs with Java and Spring');
    const added = parts.filter((p) => p.kind === 'added').map((p) => p.text.trim());
    expect(added.join(' ')).toContain('REST');
    expect(added.join(' ')).toContain('Spring');
    expect(parts.filter((p) => p.kind === 'removed')).toHaveLength(0);
    expect(parts.map((p) => p.text).join('').replace(/\s+/g, ' ')).toContain('Built');
  });

  it('handles a brand-new or deleted text', () => {
    expect(diffWords('', 'New summary')).toEqual([{ text: 'New summary', kind: 'added' }]);
    expect(diffWords('Old', '')).toEqual([{ text: 'Old', kind: 'removed' }]);
  });
});
