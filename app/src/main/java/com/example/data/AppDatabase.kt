package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [AssistantLog::class, Reminder::class, Routine::class, Contact::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assistantDao(): AssistantDao
    abstract fun reminderDao(): ReminderDao
    abstract fun routineDao(): RoutineDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nova_database"
                )
                    .addCallback(AppDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    try {
                        populateDatabase(database)
                    } catch (e: Exception) {
                        android.util.Log.e("AppDatabase", "Error seeding Room database", e)
                    }
                }
            }
        }

        suspend fun populateDatabase(db: AppDatabase) {
            try {
                // Seed Logs
                db.assistantDao().insertLog(
                    AssistantLog(
                        sender = "nova",
                        message = "Hi! I am Nova, your offline-capable AI companion. Say \"Hey Nova\" or tap the activation crystal below to talk to me! I can open apps, track reminders, send offline messages, trigger automated routines, or keep you company."
                    )
                )

                // Seed Contacts
                db.contactDao().insertContact(
                    Contact(name = "Mom", phoneNumber = "+1-555-0199", email = "mom@family.com", isFavorite = true)
                )
                db.contactDao().insertContact(
                    Contact(name = "Rahim", phoneNumber = "+880-171-234567", email = "rahim@tech.com", isFavorite = true)
                )
                db.contactDao().insertContact(
                    Contact(name = "John Doe", phoneNumber = "+1-555-0432", email = "john.doe@work.com", isFavorite = false)
                )

                // Seed Reminders
                db.reminderDao().insertReminder(
                    Reminder(title = "Check daily agenda and schedules", timeLabel = "09:00 AM")
                )
                db.reminderDao().insertReminder(
                    Reminder(title = "Complete reading offline wiki summary", timeLabel = "03:30 PM")
                )

                // Seed Routines
                db.routineDao().insertRoutine(
                    Routine(
                        name = "🏃 Morning Routine",
                        actionsSummary = "Turn on WiFi, open WhatsApp, and say a motivating quote.",
                        actionsList = "turn_on_wifi,open_whatsapp,say_quote"
                    )
                )
                db.routineDao().insertRoutine(
                    Routine(
                        name = "🔇 Work Focus",
                        actionsSummary = "Reduce volume, turn off Bluetooth, and open Calendar.",
                        actionsList = "turn_off_bluetooth,decrease_volume,open_calendar"
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("AppDatabase", "Error during table populating", e)
            }
        }
    }
}
