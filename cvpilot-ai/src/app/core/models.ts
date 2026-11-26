export type CvSource = 'docx' | 'pdf' | 'tex';

export interface PersonalInfo {
  fullName: string;
  email: string;
  phone: string;
  location: string;
  links: string[];
}

export interface Experience {
  id: string;
  role: string;
  company: string;
  location: string;
  start: string;
  end: string;
  bullets: string[];
  /** The "Technologies:" line under the role. */
  technologies: string[];
}

export interface EducationItem {
  id: string;
  degree: string;
  school: string;
  location: string;
  start: string;
  end: string;
  details: string;
}

export interface ProjectItem {
  id: string;
  name: string;
  description: string;
  technologies: string[];
  link: string;
}

export interface CertificationItem {
  id: string;
  name: string;
  issuer: string;
  year: string;
}

export interface LanguageItem {
  id: string;
  name: string;
  level: string;
}

export interface CvProfile {
  personalInfo: PersonalInfo;
  professionalTitle: string;
  summary: string;
  skills: string[];
  experiences: Experience[];
  education: EducationItem[];
  projects: ProjectItem[];
  certifications: CertificationItem[];
  languages: LanguageItem[];
  /** Awards, rankings, competitions: one line each. */
  achievements: string[];
}

export interface CvRecord {
  id: string;
  fileName: string;
  mimeType: string;
  sourceFormat: CvSource;
  sizeBytes: number;
  language: string;
  updatedAt: string;
  /** Set when the profile could not be extracted automatically and needs manual completion. */
  extractionNote: string | null;
  profile: CvProfile;
}

export type CvSummary = Omit<CvRecord, 'profile' | 'mimeType' | 'extractionNote'>;

export interface CvTextInput {
  fileName: string;
  source: string;
}

export interface JobRequirements {
  technical: string[];
  optional: string[];
  yearsExperience: number | null;
  education: string | null;
  responsibilities: string[];
  tools: string[];
  softSkills: string[];
}

export interface JobPosition {
  id: string;
  title: string;
  summary: string;
  requirements: JobRequirements;
}

export type JobSource = 'paste' | 'pdf' | 'docx';

export interface JobPosting {
  id: string;
  cvId: string;
  rawText: string;
  source: JobSource;
  fileName: string | null;
  createdAt: string;
  positions: JobPosition[];
}

export interface JobInput {
  cvId: string;
  text: string;
}

export interface Analysis {
  id: string;
  cvId: string;
  jobId: string;
  jobTitle: string;
  language: string;
  sourceFormat: CvSource;
  score: number;
  exported: boolean;
  createdAt: string;
}

export interface SourceRef {
  line: number;
  text: string;
}

export interface SectionProposal {
  key: string;
  label: string;
  original: string;
  proposed: string;
  sources: SourceRef[];
  violations: string[];
  approved: boolean;
  locked: boolean;
}

export interface Customization {
  analysisId: string;
  language: string;
  sections: SectionProposal[];
  decisions: CvDecision[];
}

export type DecisionKind = 'skill' | 'experience' | 'project';
export type Relevance = 'highlight' | 'keep' | 'deprioritize' | 'suggest_remove';
export type DecisionAction = 'highlight' | 'keep' | 'deprioritize' | 'remove';

export interface CvDecision {
  id: string;
  kind: DecisionKind;
  label: string;
  reason: string;
  suggestion: Relevance;
  action: DecisionAction;
}

export type ExportFormat = 'docx' | 'pdf' | 'tex';

export type ChatRole = 'bot' | 'user';

export interface ChatMessage {
  id: string;
  role: ChatRole;
  text: string;
  at: string;
}

export type MatchLevel = 'matched' | 'partial' | 'missing' | 'unverified';
export type Priority = 'high' | 'medium' | 'low';

export interface CriterionEvidence {
  requirement: string;
  level: MatchLevel;
}

export type ScoreCriterionKey = 'technical' | 'experience' | 'projects' | 'education' | 'other';

export interface ScoreCriterion {
  key: ScoreCriterionKey;
  label: string;
  weight: number;
  ratio: number;
  points: number;
  evidence: CriterionEvidence[];
}

export type WeaknessStatus = 'not_mentioned' | 'confirmed_missing';

export interface Weakness {
  id: string;
  title: string;
  description: string;
  whyItMatters: string;
  severity: Priority;
  improvement: string;
  status: WeaknessStatus;
}

export interface SkillRecommendation {
  id: string;
  skill: string;
  why: string;
  relation: string;
  priority: Priority;
  activity: string;
}

export interface AnalysisInsights {
  analysisId: string;
  score: number;
  criteria: ScoreCriterion[];
  strengths: string[];
  weaknesses: Weakness[];
  improvements: string[];
  skillRecommendations: SkillRecommendation[];
  disclaimer: string;
}

export interface ChatTurn {
  messages: ChatMessage[];
  customization: Customization;
  insights: AnalysisInsights;
}

export interface AnalysisSummary {
  id: string;
  jobTitle: string;
  cvFileName: string;
  createdAt: string;
  exported: boolean;
  sourceFormat: CvSource;
  score: number;
}

export type ActivityType =
  | 'cv_uploaded'
  | 'cv_updated'
  | 'cv_deleted'
  | 'job_created'
  | 'analysis_created'
  | 'analysis_exported'
  | 'analysis_deleted'
  | 'agent_session_started'
  | 'agent_changes_saved';

export interface ActivityItem {
  id: string;
  type: ActivityType;
  label: string;
  at: string;
}

export interface DashboardStats {
  cvCount: number;
  analysisCount: number;
  generatedCount: number;
  /** Null until the first customization exists. */
  averageScore: number | null;
  bestScore: number | null;
}

export interface Dashboard {
  stats: DashboardStats;
  recentJobs: JobPosting[];
  recentAnalyses: AnalysisSummary[];
  activity: ActivityItem[];
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface PageQuery {
  search?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export type AnalysisStatusFilter = 'all' | 'exported' | 'draft';

export interface SystemStatus {
  aiConfigured: boolean;
  aiProvider: string;
  aiModel: string;
  aiDailyRemaining: number;
}
