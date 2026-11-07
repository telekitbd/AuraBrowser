package com.aura.browser

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Pinch / double-tap zoom image view. */
class ZoomImageView(ctx: Context) : ImageView(ctx) {
    private val m = Matrix()
    private var bmp: Bitmap? = null
    private var minScale = 1f
    private var lx = 0f
    private var ly = 0f
    private var dragging = false

    private val sd = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val cur = scaleNow()
            val target = (cur * d.scaleFactor).coerceIn(minScale, minScale * 6f)
            val f = target / cur
            m.postScale(f, f, d.focusX, d.focusY)
            fix()
            imageMatrix = m
            return true
        }
    })

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scaleNow() > minScale * 1.05f) {
                fit()
            } else {
                val f = 2.5f
                m.postScale(f, f, e.x, e.y)
                fix()
                imageMatrix = m
            }
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    fun setBitmap(b: Bitmap?) {
        bmp = b
        setImageBitmap(b)
        fit()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        fit()
    }

    private fun fit() {
        val b = bmp ?: return
        if (width == 0 || height == 0) return
        val s = minOf(width / b.width.toFloat(), height / b.height.toFloat())
        minScale = s
        m.reset()
        m.postScale(s, s)
        m.postTranslate((width - b.width * s) / 2f, (height - b.height * s) / 2f)
        imageMatrix = m
    }

    private fun scaleNow(): Float {
        val v = FloatArray(9)
        m.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    private fun fix() {
        val b = bmp ?: return
        val r = RectF(0f, 0f, b.width.toFloat(), b.height.toFloat())
        m.mapRect(r)
        var dx = 0f
        var dy = 0f
        if (r.width() <= width) dx = (width - r.width()) / 2f - r.left
        else if (r.left > 0) dx = -r.left
        else if (r.right < width) dx = width - r.right
        if (r.height() <= height) dy = (height - r.height()) / 2f - r.top
        else if (r.top > 0) dy = -r.top
        else if (r.bottom < height) dy = height - r.bottom
        m.postTranslate(dx, dy)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        gd.onTouchEvent(e)
        sd.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lx = e.x
                ly = e.y
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!sd.isInProgress && e.pointerCount == 1 && dragging) {
                    m.postTranslate(e.x - lx, e.y - ly)
                    fix()
                    imageMatrix = m
                    lx = e.x
                    ly = e.y
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> dragging = false
            MotionEvent.ACTION_POINTER_UP -> {
                val i = if (e.actionIndex == 0) 1 else 0
                lx = e.getX(i)
                ly = e.getY(i)
                dragging = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }
}

class ImagesActivity : AppCompatActivity() {

    private var urls: List<String> = emptyList()
    private val selected = LinkedHashSet<Int>()
    private lateinit var grid: GridView
    private lateinit var titleView: TextView
    private lateinit var selectBtn: TextView
    private lateinit var saveBtn: TextView
    private lateinit var adapter: BaseAdapter
    private var cell = 0
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        urls = ImageHolder.urls
        if (urls.isEmpty()) {
            toast("No images found on this page")
            finish()
            return
        }
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(6), 0, dp(10), 0)
        val back = iconButton(R.drawable.ic_arrow_back, 48, 56)
        back.setOnClickListener { finish() }
        bar.addView(back)
        titleView = tv("", 18f)
        titleView.setPadding(dp(6), 0, 0, 0)
        bar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        selectBtn = pill("Select all")
        selectBtn.setOnClickListener {
            if (selected.size == urls.size) selected.clear() else {
                selected.clear()
                for (i in urls.indices) selected.add(i)
            }
            refresh()
        }
        bar.addView(selectBtn)
        saveBtn = pill("Save all")
        saveBtn.setOnClickListener { saveSelected() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginStart = dp(8)
        bar.addView(saveBtn, lp)
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        val gap = dp(3)
        cell = (resources.displayMetrics.widthPixels - gap * 4) / 3
        grid = GridView(this)
        grid.numColumns = 3
        grid.horizontalSpacing = gap
        grid.verticalSpacing = gap
        grid.setPadding(gap, gap, gap, gap)
        grid.clipToPadding = false
        adapter = object : BaseAdapter() {
            override fun getCount() = urls.size
            override fun getItem(position: Int) = urls[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val c = (convertView as? FrameLayout) ?: makeCell()
                c.layoutParams = AbsListView.LayoutParams(cell, cell)
                val iv = c.getChildAt(0) as ImageView
                val chk = c.getChildAt(1) as TextView
                val u = urls[position]
                iv.setImageDrawable(null)
                iv.tag = u
                ImgNet.load(u, 320) { b ->
                    if (b != null && iv.tag == u) iv.setImageBitmap(b)
                }
                chk.visibility = if (selected.contains(position)) View.VISIBLE else View.GONE
                c.alpha = if (selected.isEmpty() || selected.contains(position)) 1f else 0.55f
                return c
            }
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            if (selected.isNotEmpty()) toggle(pos) else preview(pos)
        }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            toggle(pos)
            true
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun pill(text: String): TextView {
        val t = tv(text, 13f)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(18).toFloat()
        bg.setColor(0xFF2A2A33.toInt())
        t.background = bg
        t.setPadding(dp(14), dp(8), dp(14), dp(8))
        return t
    }

    private fun makeCell(): FrameLayout {
        val f = FrameLayout(this)
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.setBackgroundColor(0xFF1A1A1F.toInt())
        f.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val chk = tv("\u2713", 14f)
        chk.gravity = Gravity.CENTER
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(0xFF7C8CFF.toInt())
        chk.background = bg
        val lp = FrameLayout.LayoutParams(dp(24), dp(24))
        lp.gravity = Gravity.TOP or Gravity.END
        lp.setMargins(0, dp(6), dp(6), 0)
        f.addView(chk, lp)
        chk.visibility = View.GONE
        return f
    }

    private fun toggle(pos: Int) {
        if (!selected.add(pos)) selected.remove(pos)
        refresh()
    }

    private fun refresh() {
        titleView.text = if (selected.isEmpty()) "Images (${urls.size})" else "${selected.size} selected"
        saveBtn.text = if (selected.isEmpty()) "Save all" else "Save (${selected.size})"
        selectBtn.text = if (selected.size == urls.size) "None" else "Select all"
        adapter.notifyDataSetChanged()
    }

    private fun saveSelected() {
        if (saving) return
        val list = if (selected.isEmpty()) urls else selected.sorted().map { urls[it] }
        saving = true
        ImgNet.saveAll(this, list, { n, total ->
            titleView.text = "Saving $n/$total"
        }, { ok, total ->
            saving = false
            toast("Saved $ok of $total to Pictures/AuraBrowser")
            refresh()
        })
    }

    private fun preview(start: Int) {
        var pos = start
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)
        val zoom = ZoomImageView(this)
        root.addView(zoom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val info = tv("", 13f, 0xFFAAAAAA.toInt())
        info.gravity = Gravity.CENTER
        info.setPadding(0, dp(6), 0, dp(2))
        root.addView(info)
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER
        row.setPadding(dp(4), dp(2), dp(4), dp(10))
        root.addView(row)

        fun show() {
            val my = pos
            info.text = "${pos + 1} / ${urls.size}"
            zoom.setBitmap(null)
            ImgNet.load(urls[pos], 1600) { b ->
                if (b != null && pos == my) zoom.setBitmap(b)
            }
        }

        fun btn(label: String, action: () -> Unit) {
            val t = tv(label, 14f)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(4), dp(12), dp(4), dp(12))
            t.setOnClickListener { action() }
            row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        fun withSaved(then: (Uri) -> Unit) {
            toast("Please wait…")
            val u = urls[pos]
            Thread {
                val uri = ImgNet.saveUrl(applicationContext, u)
                runOnUiThread {
                    if (uri == null) toast("Could not save image") else then(uri)
                }
            }.start()
        }

        btn("Prev") {
            if (pos > 0) {
                pos--
                show()
            }
        }
        btn("Next") {
            if (pos < urls.size - 1) {
                pos++
                show()
            }
        }
        btn("Save") { withSaved { toast("Saved to Pictures/AuraBrowser") } }
        btn("Share") {
            withSaved { uri ->
                val i = Intent(Intent.ACTION_SEND)
                i.type = "image/*"
                i.putExtra(Intent.EXTRA_STREAM, uri)
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(i, "Share image"))
            }
        }
        btn("Edit") {
            withSaved { uri ->
                dlg.dismiss()
                startActivity(Intent(this, ImageEditorActivity::class.java).setData(uri))
            }
        }
        btn("Close") { dlg.dismiss() }
        dlg.setContentView(root)
        dlg.show()
        show()
    }
}
