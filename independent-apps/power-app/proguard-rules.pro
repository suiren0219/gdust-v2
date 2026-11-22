# 核心序列化类型与 WorkManager 反射入口。持久化 codec 本身使用显式 JSON 字段。
-keep class com.menglimi.power.PowerBinding { *; }
-keep class com.menglimi.power.PowerRoom { *; }
-keep class com.menglimi.power.PowerDetail { *; }
-keep class com.menglimi.power.PowerSample { *; }
-keep class com.menglimi.power.PowerReading { *; }
-keep class com.menglimi.power.PowerMonitorConfig { *; }
-keep class com.menglimi.power.PowerMonitorState { *; }
-keep class com.menglimi.dormpower.power.PowerSnapshot { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(...); }
# Glance 小部件与 ActionCallback 由系统/库按类名实例化。
-keep class com.menglimi.dormpower.widget.** { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
