package application

import domain.Project

interface ProjectRepository {
    fun getAll(): List<Project>
    fun get(projectId: Long): Project?
    fun save(project: Project): Unit
}