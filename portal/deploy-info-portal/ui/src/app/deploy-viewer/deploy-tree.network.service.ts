import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { GroupEntry } from './tree-model';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class DeployTreeNetworkService {
    private http = inject(HttpClient)

    getTree(): Observable<GroupEntry[]> {
        return this.http.get<GroupEntry[]>(`${environment.url}/portal/tree`)
    }
}