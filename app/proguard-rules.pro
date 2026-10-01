# R8-правила релизной сборки.
# Весь код приложения вызывается статически, кроме маршрутов
# навигации (@Serializable): они кладутся в SavedState через генерируемые
# сериализаторы. Остальное R8 вправе вырезать — так и задумано.

# Читаемые стектрейсы: пользователи копируют логи, по ним ловим краши
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

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
