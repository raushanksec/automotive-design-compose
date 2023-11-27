/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.designcompose.squoosh

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontLoader
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.android.designcompose.AnimatedAction
import com.android.designcompose.ComponentReplacementContext
import com.android.designcompose.CustomizationContext
import com.android.designcompose.DesignComposeCallbacks
import com.android.designcompose.DocServer
import com.android.designcompose.DocumentSwitcher
import com.android.designcompose.InteractionStateManager
import com.android.designcompose.LayoutManager
import com.android.designcompose.LiveUpdateMode
import com.android.designcompose.ParentLayoutInfo
import com.android.designcompose.clonedWithAnimatedActionsApplied
import com.android.designcompose.common.DocumentServerParams
import com.android.designcompose.doc
import com.android.designcompose.rootNode
import com.android.designcompose.serdegen.Layout
import com.android.designcompose.serdegen.LayoutNode
import com.android.designcompose.serdegen.LayoutNodeList
import com.android.designcompose.serdegen.LayoutParentChildren
import com.android.designcompose.serdegen.NodeQuery
import com.android.designcompose.squooshAnimatedActions
import com.android.designcompose.squooshCompleteAnimatedAction
import com.android.designcompose.squooshVariantMemory
import com.android.designcompose.stateForDoc
import kotlin.math.roundToInt

const val TAG: String = "DC_SQUOOSH"

// We want to provide the node to a Compose layout customization, and we do that using
// the ParentDataModifier.
private data class SquooshParentData(val node: SquooshResolvedNode) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any { return this@SquooshParentData }
}

// We need to share some information on animations with the `squooshRender` Modifier so
// that it can apply the correct values.
internal class SquooshAnimationRenderingInfo(
    var control: SquooshAnimationControl,
    val animation: TargetBasedAnimation<Float, AnimationVector1D>,
    val action: AnimatedAction?,
    val variant: VariantAnimationInfo?,
    var startTimeNanos: Long = 0L,
)

