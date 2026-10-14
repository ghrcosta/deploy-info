import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { ProjectsConfigNetworkService } from './projects-config.network.service';
import { Project } from './projects-config.component';

const PROJECTS: Project[] = [
  { projectId: 'acme-web-prod', group: 'Platform', serviceAccount: 'collector@acme-web-prod.iam.gserviceaccount.com' }
];

// environment.url is empty in tests (same-origin, see environment.ts).
const PROJECTS_URL = '/project/all';

describe('ProjectsConfigNetworkService', () => {
  let service: ProjectsConfigNetworkService;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ]
    }).compileComponents();

    service = TestBed.inject(ProjectsConfigNetworkService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('delivers the project list from a 200 response', () => {
    const received: Project[][] = [];
    service.getProjects().subscribe(projects => received.push(projects));

    httpMock.expectOne(PROJECTS_URL).flush(PROJECTS);

    expect(received).toEqual([PROJECTS]);
  });

  it('delivers an empty list when the backend answers 204 No Content', () => {
    // The collector responds `204 No Content` (empty body) when no project is
    // configured; HttpClient surfaces that as a `null` body, which must never
    // leak to consumers or the page stays on its loading state forever.
    const received: Project[][] = [];
    service.getProjects().subscribe(projects => received.push(projects));

    const request = httpMock.expectOne(PROJECTS_URL);
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(received).toEqual([[]]);
  });
});
