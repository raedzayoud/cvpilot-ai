import { HttpErrorResponse } from '@angular/common/http';

/** Turns any HTTP or runtime error into a sentence that can be shown to the user. */
export function messageFrom(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Cannot reach the CVPilot server. Check that the backend is running and try again.';
    }
    const body = error.error as { message?: unknown } | null;
    if (body && typeof body === 'object' && typeof body.message === 'string' && body.message) {
      return body.message;
    }
    if (error.status === 429) return 'Too many requests. Wait a moment and try again.';
    if (error.status === 413) return 'That file or request is too large.';
    if (error.status >= 500) return 'Something went wrong on the server. Try again.';
    if (error.status === 404) return 'That item no longer exists.';
  }
  if (error instanceof Error && error.message) return error.message;
  return 'Something went wrong. Try again.';
}

/** Blob responses (file downloads) carry JSON errors as a Blob; read it so the message is not lost. */
export async function messageFromBlobError(error: unknown): Promise<string> {
  if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
    try {
      const body = JSON.parse(await error.error.text()) as { message?: string };
      if (body.message) return body.message;
    } catch {
      // fall through to the generic message
    }
  }
  return messageFrom(error);
}
