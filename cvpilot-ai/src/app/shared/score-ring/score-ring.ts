import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Circular 0-100 match score. Color bands: <50 low, 50-74 fair, 75+ strong. */
@Component({
  selector: 'app-score-ring',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="ring"
      [class]="'ring ' + band()"
      [style.width.px]="size()"
      [style.height.px]="size()"
      role="img"
      [attr.aria-label]="'Match score ' + score() + ' out of 100'"
    >
      <svg viewBox="0 0 36 36" aria-hidden="true">
        <circle class="track" cx="18" cy="18" r="15.9" />
        <circle class="value" cx="18" cy="18" r="15.9" [attr.stroke-dasharray]="dash()" />
      </svg>
      <span class="label" [style.font-size.px]="size() * 0.3">{{ score() }}</span>
    </div>
  `,
  styles: `
    .ring {
      position: relative;
      display: grid;
      place-items: center;
      flex: none;
    }
    svg {
      position: absolute;
      inset: 0;
      transform: rotate(-90deg);
    }
    circle {
      fill: none;
      stroke-width: 3.2;
    }
    .track {
      stroke: var(--line);
    }
    .value {
      stroke: var(--primary);
      stroke-linecap: round;
      transition: stroke-dasharray 0.6s ease;
    }
    .low .value {
      stroke: var(--error);
    }
    .fair .value {
      stroke: var(--warning);
    }
    .strong .value {
      stroke: var(--success);
    }
    .label {
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      letter-spacing: -0.02em;
    }
  `,
})
export class ScoreRing {
  score = input.required<number>();
  size = input(56);
  protected readonly band = computed(() =>
    this.score() >= 75 ? 'strong' : this.score() >= 50 ? 'fair' : 'low',
  );
  protected readonly dash = computed(() => `${Math.max(0, Math.min(100, this.score()))} 100`);
}