// Experiment -- minimal DesignCompose root node; no switcher, no interactions, etc.
@Composable
fun SquooshRoot(
    docName: String,
    incomingDocId: String,
    rootNodeQuery: NodeQuery,
    customizationContext: CustomizationContext = CustomizationContext(),
    serverParams: DocumentServerParams = DocumentServerParams(),
    liveUpdateMode: LiveUpdateMode = LiveUpdateMode.LIVE,
    designComposeCallbacks: DesignComposeCallbacks? = null,
) {
    val debugStartTime = SystemClock.elapsedRealtime()
    val docId = DocumentSwitcher.getSwitchedDocId(incomingDocId)
    val doc = DocServer.doc(
        docName,
        docId,
        serverParams,
        designComposeCallbacks?.newDocDataCallback,
        liveUpdateMode == LiveUpdateMode.OFFLINE
    )

    if (doc == null) {
        Log.d(TAG, "No doc! $docName / $incomingDocId")
        return
    }

    val interactionState = InteractionStateManager.stateForDoc(docId)

    // We're starting to support animated transitions
    interactionState.supportAnimations = true

    val startFrame = interactionState.rootNode(initialNode = rootNodeQuery, doc = doc, isRoot = true)

    if (startFrame == null) {
        Log.d(TAG, "No start frame $docName / $incomingDocId")
        SquooshLayout.keepJniBits() // XXX: Must call this from somewhere otherwise it gets stripped and the jni lib won't link.
        return
    }
    LaunchedEffect(docId) { designComposeCallbacks?.docReadyCallback?.invoke(docId) }

    // Ensure we get invalidated when the variant memory is updated from an interaction.
    interactionState.squooshVariantMemory(doc)

    // XXX: This may not be needed since density gets packed into the TextMeasureData now
    //      (originally the `measureTextBoundsFunc` in DesignText.kt read the density from
    //      a global).
    val density = LocalDensity.current
    LaunchedEffect(density.density) { LayoutManager.setDensity(density.density) }

    val variantParentName = when (rootNodeQuery) {
        is NodeQuery.NodeVariant -> rootNodeQuery.field1
        else -> ""
    }

    val rootLayoutId = remember(docId) { SquooshLayout.getNextLayoutId() * 100000000 }
    val layoutIdAllocator = remember { SquooshLayoutIdAllocator() }
    val layoutCache = remember { HashMap<Int, Int>() }
    val layoutValueCache = remember { HashMap<Int, Layout>() }

    // We need to remember the previous set of variant properties that we rendered
    // with so we can see if there are any transitions caused by changing variant props.
    val variantTransitions = remember(docId) { SquooshVariantTransition() }
    variantTransitions.treeBuildPhase = TreeBuildPhase.BasePhase
    Log.d(TAG, "Start with ${customizationContext.variantProperties}")

    // Ok, now we have done the dull stuff, we need to build a tree applying
    // the correct variants etc and then build/update the tree. How do we know
    // what's different from last time? Does the Rust side track

    val debugResolveVariantsStartTime = SystemClock.elapsedRealtime()
    val debugStartDuration = debugResolveVariantsStartTime - debugStartTime

    val childComposables: ArrayList<SquooshChildComposable> = arrayListOf()
    var root = resolveVariantsRecursively(
        startFrame,
        rootLayoutId,
        doc,
        customizationContext,
        variantTransitions,
        interactionState,
        null,
        density,
        LocalFontLoader.current,
        childComposables,
        layoutIdAllocator,
        variantParentName
    )

    val debugRemoveOldLayoutNodesStartTime = SystemClock.elapsedRealtime()
    val debugResolveVariantsDuration = debugRemoveOldLayoutNodesStartTime - debugResolveVariantsStartTime

    val removalNodes = layoutIdAllocator.removalNodes()
    for (layoutId in removalNodes) {
        SquooshLayout.removeNode(rootLayoutId, layoutId)
        layoutValueCache.remove(layoutId)
        layoutCache.remove(layoutId)
    }

    val debugBuildLayoutTreeStartTime = SystemClock.elapsedRealtime()
    val debugRemoveOldLayoutNodesDuration = debugBuildLayoutTreeStartTime - debugRemoveOldLayoutNodesStartTime

    val layoutNodes: ArrayList<LayoutNode> = arrayListOf()
    val layoutParentChildren: ArrayList<LayoutParentChildren> = arrayListOf()
    updateLayoutTree(root, layoutCache, layoutNodes, layoutParentChildren)
    val layoutNodeList = LayoutNodeList(layoutNodes, layoutParentChildren)

    val debugPerformLayoutStartTime = SystemClock.elapsedRealtime()
    val debugBuildLayoutTreeDuration = debugPerformLayoutStartTime - debugBuildLayoutTreeStartTime

    val updatedLayouts = SquooshLayout.doLayout(
        root.layoutId,
        layoutNodeList
    )
    val priorLayoutCacheSize = layoutValueCache.size
    layoutValueCache.putAll(updatedLayouts)

    val debugPopulateLayoutStartTime = SystemClock.elapsedRealtime()
    val debugPerformLayoutDuration = debugPopulateLayoutStartTime - debugPerformLayoutStartTime

    populateComputedLayout(root, layoutValueCache)

    val debugTransitionTreeStartTime = SystemClock.elapsedRealtime()
    val debugPopulateComputedLayoutDuration = debugTransitionTreeStartTime - debugPopulateLayoutStartTime

    Log.d(TAG, "$docName remove ${removalNodes.size} nodes from layout value cache")
    Log.d(TAG, "$docName layout invalidated ${updatedLayouts.size} nodes; layout cache size: ${layoutValueCache.size}; prior layout cache size: $priorLayoutCacheSize")
    Log.d(TAG, "$docName init: $debugStartDuration resolveVariants: $debugResolveVariantsDuration ms, buildLayout: $debugBuildLayoutTreeDuration ms, remove old nodes: $debugRemoveOldLayoutNodesDuration ms, eval. layout $debugPerformLayoutDuration ms, populate layout values: $debugPopulateComputedLayoutDuration ms")

    // We can render "root", it's a full tree with layout info.
    //
    // But, if we have any transitions then we need to prepare another tree with all of the target
    // states for the transitions. Then we construct transitions between the states.

    // Get the list of actions that currently need to be animated from the interaction state.
    val animatedActions = interactionState.squooshAnimatedActions(doc)

    // Also get invalidated when an enum animation completes. This is a temp hack.
    val (lastVariantAnimCompleted, setLastVariantAnimCompleted) = remember { mutableIntStateOf(0) }

    // We maintain a list of animations (animation id, info) that we're currently running so
    // that we can just update the key info when recomposing mid-animation.
    val currentAnimations = remember { HashMap<Int, SquooshAnimationRenderingInfo>() }

    // Animation timecodes go into a `MutableState`. This way we can modify them in a coroutine
    // and only invalidate rendering. So animation progression doesn't cause or require
    // recomposition, resulting in a good performance uplift.
    val animationValues: MutableState<Map<Int, Float>> = remember { mutableStateOf(mapOf()) }

    // Get the interaction state with all of the animated actions applied. We use this to generate
    // the tree with all actions applied (which is then used as the "dest" in the animation).
    val transitionedInteractionState = interactionState.clonedWithAnimatedActionsApplied()

    // Track the most recent animation that was addded to the interaction state so that we know
    // to restart the coroutine that updates the animation clock when a new animation is added.
    var lastAnimationId = Int.MAX_VALUE

    if ((transitionedInteractionState != null && animatedActions.isNotEmpty()) || variantTransitions.needsTransitionPhase()) {
        variantTransitions.treeBuildPhase = TreeBuildPhase.TransitionTargetPhase
        Log.d(TAG, "$docName: creating a new root with transitions applied... ${variantTransitions.transitions.size} variant transitions")
        // We need to make a new root with this interaction state applied, and then compute the
        // animation control between the trees.
        childComposables.clear()
        val transitionRoot = resolveVariantsRecursively(
            startFrame,
            rootLayoutId,
            doc,
            customizationContext,
            variantTransitions,
            transitionedInteractionState ?: interactionState,
            null,
            density,
            LocalFontLoader.current,
            childComposables,
            layoutIdAllocator,
            variantParentName
        )
        // Layout maintenance
        val txRemovalNodes = layoutIdAllocator.removalNodes()
        for (layoutId in txRemovalNodes) {
            SquooshLayout.removeNode(rootLayoutId, layoutId)
            layoutValueCache.remove(layoutId)
            layoutCache.remove(layoutId)
        }
        // Build layout tree
        val txLayoutNodes: ArrayList<LayoutNode> = arrayListOf()
        val txLayoutParentChildren: ArrayList<LayoutParentChildren> = arrayListOf()
        updateLayoutTree(transitionRoot, layoutCache, txLayoutNodes, txLayoutParentChildren)
        val txLayoutNodeList = LayoutNodeList(txLayoutNodes, txLayoutParentChildren)
        // Perform layout
        val txUpdatedLayouts = SquooshLayout.doLayout(
            transitionRoot.layoutId,
            txLayoutNodeList
        )
        layoutValueCache.putAll(txUpdatedLayouts)
        // Populate layouts
        populateComputedLayout(transitionRoot, layoutValueCache)

        val debugProcessAnimationsStartTime = SystemClock.elapsedRealtime()
        val debugTransitionTreeDuration = debugProcessAnimationsStartTime - debugTransitionTreeStartTime

        Log.d(TAG, "Creating transition root took $debugTransitionTreeDuration ms")

        val nextAnimations = HashMap<Int, SquooshAnimationRenderingInfo>()
        for (animatedAction in interactionState.animations) { // XXX: in animatedActions?
            lastAnimationId = animatedAction.id
            val animationControl =
                mergeTreesAndCreateSquooshAnimationControl(
                    root, animatedAction.instanceNodeId,
                    transitionRoot, animatedAction.newVariantId
                )

            if (animationControl == null) {
                Log.d(TAG, "Unable to animate ${animatedAction.instanceNodeId} to ${animatedAction.newVariantId}")
                // XXX: Should we just commit the action here with no transition as if the transition
                //      had ended?
                continue
            }
            val animationRenderingInfo = currentAnimations[animatedAction.id]
            if (animationRenderingInfo == null) {
                // If this transition interrupted another one operating on the same element, then
                // sample the position of the interrupted animation.
                // XXX: This won't be correct for more than two states.
                val initialValue: Float =
                    if (animatedAction.interruptedId != null) {
                        // XXX: This is a read of volatile animation state during Composition; this
                        //      means we will do a recompose on the next animation frame. We might
                        //      need a shadow cache of last animation values that Compose isn't
                        //      tracking to avoid this.
                        val interruptedAnimation = animationValues.value[animatedAction.interruptedId]
                        interruptedAnimation ?: 1f
                    } else {
                        1f
                    }
                val animatable = TargetBasedAnimation(
                    animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessVeryLow),
                    typeConverter = Float.VectorConverter,
                    initialValue = 1f - initialValue,
                    targetValue = 1f
                )
                nextAnimations[animatedAction.id] = SquooshAnimationRenderingInfo(
                    control = animationControl,
                    animation = animatable,
                    action = animatedAction,
                    variant = null
                )
            } else {
                // Update the control with one that knows about new nodes.
                animationRenderingInfo.control = animationControl
                nextAnimations[animatedAction.id] = animationRenderingInfo
            }
        }

        // Now do the same for the variant animations
        for (variantTransition in variantTransitions.transitions.values) {
            lastAnimationId = variantTransition.id

            val animationControl =
                mergeTreesAndCreateSquooshAnimationControl(
                    root, variantTransition.fromNodeId,
                    transitionRoot, variantTransition.toNodeId
                )
            if (animationControl == null) {
                Log.e(TAG, "Unable to animate variant transition ${variantTransition.fromNodeId} to ${variantTransition.toNodeId}")
                continue
            }
            val animationRenderingInfo = currentAnimations[variantTransition.id]
            if (animationRenderingInfo == null) {
                val initialValue: Float =
                    if (variantTransition.interruptedId != null) {
                        // XXX: This is a read of volatile animation state during Composition; this
                        //      means we will do a recompose on the next animation frame. We might
                        //      need a shadow cache of last animation values that Compose isn't
                        //      tracking to avoid this.
                        val interruptedAnimation = animationValues.value[variantTransition.interruptedId]
                        Log.e(TAG, "interrupted!! $interruptedAnimation")
                        interruptedAnimation ?: 1f
                    } else {
                        1f
                    }
                val animatable = TargetBasedAnimation(
                    animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessVeryLow),
                    typeConverter = Float.VectorConverter,
                    initialValue = 1f - initialValue,
                    targetValue = 1f
                )
                nextAnimations[variantTransition.id] = SquooshAnimationRenderingInfo(
                    control = animationControl,
                    animation = animatable,
                    action = null,
                    variant = variantTransition
                )
            } else {
                animationRenderingInfo.control = animationControl
                nextAnimations[variantTransition.id] = animationRenderingInfo
            }
        }

        // Make sure we draw from the target root
        root = transitionRoot

        // Evolve the animations list; it would be better to just remove everything that doesn't
        // have a key in transitions...
        currentAnimations.clear()
        currentAnimations.putAll(nextAnimations)

        Log.d(TAG, "Updating animations took ${SystemClock.elapsedRealtime() - debugProcessAnimationsStartTime} ms; resulting in ${nextAnimations.size} animations")
    }

    // The Variant Transitions system needs to know that we've finished doing render stuff and
    // won't be telling it about new nodes.
    variantTransitions.afterRenderPhases()

    LaunchedEffect(lastAnimationId) {
        // While there are transitions to be run, we should run them; we just update the floats
        // in the mutable state. Those are then used by the render function, and we thus avoid
        // needing to recompose in order to propagate the animation state.
        //
        // We also complete transitions in this block, and that action does cause a recomposition
        // via the subscription that SquooshRoot makes to the InteractionState's list of transitions.
        while (interactionState.animations.isNotEmpty() || variantTransitions.transitions.isNotEmpty()) {
            withFrameNanos { frameTimeNanos ->
                val animState = HashMap(animationValues.value)
                for ((id, anim) in currentAnimations) {
                    // If we haven't started this animation yet, then start it now.
                    if (anim.startTimeNanos == 0L) {
                        anim.startTimeNanos = frameTimeNanos
                    }

                    val playTimeNanos = frameTimeNanos - anim.startTimeNanos

                    // Compute where it's meant to be, and update the value in animState.
                    val position =
                        anim.animation.getValueFromNanos(playTimeNanos)
                    animState[id] = position

                    // If the animation is complete, then we need to remove it from the transitions
                    // list, and apply it to the base interaction state.
                    if (anim.animation.isFinishedFromNanos(playTimeNanos)) {
                        animState.remove(id)
                        if (anim.action != null)
                            interactionState.squooshCompleteAnimatedAction(anim.action)
                        if (anim.variant != null) {
                            variantTransitions.completedAnimatedVariant(anim.variant)
                            // Hack
                            setLastVariantAnimCompleted(id)
                        }
                    }
                }
                animationValues.value = animState
            }
        }
    }

    // Select which child to draw using this holder.
    val childRenderSelector = SquooshChildRenderSelector()

    androidx.compose.ui.layout.Layout(
        modifier = Modifier
            .size(
                width = root.computedLayout!!.width.dp,
                height = root.computedLayout!!.height.dp
            )
            .squooshRender(
                root,
                doc,
                docName,
                customizationContext,
                childRenderSelector,
                // Is there a nicer way of passing these two?
                currentAnimations,
                animationValues,
            ),
        measurePolicy = { measurables, constraints ->
            val placeables = measurables.map { measurable ->
                val squooshData = measurable.parentData as? SquooshParentData
                if (squooshData == null || squooshData.node.computedLayout == null) {
                    // Oops! No data, just lay it out however it wants.
                    Pair(measurable.measure(constraints), null)
                } else {
                    // Ok, we can get some layout data. This lets us determine a width
                    // and height from layout. We also need to extract a transform, then
                    // we can position this view appropriately, and create a layer for it
                    // if it has a rotation applied (unfortunately Compose doesn't seem to
                    // accept a full matrix transform, so we can't support shears).
                    val w = (squooshData.node.computedLayout!!.width * density.density).roundToInt()
                    val h = (squooshData.node.computedLayout!!.height * density.density).roundToInt()

                    Pair(measurable.measure(Constraints(
                        minWidth = w,
                        maxWidth = w,
                        minHeight = h,
                        maxHeight = h
                    )), squooshData.node)
                }
            }

            layout(constraints.maxWidth, constraints.maxHeight) {
                // Place children in the parent layout
                placeables.forEach { (placeable, node) ->
                    // If we don't have a node, then just place this and finish.
                    if (node == null) {
                        placeable.placeRelative(x = 0, y = 0)
                    } else {
                        // Ok, we can look up the position and transform by iterating over the
                        // parents. We don't support transforms here yet. Child composables will
                        // be rendered with transforms, but won't use them for input.
                        //
                        // We always take the offset from the root, but if there are layers of
                        // custom composables (containing each other) then this will give the
                        // wrong offset.
                        //
                        // XXX XXX: Create ticket to implement transformed input.
                        val offsetFromRoot = node.offsetFromAncestor()

                        placeable.placeRelative(
                            x = (offsetFromRoot.x * density.density).roundToInt(),
                            y = (offsetFromRoot.y * density.density).roundToInt()
                        )
                    }
                }
            }
        },
        content = {
            // Now render all of the children
            val debugRenderChildComposablesStartTime = SystemClock.elapsedRealtime()
            for (child in childComposables) {
                if (child.node.computedLayout == null) {
                    continue
                }
                var composableChildModifier = Modifier
                    .drawWithContent {
                        if (child.node == childRenderSelector.selectedRenderChild)
                            drawContent()
                    }
                    .then(SquooshParentData(node = child.node))

                if (child.component == null && child.content == null) {
                    composableChildModifier = composableChildModifier.squooshInteraction(
                        doc,
                        interactionState,
                        customizationContext,
                        child
                    )
                }

                Box(modifier = composableChildModifier) {
                    if (child.component != null) {
                        child.component.invoke(object : ComponentReplacementContext {
                            override val appearanceModifier: Modifier = Modifier
                            override val layoutModifier: Modifier = Modifier

                            @Composable
                            override fun Content() {
                            }

                            override val textStyle: TextStyle? = null
                            override val parentLayout: ParentLayoutInfo? = null
                        })
                    } else if (child.content != null) {
                        Log.d(TAG, "Unimplemented: child.content")
                    }
                }
            }
            Log.d(TAG, "$docName generate child composables took ${SystemClock.elapsedRealtime() - debugRenderChildComposablesStartTime}ms")
        }
    )
}
