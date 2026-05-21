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
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance

                // Secure background seeding right after DB instantiation
                scope.launch(Dispatchers.IO) {
                    try {
                        seedDatabase(instance)
                    } catch (e: Throwable) {
                        android.util.Log.e("AppDatabase", "Seeding failed", e)
                    }
                }

                instance
            }
        }

        private suspend fun seedDatabase(db: AppDatabase) {
            try {
                // Seed Logs - using explicit unique IDs to remain completely idempotent
                db.assistantDao().insertLog(
                    AssistantLog(
                        id = 1,
                        sender = "nova",
                        message = "Hi! I am Nova, your offline-capable AI companion. Say \"Hey Nova\" or tap the activation crystal below to talk to me! I can open apps, track reminders, send offline messages, trigger automated routines, or keep you company."
                    )
                )

                // Seed Contacts
                db.contactDao().insertContact(
                    Contact(id = 1, name = "Mom", phoneNumber = "+1-555-0199", email = "mom@family.com", isFavorite = true)
                )
                db.contactDao().insertContact(
                    Contact(id = 2, name = "Rahim", phoneNumber = "+880-171-234567", email = "rahim@tech.com", isFavorite = true)
                )
                db.contactDao().insertContact(
                    Contact(id = 3, name = "John Doe", phoneNumber = "+1-555-0432", email = "john.doe@work.com", isFavorite = false)
                )

                // Seed Reminders
                db.reminderDao().insertReminder(
                    Reminder(id = 1, title = "Check daily agenda and schedules", timeLabel = "09:00 AM")
                )
                db.reminderDao().insertReminder(
                    Reminder(id = 2, title = "Complete reading offline wiki summary", timeLabel = "03:30 PM")
                )

                // Seed Routines
                db.routineDao().insertRoutine(
                    Routine(
                        id = 1,
                        name = "🏃 Morning Routine",
                        actionsSummary = "Turn on WiFi, open WhatsApp, and say a motivating quote.",
                        actionsList = "turn_on_wifi,open_whatsapp,say_quote"
                    )
                )
                db.routineDao().insertRoutine(
                    Routine(
                        id = 2,
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
