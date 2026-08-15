package com.kingzcheung.xime.relationship

import android.content.Context
import com.kingzcheung.xime.relationship.network.RelationshipAiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object RelationshipFeedbackManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun record(
        context: Context,
        personId: String,
        requestId: String,
        candidateId: String,
        style: String,
        positive: Boolean,
    ) {
        ReplyPreferenceStore.record(context, personId, style, positive)
        scope.launch {
            RelationshipAiClient.instance.sendFeedback(
                requestId = requestId,
                candidateId = candidateId,
                action = if (positive) "SELECTED" else "DISLIKED",
            )
        }
    }
}
