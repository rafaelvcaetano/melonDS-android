package me.magnum.melonds.impl.dtos.layout

import com.google.gson.annotations.SerializedName
import me.magnum.melonds.domain.model.layout.UILayout

data class UILayoutDto(
    @SerializedName("mainScreenLayoutDto")
    val mainScreenLayout: ScreenLayoutDto,
    @SerializedName("secondaryScreenLayoutDto")
    val secondaryScreenLayout: ScreenLayoutDto,
) {

    companion object {
        fun fromModel(uiLayout: UILayout): UILayoutDto {
            return UILayoutDto(
                mainScreenLayout = ScreenLayoutDto.fromModel(uiLayout.mainScreenLayout),
                secondaryScreenLayout = ScreenLayoutDto.fromModel(uiLayout.secondaryScreenLayout),
            )
        }
    }

    fun toModel(): UILayout {
        return UILayout(
            mainScreenLayout = mainScreenLayout.toModel(),
            secondaryScreenLayout = secondaryScreenLayout.toModel(),
        )
    }
}