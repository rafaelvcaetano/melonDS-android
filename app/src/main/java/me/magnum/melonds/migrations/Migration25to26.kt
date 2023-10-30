package me.magnum.melonds.migrations

import me.magnum.melonds.impl.dtos.layout.LayoutConfigurationDto
import me.magnum.melonds.impl.dtos.layout.PositionedLayoutComponentDto
import me.magnum.melonds.impl.dtos.layout.RectDto
import me.magnum.melonds.impl.dtos.layout.UILayoutDto
import me.magnum.melonds.migrations.helper.GenericJsonArrayMigrationHelper
import me.magnum.melonds.migrations.legacy.layout.LayoutConfiguration25
import me.magnum.melonds.migrations.legacy.layout.UILayout25

class Migration25to26(
    private val layoutMigrationHelper: GenericJsonArrayMigrationHelper,
) : Migration {

    companion object {
        private const val LAYOUTS_DATA_FILE = "layouts.json"
    }

    override val from = 25
    override val to = 26

    override fun migrate() {
        layoutMigrationHelper.migrateJsonArrayData<LayoutConfiguration25, LayoutConfigurationDto>(LAYOUTS_DATA_FILE) {
            try {
                LayoutConfigurationDto(
                    it.id,
                    it.name,
                    it.type,
                    it.orientation,
                    it.useCustomOpacity,
                    it.opacity,
                    mapUiLayoutToNewDto(it.portraitLayout),
                    mapUiLayoutToNewDto(it.landscapeLayout),
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun mapUiLayoutToNewDto(uiLayout25: UILayout25): UILayoutDto {
        return UILayoutDto(
            uiLayout25.backgroundId,
            uiLayout25.backgroundMode,
            uiLayout25.components.map {
                PositionedLayoutComponentDto(
                    RectDto(it.rect.x, it.rect.y, it.rect.width, it.rect.height),
                    it.component,
                )
            }
        )
    }
}