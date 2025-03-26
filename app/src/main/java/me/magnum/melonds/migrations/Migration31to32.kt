package me.magnum.melonds.migrations

import android.content.Context
import me.magnum.melonds.domain.model.Point
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.impl.dtos.layout.LayoutConfigurationDto
import me.magnum.melonds.impl.dtos.layout.PointDto
import me.magnum.melonds.impl.dtos.layout.UILayoutVariantDto
import me.magnum.melonds.migrations.helper.GenericJsonArrayMigrationHelper
import me.magnum.melonds.migrations.legacy.layout.LayoutConfigurationDto31
import me.magnum.melonds.utils.WindowManagerCompat

class Migration31to32(
    private val context: Context,
    private val layoutMigrationHelper: GenericJsonArrayMigrationHelper,
) : Migration {

    companion object {
        private const val LAYOUTS_DATA_FILE = "layouts.json"
    }

    override val from = 31
    override val to = 32

    override fun migrate() {
        val windowSize = WindowManagerCompat.getWindowSize(context)
        val (portraitSize, landscapeSize) = if (windowSize.x > windowSize.y) {
            Point(windowSize.y, windowSize.x) to Point(windowSize.x, windowSize.y)
        } else {
            Point(windowSize.x, windowSize.y) to Point(windowSize.y, windowSize.x)
        }

        layoutMigrationHelper.migrateJsonArrayData<LayoutConfigurationDto31, LayoutConfigurationDto>(LAYOUTS_DATA_FILE) {
            try {
                LayoutConfigurationDto(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    orientation = it.orientation,
                    useCustomOpacity = it.useCustomOpacity,
                    opacity = it.opacity,
                    layoutVariants = listOf(
                        LayoutConfigurationDto.LayoutEntryDto(
                            variant = UILayoutVariantDto(
                                uiSize = PointDto.fromModel(portraitSize),
                                orientation = Orientation.PORTRAIT.name,
                                folds = emptyList(),
                            ),
                            layout = it.portraitLayout,
                        ),
                        LayoutConfigurationDto.LayoutEntryDto(
                            variant = UILayoutVariantDto(
                                uiSize = PointDto.fromModel(landscapeSize),
                                orientation = Orientation.LANDSCAPE.name,
                                folds = emptyList(),
                            ),
                            layout = it.landscapeLayout,
                        ),
                    )
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}