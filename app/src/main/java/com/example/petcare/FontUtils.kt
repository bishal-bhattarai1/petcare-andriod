package com.example.petcare

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat

/** The app font (Figtree) in the given style. Use instead of Typeface.DEFAULT*, which falls back to Roboto. */
fun Context.figtree(style: Int = Typeface.NORMAL): Typeface =
    Typeface.create(ResourcesCompat.getFont(this, R.font.figtree), style)
