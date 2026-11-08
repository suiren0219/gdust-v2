# WorkManager 通过反射实例化 Room 生成的数据库类，R8 默认会摇掉构造函数导致启动闪退
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(...); }
-keep class androidx.work.impl.** { *; }
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(...); }
-keep class androidx.startup.** { *; }

# Gson 反序列化的本地模型（字段名经反射读取，不能混淆）
-keep class com.menglimi.gdust.v2.data.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
