package com.phuuun.legato.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuuun.legato.ui.theme.Bold
import com.phuuun.legato.ui.theme.Motion
import com.phuuun.legato.ui.theme.enterFrom
import com.phuuun.legato.ui.theme.pressScale
import com.phuuun.legato.ui.theme.tap
import kotlinx.coroutines.launch
import kotlin.math.abs

// Tsundoku's Bold controls. No ripples anywhere: the press scale, or a sinking shadow, is the feedback.

internal val FieldShape = RoundedCornerShape(14.dp)
private val DialogShape = RoundedCornerShape(20.dp)

/** Text field colours with every border and container off: [fieldFrame] draws them. */
@Composable
internal fun bareFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
    errorBorderColor = Color.Transparent, disabledBorderColor = Color.Transparent,
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
    focusedLabelColor = Bold.Ink, cursorColor = Bold.Ink,
)

/** Surface fill with a 2dp outline that fades Quiet line to Ink when [focused] (Error when [error]), no shadow. */
@Composable
internal fun Modifier.fieldFrame(focused: Boolean, error: Boolean = false): Modifier {
    val line by animateColorAsState(if (error) Bold.Error else if (focused) Bold.Ink else Bold.QuietLine, Motion.settle(), label = "field line")
    return background(Bold.Surface, FieldShape)
        .border(2.dp, line, FieldShape)
}

/** Material buttons draw a ripple from this; ours give press feedback with [pressScale] or a sinking shadow instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoRipple(content: @Composable () -> Unit) = CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)

/** The primary button: Mint with a Paper outline; shrinks a little when pressed. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val active = enabled && !loading
    val fill by animateColorAsState(if (active) Bold.Mint else Bold.Mint.copy(alpha = 0.3f), Motion.settle(), label = "pill fill")
    NoRipple {
        Button(
            onClick = onClick,
            enabled = active,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = Bold.DarkInk, disabledContainerColor = fill, disabledContentColor = Bold.DarkInk),
            border = BorderStroke(2.dp, Bold.Paper),
            contentPadding = PaddingValues(horizontal = 28.dp),
            interactionSource = source,
            modifier = modifier.height(52.dp).pressScale(pressed),
        ) {
            AnimatedContent(loading, contentAlignment = Alignment.Center, label = "pill") { busy ->
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Bold.DarkInk, strokeWidth = 2.dp)
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon != null) Icon(icon, contentDescription = null, Modifier.size(20.dp))
                    Text(text, fontSize = 15.sp, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The secondary button: transparent with a 2dp Ink outline. */
@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val line by animateColorAsState(if (enabled) Bold.Ink else Bold.Ink.copy(alpha = 0.3f), Motion.settle(), label = "outline")
    NoRipple {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            border = BorderStroke(2.dp, line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Bold.Ink),
            contentPadding = PaddingValues(horizontal = 24.dp),
            interactionSource = source,
            modifier = modifier.height(52.dp).pressScale(pressed),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, Modifier.padding(end = 8.dp).size(20.dp))
            Text(text, fontSize = 15.sp, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A small outlined pill for lesser actions in a row: Retry, Edit order, Change cover. Quiet line when [enabled] is off. */
@Composable
fun SmallPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val line by animateColorAsState(if (enabled) Bold.Ink else Bold.QuietLine, Motion.settle(), label = "small pill")
    Row(
        modifier.tap(onClick, enabled = enabled, role = Role.Button).clip(CircleShape).border(2.dp, line, CircleShape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val color = if (enabled) Bold.Ink else Bold.Muted
        if (icon != null) Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = color)
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A round 40dp outline around an icon, like Tsundoku's header buttons; [filled] makes it Ink with a Paper icon. */
@Composable
fun RoundButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier.size(40.dp).semantics { contentDescription = label }.tap(onClick, role = Role.Button).clip(CircleShape)
            .then(if (filled) Modifier.background(Bold.Ink) else Modifier.border(2.dp, Bold.Ink, CircleShape)),
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalContentColor provides if (filled) Bold.Paper else Bold.Ink, content = content) }
}

/** IconButton's place, without the ripple: a 48dp target that shrinks a little when pressed. */
@Composable
fun TapIcon(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier.size(48.dp).tap(onClick, enabled = enabled, role = Role.Button, to = 0.85f).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalContentColor provides if (enabled) LocalContentColor.current else Bold.QuietLine, content = content) }
}

/** A small pill in [color] with a Dark ink label: FINISHED, a count. */
@Composable
fun StatusSticker(text: String, color: Color, modifier: Modifier = Modifier) =
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = Bold.DarkInk,
        modifier = modifier.background(color, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
    )

