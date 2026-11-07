package com.aura.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream

/** Canvas that shows the bitmap and handles cropping / freehand drawing. */
class EditCanvas(ctx: Context) : View(ctx) {
    var base: Bitmap? = null
        set(v) {
            field = v
            layoutImage()
            invalidate()
        }
    var filter: ColorMatrixColorFilter? = null
        set(v) {
            field = v
            invalidate()
        }

    /** 0 = view only, 1 = crop, 2 = draw */
    var mode = 0
        set(v) {
            field = v
            if (v == 1) resetCrop()
            invalidate()
        }
    var penColor = Color.RED
    var penSize = 14
    var onStrokeStart: (() -> Unit)? = null

    private val toView = Matrix()
    private val toBmp = Matrix()
    private val img = RectF()
    private val crop = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var drag = -1
    private var px = 0f
    private var py = 0f
    private var lastX = 0f
    private var lastY = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        layoutImage()
        if (mode == 1) resetCrop()
    }

    private fun layoutImage() {
        val b = base ?: return
        if (width == 0 || height == 0) return
        val s = minOf(width / b.width.toFloat(), height / b.height.toFloat())
        val dx = (width - b.width * s) / 2f
        val dy = (height - b.height * s) / 2f
        toView.reset()
        toView.postScale(s, s)
        toView.postTranslate(dx, dy)
        toView.invert(toBmp)
        img.set(dx, dy, dx + b.width * s, dy + b.height * s)
    }

    fun resetCrop() {
        layoutImage()
        crop.set(img)
    }

    fun setCropRatio(rw: Float, rh: Float) {
        layoutImage()
        val target = rw / rh
        var w = img.width()
        var h = w / target
        if (h > img.height()) {
            h = img.height()
            w = h * target
        }
        val cx = img.centerX()
        val cy = img.centerY()
        crop.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        invalidate()
    }

    fun applyCrop(): Bitmap? {
        val b = base ?: return null
        val pts = floatArrayOf(crop.left, crop.top, crop.right, crop.bottom)
        toBmp.mapPoints(pts)
        val x = pts[0].toInt().coerceIn(0, b.width - 1)
        val y = pts[1].toInt().coerceIn(0, b.height - 1)
        val w = (pts[2].toInt() - x).coerceIn(1, b.width - x)
        val h = (pts[3].toInt() - y).coerceIn(1, b.height - y)
        return Bitmap.createBitmap(b, x, y, w, h)
    }

    override fun onDraw(c: Canvas) {
        val b = base ?: return
        paint.colorFilter = filter
        c.drawBitmap(b, toView, paint)
        if (mode == 1) {
            c.drawRect(0f, 0f, width.toFloat(), crop.top, dim)
            c.drawRect(0f, crop.bottom, width.toFloat(), height.toFloat(), dim)
            c.drawRect(0f, crop.top, crop.left, crop.bottom, dim)
            c.drawRect(crop.right, crop.top, width.toFloat(), crop.bottom, dim)
            c.drawRect(crop, line)
            val tw = crop.width() / 3f
            val th = crop.height() / 3f
            line.strokeWidth = 1.5f
            for (i in 1..2) {
                c.drawLine(crop.left + tw * i, crop.top, crop.left + tw * i, crop.bottom, line)
                c.drawLine(crop.left, crop.top + th * i, crop.right, crop.top + th * i, line)
            }
            line.strokeWidth = 3f
            val r = context.dp(7).toFloat()
            c.drawCircle(crop.left, crop.top, r, handle)
            c.drawCircle(crop.right, crop.top, r, handle)
            c.drawCircle(crop.left, crop.bottom, r, handle)
            c.drawCircle(crop.right, crop.bottom, r, handle)
        }
    }

    private fun near(x: Float, y: Float, cx: Float, cy: Float): Boolean {
        val t = context.dp(40).toFloat()
        return Math.abs(x - cx) < t && Math.abs(y - cy) < t
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (mode == 1) return cropTouch(e)
        if (mode == 2) return drawTouch(e)
        return false
    }

    private fun cropTouch(e: MotionEvent): Boolean {
        val min = context.dp(48).toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                px = e.x
                py = e.y
                drag = when {
                    near(e.x, e.y, crop.left, crop.top) -> 0
                    near(e.x, e.y, crop.right, crop.top) -> 1
                    near(e.x, e.y, crop.left, crop.bottom) -> 2
                    near(e.x, e.y, crop.right, crop.bottom) -> 3
                    crop.contains(e.x, e.y) -> 4
                    else -> -1
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - px
                val dy = e.y - py
                px = e.x
                py = e.y
                when (drag) {
                    0 -> {
                        crop.left = minOf(crop.left + dx, crop.right - min)
                        crop.top = minOf(crop.top + dy, crop.bottom - min)
                    }
                    1 -> {
                        crop.right = maxOf(crop.right + dx, crop.left + min)
                        crop.top = minOf(crop.top + dy, crop.bottom - min)
                    }
                    2 -> {
                        crop.left = minOf(crop.left + dx, crop.right - min)
                        crop.bottom = maxOf(crop.bottom + dy, crop.top + min)
                    }
                    3 -> {
                        crop.right = maxOf(crop.right + dx, crop.left + min)
                        crop.bottom = maxOf(crop.bottom + dy, crop.top + min)
                    }
                    4 -> {
                        crop.offset(dx, dy)
                        if (crop.left < img.left) crop.offset(img.left - crop.left, 0f)
                        if (crop.right > img.right) crop.offset(img.right - crop.right, 0f)
                        if (crop.top < img.top) crop.offset(0f, img.top - crop.top)
                        if (crop.bottom > img.bottom) crop.offset(0f, img.bottom - crop.bottom)
                    }
                }
                crop.left = maxOf(crop.left, img.left)
                crop.top = maxOf(crop.top, img.top)
                crop.right = minOf(crop.right, img.right)
                crop.bottom = minOf(crop.bottom, img.bottom)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = -1
        }
        return true
    }

    private fun drawTouch(e: MotionEvent): Boolean {
        val b = base ?: return false
        val p = floatArrayOf(e.x, e.y)
        toBmp.mapPoints(p)
        val cv = Canvas(b)
        pen.color = penColor
        pen.strokeWidth = maxOf(1f, penSize * b.width / 1000f)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onStrokeStart?.invoke()
                lastX = p[0]
                lastY = p[1]
                cv.drawPoint(lastX, lastY, pen)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                cv.drawLine(lastX, lastY, p[0], p[1], pen)
                lastX = p[0]
                lastY = p[1]
                invalidate()
            }
        }
        return true
    }
}

