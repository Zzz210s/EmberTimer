package com.embertimer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProfileEntity::class, DailyTotalEntity::class], version = 2, exportSchema = false)
abstract class EmberDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun dailyTotalDao(): DailyTotalDao

    companion object {
        /**
         * v1 -> v2(Task 6 / #10):profiles 增 mode 列(0=倒计时,1=正计时)。
         * 非破坏:旧库升级逐行保留、mode 补 0;新增列带 DEFAULT 与实体
         * @ColumnInfo(defaultValue = "0") 对齐(Room 迁移后逐列校验)。
         * 本次为唯一 schema 变更,后续任务不得再动表结构。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `mode` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): EmberDatabase =
            Room.databaseBuilder(context, EmberDatabase::class.java, "ember.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
