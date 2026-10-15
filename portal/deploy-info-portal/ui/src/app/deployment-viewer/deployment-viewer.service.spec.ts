import { DeploymentViewerService, DeploymentSelection } from './deployment-viewer.service';
import { VersionEntry } from './tree-model';

const SELECTION: DeploymentSelection = {
  project: 'proj-qa',
  service: 'web',
  version: { id: 'v1', name: 'v1', type: 'GAE', location: null, url: null, author: 'a@b.c', timestamp: 1, storageFolder: 'folder' } as VersionEntry,
};

describe('DeploymentViewerService', () => {
  it('delivers each deployment selection to the subscribers', () => {
    const service = new DeploymentViewerService();
    const received: DeploymentSelection[] = [];
    service.deploymentClickedEventObservable.subscribe(event => received.push(event));

    service.newDeploymentClickedEvent(SELECTION);
    service.newDeploymentClickedEvent({ ...SELECTION, version: { ...SELECTION.version, id: 'v2' } });

    expect(received.length).toBe(2);
    expect(received[0]).toEqual(SELECTION);
    expect(received[1].version.id).toBe('v2');
  });
});
