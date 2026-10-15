import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { DeploymentContent } from './content-model';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class DeploymentContentNetworkService {
    private http = inject(HttpClient)

    getContent(folder: string): Observable<DeploymentContent> {
        return this.http.get<DeploymentContent>(`${environment.url}/portal/deploy/content`, {
            params: { folder: folder },
        })
    }
}