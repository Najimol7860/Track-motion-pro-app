package com.example.tracker.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracking_projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val clipId: String,
    val customVideoUri: String? = null,
    val trackingMode: String,
    val totalFrames: Int,
    val currentFrame: Int = 0,
    val keyframesData: String, // Serialized keyframes
    val attachedLayersData: String, // Serialized layers
    val settingsData: String, // Serialized settings
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM tracking_projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM tracking_projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(project: ProjectEntity): Long

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM tracking_projects WHERE id = :projectId")
    suspend fun deleteById(projectId: Long)
}

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "motion_tracker.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
