package me.magnum.melonds.ui.backgrounds

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapRegionDecoder
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.common.contracts.FilePickerContract
import me.magnum.melonds.databinding.ActivityBackgroundsBinding
import me.magnum.melonds.databinding.DialogTextInputBinding
import me.magnum.melonds.databinding.ItemBackgroundBinding
import me.magnum.melonds.domain.model.Background
import me.magnum.melonds.domain.model.Orientation
import me.magnum.melonds.impl.BackgroundThumbnailProvider
import me.magnum.melonds.parcelables.BackgroundParcelable
import me.magnum.melonds.ui.backgroundpreview.BackgroundPreviewActivity
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class BackgroundsActivity : AppCompatActivity() {
    companion object {
        const val KEY_INITIAL_BACKGROUND_ID = "initial_background_id"
        const val KEY_ORIENTATION_FILTER = "orientation_filter"
        const val KEY_SELECTED_BACKGROUND_ID = "selected_background_id"
    }

    @Inject
    lateinit var picasso: Picasso
    @Inject
    lateinit var backgroundThumbnailProvider: BackgroundThumbnailProvider

    private val viewModel: BackgroundsViewModel by viewModels()
    private lateinit var binding: ActivityBackgroundsBinding
    private lateinit var backgroundListAdapter: BackgroundAdapter

    private val backgroundPicker = registerForActivityResult(FilePickerContract(false)) {
        it?.let { addBackground(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackgroundsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val backgroundInteractionListener = object : BackgroundInteractionListener {
            override fun onBackgroundSelected(background: Background?) {
                this@BackgroundsActivity.onBackgroundSelected(background)
            }

            override fun onBackgroundPreview(background: Background, view: ImageView) {
                this@BackgroundsActivity.onBackgroundPreview(background, view)
            }

            override fun onBackgroundDelete(background: Background) {
                this@BackgroundsActivity.onBackgroundDelete(background)
            }
        }
        backgroundListAdapter = BackgroundAdapter(backgroundThumbnailProvider, backgroundInteractionListener)

        binding.progressBarCheats.isVisible = true
        binding.listBackgrounds.apply {
            layoutManager = FlexboxLayoutManager(context, FlexDirection.ROW).apply {
                justifyContent = JustifyContent.SPACE_AROUND
                alignItems = AlignItems.FLEX_START
                adapter = backgroundListAdapter
            }
        }

        viewModel.getBackgrounds().observe(this) {
            binding.progressBarCheats.isGone = true
            backgroundListAdapter.setBackgrounds(it)
        }
        viewModel.getSelectedBackgroundId().observe(this) {
            backgroundListAdapter.setSelectedBackgroundId(it)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.backgrounds_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
            }
            R.id.action_backgrounds_add -> {
                backgroundPicker.launch(Pair(null, arrayOf("image/png", "image/jpeg")))
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun addBackground(uri: Uri) {
        val documentName = DocumentFile.fromSingleUri(this, uri)?.name?.substringBeforeLast('.') ?: ""
        val binding = DialogTextInputBinding.inflate(layoutInflater)
        binding.editText.setText(documentName, TextView.BufferType.NORMAL)

        AlertDialog.Builder(this)
                .setTitle(R.string.background_name)
                .setView(binding.root)
                .setPositiveButton(R.string.ok) { _, _ ->
                    var orientation = Orientation.PORTRAIT
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                    // Only persist URI permission after making sure the user has finalized the process
                    contentResolver.takePersistableUriPermission(uri, flags)
                    contentResolver.openInputStream(uri)?.let {
                        val decoder = BitmapRegionDecoder.newInstance(it, true)
                        val width = decoder.width
                        val height = decoder.height

                        if (width > height) {
                            orientation = Orientation.LANDSCAPE
                        }
                        decoder.recycle()
                    }

                    val backgroundName = binding.editText.text.toString()
                    val background = Background(null, backgroundName, orientation, uri)
                    viewModel.addBackground(background)

                    if (orientation != viewModel.getCurrentOrientationFilter()) {
                        Toast.makeText(this, R.string.background_add_wrong_orientation, Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun onBackgroundSelected(background: Background?) {
        val intent = Intent().apply {
            putExtra(KEY_SELECTED_BACKGROUND_ID, background?.id?.toString())
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun onBackgroundPreview(background: Background, view: ImageView) {
        val intent = Intent(this, BackgroundPreviewActivity::class.java).apply {
            putExtra(BackgroundPreviewActivity.KEY_BACKGROUND, BackgroundParcelable(background))
        }
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, view, BackgroundPreviewActivity.KEY_TRANSITION_IMAGE)
        startActivity(intent, options.toBundle())
    }

    private fun onBackgroundDelete(background: Background) {
        viewModel.deleteBackground(background)
    }

    private class BackgroundAdapter(
            private val backgroundThumbnailProvider: BackgroundThumbnailProvider,
            private val backgroundInteractionListener: BackgroundInteractionListener
    ) : RecyclerView.Adapter<BackgroundAdapter.ViewHolder>() {

        class ViewHolder(private val binding: ItemBackgroundBinding, private val backgroundThumbnailProvider: BackgroundThumbnailProvider) : RecyclerView.ViewHolder(binding.root) {
            private var background: Background? = null

            fun setBackground(background: Background?, selected: Boolean) {
                this.background = background
                if (background != null) {
                    val thumbnail = backgroundThumbnailProvider.getBackgroundThumbnail(background)
                    binding.imageBackgroundPreview.setImageBitmap(thumbnail)
                    binding.textBackgroundName.text = background.name
                    binding.buttonOptions.isVisible = true
                } else {
                    binding.imageBackgroundPreview.setImageResource(R.drawable.ic_block)
                    binding.imageBackgroundPreview.setColorFilter(ContextCompat.getColor(itemView.context, R.color.buttonContrasted), PorterDuff.Mode.SRC_IN)
                    binding.textBackgroundName.setText(R.string.none)
                    binding.buttonOptions.isInvisible = true
                }

                if (selected) {
                    binding.cardBackground.foreground = ContextCompat.getDrawable(itemView.context, R.drawable.background_background_selected)
                } else {
                    binding.cardBackground.foreground = null
                }
            }

            fun getBackground() = background
        }

        private val backgrounds = mutableListOf<Background?>()
        private var selectedBackgroundId: UUID? = null

        fun setBackgrounds(newBackgrounds: List<Background?>) {
            // Insert option for "None"
            val newFullList = listOf(null, *newBackgrounds.toTypedArray())
            val result = DiffUtil.calculateDiff(BackgroundsDiffUtilCallback(backgrounds, newFullList))
            result.dispatchUpdatesTo(this)

            backgrounds.clear()
            backgrounds.addAll(newFullList)
        }

        fun setSelectedBackgroundId(id: UUID?) {
            if (id == selectedBackgroundId) {
                return
            }

            val previousLayoutIndex = backgrounds.indexOfFirst { it?.id == selectedBackgroundId }
            val newLayoutIndex = backgrounds.indexOfFirst { it?.id == id }
            selectedBackgroundId = id

            if (previousLayoutIndex >= 0) {
                notifyItemChanged(previousLayoutIndex)
            }
            if (newLayoutIndex >= 0) {
                notifyItemChanged(newLayoutIndex)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemBackgroundBinding.inflate(inflater)
            val holder = ViewHolder(binding, backgroundThumbnailProvider)
            binding.containerBackground.setOnClickListener {
                backgroundInteractionListener.onBackgroundSelected(holder.getBackground())
            }
            binding.buttonOptions.setOnClickListener {
                val popup = PopupMenu(parent.context, binding.buttonOptions)
                popup.menuInflater.inflate(R.menu.background_item_menu, popup.menu)
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.action_background_preview -> backgroundInteractionListener.onBackgroundPreview(holder.getBackground()!!, binding.imageBackgroundPreview)
                        R.id.action_background_delete -> backgroundInteractionListener.onBackgroundDelete(holder.getBackground()!!)
                        else -> return@setOnMenuItemClickListener false
                    }
                    return@setOnMenuItemClickListener true
                }
                popup.show()
            }
            return holder
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val background = backgrounds[position]
            val isSelected = background?.id == selectedBackgroundId
            holder.setBackground(background, isSelected)
        }

        override fun getItemCount(): Int {
            return backgrounds.size
        }

        class BackgroundsDiffUtilCallback(private val oldBackgrounds: List<Background?>, private val newBackgrounds: List<Background?>) : DiffUtil.Callback() {
            override fun getOldListSize() = oldBackgrounds.size

            override fun getNewListSize() = newBackgrounds.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldBackground = oldBackgrounds[oldItemPosition]
                val newBackground = newBackgrounds[newItemPosition]

                return oldBackground?.id == newBackground?.id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldBackground = oldBackgrounds[oldItemPosition]
                val newBackground = newBackgrounds[newItemPosition]

                return oldBackground == newBackground
            }
        }
    }

    private interface BackgroundInteractionListener {
        fun onBackgroundSelected(background: Background?)
        fun onBackgroundPreview(background: Background, view: ImageView)
        fun onBackgroundDelete(background: Background)
    }
}