class ImageEditorActivity : AppCompatActivity() {

    private lateinit var canvas: EditCanvas
    private lateinit var panel: LinearLayout
    private lateinit var tabs: LinearLayout
    private val undo = ArrayDeque<Bitmap>()

    private var brightness = 0
    private var contrast = 100
    private var saturation = 100
    private var filterIdx = 0
    private var tab = -1

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) finish() else loadUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(6), 0, dp(12), 0)
        val close = iconButton(R.drawable.ic_close, 48, 56)
        close.setOnClickListener { finish() }
        bar.addView(close)
        val title = tv("Image editor", 18f)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val undoBtn = chip("Undo") { undoOnce() }
        bar.addView(undoBtn)
        val saveBtn = chip("Save") { save() }
        (saveBtn.background as GradientDrawable).setColor(0xFF5B6CFF.toInt())
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginStart = dp(8)
        bar.addView(saveBtn, lp)
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        canvas = EditCanvas(this)
        canvas.onStrokeStart = { pushUndo() }
        root.addView(canvas, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(0xFF161619.toInt())
        panel.setPadding(dp(12), dp(8), dp(12), dp(4))
        root.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.setBackgroundColor(0xFF161619.toInt())
        tabs = LinearLayout(this)
        tabs.setPadding(dp(8), dp(4), dp(8), dp(10))
        hs.addView(tabs)
        val names = listOf("Rotate", "Crop", "Adjust", "Filters", "Draw")
        for ((i, n) in names.withIndex()) {
            val t = chip(n) { selectTab(i) }
            val l = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            l.marginEnd = dp(8)
            tabs.addView(t, l)
        }
        root.addView(hs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)

        val d: Uri? = intent.data
        if (d != null) loadUri(d) else picker.launch("image/*")
        selectTab(0)
    }

    // ------------------------------------------------------------ helpers

    private fun chip(text: String, action: () -> Unit): TextView {
        val t = tv(text, 14f)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(18).toFloat()
        bg.setColor(0xFF2A2A33.toInt())
        t.background = bg
        t.gravity = Gravity.CENTER
        t.setPadding(dp(16), dp(9), dp(16), dp(9))
        t.setOnClickListener { action() }
        return t
    }

    private fun seekRow(label: String, min: Int, max: Int, init: Int, onChange: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        val l = tv(label, 13f, 0xFFBBBBBB.toInt())
        row.addView(l, LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT))
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = init - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(p + min)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(sb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun scrollRow(): Pair<HorizontalScrollView, LinearLayout> {
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(4), 0, dp(4))
        hs.addView(row)
        return Pair(hs, row)
    }

    private fun gap(): LinearLayout.LayoutParams {
        val l = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        l.marginEnd = dp(8)
        return l
    }

    // ------------------------------------------------------------ loading

    private fun loadUri(uri: Uri) {
        Thread {
            var bmp: Bitmap? = null
            try {
                val data = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (data != null) {
                    var b = ImgNet.decode(data, 1100)
                    if (b != null) {
                        val big = maxOf(b.width, b.height)
                        if (big > 2400) {
                            val s = 2400f / big
                            b = Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
                        }
                        var deg = 0f
                        try {
                            val ex = ExifInterface(java.io.ByteArrayInputStream(data))
                            deg = when (ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                else -> 0f
                            }
                        } catch (_: Exception) {
                        }
                        if (deg != 0f) {
                            val m = Matrix()
                            m.postRotate(deg)
                            b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
                        }
                        bmp = b.copy(Bitmap.Config.ARGB_8888, true)
                    }
                }
            } catch (e: Throwable) {
                bmp = null
            }
            runOnUiThread {
                if (bmp == null) {
                    toast("Could not open image")
                    finish()
                } else {
                    undo.clear()
                    canvas.base = bmp
                }
            }
        }.start()
    }

    // ------------------------------------------------------------ undo / transforms

    private fun pushUndo() {
        val b = canvas.base ?: return
        undo.addLast(b.copy(Bitmap.Config.ARGB_8888, true))
        while (undo.size > 4) undo.removeFirst()
    }

    private fun undoOnce() {
        if (undo.isEmpty()) {
            toast("Nothing to undo")
            return
        }
        canvas.base = undo.removeLast()
    }

    private fun transform(m: Matrix) {
        val b = canvas.base ?: return
        pushUndo()
        canvas.base = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true).copy(Bitmap.Config.ARGB_8888, true)
    }

    // ------------------------------------------------------------ tabs

    private fun selectTab(i: Int) {
        tab = i
        for (k in 0 until tabs.childCount) {
            val t = tabs.getChildAt(k) as TextView
            (t.background as GradientDrawable).setColor(if (k == i) 0xFF5B6CFF.toInt() else 0xFF2A2A33.toInt())
        }
        canvas.mode = when (i) {
            1 -> 1
            4 -> 2
            else -> 0
        }
        panel.removeAllViews()
        when (i) {
            0 -> {
                val (hs, row) = scrollRow()
                row.addView(chip("Rotate left") { transform(Matrix().apply { postRotate(-90f) }) }, gap())
                row.addView(chip("Rotate right") { transform(Matrix().apply { postRotate(90f) }) }, gap())
                row.addView(chip("Flip horizontal") { transform(Matrix().apply { postScale(-1f, 1f) }) }, gap())
                row.addView(chip("Flip vertical") { transform(Matrix().apply { postScale(1f, -1f) }) }, gap())
                panel.addView(hs)
            }
            1 -> {
                val (hs, row) = scrollRow()
                row.addView(chip("Free") { canvas.resetCrop(); canvas.invalidate() }, gap())
                row.addView(chip("1:1") { canvas.setCropRatio(1f, 1f) }, gap())
                row.addView(chip("4:3") { canvas.setCropRatio(4f, 3f) }, gap())
                row.addView(chip("3:4") { canvas.setCropRatio(3f, 4f) }, gap())
                row.addView(chip("16:9") { canvas.setCropRatio(16f, 9f) }, gap())
                row.addView(chip("9:16") { canvas.setCropRatio(9f, 16f) }, gap())
                panel.addView(hs)
                val apply = chip("Apply crop") {
                    val nb = canvas.applyCrop()
                    if (nb != null) {
                        pushUndo()
                        canvas.base = nb.copy(Bitmap.Config.ARGB_8888, true)
                        canvas.resetCrop()
                        canvas.invalidate()
                    }
                }
                (apply.background as GradientDrawable).setColor(0xFF5B6CFF.toInt())
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(6)
                panel.addView(apply, lp)
            }
            2 -> {
                panel.addView(seekRow("Brightness", -100, 100, brightness) { brightness = it; applyFilter() })
                panel.addView(seekRow("Contrast", 50, 200, contrast) { contrast = it; applyFilter() })
                panel.addView(seekRow("Saturation", 0, 200, saturation) { saturation = it; applyFilter() })
                val reset = chip("Reset adjustments") {
                    brightness = 0
                    contrast = 100
                    saturation = 100
                    applyFilter()
                    selectTab(2)
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.topMargin = dp(6)
                panel.addView(reset, lp)
            }
            3 -> {
                val (hs, row) = scrollRow()
                val names = listOf("None", "Mono", "Sepia", "Invert", "Warm", "Cool", "Vivid", "Fade")
                for ((k, n) in names.withIndex()) {
                    val c = chip(n) {
                        filterIdx = k
                        applyFilter()
                        selectTab(3)
                    }
                    (c.background as GradientDrawable).setColor(if (k == filterIdx) 0xFF5B6CFF.toInt() else 0xFF2A2A33.toInt())
                    row.addView(c, gap())
                }
                panel.addView(hs)
            }
            4 -> {
                val (hs, row) = scrollRow()
                val cols = intArrayOf(
                    Color.RED, 0xFFFFEB3B.toInt(), 0xFF4CAF50.toInt(), 0xFF2196F3.toInt(),
                    Color.WHITE, Color.BLACK, 0xFFFF9800.toInt(), 0xFFE91E63.toInt()
                )
                for (c in cols) {
                    val dot = View(this)
                    val g = GradientDrawable()
                    g.shape = GradientDrawable.OVAL
                    g.setColor(c)
                    g.setStroke(dp(2), if (c == canvas.penColor) 0xFF5B6CFF.toInt() else 0xFF555555.toInt())
                    dot.background = g
                    dot.setOnClickListener {
                        canvas.penColor = c
                        selectTab(4)
                    }
                    val l = LinearLayout.LayoutParams(dp(34), dp(34))
                    l.marginEnd = dp(10)
                    row.addView(dot, l)
                }
                panel.addView(hs)
                panel.addView(seekRow("Brush size", 2, 60, canvas.penSize) { canvas.penSize = it })
            }
        }
    }

    // ------------------------------------------------------------ colour filters

    private fun applyFilter() {
        val cm = ColorMatrix()
        cm.setSaturation(saturation / 100f)
        val c = contrast / 100f
        val t = (1f - c) * 127.5f + brightness * 1.27f
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        val f: ColorMatrix? = when (filterIdx) {
            1 -> ColorMatrix().apply { setSaturation(0f) }
            2 -> ColorMatrix(
                floatArrayOf(
                    .393f, .769f, .189f, 0f, 0f,
                    .349f, .686f, .168f, 0f, 0f,
                    .272f, .534f, .131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            3 -> ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            4 -> ColorMatrix(
                floatArrayOf(
                    1.1f, 0f, 0f, 0f, 10f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 0.85f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            5 -> ColorMatrix(
                floatArrayOf(
                    0.9f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1.15f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            6 -> ColorMatrix().apply { setSaturation(1.7f) }
            7 -> ColorMatrix(
                floatArrayOf(
                    0.85f, 0f, 0f, 0f, 24f,
                    0f, 0.85f, 0f, 0f, 24f,
                    0f, 0f, 0.85f, 0f, 24f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            else -> null
        }
        if (f != null) cm.postConcat(f)
        val noChange = brightness == 0 && contrast == 100 && saturation == 100 && filterIdx == 0
        canvas.filter = if (noChange) null else ColorMatrixColorFilter(cm)
    }

    // ------------------------------------------------------------ save

    private fun save() {
        val b = canvas.base
        if (b == null) {
            toast("Image not ready")
            return
        }
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = canvas.filter
        Canvas(out).drawBitmap(b, 0f, 0f, p)
        val png = b.hasAlpha()
        val bos = ByteArrayOutputStream()
        out.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, bos)
        val name = "aura_edit_" + System.currentTimeMillis() + if (png) ".png" else ".jpg"
        val uri = ImgNet.saveBytes(this, bos.toByteArray(), name, if (png) "image/png" else "image/jpeg")
        if (uri == null) {
            toast("Save failed")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Saved")
            .setMessage("Saved to Pictures/AuraBrowser")
            .setPositiveButton("Share") { _, _ ->
                val i = Intent(Intent.ACTION_SEND)
                i.type = if (png) "image/png" else "image/jpeg"
                i.putExtra(Intent.EXTRA_STREAM, uri)
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(i, "Share image"))
            }
            .setNegativeButton("Keep editing", null)
            .setNeutralButton("Done") { _, _ -> finish() }
            .show()
    }
}
