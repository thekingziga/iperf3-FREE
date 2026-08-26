# Keep the generated serializers for the config/history models.
-keepclassmembers class dev.ziga.iperf3.** {
    *** Companion;
}
-keepclasseswithmembers class dev.ziga.iperf3.** {
    kotlinx.serialization.KSerializer serializer(...);
}
