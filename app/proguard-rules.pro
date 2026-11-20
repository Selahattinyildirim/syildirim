# Room ve WorkManager kendi kurallarını içerir.
-keep class com.sarjdefteri.app.data.db.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
