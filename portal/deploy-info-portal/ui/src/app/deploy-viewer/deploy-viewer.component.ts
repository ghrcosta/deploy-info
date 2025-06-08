import { Component, Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { DeployNavigatorComponent } from "./deploy-navigator/deploy-navigator.component";
import { FileViewerComponent } from './file-viewer/file-viewer.component';

@Component({
  selector: 'deploy-viewer',
  imports: [
    DeployNavigatorComponent,
    FileViewerComponent,
  ],
  templateUrl: './deploy-viewer.component.html',
  styleUrl: './deploy-viewer.component.scss'
})
export class DeployViewerComponent { }

@Injectable({
    providedIn: 'root'
})
export class DeployViewerService {
    private _deployClickedSubject = new Subject<string>();
    deployClickedEventObservable = this._deployClickedSubject.asObservable();

    newDeployClickedEvent(event: string) {
        this._deployClickedSubject.next(event);
    }
}