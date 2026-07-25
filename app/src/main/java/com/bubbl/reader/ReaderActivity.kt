package com.bubbl.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderActivity : AppCompatActivity() {

    private lateinit var overlay: View
    private lateinit var balloonImage: ImageView
    private var balloonBmp: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        goImmersive()

        val pages = BookHolder.pages
        if (pages.isEmpty()) { finish(); return }

        val pager = findViewById<ViewPager2>(R.id.pager)
        val label = findViewById<TextView>(R.id.pageLabel)
        val slider = findViewById<Slider>(R.id.pageSlider)
        overlay = findViewById(R.id.balloonOverlay)
        balloonImage = findViewById(R.id.balloonImage)
        overlay.setOnClickListener { hideBalloon() }

        findViewById<TextView>(R.id.title).text = BookHolder.title
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        pager.adapter = PageAdapter(pages) { view, uri, sx, sy -> onTap(view, uri, sx, sy) }
        label.text = "1 / ${pages.size}"

        if (pages.size < 2) {
            slider.visibility = View.GONE
        } else {
            slider.valueFrom = 1f
            slider.valueTo = pages.size.toFloat()
            slider.value = 1f
            slider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) pager.setCurrentItem(value.toInt() - 1, false)
            }
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                label.text = "${position + 1} / ${pages.size}"
                if (pages.size >= 2) slider.value = (position + 1).toFloat()
            }
        })

        // Back fecha o overlay antes de sair
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (overlay.visibility == View.VISIBLE) hideBalloon()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    /** Toque numa página: tenta isolar o balão; se não achar, zoom no ponto. */
    private fun onTap(view: SubsamplingScaleImageView, uri: Uri, sx: Float, sy: Float) {
        val srcW = view.sWidth
        val srcH = view.sHeight
        lifecycleScope.launch {
            val rect: Rect? = withContext(Dispatchers.Default) {
                BalloonDetector.detect(contentResolver, uri, sx, sy, srcW, srcH)
            }
            val bmp: Bitmap? = if (rect != null) withContext(Dispatchers.IO) {
                BalloonDetector.crop(contentResolver, uri, rect)
            } else null

            if (bmp != null && rect != null) showBalloon(view, rect, bmp)
            else fallbackZoom(view, sx, sy)
        }
    }

    /** Posiciona o balão sobre onde ele está na página e infla ~2x no lugar. */
    private fun showBalloon(view: SubsamplingScaleImageView, rect: Rect, bmp: Bitmap) {
        val tl = view.sourceToViewCoord(rect.left.toFloat(), rect.top.toFloat())
        val br = view.sourceToViewCoord(rect.right.toFloat(), rect.bottom.toFloat())
        if (tl == null || br == null) { fallbackZoom(view, rect.exactCenterX(), rect.exactCenterY()); return }

        val lp = balloonImage.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.TOP or Gravity.START
        lp.width = (br.x - tl.x).toInt().coerceAtLeast(1)
        lp.height = (br.y - tl.y).toInt().coerceAtLeast(1)
        lp.leftMargin = tl.x.toInt()
        lp.topMargin = tl.y.toInt()
        balloonImage.layoutParams = lp

        balloonBmp?.recycle()
        balloonBmp = bmp
        balloonImage.setImageBitmap(bmp)
        balloonImage.scaleX = 1f
        balloonImage.scaleY = 1f

        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(120).start()
        // infla no centro do próprio balão (dobra de tamanho)
        balloonImage.animate().scaleX(2f).scaleY(2f)
            .setDuration(260).setInterpolator(OvershootInterpolator(1.4f)).start()
    }

    private fun hideBalloon() {
        balloonImage.animate().scaleX(1f).scaleY(1f).setDuration(150).withEndAction {
            overlay.visibility = View.GONE
            balloonImage.setImageDrawable(null)
            balloonBmp?.recycle()
            balloonBmp = null
        }.start()
        overlay.animate().alpha(0f).setDuration(150).start()
    }

    /** Sem balão: zoom animado no ponto tocado (comportamento antigo). */
    private fun fallbackZoom(view: SubsamplingScaleImageView, sx: Float, sy: Float) {
        if (!view.isReady) return
        val fit = view.minScale
        val zoomedIn = view.scale > fit * 1.6f
        val target = if (zoomedIn) fit else fit * 2.8f
        view.animateScaleAndCenter(target, PointF(sx, sy))?.withDuration(280)?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        balloonBmp?.recycle()
        balloonBmp = null
    }

    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private class PageAdapter(
        private val pages: List<Uri>,
        private val onTap: (SubsamplingScaleImageView, Uri, Float, Float) -> Unit
    ) : RecyclerView.Adapter<PageAdapter.VH>() {

        class VH(val image: SubsamplingScaleImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_page, parent, false) as SubsamplingScaleImageView
            attachTap(v)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.image.recycle()
            holder.image.tag = pages[position]
            holder.image.setImage(ImageSource.uri(pages[position]))
        }

        override fun getItemCount() = pages.size

        override fun onViewRecycled(holder: VH) {
            holder.image.recycle()
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun attachTap(view: SubsamplingScaleImageView) {
            view.setOnImageEventListener(object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    view.maxScale = view.minScale * 5f
                    view.setDoubleTapZoomScale(view.minScale * 2.8f)
                }
            })

            val detector = GestureDetector(view.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!view.isReady) return false
                        val p: PointF = view.viewToSourceCoord(e.x, e.y) ?: return false
                        val uri = view.tag as? Uri ?: return false
                        onTap(view, uri, p.x, p.y)
                        return true
                    }
                })

            view.setOnTouchListener { _, ev -> detector.onTouchEvent(ev); false }
        }
    }
}
