import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
    providedIn: 'root'
})
export class HomeService {
    // BehaviorSubject so subscribers always receive the current value immediately,
    // instead of only reacting to clicks emitted after they subscribed.
    private _showSettingsSubject = new BehaviorSubject<boolean>(false);
    showSettingsEventObservable = this._showSettingsSubject.asObservable();

    showSettingsClickedEvent(showSettings: boolean) {
        this._showSettingsSubject.next(showSettings);
    }
}