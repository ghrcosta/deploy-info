import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { DeployContent } from './content-model';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class DeployContentNetworkService {
    private http = inject(HttpClient)

    getContent(folder: string): Observable<DeployContent> {
        return this.http.get<DeployContent>(`${environment.url}/portal/deploy/content`, {
            params: { folder: folder },
        })
    }
}