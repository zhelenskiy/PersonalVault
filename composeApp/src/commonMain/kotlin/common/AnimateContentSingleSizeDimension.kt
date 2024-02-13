package common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


// copied from androidx.compose.animation.animateContentSize and slightly modified

/**
 * This modifier animates its own height when its child modifier (or the child composable if it
 * is already at the tail of the chain) changes height. This allows the parent modifier to observe
 * a smooth height change, resulting in an overall continuous visual change.
 *
 * A [FiniteAnimationSpec] can be optionally specified for the height change animation. By default,
 * [spring] will be used.
 *
 * An optional [finishedListener] can be supplied to get notified when the height change animation is
 * finished. Since the content height change can be dynamic in many cases, both initial value and
 * target value (i.e. final height) will be passed to the [finishedListener]. __Note:__ if the
 * animation is interrupted, the initial value will be the height at the point of interruption. This
 * is intended to help determine the direction of the height change (i.e. expand or collapse in x and
 * y dimensions).
 *
 * @param animationSpec a finite animation that will be used to animate height change, [spring] by
 *                      default
 * @param finishedListener an optional listener to be called when the content change animation is
 *                         completed.
 */

fun Modifier.animateContentHeight(
    animationSpec: FiniteAnimationSpec<Int> = spring(),
    finishedListener: ((initialValue: Int, targetValue: Int) -> Unit)? = null
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "animateContentHeight"
        properties["animationSpec"] = animationSpec
        properties["finishedListener"] = finishedListener
    }
) {
    // TODO: Listener could be a fun interface after 1.4
    val scope = rememberCoroutineScope()
    val animModifier = remember(scope) {
        IntAnimationModifier(animationSpec, scope, AnimationDimension.Height)
    }
    animModifier.listener = finishedListener
    this.clipToBounds().then(animModifier)
}

/**
 * This modifier animates its own width when its child modifier (or the child composable if it
 * is already at the tail of the chain) changes width. This allows the parent modifier to observe
 * a smooth width change, resulting in an overall continuous visual change.
 *
 * A [FiniteAnimationSpec] can be optionally specified for the width change animation. By default,
 * [spring] will be used.
 *
 * An optional [finishedListener] can be supplied to get notified when the width change animation is
 * finished. Since the content width change can be dynamic in many cases, both initial value and
 * target value (i.e. final s=width) will be passed to the [finishedListener]. __Note:__ if the
 * animation is interrupted, the initial value will be the width at the point of interruption. This
 * is intended to help determine the direction of the width change (i.e. expand or collapse in x and
 * y dimensions).
 *
 * @param animationSpec a finite animation that will be used to animate width change, [spring] by
 *                      default
 * @param finishedListener an optional listener to be called when the content change animation is
 *                         completed.
 */
fun Modifier.animateContentWidth(
    animationSpec: FiniteAnimationSpec<Int> = spring(),
    finishedListener: ((initialValue: Int, targetValue: Int) -> Unit)? = null
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "animateContentWidth"
        properties["animationSpec"] = animationSpec
        properties["finishedListener"] = finishedListener
    }
) {
    // TODO: Listener could be a fun interface after 1.4
    val scope = rememberCoroutineScope()
    val animModifier = remember(scope) {
        IntAnimationModifier(animationSpec, scope, AnimationDimension.Width)
    }
    animModifier.listener = finishedListener
    this.clipToBounds().then(animModifier)
}

private enum class AnimationDimension {
    Width, Height
}

/**
 * This class creates a [LayoutModifier] that measures children, and responds to children's size dimension
 * change by animating to that size dimension. The size dimension reported to parents will be the animated size dimension.
 */
private class IntAnimationModifier(
    val animSpec: AnimationSpec<Int>,
    val scope: CoroutineScope,
    private val animationDirection: AnimationDimension,
) : LayoutModifierWithPassThroughIntrinsics() {
    var listener: ((startSizeDimension: Int, endSizeDimension: Int) -> Unit)? = null

    data class AnimData(
        val anim: Animatable<Int, AnimationVector1D>,
        var startSizeDimension: Int
    )

    var animData: AnimData? by mutableStateOf(null)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {

        val placeable = measurable.measure(constraints)

        val measuredSize = IntSize(placeable.width, placeable.height)

        val (width, height) = when (animationDirection) {
            AnimationDimension.Width -> animateTo(measuredSize.width) to measuredSize.height
            AnimationDimension.Height -> measuredSize.width to animateTo(measuredSize.height)
        }
        return layout(width, height) {
            placeable.placeRelative(0, 0)
        }
    }

    fun animateTo(targetSizeDimension: Int): Int {
        val data = animData?.apply {
            if (targetSizeDimension != anim.targetValue) {
                startSizeDimension = anim.value
                scope.launch {
                    val result = anim.animateTo(targetSizeDimension, animSpec)
                    if (result.endReason == AnimationEndReason.Finished) {
                        listener?.invoke(startSizeDimension, result.endState.value)
                    }
                }
            }
        } ?: AnimData(
            Animatable(
                targetSizeDimension, Int.VectorConverter, 1
            ),
            targetSizeDimension
        )

        animData = data
        return data.anim.value
    }
}

internal abstract class LayoutModifierWithPassThroughIntrinsics : LayoutModifier {
    final override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ) = measurable.minIntrinsicWidth(height)

    final override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ) = measurable.minIntrinsicHeight(width)

    final override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ) = measurable.maxIntrinsicWidth(height)

    final override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ) = measurable.maxIntrinsicHeight(width)
}
