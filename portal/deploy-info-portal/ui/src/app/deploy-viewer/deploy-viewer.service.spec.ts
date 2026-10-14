import { DeployViewerService, DeploySelection } from './deploy-viewer.service';
import { VersionEntry } from './tree-model';

const SELECTION: DeploySelection = {
  project: 'proj-qa',
  service: 'web',
  version: { id: 'v1', name: 'v1', type: 'GAE', location: null, url: null, author: 'a@b.c', timestamp: 1, storageFolder: 'folder' } as VersionEntry,
};

describe('DeployViewerService', () => {
  it('delivers each deployment selection to the subscribers', () => {
    const service = new DeployViewerService();
    const received: DeploySelection[] = [];
    service.deployClickedEventObservable.subscribe(event => received.push(event));

    service.newDeployClickedEvent(SELECTION);
    service.newDeployClickedEvent({ ...SELECTION, version: { ...SELECTION.version, id: 'v2' } });

    expect(received.length).toBe(2);
    expect(received[0]).toEqual(SELECTION);
    expect(received[1].version.id).toBe('v2');
  });
});
