package me.magnum.melonds.ui.inputsetup.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import me.magnum.melonds.R
import me.magnum.melonds.ui.theme.MelonTheme

@Composable
fun InputSetupScreen2() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InputSlot("Left", "Not set")
                InputSlot("Up", "Not set")
                InputSlot("Right", "Not set")
                InputSlot("Down", "Not set")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InputSlot("A", "Not set")
                InputSlot("B", "Not set")
                InputSlot("X", "Not set")
                InputSlot("Y", "Not set")
            }
        }
        Image(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).wrapContentHeight(),
            painter = painterResource(id = R.drawable.ds_lite),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                modifier = Modifier.widthIn(max = 100.dp),
                value = "Not set",
                onValueChange = { },
                label = {
                    Text("START")
                },
            )
            OutlinedTextField(
                modifier = Modifier.widthIn(max = 100.dp),
                value = "Not set",
                onValueChange = { },
                label = {
                    Text("SELECT")
                },
            )
        }
    }
}

@Composable
private fun InputSlot(
    label: String,
    keyText: String,
) {
    var labelSize = Size(0f, 0f)

    Layout(
        content = {
            Box(
                modifier = Modifier.layoutId(LAYOUT_OUTLINE)
                    .drawWithContent {
                        val labelPadding = 4.dp.toPx()
                        val startPadding = 16.dp.toPx()
                        clipRect(
                            left = startPadding - labelPadding,
                            right = startPadding + labelSize.width + labelPadding,
                            top = 0f,
                            bottom = labelSize.height,
                            clipOp = ClipOp.Difference,
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    }
            ) {
                Box(Modifier.fillMaxSize().border(2.dp, MaterialTheme.colors.secondary, MaterialTheme.shapes.small))
            }
            Text(
                modifier = Modifier.layoutId(LAYOUT_LABEL),
                text = label,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface,
            )
            Text(
                modifier = Modifier.layoutId(LAYOUT_KEY),
                text = keyText,
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface,
            )
        },
        measurePolicy = { measurables, constraints ->
            val horizontalPadding = 16.dp.roundToPx()
            val verticalPadding = 8.dp.roundToPx()
            val labelPlaceable = measurables.first { it.layoutId == LAYOUT_LABEL }.measure(constraints)
            val keyTextPlaceable = measurables.first { it.layoutId == LAYOUT_KEY }.measure(constraints)

            val width = keyTextPlaceable.width + horizontalPadding * 2
            val height = keyTextPlaceable.height + verticalPadding * 2 + labelPlaceable.height / 2
            labelSize = Size(labelPlaceable.width.toFloat(), labelPlaceable.height.toFloat())

            val outlineWidth = width
            val outlineHeight = height - labelPlaceable.height / 2
            val outlineConstraints = Constraints(
                minWidth = outlineWidth,
                maxWidth = outlineWidth,
                minHeight = outlineHeight,
                maxHeight = outlineHeight,
            )
            val outline = measurables.first { it.layoutId == LAYOUT_OUTLINE }.measure(outlineConstraints)

            layout(width, height) {
                outline.place(0, labelPlaceable.height / 2)
                labelPlaceable.place(horizontalPadding, 0)
                keyTextPlaceable.place(horizontalPadding, labelPlaceable.height / 2 + verticalPadding)
            }
        }
    )
}

private const val LAYOUT_LABEL = 0
private const val LAYOUT_KEY = 1
private const val LAYOUT_OUTLINE = 2

@Preview(showBackground = true)
@Composable
private fun PreviewInputSetupScreen() {
    MelonTheme {
        InputSetupScreen2()
    }
}