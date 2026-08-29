package com.rootmyvivo.core

/** AIDL-подобный интерфейс для ShellBridge UserService (Shizuku). */
interface IShellService {
    fun exec(command: String): String
    companion object {
        private const val DESCRIPTOR = "com.rootmyvivo.core.IShellService"
        private const val TRANSACTION_EXEC = android.os.IBinder.FIRST_CALL_TRANSACTION

        fun asInterface(binder: android.os.IBinder): IShellService {
            return Proxy(binder)
        }
    }

    class Proxy(private val binder: android.os.IBinder) : IShellService {
        override fun exec(command: String): String {
            val data = android.os.Parcel.obtain()
            val reply = android.os.Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(command)
                binder.transact(TRANSACTION_EXEC, data, reply, 0)
                reply.readException()
                reply.readString() ?: "EXIT=-1\nempty"
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    abstract class Stub : android.os.Binder(), IShellService {
        override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
            when (code) {
                TRANSACTION_EXEC -> {
                    data.enforceInterface(DESCRIPTOR)
                    val cmd = data.readString() ?: return false
                    val result = exec(cmd)
                    reply?.writeNoException()
                    reply?.writeString(result)
                    return true
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
        }
    }
}
