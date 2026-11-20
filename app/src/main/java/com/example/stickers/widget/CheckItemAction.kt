package com.example.stickers.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.example.stickers.appContainer

/**
 * Widget tick (DESIGN.md §5.2): the checklist item is deleted permanently, no undo.
 * The repository refreshes the widget afterwards. Ticking an item that is already gone
 * (fast double tap, stale widget) is a no-op.
 */
class CheckItemAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val blockId = parameters[BlockIdKey] ?: return
        context.appContainer.repository.deleteBlock(blockId)
    }

    companion object {
        val BlockIdKey = ActionParameters.Key<Long>("blockId")

        fun forBlock(blockId: Long) =
            actionRunCallback<CheckItemAction>(actionParametersOf(BlockIdKey to blockId))
    }
}
