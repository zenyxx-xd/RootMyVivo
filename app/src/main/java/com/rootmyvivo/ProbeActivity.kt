package com.rootmyvivo

import android.app.Activity
import android.os.Bundle

/**
 * Детектор сломанной системы: активити в отдельном процессе (:probe).
 * Запуск из shell-домена (`am start -W`): если зигота зациклилась после эксплойта,
 * процесс не ответвится и команда зависнет/упадёт → система повреждена.
 * Активити ничего не рисует и убивает свой процесс — каждый запуск требует
 * свежего fork от зиготы, кэш в ОЗУ исключён.
 */
class ProbeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
