package application

import domain.Project

interface ProjectRepository {
    fun getAll(): List<Project>
    fun get(projectId: String): Project?
    fun save(project: Project)
    fun delete(projectId: String)
}