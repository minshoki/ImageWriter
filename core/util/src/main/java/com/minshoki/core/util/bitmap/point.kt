package com.minshoki.core.util.bitmap

import android.graphics.Point
import android.graphics.Rect


infix fun Int.point(y: Int) = Point(this, y)

fun Rect.rotate(degress: Int, originalPoint: Point): Rect {
    val result = Rect()

    when (degress) {
        270 -> {
            result.left = top
            result.top = originalPoint.x - right
            result.right = bottom
            result.bottom = originalPoint.x - left
            return result
        }

        180 -> {
            result.left = originalPoint.x - right
            result.top = originalPoint.y - bottom
            result.right = originalPoint.x - left
            result.bottom = originalPoint.y - top
            return result
        }

        90 -> {
            result.left = originalPoint.y - bottom
            result.top = left
            result.right = originalPoint.y - top
            result.bottom = right
            return result
        }

        else -> return this
    }
}
fun Point.rotate(degress: Int, originalPoint: Point): Point {
    when(degress) {
        90 -> {
            val newX = originalPoint.y - y
            val newY = x
            return newX point newY
        }
        180 -> {
            val newX = originalPoint.x - x
            val newY = originalPoint.y - y
            return newX point newY
        }
        270 -> {
            val newX = y
            val newY = originalPoint.x - x
            return newX point newY
        }
        else -> return this
    }
}