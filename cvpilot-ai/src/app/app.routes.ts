import { CanDeactivateFn, Routes } from '@angular/router';

/** Pages with unsaved edits implement canLeave(); the guard asks before navigating away. */
export interface LeaveGuarded {
  canLeave(): boolean | Promise<boolean>;
}

const confirmLeave: CanDeactivateFn<LeaveGuarded> = (component) => component?.canLeave?.() ?? true;

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Dashboard · CVPilot AI',
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardPage),
  },
  {
    path: 'cvs',
    title: 'My CVs · CVPilot AI',
    loadComponent: () => import('./features/cvs/cv-list/cv-list').then((m) => m.CvList),
  },
  {
    path: 'cvs/:id',
    title: 'Edit CV · CVPilot AI',
    canDeactivate: [confirmLeave],
    loadComponent: () => import('./features/cvs/cv-editor/cv-editor').then((m) => m.CvEditor),
  },
  {
    path: 'jobs',
    title: 'New customization · CVPilot AI',
    loadComponent: () => import('./features/jobs/job-input/job-input').then((m) => m.JobInput),
  },
  {
    path: 'agent',
    title: 'AI CV Agent · CVPilot AI',
    loadComponent: () => import('./features/agent/agent-home/agent-home').then((m) => m.AgentHome),
  },
  {
    path: 'agent/:id',
    title: 'AI CV Agent · CVPilot AI',
    loadComponent: () =>
      import('./features/agent/agent-workspace/agent-workspace').then((m) => m.AgentWorkspacePage),
  },
  {
    path: 'analyses',
    title: 'History · CVPilot AI',
    loadComponent: () => import('./features/history/history').then((m) => m.History),
  },
  {
    path: 'analyses/:id',
    pathMatch: 'full',
    redirectTo: (route) => `/analyses/${route.params['id']}/review`,
  },
  {
    path: 'analyses/:id/review',
    title: 'Customize · CVPilot AI',
    loadComponent: () =>
      import('./features/analysis/customization-chat/customization-chat').then(
        (m) => m.CustomizationChat,
      ),
  },
  { path: '**', redirectTo: '' },
];
