package com.flywith24.doodle

import android.content.res.Resources
import android.util.TypedValue

/**
 * @author Flywith24
 * @date   2020/8/3
 * time   14:38
 * description
 */
val Float.dp
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        Resources.getSystem().displayMetrics
    )
