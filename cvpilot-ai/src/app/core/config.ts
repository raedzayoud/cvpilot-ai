import { environment } from '../../environments/environment';

/** Base URL of the Spring Boot REST API, set per build in src/environments. */
export const API_BASE = environment.apiBaseUrl.replace(/\/+$/, '');
