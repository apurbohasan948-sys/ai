package com.example.data

import kotlinx.coroutines.flow.Flow

class AssistantRepository(
    private val assistantDao: AssistantDao,
    private val reminderDao: ReminderDao,
    private val routineDao: RoutineDao,
    private val contactDao: ContactDao
) {
    val allLogs: Flow<List<AssistantLog>> = assistantDao.getAllLogs()
    val allReminders: Flow<List<Reminder>> = reminderDao.getAllReminders()
    val allRoutines: Flow<List<Routine>> = routineDao.getAllRoutines()
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()

    suspend fun insertLog(log: AssistantLog) = assistantDao.insertLog(log)
    suspend fun clearLogs() = assistantDao.clearLogs()

    suspend fun insertReminder(reminder: Reminder) = reminderDao.insertReminder(reminder)
    suspend fun updateReminder(reminder: Reminder) = reminderDao.updateReminder(reminder)
    suspend fun deleteReminderById(id: Int) = reminderDao.deleteReminderById(id)

    suspend fun insertRoutine(routine: Routine) = routineDao.insertRoutine(routine)
    suspend fun deleteRoutine(routine: Routine) = routineDao.deleteRoutine(routine)

    suspend fun insertContact(contact: Contact) = contactDao.insertContact(contact)
    suspend fun deleteContact(contact: Contact) = contactDao.deleteContact(contact)
}
