import { CvProfile, Experience, JobRequirements, ProjectItem } from './models';

export type AgentOperation =
  | 'update_headline'
  | 'update_summary'
  | 'update_skills'
  | 'add_experience'
  | 'update_experience'
  | 'remove_experience'
  | 'add_project'
  | 'update_project'
  | 'remove_project'
  | 'reorder_sections'
  | 'replace_technology'
  | 'remove_section';

export type ProposalStatus = 'pending' | 'accepted' | 'rejected' | 'superseded' | 'stale';

export type AgentIntent =
  | 'tailor_to_job'
  | 'optimize_ats'
  | 'rewrite'
  | 'add_content'
  | 'remove_content'
  | 'reorder'
  | 'question'
  | 'other';

export interface Evidence {
  term: string;
  source: 'cv' | 'user_confirmed';
}

export interface ProposedChange {
  operation: AgentOperation;
  section: string;
  targetId?: string;
  label: string;
  originalText: string;
  updatedText: string;
  reason: string;
  jobRequirements: string[];
  keywordsAdded: string[];
  keywordsRemoved: string[];
  evidence: Evidence[];
  text?: string;
  list?: string[];
  experience?: Experience;
  project?: ProjectItem;
}

export interface AgentProposal {
  id: string;
  status: ProposalStatus;
  change: ProposedChange;
  createdAt: string;
  decidedAt: string | null;
}

export interface TurnMeta {
  intent: AgentIntent;
  toolsUsed: string[];
  blockedClaims: string[];
  proposalIds: string[];
}

export interface AgentMessage {
  id: string;
  role: 'user' | 'agent';
  text: string;
  at: string;
  meta: TurnMeta | null;
}

export interface AgentSessionSummary {
  id: string;
  cvId: string;
  cvFileName: string;
  title: string;
  updatedAt: string;
  savedAt: string | null;
}

export interface JobTarget {
  title: string;
  requirements: JobRequirements;
  matchedSkills: string[];
  missingSkills: string[];
  score: number;
}

export interface ConfirmedFact {
  quote: string;
  skills: string[];
  at: string;
}

export interface AgentWorkspace {
  session: AgentSessionSummary;
  messages: AgentMessage[];
  proposals: AgentProposal[];
  working: CvProfile;
  original: CvProfile;
  sectionOrder: string[];
  jobTarget: JobTarget | null;
  confirmedFacts: ConfirmedFact[];
  stats: { pending: number; accepted: number; rejected: number };
  unsavedChanges: boolean;
  aiAvailable: boolean;
}

/** Optional manual edit sent with "accept": text, list (skills or bullets) or description. */
export interface AcceptEdit {
  text?: string;
  list?: string[];
  description?: string;
}
