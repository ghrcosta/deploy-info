import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeployNavigatorComponent } from './deploy-navigator.component';
import { DeployTreeNetworkService } from '../deploy-tree.network.service';
import { GroupEntry } from '../tree-model';
import { of } from 'rxjs';

describe('DeployNavigatorComponent', () => {
  let component: DeployNavigatorComponent;
  let fixture: ComponentFixture<DeployNavigatorComponent>;
  let networkService: jasmine.SpyObj<DeployTreeNetworkService>;

  const TEST_TREE: GroupEntry[] = [
    {
      name: 'PROD',
      projects: [
        {
          name: 'Project C',
          services: [
            {
              name: 'web',
              type: 'GAE',
              versions: [
                { id: 'proj-qa_GAE_-_web_v42', name: 'v42', type: 'GAE', location: null, url: null, author: 'a@b.c', timestamp: 123, storageFolder: 'folder' },
                { id: 'proj-qa_GAE_-_web_v7', name: 'v7', type: 'GAE', location: null, url: null, author: 'a@b.c', timestamp: 456, storageFolder: 'folder2' },
              ]
            },
          ]
        },
      ]
    },
    {
      name: '',
      projects: [
        {
          name: 'Project D',
          services: [
            {
              name: 'worker',
              type: 'RUN',
              versions: [
                { id: 'proj-qa_RUN_eu_worker_v1', name: 'v1', type: 'RUN', location: 'eu', url: null, author: 'a@b.c', timestamp: 789, storageFolder: 'folder3' },
              ]
            },
          ]
        },
      ]
    },
  ];

  beforeEach(async () => {
    networkService = jasmine.createSpyObj('DeployTreeNetworkService', ['getTree']);
    networkService.getTree.and.returnValue(of(TEST_TREE));

    await TestBed.configureTestingModule({
      imports: [DeployNavigatorComponent],
      providers: [{ provide: DeployTreeNetworkService, useValue: networkService }],
    })
    .compileComponents();

    fixture = TestBed.createComponent(DeployNavigatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should fetch the tree and convert it to nodes', () => {
    expect(networkService.getTree).toHaveBeenCalled();
    expect(component.groupList.length).toEqual(2);
    expect(component.groupList[0].name).toEqual('PROD');
    expect(component.groupList[0].projects[0].services[0].children!.length).toEqual(2);
    expect(component.groupList[1].name).toEqual('');
  });
});
