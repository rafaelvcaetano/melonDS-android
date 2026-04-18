package me.magnum.melonds.migrations

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.core.content.getSystemService
import me.magnum.melonds.domain.model.layout.BackgroundMode
import me.magnum.melonds.domain.model.layout.LayoutDisplay
import me.magnum.melonds.impl.dtos.layout.LayoutConfigurationDto
import me.magnum.melonds.impl.dtos.layout.LayoutDisplayDto
import me.magnum.melonds.impl.dtos.layout.LayoutDisplayPairDto
import me.magnum.melonds.impl.dtos.layout.PositionedLayoutComponentDto
import me.magnum.melonds.impl.dtos.layout.RectDto
import me.magnum.melonds.impl.dtos.layout.ScreenLayoutDto
import me.magnum.melonds.impl.dtos.layout.UILayoutDto
import me.magnum.melonds.impl.dtos.layout.UILayoutVariantDto
import me.magnum.melonds.migrations.helper.GenericJsonArrayMigrationHelper
import me.magnum.melonds.migrations.legacy.layout.LayoutConfigurationDto35
import me.magnum.melonds.migrations.legacy.layout.UILayoutDto35
import me.magnum.melonds.migrations.legacy.layout.UILayoutVariantDto35

class Migration35to36(
    private val context: Context,
    private val layoutMigrationHelper: GenericJsonArrayMigrationHelper,
) : Migration {

    companion object {
        private const val LAYOUTS_DATA_FILE = "layouts.json"
    }

    override val from = 35
    override val to = 36

    override fun migrate() {
        layoutMigrationHelper.migrateJsonArrayData<LayoutConfigurationDto35, LayoutConfigurationDto>(LAYOUTS_DATA_FILE) {
            if (it.type == "EXTERNAL") {
                return@migrateJsonArrayData null
            }

            try {
                LayoutConfigurationDto(
                    it.id,
                    it.name,
                    it.type,
                    it.orientation,
                    it.useCustomOpacity,
                    it.opacity,
                    it.layoutVariants.map {
                        LayoutConfigurationDto.LayoutEntryDto(
                            variant = mapUiLayoutVariantToNewDto(it.variant) ?: return@migrateJsonArrayData null,
                            layout = mapUiLayoutToNewDto(it.layout),
                        )
                    },
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun mapUiLayoutVariantToNewDto(uiLayoutVariant35: UILayoutVariantDto35): UILayoutVariantDto? {
        // Assume that the default display was always used, which should be accurate for the vast majority of users
        val defaultDisplay = context.getSystemService<DisplayManager>()?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val displaySize = Point()
        defaultDisplay.getRealSize(displaySize)

        val mainLayoutDisplay = LayoutDisplayDto(
            id = defaultDisplay.displayId,
            type = LayoutDisplay.Type.BUILT_IN.name,
            width = displaySize.x,
            height = displaySize.y,
        )

        return UILayoutVariantDto(
            uiSize = uiLayoutVariant35.uiSize,
            orientation = uiLayoutVariant35.orientation,
            folds = uiLayoutVariant35.folds,
            displays = LayoutDisplayPairDto(mainLayoutDisplay, null),
        )
    }

    private fun mapUiLayoutToNewDto(uiLayout35: UILayoutDto35): UILayoutDto {
        return UILayoutDto(
            mainScreenLayout = ScreenLayoutDto(
                backgroundId = uiLayout35.backgroundId,
                backgroundMode = uiLayout35.backgroundMode,
                components = uiLayout35.components?.map {
                    PositionedLayoutComponentDto(
                        rect = RectDto(it.rect.x, it.rect.y, it.rect.width, it.rect.height),
                        component = it.component,
                    )
                },
            ),
            secondaryScreenLayout = ScreenLayoutDto(null, BackgroundMode.FIT_CENTER.name, null),
        )
    }
}