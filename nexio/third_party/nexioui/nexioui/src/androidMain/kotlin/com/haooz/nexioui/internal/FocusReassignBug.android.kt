package com.haooz.nexioui.internal

import android.os.Build

internal actual val hasFocusReassignBug: Boolean
    get() = Build.VERSION.SDK_INT in 26..27
