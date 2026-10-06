# PdfBox-Android references optional JPEG2000 and other decoders it does not ship.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
# Ktor references JVM-only logging.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
# JavaMail finds its IMAP/SMTP providers and MIME content handlers by reflection (META-INF/javamail.* and mailcap).
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.activation.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn javax.naming.**
-dontwarn java.beans.**
# JExcelApi (.xls statements): log4j is excluded, and its logger class is chosen by name at runtime.
-dontwarn org.apache.log4j.**
-dontwarn java.awt.**
-dontwarn jxl.**
-keep class jxl.common.log.** { *; }
