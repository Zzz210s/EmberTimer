package com.embertimer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProfileEntity::class, DailyTotalEntity::class], version = 1, exportSchema = false)
abstract class EmberDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun dailyTotalDao(): DailyTotalDao

    companion object {
        fun build(context: Context): EmberDatabase =
            Room.databaseBuilder(context, EmberDatabase::class.java, "ember.db").build()
    }
}
