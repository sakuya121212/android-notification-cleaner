package io.github.sakuya121212.notificationcleaner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NotificationEntity::class, AppFilterEntity::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun appFilterDao(): AppFilterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notification_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    // Versions 1 and 2 predate the schema retained in this source tree.
                    // Recreate only those unknown schemas rather than crashing on upgrade.
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Drops legacy captured notification content and adds duplicate protection. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM notifications")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notifications_key ON notifications(`key`)")
            }
        }
        /** Makes notification keys non-null so the unique index covers every row. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE notifications_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "packageName TEXT NOT NULL, appName TEXT, title TEXT, text TEXT, " +
                        "postTime INTEGER NOT NULL, `key` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO notifications_new (id, packageName, appName, title, text, postTime, `key`) " +
                        "SELECT id, packageName, appName, title, text, postTime, " +
                        "COALESCE(`key`, 'legacy-' || id) FROM notifications"
                )
                db.execSQL("DROP TABLE notifications")
                db.execSQL("ALTER TABLE notifications_new RENAME TO notifications")
                db.execSQL("CREATE UNIQUE INDEX index_notifications_key ON notifications(`key`)")
            }
        }
    }
}
