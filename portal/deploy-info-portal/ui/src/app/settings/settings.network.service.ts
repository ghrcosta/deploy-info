import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Project } from "./settings.component";
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class SettingsNetworkService {
    private http = inject(HttpClient)

    getProjects(): Observable<ProjectDTO[]> {
        return this.http.get<ProjectDTO[]>(`${environment.url}/settings/projects`)
    }

    addProject(newProject: Project): Observable<AddProjectResultDTO> {
        const newProjectDTO: ProjectDTO = {
            name: newProject.name,
            category: newProject.category,
            serviceAccount: newProject.serviceAccount
        }
        return this.http.post<AddProjectResultDTO>(`${environment.url}/settings/project`, newProjectDTO)
    }
}

export interface ProjectDTO {
    name: string;
    category?: string;
    serviceAccount: string;
}

export interface AddProjectResultDTO {
    issues?: AddProjectIssuesDTO;
    projects?: ProjectDTO[];
}
export interface AddProjectIssuesDTO {
    issueNameConflict: boolean;
    issueServiceAccountError: boolean;
}

export interface EditProjectResultDTO {
    issues?: EditProjectIssuesDTO;
    projects?: ProjectDTO[];
}
export interface EditProjectIssuesDTO {
    issueProjectNotFound: boolean;
    issueServiceAccountError: boolean;
}