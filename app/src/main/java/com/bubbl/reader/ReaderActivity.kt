package com.bubbl.reader

import android.annotation.SuppressLint
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.slider.Slider

class ReaderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)
        goImmersive()

        val pages = BookHolder.pages
        if (pages.isEmpty()) { finish(); return }

        val pager = findViewById<ViewPager2>(R.id.pager)
        val label = findViewById<TextView>(R.id.pageLabel)
        val slider = findViewById<Slider>(R.id.pageSlider)
        findViewById<TextView>(R.id.title).text = BookHolder.title
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        pager.adapter = PageAdapter(pages)
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
    }

    /** Esconde barras de sistema; reaparecem com swipe (transient). */
    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private class PageAdapter(
        private val pages: List<Uri>
    ) : RecyclerView.Adapter<PageAdapter.VH>() {

        class VH(val image: SubsamplingScaleImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_page, parent, false) as SubsamplingScaleImageView
            attachTapToZoom(v)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.image.recycle()
            holder.image.setImage(ImageSource.uri(pages[position]))
        }

        override fun getItemCount() = pages.size

        override fun onViewRecycled(holder: VH) {
            holder.image.recycle()
        }

        /** Tap único = zoom automático ancorado no ponto tocado (o "balão"). */
        @SuppressLint("ClickableViewAccessibility")
        private fun attachTapToZoom(view: SubsamplingScaleImageView) {
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
                        val fit = view.minScale
                        val zoomedIn = view.scale > fit * 1.6f
                        val target = if (zoomedIn) fit else fit * 2.8f
                        val center: PointF = view.viewToSourceCoord(e.x, e.y) ?: return false
                        view.animateScaleAndCenter(target, center)?.withDuration(280)?.start()
                        return true
                    }
                })

            view.setOnTouchListener { _, ev -> detector.onTouchEvent(ev); false }
        }
    }
}
