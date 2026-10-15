import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { VersionEntry } from './tree-model';

/**
 * What a version-node click carries to the file viewer: the project/service display names come from
 * the tree path, the version node (see `tree-model.ts`) carries the header fields and the
 * `storageFolder` that keys the content request.
 */
export interface DeploymentSelection {
    project: string;
    service: string;
    version: VersionEntry;
}

@Injectable({
    providedIn: 'root'
})
export class DeploymentViewerService {
    private _deploymentClickedSubject = new Subject<DeploymentSelection>();
    deploymentClickedEventObservable = this._deploymentClickedSubject.asObservable();

    newDeploymentClickedEvent(event: DeploymentSelection) {
        this._deploymentClickedSubject.next(event);
    }
}
