/**
 * Wire-level shapes of `GET /portal/tree` (see `documentation/api.md`): the navigator tree.
 */
export interface GroupEntry {
    name: string;
    projects: ProjectEntry[];
}
export interface ProjectEntry {
    projectId: string;
    services: ServiceEntry[];
}
export interface ServiceEntry {
    name: string;
    type: string;
    versions: VersionEntry[];
}
export interface VersionEntry {
    id: string;
    name: string;
    type: string;
    location: string | null;
    url: string | null;
    author: string;
    timestamp: number;
    storageFolder: string;
}