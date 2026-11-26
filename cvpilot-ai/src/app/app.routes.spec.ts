import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { routes } from './app.routes';

describe('routes', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('resolves /analyses/:id/review without a redirect loop', async () => {
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/analyses/a1/review');

    expect(router.url).toBe('/analyses/a1/review');
  });

  it('redirects /analyses/:id to its review page', async () => {
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/analyses/a1');

    expect(router.url).toBe('/analyses/a1/review');
  });
});