/** Ink fill with Paper text when [chosen]; otherwise a Quiet line outline. */
@Composable
fun ChoicePill(text: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(if (chosen) Bold.Ink else Color.Transparent, Motion.settle(), label = "choice fill")
    val line by animateColorAsState(if (chosen) Bold.Ink else Bold.QuietLine, Motion.settle(), label = "choice line")
    val label by animateColorAsState(if (chosen) Bold.Paper else Bold.Ink, Motion.settle(), label = "choice label")
    Box(
        modifier.tap(onClick, role = Role.RadioButton).clip(CircleShape).background(fill).border(2.dp, line, CircleShape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = label, maxLines = 1) }
}

/**
 * Chips side by side with an Ink fill sliding under the chosen one; [position] runs from 0 (the first) to the last
 * index, so it can follow a pager mid-swipe. [item] draws chip i in the colour it should be right now.
 */
@Composable
fun PillSwitch(count: Int, position: Float, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, item: @Composable RowScope.(i: Int, color: Color) -> Unit) {
    val gap = 8.dp
    BoxWithConstraints(modifier.height(44.dp)) {
        val chip = (maxWidth - gap * (count - 1)) / count
        Box(
            Modifier.width(chip).fillMaxHeight()
                .graphicsLayer { translationX = (chip + gap).toPx() * position }
                .background(Bold.Ink, CircleShape)
        )
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(count) { i ->
                val selected = 1 - abs(position - i).coerceIn(0f, 1f)
                Row(
                    Modifier.weight(1f).fillMaxHeight().tap({ onSelect(i) }, role = Role.Tab).clip(CircleShape).border(2.dp, Bold.Ink, CircleShape),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) { item(i, lerp(Bold.Muted, Bold.Paper, selected)) }
            }
        }
    }
}

@Composable
fun PillSwitch(options: List<String>, position: Float, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) =
    PillSwitch(options.size, position, onSelect, modifier) { i, color ->
        Text(options[i], fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }

/**
 * A bottom sheet: Surface, 28dp top corners, an Ink drag handle, then [content]. Content gets a `close` that slides the
 * sheet down and then calls [onDismiss]; touches are swallowed while it goes, so a second tap can't repeat an action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val close = {
        if (!closing) {
            closing = true
            scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Bold.Surface,
        contentColor = Bold.Ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        // Drawn inside: Material's handle slot wraps it in a focusable box that turns grey once a text field goes away.
        dragHandle = null,
    ) {
        Box {
            Column {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.padding(top = 12.dp, bottom = 16.dp).size(width = 36.dp, height = 4.dp).background(Bold.Ink, CircleShape))
                }
                content(close)
            }
            if (closing) Box(Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            })
        }
    }
}

/** AlertDialog in the Bold frame: Surface high, a 2dp Ink outline, settling in; its text buttons are Ink, not Mint. */
@Composable
fun BoldDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) = MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Bold.Ink)) {
    AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, dismissButton = dismissButton, title = title, text = text,
        modifier = Modifier.enterFrom().border(2.dp, Bold.Ink, DialogShape),
        shape = DialogShape,
        containerColor = Bold.SurfaceHigh,
    )
}

/** DropdownMenu in the Bold frame. */
@Composable
fun BoldMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) =
    DropdownMenu(expanded, onDismissRequest, shape = DialogShape, containerColor = Bold.SurfaceHigh, border = BorderStroke(2.dp, Bold.Ink), content = content)

/** Undo snackbars: Surface high with an Ink outline and a Mint action. */
@Composable
fun BoldSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) =
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            data, Modifier.padding(12.dp).enterFrom(offsetY = 12.dp).border(2.dp, Bold.Ink, DialogShape),
            shape = DialogShape, containerColor = Bold.SurfaceHigh, contentColor = Bold.Ink, actionColor = Bold.Mint,
        )
    }

/** Seek and EQ sliders: Mint track, Ink thumb. */
@Composable
fun boldSliderColors() = SliderDefaults.colors(thumbColor = Bold.Ink, activeTrackColor = Bold.Mint, inactiveTrackColor = Bold.QuietLine, disabledThumbColor = Bold.QuietLine, disabledActiveTrackColor = Bold.QuietLine)

/** Filters as you type; the keyboard's search key just puts the keyboard away. Ink outline with a hard shadow. */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Search", color = Bold.Placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Bold.Ink, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (query.isEmpty()) null else {
            { TapIcon(onClick = { onQuery(""); focusManager.clearFocus() }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = Bold.Ink) } }
        },
        singleLine = true,
        shape = FieldShape,
        colors = bareFieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier.fillMaxWidth()
            .background(Bold.Surface, FieldShape)
            .border(2.dp, Bold.Ink, FieldShape),
    )
}
