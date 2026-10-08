import { Component, Injectable, ChangeDetectionStrategy } from '@angular/core';
import { Subject } from 'rxjs';
import { VersionEntry } from './tree-model';
import { DeployNavigatorComponent } from "./deploy-navigator/deploy-navigator.component";
import { FileViewerComponent } from './file-viewer/file-viewer.component';

@Component({
  selector: 'deploy-viewer',
  imports: [
    DeployNavigatorComponent,
    FileViewerComponent,
  ],
  templateUrl: './deploy-viewer.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './deploy-viewer.component.scss'
})
export class DeployViewerComponent { }

/**
 * What a version-node click carries to the file viewer: the project/service display names come from
 * the tree path, the version node (see `tree-model.ts`) carries the header fields and the
 * `storageFolder` that keys the content request.
 */
export interface DeploySelection {
    project: string;
    service: string;
    version: VersionEntry;
}

@Injectable({
    providedIn: 'root'
})
export class DeployViewerService {
    private _deployClickedSubject = new Subject<DeploySelection>();
    deployClickedEventObservable = this._deployClickedSubject.asObservable();

    newDeployClickedEvent(event: DeploySelection) {
        this._deployClickedSubject.next(event);
    }
}