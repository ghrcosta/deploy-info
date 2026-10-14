import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

/** The main views the home shell can show in its content area. */
export type HomeView = 'deploys' | 'projects' | 'help';

@Injectable({
    providedIn: 'root'
})
export class HomeService {
    // BehaviorSubject so subscribers get the current value immediately (same pattern as AuthService).
    private _viewSubject = new BehaviorSubject<HomeView>('deploys');
    viewObservable = this._viewSubject.asObservable();

    openView(view: HomeView) {
        this._viewSubject.next(view);
    }
}
