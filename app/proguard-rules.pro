# R8-правила релизной сборки.
# Весь код приложения вызывается статически, кроме двух мест: Shizuku
# создаёт ShellServiceImpl рефлексией по имени класса, и маршруты
# навигации (@Serializable) кладутся в SavedState через генерируемые
# сериализаторы. Остальное R8 вправе вырезать — так и задумано.

# Читаемые стектрейсы: пользователи копируют логи, по ним ловим краши
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Shizuku UserService: экземпляр создаётся по имени класса в обход статических ссылок
-keep class com.rootmyvivo.shell.ShellServiceImpl { public <init>(...); }
-keep class com.rootmyvivo.shell.IShellService { *; }
-keep class com.rootmyvivo.shell.IShellService$* { *; }

# kotlinx.serialization: маршруты навигации (@Serializable RmvRoute) —
# сгенерированные сериализаторы обязаны выжить
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
