import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Project } from "./projects-config.component";
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class ProjectsConfigNetworkService {
    private http = inject(HttpClient)

    getProjects(): Observable<Project[]> {
        return this.http.get<Project[]>(`${environment.url}/settings/projects`)
    }
}
