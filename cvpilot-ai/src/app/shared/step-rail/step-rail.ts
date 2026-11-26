import { Component, input } from '@angular/core';

export interface Step {
  label: string;
  state: 'done' | 'current' | 'upcoming';
}

@Component({
  selector: 'app-step-rail',
  templateUrl: './step-rail.html',
  styleUrl: './step-rail.css',
})
export class StepRail {
  steps = input.required<Step[]>();
}
