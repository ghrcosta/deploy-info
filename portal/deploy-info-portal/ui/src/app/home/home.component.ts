import { Component, Injectable } from '@angular/core';
import { DeployNavigatorComponent } from "./deploy-navigator/deploy-navigator.component";
import { FileViewerComponent } from './file-viewer/file-viewer.component';
import { TopBarComponent } from "./top-bar/top-bar.component";
import { Subject } from 'rxjs';

@Component({
    selector: 'app-home',
    templateUrl: './home.component.html',
    styleUrl: './home.component.scss',
    imports: [
        DeployNavigatorComponent,
        FileViewerComponent,
        TopBarComponent
    ],
})
export class HomeComponent { }

@Injectable({
    providedIn: 'root'
})
export class HomeService {
    private _deployClickedSubject = new Subject<string>();
    deployClickedEventObservable = this._deployClickedSubject.asObservable();

    newDeployClickedEvent(event: string) {
        this._deployClickedSubject.next(event);
    }
}