package com.cellular.rpc.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        OutboxEntity::class,
        WidgetCacheEntity::class,
        PacketLogEntity::class,
        ChatMessageEntity::class,
        DynamicFeatureEntity::class,
        ConversationThreadEntity::class,
        CustomActionEntity::class,
        AppBlueprintEntity::class,
        MutationLogEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun outboxDao(): OutboxDao
    abstract fun widgetCacheDao(): WidgetCacheDao
    abstract fun packetLogDao(): PacketLogDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun dynamicFeatureDao(): DynamicFeatureDao
    abstract fun conversationThreadDao(): ConversationThreadDao
    abstract fun customActionDao(): CustomActionDao
    abstract fun appBlueprintDao(): AppBlueprintDao
    abstract fun mutationLogDao(): MutationLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cellular_rpc.db"
                ).fallbackToDestructiveMigration().build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
