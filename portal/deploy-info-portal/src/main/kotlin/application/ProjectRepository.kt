package application

import domain.Project

interface ProjectRepository {
    fun getAll(): List<Project>
    fun get(projectName: String): Project?
    fun save(project: Project)
    fun delete(projectName: String)
}