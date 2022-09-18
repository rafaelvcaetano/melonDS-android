package me.magnum.melonds.ui.backgroundpreview

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityBackgroundPreviewBinding
import me.magnum.melonds.extensions.insetsControllerCompat
import me.magnum.melonds.extensions.parcelable
import me.magnum.melonds.impl.BackgroundThumbnailProvider
import me.magnum.melonds.parcelables.BackgroundParcelable
import java.lang.Exception
import javax.inject.Inject

@AndroidEntryPoint
class BackgroundPreviewActivity : AppCompatActivity() {
    companion object {
        const val KEY_BACKGROUND = "background"

        const val KEY_TRANSITION_IMAGE = "image"
    }

    @Inject
    lateinit var picasso: Picasso
    @Inject
    lateinit var backgroundThumbnailProvider: BackgroundThumbnailProvider

    private lateinit var binding: ActivityBackgroundPreviewBinding
    private var isDecorVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackgroundPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        binding.image.setOnClickListener {
            toggleNavigationVisibility()
        }
    }

    override fun onStart() {
        super.onStart()
        val background = intent.parcelable<BackgroundParcelable>(KEY_BACKGROUND)?.background ?: throw NullPointerException("No background provided")
        val thumbnail = backgroundThumbnailProvider.getBackgroundThumbnail(background)
        picasso.load(background.uri).placeholder(BitmapDrawable(resources, thumbnail)).noFade().into(binding.image, object : Callback {
            override fun onSuccess() {
            }

            override fun onError(e: Exception?) {
                e?.printStackTrace()
                Toast.makeText(this@BackgroundPreviewActivity, R.string.layout_background_load_failed, Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> supportFinishAfterTransition()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * Toggles the visibility of window navigation components (toolbar and bottom navigation bar).
     */
    private fun toggleNavigationVisibility() {
        if (isDecorVisible) {
            window.insetsControllerCompat?.apply {
                hide(WindowInsetsCompat.Type.navigationBars())
            }
            val toolbarAnimation = TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, -1f).apply {
                fillAfter = true
                duration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
            }
            binding.toolbar.startAnimation(toolbarAnimation)
        } else {
            window.insetsControllerCompat?.apply {
                show(WindowInsetsCompat.Type.navigationBars())
            }
            val toolbarAnimation = TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, -1f, Animation.RELATIVE_TO_SELF, 0f).apply {
                fillAfter = true
                duration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
            }
            binding.toolbar.startAnimation(toolbarAnimation)
        }
        isDecorVisible = !isDecorVisible
    }

    override fun onStop() {
        super.onStop()
        picasso.cancelRequest(binding.image)
    }
}