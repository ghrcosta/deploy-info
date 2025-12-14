import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Project } from "../settings.component";
import { AddProjectResult, EditProjectResult } from './project-data-dialog.component';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class ProjectDataDialogNetworkService {
    private http = inject(HttpClient)

    addProject(project: Project): Observable<AddProjectResult> {
        return this.http.post<AddProjectResult>(`${environment.url}/settings/project`, project)
    }

    editProject(project: Project): Observable<EditProjectResult> {
        return this.http.put<EditProjectResult>(`${environment.url}/settings/project`, project)
    }

    deleteProject(projectName: string): Observable<Project[]> {
        return this.http.delete<Project[]>(`${environment.url}/settings/project/${projectName}`)
    }
}

