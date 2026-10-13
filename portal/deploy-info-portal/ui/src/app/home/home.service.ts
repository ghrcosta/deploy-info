import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

/** The main views the home shell can show in its content area. */
export type HomeView = 'deploys' | 'projects' | 'help';

@Injectable({
    providedIn: 'root'
})
export class HomeService {
    // BehaviorSubject so subscribers always receive the current value immediately,
    // instead of only reacting to clicks emitted after they subscribed.
    private _viewSubject = new BehaviorSubject<HomeView>('deploys');
    viewObservable = this._viewSubject.asObservable();

    openView(view: HomeView) {
        this._viewSubject.next(view);
    }
}
