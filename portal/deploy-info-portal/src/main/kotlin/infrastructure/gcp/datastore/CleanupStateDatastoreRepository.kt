package infrastructure.gcp.datastore

import application.cleanup.CleanupStateRepository
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate
import domain.CleanupState
import org.springframework.stereotype.Repository
import java.time.Instant

/** Datastore-backed cleanup state (a single entity, no locking — parallel sweeps are accepted). */
@Repository
class CleanupStateDatastoreRepository(
    private val datastoreTemplate: DatastoreTemplate,
) : CleanupStateRepository {

    override fun get(): CleanupState? =
        datastoreTemplate.findById(CleanupStateEntity.SINGLETON_ID, CleanupStateEntity::class.java)?.toModel()

    override fun markCompleted(now: Instant) {
        datastoreTemplate.save(CleanupStateEntity(CleanupState(now)))
    }
}
