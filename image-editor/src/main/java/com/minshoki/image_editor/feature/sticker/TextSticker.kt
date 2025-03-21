package com.minshoki.image_editor.feature.sticker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.SparseArray
import androidx.annotation.Dimension
import androidx.annotation.Dimension.Companion.SP
import androidx.annotation.IntRange
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import com.minshoki.core.util.dp
import com.minshoki.image_editor.core.StickerType
import com.minshoki.image_editor.core.TextStickerColors
import com.minshoki.image_editor.model.StickerJsonFileDataModel
import com.minshoki.image_editor.model.TextStickerJsonFileDataModel
import kotlin.math.ceil


class TextSticker @JvmOverloads constructor(
    @get:Nullable var text: String? = "",
    override var drawable: Drawable? = null,
    private val maxWidth: Int = 100
) : Sticker() {
    private val textRect: Rect
    private val textPaint: TextPaint
    private var staticLayout: StaticLayout
    private var alignment: Layout.Alignment
    private var originText: String? = ""
    private var textColor: TextStickerColors.TextColor
    private var textBackgroundColor: TextStickerColors.BackgroundColor
    /**
     * Upper bounds for text size.
     * This acts as a starting point for resizing.
     */
    private var maxTextSizePixels: Float
    /**
     * @return lower text size limit, in pixels.
     */
    /**
     * Lower bounds for text size.
     */
    var minTextSizePixels: Float
        private set

    /**
     * Line spacing multiplier.
     */
    private var lineSpacingMultiplier = 1.0f

    /**
     * Additional line spacing.
     */
    private var lineSpacingExtra = 0.0f
    private var curTextSize = 25f

    /**
     * Map used to store views' tags.
     */
    private var mKeyedTags: SparseArray<Any>? = null
    private var stickerWidth_origin = 0
    private var stickerHeight_origin = 0
    private var isFirstGetWidth = true
    private var isFirstGetHeight = true

    //  public TextSticker(@NonNull Context context, @Nullable Drawable drawable) {
    //    this.context = context;
    //    this.drawable = drawable;
    //    if (drawable == null) {
    //      this.drawable = ContextCompat.getDrawable(context, R.drawable.sticker_transparent_background);
    //    }
    //    textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    //    realBounds = new Rect(0, 0, getWidth(), getHeight());
    //    textRect = new Rect(0, 0, getWidth(), getHeight());
    //    minTextSizePixels = convertSpToPx(6);
    //    maxTextSizePixels = convertSpToPx(32);
    //    alignment = Layout.Alignment.ALIGN_CENTER;
    //    textPaint.setTextSize(maxTextSizePixels);
    //  }
    init {
        curTextSize = 20f.dp.toFloat()
        textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        textPaint.setShadowLayer(5f, 0f, 0f, Color.parseColor("#1e000000"))
        minTextSizePixels = 12.dp.toFloat()
        maxTextSizePixels = 50.dp.toFloat()
        alignment = Layout.Alignment.ALIGN_NORMAL
        textPaint.textSize = curTextSize
        textPaint.style = Paint.Style.FILL

        textColor = TextStickerColors.TextColor.WHITE
        textBackgroundColor = TextStickerColors.BackgroundColor.NONE

        textRect = Rect(0, 0, width, height)
        staticLayout = StaticLayout(
            text, textPaint, textRect.width(), alignment, lineSpacingMultiplier,
            lineSpacingExtra, true
        )
    }

    override fun draw(canvas: Canvas) {
        val matrix = matrix
        canvas.save()
        canvas.concat(matrix)
        if (drawable != null) {
            drawable?.bounds = textRect
            drawable?.draw(canvas)
        }
        if (textRect.width() == width) {
            val dy = height / 2 - staticLayout.height / 2
            // center vertical
            canvas.translate(0f, dy.toFloat())
        } else {
            val dx = textRect.left
            val dy = textRect.top + textRect.height() / 2 - staticLayout.height / 2
            canvas.translate(dx.toFloat(), dy.toFloat())
        }
        staticLayout.draw(canvas)
        canvas.restore()
    }

    override val width: Int
        get() {
            // 单行
            if (text != originText || isFirstGetWidth) {
                originText = text
                isFirstGetWidth = false
                if (text!!.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray().size < 2) {
                    return if (textPaint.measureText(text) < maxWidth) {
                        stickerWidth_origin = textPaint.measureText(text).toInt() + 18 + 36.dp
                        stickerWidth_origin
                    } else {
                        stickerWidth_origin = maxWidth + 18 + 36.dp
                        stickerWidth_origin
                    }
                }
                val texts = text!!.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                stickerWidth_origin = 0
                var text_m: Int
                for (i in texts.indices) {
                    text_m = if (textPaint.measureText(texts[i]) < maxWidth) {
                        textPaint.measureText(texts[i]).toInt()
                    } else {
                        maxWidth
                    }
                    if (text_m > stickerWidth_origin) {
                        stickerWidth_origin = text_m
                    }
                }
                stickerWidth_origin = stickerWidth_origin + 18 + 36.dp
                return stickerWidth_origin
            }
            return stickerWidth_origin
        }
    override val height: Int
        get() {
            if (text != originText || isFirstGetHeight) {
                originText = text
                isFirstGetHeight = false
                var lines = 0
                val texts = text!!.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                var line_m: Int
                for (i in texts.indices) {
                    line_m = ceil((textPaint.measureText(texts[i]) / maxWidth).toDouble())
                        .toInt()
                    if (line_m > 1) {
                        lines += line_m
                    } else {
                        lines++
                    }
                }
                if (text!!.endsWith("\n")) {
                    lines++
                }
                //        int lines = (int) Math.ceil(textPaint.measureText(text) / maxWidth);
                //        Rect bounds = new Rect();
                //        textPaint.getTextBounds(text, 0, text.length(), bounds);
                stickerHeight_origin = (textPaint.fontSpacing * lines).toInt() + 16 + 32.dp
                return stickerHeight_origin
                //        Paint.FontMetricsInt fontMetrics = textPaint.getFontMetricsInt();
                //        return (Math.abs(fontMetrics.top) + Math.abs(fontMetrics.bottom)) * lines;//字体高度


                //        float y = fontMetrics.descent+curTextSize/10+5;
                //        float temph =(int)(fontMetrics.bottom-fontMetrics.top)/2;
                //        temph += y;
                //        return (int) (temph + 2)*lines +2;
            }
            return stickerHeight_origin
        }

    override fun mapperSaveFileJsonDataModel(): StickerJsonFileDataModel {
        val floatArray = FloatArray(9)
        matrix.getValues(floatArray)
        val textData = TextStickerJsonFileDataModel(
            text = text.toString(),
            textColor = textColor,
            backgroundColor = textBackgroundColor
        )

        return StickerJsonFileDataModel(
            type = StickerType.TEXT,
            matrixValues = floatArray,
            imageUrl = "",
            width = width,
            height = height,
            textData = textData
        )
    }

    val minWidth: Int
        get() = stickerWidth_origin * 12 / 25
    val minHeight: Int
        get() = 0

    override fun release() {
        super.release()
        //        if (drawable != null) {
//            drawable = null;
//        }
    }

    override fun setAlpha(@IntRange(from = 0, to = 255) alpha: Int): TextSticker {
        textPaint.setAlpha(alpha)
        return this
    }


    override fun setDrawable(drawable: Drawable): TextSticker {
        this.drawable = drawable
        //        realBounds.set(0, 0, getWidth(), getHeight());
        textRect[0, 0, width] = height
        return this
    }

    fun setDrawable(drawable: Drawable, region: Rect?): TextSticker {
        this.drawable = drawable
        //        realBounds.set(0, 0, getWidth(), getHeight());
        if (region == null) {
            textRect[0, 0, width] = height
        } else {
            textRect[region.left, region.top, region.right] = region.bottom
        }
        return this
    }


    fun setTypeface(typeface: Typeface?): TextSticker {
        // 切换字体时，重置宽高
        isFirstGetWidth = true
        isFirstGetHeight = true
        textPaint.setTypeface(typeface)
        return this
    }

    fun setTextColor(context: Context, color: TextStickerColors.TextColor): TextSticker {
        textColor = color
        textPaint.setColor(ContextCompat.getColor(context, color.colorRes))
        return this
    }

    fun setTextBackgroundColor(context: Context, color: TextStickerColors.BackgroundColor): TextSticker {
        textBackgroundColor = color
        setDrawable(ContextCompat.getDrawable(context, color.drawableRes)!!)
        return this
    }

    fun setTextAlign(alignment: Layout.Alignment): TextSticker {
        this.alignment = alignment
        return this
    }

    fun setMaxTextSize(@Dimension(unit = SP) size: Float): TextSticker {
        curTextSize = size
        textPaint.textSize = size.dp.toFloat()
        maxTextSizePixels = textPaint.textSize
        return this
    }

    fun setTag(key: Int, tag: Any) {
        if (mKeyedTags == null) {
            mKeyedTags = SparseArray(2)
        }
        mKeyedTags!!.put(key, tag)
    }

    fun getTag(key: Int): Any? {
        return if (mKeyedTags != null) mKeyedTags!![key] else null
    }

    /**
     * Sets the lower text size limit
     *
     * @param minTextSizeScaledPixels the minimum size to use for text in this view,
     * in scaled pixels.
     */
    fun setMinTextSize(minTextSizeScaledPixels: Float): TextSticker {
        minTextSizePixels = minTextSizeScaledPixels.dp.toFloat()
        return this
    }

    fun setLineSpacing(add: Float, multiplier: Float): TextSticker {
        lineSpacingMultiplier = multiplier
        lineSpacingExtra = add
        return this
    }

    fun setText(text: String?): TextSticker {
        this.text = text
        return this
    }

    fun getBackgroundColor() = textBackgroundColor
    fun getTextColor() = textColor
    /**
     * Resize this view's text size with respect to its width and height
     * (minus padding). You should always call this method after the initialization.
     */
    fun resizeText(): TextSticker {
//        final int availableHeightPixels = textRect.height();
//
//        final int availableWidthPixels = textRect.width();
//
//        final CharSequence text = getText();
//
//        // Safety check
//        // (Do not resize if the view does not have dimensions or if there is no text)
//        if (text == null
//                || text.length() <= 0
//                || availableHeightPixels <= 0
//                || availableWidthPixels <= 0
//                || maxTextSizePixels <= 0) {
//            return this;
//        }
        if (text == null
            || text!!.length <= 0
        ) {
            return this
        }
        //        float targetTextSizePixels = maxTextSizePixels;
//        int targetTextHeightPixels =
//                getTextHeightPixels(text, availableWidthPixels, targetTextSizePixels);
//
//        // Until we either fit within our TextView
//        // or we have reached our minimum text size,
//        // incrementally try smaller sizes
//        while (targetTextHeightPixels > availableHeightPixels
//                && targetTextSizePixels > minTextSizePixels) {
//            targetTextSizePixels = Math.max(targetTextSizePixels - 2, minTextSizePixels);
//
//            targetTextHeightPixels =
//                    getTextHeightPixels(text, availableWidthPixels, targetTextSizePixels);
//        }
//
//        // If we have reached our minimum text size and the text still doesn't fit,
//        // append an ellipsis
//        // (NOTE: Auto-ellipsize doesn't work hence why we have to do it here)
//        if (targetTextSizePixels == minTextSizePixels
//                && targetTextHeightPixels > availableHeightPixels) {
//            // Make a copy of the original TextPaint object for measuring
//            TextPaint textPaintCopy = new TextPaint(textPaint);
//            textPaintCopy.setTextSize(targetTextSizePixels);
//
//            // Measure using a StaticLayout instance
//            StaticLayout staticLayout =
//                    new StaticLayout(text, textPaintCopy, availableWidthPixels, Layout.Alignment.ALIGN_NORMAL,
//                            lineSpacingMultiplier, lineSpacingExtra, false);
//
//            // Check that we have a least one line of rendered text
//            if (staticLayout.getLineCount() > 0) {
//                // Since the line at the specific vertical position would be cut off,
//                // we must trim up to the previous line and add an ellipsis
//                int lastLine = staticLayout.getLineForVertical(availableHeightPixels) - 1;
//
//                if (lastLine >= 0) {
//                    int startOffset = staticLayout.getLineStart(lastLine);
//                    int endOffset = staticLayout.getLineEnd(lastLine);
//                    float lineWidthPixels = staticLayout.getLineWidth(lastLine);
//                    float ellipseWidth = textPaintCopy.measureText(mEllipsis);
//
//                    // Trim characters off until we have enough room to draw the ellipsis
//                    while (availableWidthPixels < lineWidthPixels + ellipseWidth) {
//                        endOffset--;
//                        lineWidthPixels =
//                                textPaintCopy.measureText(text.subSequence(startOffset, endOffset + 1).toString());
//                    }
//
//                    setText(text.subSequence(0, endOffset) + mEllipsis);
//                }
//            }
//        }
//        textPaint.setTextSize(curTextSize);
//            staticLayout =
//                    new StaticLayout(this.text, textPaint, textRect.width(), alignment, lineSpacingMultiplier,
//                            lineSpacingExtra, true);
        staticLayout = StaticLayout(
            text, textPaint, width, alignment, lineSpacingMultiplier,
            lineSpacingExtra, true
        )
        return this
    }

    /**
     * Sets the text size of a clone of the view's [TextPaint] object
     * and uses a [StaticLayout] instance to measure the height of the text.
     *
     * @return the height of the text when placed in a view
     * with the specified width
     * and when the text has the specified size.
     */
    protected fun getTextHeightPixels(
        source: CharSequence, availableWidthPixels: Int,
        textSizePixels: Float
    ): Int {
        textPaint.textSize = textSizePixels
        // It's not efficient to create a StaticLayout instance
        // every time when measuring, we can use StaticLayout.Builder
        // since api 23.
        val staticLayout = StaticLayout(
            source, textPaint, availableWidthPixels, Layout.Alignment.ALIGN_NORMAL,
            lineSpacingMultiplier, lineSpacingExtra, true
        )
        return staticLayout.height
    }

    companion object {
        /**
         * Our ellipsis string.
         */
        private const val mEllipsis = "\u2026"
    }
}