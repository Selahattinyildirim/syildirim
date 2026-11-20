# Room ve WorkManager kendi kurallarını içerir.
-keep class com.syildirim.teslacharge.data.db.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
