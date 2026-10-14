import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Project } from "./projects-config.component";
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({
    providedIn: 'root'
})
export class ProjectsConfigNetworkService {
    private http = inject(HttpClient)

    getProjects(): Observable<Project[]> {
        return this.http.get<Project[]>(`${environment.url}/project/all`)
            // The backend answers `204 No Content` (empty body) when no project is
            // configured, which HttpClient surfaces as a `null` body instead of an
            // error. Normalize it to an empty list so consumers never see `null`.
            .pipe(map(projects => projects ?? []));
    }
}
