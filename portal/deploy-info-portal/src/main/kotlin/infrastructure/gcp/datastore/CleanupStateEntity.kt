package infrastructure.gcp.datastore

import com.google.cloud.spring.data.datastore.core.mapping.Entity
import domain.CleanupState
import org.springframework.data.annotation.Id
import java.time.Instant

/** Datastore entity for the cleanup state; there is exactly one, with the singleton id. */
@Entity(name = "deploy-info/cleanup-state")
class CleanupStateEntity(
    @Id
    val id: String,

    val lastCleanupCompleted: Instant? = null,
) {
    constructor(state: CleanupState) : this(
        id = SINGLETON_ID,
        lastCleanupCompleted = state.lastCleanupCompleted,
    )

    fun toModel(): CleanupState = CleanupState(
        lastCleanupCompleted = lastCleanupCompleted,
    )

    companion object {
        /** The id of the single cleanup state entity. */
        const val SINGLETON_ID = "singleton"
    }
}
